package app.revanced.library

import app.revanced.library.installation.installer.Constants
import app.revanced.library.installation.installer.Constants.invoke
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.nio.FileSystemManager
import java.io.File
import java.util.zip.ZipFile

object MagiskUtils {
    const val MODULES_PATH = "/data/adb/modules"

    fun hasRootAccess() = Shell.isAppGrantedRoot() ?: false

    fun isDeviceRooted() =
        System.getenv("PATH")?.split(":")?.any { path -> File(path, "su").canExecute() } ?: false

    fun isInstalled(packageName: String, remoteFS: FileSystemManager) =
        remoteFS.getFile("$MODULES_PATH/$packageName-revanced").exists()

    fun isInstalledAsMagiskModule(packageName: String, remoteFS: FileSystemManager) =
        remoteFS.getFile("$MODULES_PATH/revanced_${packageName.replace('.', '_')}").exists()

    /**
     * Bind-mounts the patched APK over the stock APK path.
     * Matches the logic in the induction service script.
     */
    fun mount(packageName: String, sourceDir: String) {
        // Induction check: verify if already mounted, if so unmount to ensure clean remount
        val checkMount = Shell.getShell().newJob().add("mount | grep -q \"$sourceDir\"").exec()
        if (checkMount.isSuccess) unmount(sourceDir)

        // Induction check: verify if app is already running from system (e.g. Magisk overlay active)
        Shell.getShell().newJob().add("pm path \"$packageName\" | grep -q \"^package:/system/\"").exec()
        // Proceed with mount even if already in system partition

        val formattedPackageName = packageName.replace('.', '_')
        val modulePath = "$MODULES_PATH/revanced_$formattedPackageName"
        val fallbackModulePath = "$MODULES_PATH/$packageName-revanced"

        // Automatic detection of APK path (Unified Path vs Magisk Induction vs Legacy Root)
        val patchedApkCandidates = listOf(
            Constants.MOUNTED_APK_PATH(packageName),
            "$modulePath/system/app/$formattedPackageName/base.apk",
            "$fallbackModulePath/$packageName.apk"
        )

        val patchedApk = patchedApkCandidates.firstOrNull { path ->
            Shell.getShell().newJob().add("[ -f \"$path\" ]").exec().isSuccess
        } ?: throw Exception("Patch APK not found for $packageName")

        Shell.getShell().newJob()
            .add("mount -o bind \"$patchedApk\" \"$sourceDir\"")
            .exec()
            .assertSuccess("Failed to mount APK")
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

    fun uninstallMagiskModule(packageName: String, remoteFS: FileSystemManager) {
        val unifiedPath = Constants.MOUNTED_APK_PATH(packageName).substringBeforeLast("/")
        remoteFS.getFile(unifiedPath).deleteRecursively()

        val formattedPackageName = packageName.replace('.', '_')
        remoteFS.getFile("$MODULES_PATH/revanced_$formattedPackageName").deleteRecursively()
            .also { if (!it) throw Exception("Failed to delete Magisk module files") }
    }

    fun extractNativeLibraries(apkFile: File, systemAppPath: String, remoteFS: FileSystemManager) {
        val libPath = "$systemAppPath/lib"
        remoteFS.getFile(libPath).apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }

        ZipFile(apkFile).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.startsWith("lib/") && it.name.endsWith(".so") }
                .forEach { entry ->
                    val parts = entry.name.split("/")
                    if (parts.size < 3) return@forEach

                    val apkAbi = parts[1]
                    val libName = parts.last()
                    val systemAbi = when (apkAbi) {
                        "arm64-v8a" -> "arm64"
                        "armeabi-v7a" -> "arm"
                        "x86_64" -> "x86_64"
                        "x86" -> "x86"
                        else -> apkAbi
                    }

                    val targetDir = "$libPath/$systemAbi"
                    remoteFS.getFile(targetDir).apply { if (!exists()) mkdirs() }

                    val targetFile = "$targetDir/$libName"
                    zip.getInputStream(entry).use { inputStream ->
                        remoteFS.getFile(targetFile).newOutputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
        }
    }

    fun provisionMagiskModule(
        remoteFS: FileSystemManager,
        packageName: String,
        version: String,
        label: String,
        patchedApk: File
    ) {
        val formattedPackageName = packageName.replace('.', '_')
        val modulePath = "$MODULES_PATH/revanced_$formattedPackageName"
        val unifiedApkPath = Constants.MOUNTED_APK_PATH(packageName)

        // Ensure directories exist
        val unifiedDir = unifiedApkPath.substringBeforeLast("/")
        Shell.getShell().newJob()
            .add("mkdir -p \"$modulePath\"")
            .add("mkdir -p \"$unifiedDir\"")
            .exec()
            .assertSuccess("Failed to create induction directories")

        writeInductionFiles(remoteFS, modulePath, packageName, version, label)

        // Source of truth APK
        copyApk(remoteFS, patchedApk, unifiedApkPath)

        // Set permissions for unified path
        Shell.getShell().newJob()
            .add("chmod 644 \"$unifiedApkPath\"")
            .add("chown system:system \"$unifiedApkPath\"")
            .add("chcon u:object_r:apk_data_file:s0 \"$unifiedApkPath\"")
            .add("chmod +x \"$modulePath/service.sh\"")
            .exec()
            .assertSuccess("Failed to set file permissions")
    }

    fun provisionRootFolder(
        remoteFS: FileSystemManager,
        packageName: String,
        version: String,
        label: String,
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
            .assertSuccess("Failed to create induction directories")

        writeInductionFiles(remoteFS, modulePath, packageName, version, label)

        // Source of truth APK
        copyApk(remoteFS, patchedApk, unifiedApkPath)

        Shell.getShell().newJob()
            .add("chmod 644 \"$unifiedApkPath\"")
            .add("chown system:system \"$unifiedApkPath\"")
            .add("chcon u:object_r:apk_data_file:s0 \"$unifiedApkPath\"")
            .add("chmod +x \"$modulePath/service.sh\"")
            .exec()
            .assertSuccess("Failed to set file permissions")
    }

    private fun writeInductionFiles(
        remoteFS: FileSystemManager,
        modulePath: String,
        packageName: String,
        version: String,
        label: String
    ) {
        val moduleProp = Constants.MAGISK_MODULE_PROP
            .replace("__PKG_NAME__", packageName)
            .replace("__VERSION__", version)
            .replace("__LABEL__", label)
        remoteFS.getFile("$modulePath/module.prop").newOutputStream().use { it.write(moduleProp.toByteArray()) }

        val serviceSh = Constants.INDUCTION_SERVICE_SCRIPT
            .replace("__PKG_NAME__", packageName)
            .replace("__VERSION__", version)
            .replace("__LABEL__", label)
        remoteFS.getFile("$modulePath/service.sh").newOutputStream().use { it.write(serviceSh.toByteArray()) }
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
