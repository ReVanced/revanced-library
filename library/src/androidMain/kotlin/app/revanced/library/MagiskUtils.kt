package app.revanced.library

import app.revanced.library.installation.installer.Constants
import app.revanced.library.installation.installer.Constants.invoke
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.nio.FileSystemManager
import java.io.File

object MagiskUtils {
    const val MODULES_PATH = "/data/adb/modules"

    // Android user (0 for primary, 10+ for secondary/work profiles) via pure public API.
    private val currentUserId = android.os.Process.myUid() / 100000

    /*
    Shell.isAppGrantedRoot() queries libsu's internal state.
    It returns false until a root shell has actually been built and verified by libsu.
    */
    fun hasRootAccess() = Shell.isAppGrantedRoot() ?: false

    /*
    Checks whether su exists in PATH. Pure filesystem check, no shell needed.
    Returns true as soon as APatch makes su available, regardless of whether your app was granted root.
    */
    fun isDeviceRooted() =
        System.getenv("PATH")?.split(":")?.any { path -> File(path, "su").canExecute() } ?: false

    /*
    Shell.getShell().isRoot forces a shell to be built (or returns the cached one) and checks if it came up as root.
    This is the only reliable "root is usable right now" check.
     */
    fun isMagiskInstalled() = Shell.getShell().isRoot

    /*
    Returns true if root was granted, false if denied.
    Must be called on a background thread.
     */
    fun requestRoot(): Boolean {
        Shell.getCachedShell()?.takeIf { !it.isRoot }?.close()
        return Shell.getShell().isRoot
    }

    fun isInstalled(packageName: String, remoteFS: FileSystemManager) =
        remoteFS.getFile("$MODULES_PATH/$packageName-revanced").exists()

    fun isInstalledAsMagiskModule(packageName: String, remoteFS: FileSystemManager) =
        remoteFS.getFile("$MODULES_PATH/revanced_${packageName.replace('.', '_')}").exists()

    /*
    Bind-mounts the patched APK over the stock APK path using the Magisk mirror when
    available, ensuring the mount is visible across all process namespaces (required on
    Zygisk/MIUI).
    */
    fun mount(packageName: String, sourceDir: String) {
        if (Shell.getShell().newJob().add("mount | grep -q \"$sourceDir\"").exec().isSuccess) {
            unmount(sourceDir)
        }

        val patchedApkPath = Constants.MOUNTED_APK_PATH(packageName)
        val fallbackPath = "$MODULES_PATH/$packageName-revanced/$packageName.apk"

        val patchedApk = when {
            Shell.getShell().newJob().add("[ -f \"$patchedApkPath\" ]").exec().isSuccess -> patchedApkPath
            Shell.getShell().newJob().add("[ -f \"$fallbackPath\" ]").exec().isSuccess -> fallbackPath
            else -> throw ShellCommandException("Patched APK not found for $packageName", -1, emptyList(), emptyList())
        }

        Shell.getShell().newJob().add($$"""
            MIRROR=""
            if command -v magisk >/dev/null 2>&1; then
                if ! MAGISKTMP=$(magisk --path 2>/dev/null); then MAGISKTMP=/sbin; fi
                MIRROR=${MAGISKTMP}/.magisk/mirror
                [ -d "${MIRROR}" ] || MIRROR=""
            fi
            mount -o bind "${MIRROR}$$patchedApk" "$$sourceDir"
        """.trimIndent()).exec().assertSuccess("Failed to mount APK")
    }

    fun unmount(sourceDir: String) {
        // Skip if not mounted
        val checkMount = Shell.getShell().newJob().add("mount | grep -q \"$sourceDir\"").exec()
        if (!checkMount.isSuccess) return

        Shell.getShell().newJob()
            .add("umount -l \"$sourceDir\"")
            .exec()
            .assertSuccess("Failed to unmount APK")
    }

    fun uninstall(packageName: String, remoteFS: FileSystemManager) {
        val unifiedPath = Constants.MOUNTED_APK_PATH(packageName).substringBeforeLast("/")
        remoteFS.getFile(unifiedPath).deleteRecursively()
        
        remoteFS.getFile("$MODULES_PATH/$packageName-revanced").deleteRecursively()
            .also { if (!it) throw Exception("Failed to delete files") }
    }

    fun uninstallMagiskModule(packageName: String, patchedPackageName: String, remoteFS: FileSystemManager) {
        val unifiedPath = Constants.MOUNTED_APK_PATH(packageName).substringBeforeLast("/")
        remoteFS.getFile(unifiedPath).deleteRecursively()

        val formattedPackageName = packageName.replace('.', '_')
        val handleDisabledScriptPath = Constants.HANDLE_DISABLED_SCRIPT_PATH(formattedPackageName)

        Shell.getShell().newJob()
            .add("pm uninstall \"$patchedPackageName\"")
            .add("rm -f \"$handleDisabledScriptPath\"")
            .exec()

        remoteFS.getFile("$MODULES_PATH/revanced_$formattedPackageName").deleteRecursively()
            .also { if (!it) throw Exception("Failed to delete Magisk module files") }
    }

    /*
    * When bind-mounting a patched APK over the unpatched/stock APK, Android's PM
    * does not re-extract native libraries - it reuses whatever it already extracted from
    * the unpatched APK at install time. If the patched APK introduces a new .so file that was
    * never in the stock APK (i.e. libelements.so added by a Rev YT patch), PM's
    * lib directory will never contain it, causing a crash at runtime:
    * > UnsatisfiedLinkError: dlopen failed: library "libelements.so" not found
    *
    * This function addresses that by manually unpacking every .so from the patched APK into
    * the app's target lib directory, fixing that issue
    *
    * This was commented out because:
    * 1. Not needed for the Magisk module install path - service.sh calls pm install, so PM
    *    installs the patched APK fresh and extracts all native libs automatically
    *
    * 2. This logic would need to be moved since it belongs in RootInstaller.install()
    *
    * 3. Some potential issue is that writing to [system app's path]/lib would fail on read-only
    *    system partition (/system/app, /product/app, etc)
    *    Right now it's assuming it can write to it - Wrong!
    *
    * Considering the points above, if the bind-mount path needs to support patches
    * that introduce new native libraries, this could very well be used
    *
    * fun extractNativeLibraries(apkFile: File, systemAppPath: String, remoteFS: FileSystemManager) {
    *     val libPath = "$systemAppPath/lib"
    *     remoteFS.getFile(libPath).apply {
    *         if (exists()) deleteRecursively()
    *         mkdirs()
    *     }
    *
    *     ZipFile(apkFile).use { zip ->
    *         zip.entries().asSequence()
    *             .filter { it.name.startsWith("lib/") && it.name.endsWith(".so") }
    *             .forEach { entry ->
    *                 val parts = entry.name.split("/")
    *                 if (parts.size < 3) return@forEach
    *
    *                 val apkAbi = parts[1]
    *                 val libName = parts.last()
    *                 val systemAbi = when (apkAbi) {
    *                     "arm64-v8a" -> "arm64"
    *                     "armeabi-v7a" -> "arm"
    *                     "x86_64" -> "x86_64"
    *                     "x86" -> "x86"
    *                     else -> apkAbi
    *                 }
    *
    *                 val targetDir = "$libPath/$systemAbi"
    *                 remoteFS.getFile(targetDir).apply { if (!exists()) mkdirs() }
    *
    *                 val targetFile = "$targetDir/$libName"
    *                 zip.getInputStream(entry).use { inputStream ->
    *                     remoteFS.getFile(targetFile).newOutputStream().use { outputStream ->
    *                         inputStream.copyTo(outputStream)
    *                     }
    *                 }
    *             }
    *     }
    * }
    */

    fun installApk(apkPath: String) =
        Shell.getShell().newJob()
            .add("pm install -r -d --user $currentUserId \"$apkPath\"")
            .exec()
            .assertSuccess("Failed to install APK: $apkPath")

    fun uninstallKeepData(packageName: String) =
        Shell.getShell().newJob()
            .add("pm uninstall -k \"$packageName\"")
            .exec()

    fun prepareMagiskModule(
        remoteFS: FileSystemManager,
        packageName: String,
        patchedPackageName: String,
        patchedApk: File
    ) {
        val formattedPackageName = packageName.replace('.', '_')
        val modulePath = "$MODULES_PATH/revanced_$formattedPackageName"
        val unifiedApkPath = Constants.MOUNTED_APK_PATH(packageName)
        val handleDisabledScriptPath = Constants.HANDLE_DISABLED_SCRIPT_PATH(formattedPackageName)

        // Ensure directories exist
        val unifiedDir = unifiedApkPath.substringBeforeLast("/")
        Shell.getShell().newJob()
            .add("mkdir -p \"$modulePath\"")
            .add("mkdir -p \"$unifiedDir\"")
            .exec()
            .assertSuccess("Failed to create module directories")

        writeModuleFiles(remoteFS, modulePath, packageName, patchedPackageName)

        // Handle-disabled script: uninstalls the patched app when the module is disabled or removed.
        val handleDisabledSh = Constants.HANDLE_DISABLED_SCRIPT
            .replace("__PATCHED_PKG__", patchedPackageName)
            .replace("__FORMATTED_PKG__", formattedPackageName)
            .replace("__USER_ID__", currentUserId.toString())
        remoteFS.getFile(handleDisabledScriptPath).newOutputStream().use { it.write(handleDisabledSh.toByteArray()) }

        // Source of truth APK
        copyApk(remoteFS, patchedApk, unifiedApkPath)

        // Set permissions
        Shell.getShell().newJob()
            .add("chmod 644 \"$unifiedApkPath\"")
            .add("chown system:system \"$unifiedApkPath\"")
            .add("chcon u:object_r:apk_data_file:s0 \"$unifiedApkPath\"")
            .add("chmod +x \"$modulePath/service.sh\"")
            .add("chmod +x \"$modulePath/uninstall.sh\"")
            .add("chmod +x \"$handleDisabledScriptPath\"")
            .exec()
            .assertSuccess("Failed to set file permissions")
    }

    fun prepareRootFolder(
        remoteFS: FileSystemManager,
        packageName: String,
        patchedApk: File
    ) {
        val modulePath = "$MODULES_PATH/$packageName-revanced"
        val unifiedApkPath = Constants.MOUNTED_APK_PATH(packageName)

        // Ensure directories exist
        val unifiedDir = unifiedApkPath.substringBeforeLast("/")
        Shell.getShell().newJob()
            .add("mkdir -p \"$modulePath\"")
            .add("mkdir -p \"$unifiedDir\"")
            .exec()
            .assertSuccess("Failed to create module directories")

        // MOUNT type: patched package name == original package name (bind-mount, no rename)
        writeModuleFiles(remoteFS, modulePath, packageName, packageName)

        // Source of truth APK
        copyApk(remoteFS, patchedApk, unifiedApkPath)

        Shell.getShell().newJob()
            .add("chmod 644 \"$unifiedApkPath\"")
            .add("chown system:system \"$unifiedApkPath\"")
            .add("chcon u:object_r:apk_data_file:s0 \"$unifiedApkPath\"")
            .add("chmod +x \"$modulePath/service.sh\"")
            .add("chmod +x \"$modulePath/uninstall.sh\"")
            .exec()
            .assertSuccess("Failed to set file permissions")
    }

    private fun writeModuleFiles(
        remoteFS: FileSystemManager,
        modulePath: String,
        packageName: String,
        patchedPackageName: String,
    ) {
        val formattedPackageName = packageName.replace('.', '_')

        val moduleProp = Constants.MODULE_PROP
            .replace("__FORMATTED_PKG__", formattedPackageName)
            .replace("__PKG_NAME__", packageName)
        remoteFS.getFile("$modulePath/module.prop").newOutputStream().use { it.write(moduleProp.toByteArray()) }

        val serviceSh = Constants.MODULE_SERVICE_SCRIPT
            .replace("__PKG_NAME__", packageName)
            .replace("__PATCHED_PKG__", patchedPackageName)
            .replace("__USER_ID__", currentUserId.toString())
        remoteFS.getFile("$modulePath/service.sh").newOutputStream().use { it.write(serviceSh.toByteArray()) }

        val uninstallSh = Constants.MODULE_UNINSTALL_SCRIPT
            .replace("__PKG_NAME__", packageName)
            .replace("__PATCHED_PKG__", patchedPackageName)
            .replace("__FORMATTED_PKG__", formattedPackageName)
        remoteFS.getFile("$modulePath/uninstall.sh").newOutputStream().use { it.write(uninstallSh.toByteArray()) }
    }

    private fun copyApk(remoteFS: FileSystemManager, source: File, destination: String) {
        remoteFS.getFile(source.absolutePath)
            .also { if (!it.exists()) throw Exception("Source APK file doesn't exist: ${source.absolutePath}") }
            .newInputStream().use { inputStream ->
                remoteFS.getFile(destination).newOutputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
    }

    private fun Shell.Result.assertSuccess(errorMessage: String) {
        if (!isSuccess) {
            throw ShellCommandException(errorMessage, code, out, err)
        }
    }
}

class ShellCommandException(
    val userMessage: String,
    val exitCode: Int,
    val stdout: List<String>,
    val stderr: List<String>
) : Exception(format(userMessage, exitCode, stdout, stderr)) {
    companion object {
        private fun format(
            message: String,
            exitCode: Int,
            stdout: List<String>,
            stderr: List<String>
        ): String = buildString {
            appendLine(message)
            appendLine("Exit code: $exitCode")

            val output = stdout.filter { it.isNotBlank() }
            val errors = stderr.filter { it.isNotBlank() }

            if (output.isNotEmpty()) {
                appendLine("stdout:")
                output.forEach(::appendLine)
            }
            if (errors.isNotEmpty()) {
                appendLine("stderr:")
                errors.forEach(::appendLine)
            }
        }
    }
}
