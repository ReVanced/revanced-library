package app.revanced.library

import android.content.res.AssetManager
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
     * Matches the logic in [SERVICE_SH_TEMPLATE].
     */
    fun mount(packageName: String, sourceDir: String) {
        // Induction check: verify if already mounted
        val checkMount = Shell.getShell().newJob().add("mount | grep -q \"$sourceDir\"").exec()
        if (checkMount.isSuccess) return

        // Induction check: verify if app is already running from system (e.g. Magisk overlay active)
        val checkSystem = Shell.getShell().newJob().add("pm path \"$packageName\" | grep -q \"^package:/system/\"").exec()
        if (checkSystem.isSuccess) return

        val sanitizedPackageName = packageName.replace('.', '_')
        val modulePath = "$MODULES_PATH/revanced_$sanitizedPackageName"
        val fallbackModulePath = "$MODULES_PATH/$packageName-revanced"

        // Automatic detection of APK path (Magisk Induction vs Standard Root)
        val patchedApkCandidates = listOf(
            "$modulePath/system/app/$sanitizedPackageName/base.apk",
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
        remoteFS.getFile("$MODULES_PATH/$packageName-revanced").deleteRecursively()
            .also { if (!it) throw Exception("Failed to delete files") }
    }

    fun uninstallMagiskModule(packageName: String, remoteFS: FileSystemManager) {
        val sanitizedPackageName = packageName.replace('.', '_')
        remoteFS.getFile("$MODULES_PATH/revanced_$sanitizedPackageName").deleteRecursively()
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
        assets: AssetManager,
        packageName: String,
        version: String,
        label: String,
        patchedApk: File
    ) {
        val sanitizedPackageName = packageName.replace('.', '_')
        val modulePath = "$MODULES_PATH/revanced_$sanitizedPackageName"
        val systemAppPath = "$modulePath/system/app/$sanitizedPackageName"

        Shell.getShell().newJob()
            .add("mkdir -p \"$systemAppPath\"")
            .exec()
            .assertSuccess("Failed to create system app directory")

        val moduleProp = buildString {
            appendLine("id=revanced_$sanitizedPackageName")
            appendLine("name=$label ReVanced")
            appendLine("version=$version")
            appendLine("versionCode=1")
            appendLine("author=ReVanced")
            append("description=Patched by ReVanced")
        }
        remoteFS.getFile("$modulePath/module.prop").newOutputStream().use { it.write(moduleProp.toByteArray()) }

        assets.open("root/service.sh").use { inputStream ->
            remoteFS.getFile("$modulePath/service.sh").newOutputStream().use { outputStream ->
                val content = String(inputStream.readBytes())
                    .replace("__PKG_NAME__", packageName)
                    .replace("__VERSION__", version)
                    .replace("__LABEL__", label)
                    .toByteArray()
                outputStream.write(content)
            }
        }

        val targetApkPath = "$systemAppPath/base.apk"
        remoteFS.getFile(patchedApk.absolutePath)
            .also { if (!it.exists()) throw Exception("File doesn't exist") }
            .newInputStream().use { inputStream ->
                remoteFS.getFile(targetApkPath).newOutputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

        extractNativeLibraries(patchedApk, systemAppPath, remoteFS)

        Shell.getShell().newJob()
            .add("chmod 644 \"$targetApkPath\"")
            .add("chmod 755 \"$systemAppPath\"")
            .add("chmod -R 755 \"$systemAppPath/lib\"")
            .add("find \"$systemAppPath/lib\" -type f -name \"*.so\" -exec chmod 644 {} +")
            .add("chown -R system:system \"$modulePath/system\"")
            .add("chcon -R u:object_r:system_file:s0 \"$modulePath/system\"")
            .add("chmod +x \"$modulePath/service.sh\"")
            .exec()
            .assertSuccess("Failed to set file permissions")
    }

    fun provisionRootFolder(
        remoteFS: FileSystemManager,
        assets: AssetManager,
        packageName: String,
        version: String,
        label: String,
        patchedApk: File
    ) {
        val modulePath = "$MODULES_PATH/$packageName-revanced"
        remoteFS.getFile(modulePath).apply {
            if (!mkdirs() && !exists()) {
                throw Exception("Failed to create module directory")
            }
        }

        listOf(
            "service.sh",
            "module.prop",
        ).forEach { file ->
            assets.open("root/$file").use { inputStream ->
                remoteFS.getFile("$modulePath/$file").newOutputStream().use { outputStream ->
                    val content = String(inputStream.readBytes())
                        .replace("__PKG_NAME__", packageName)
                        .replace("__VERSION__", version)
                        .replace("__LABEL__", label)
                        .toByteArray()
                    outputStream.write(content)
                }
            }
        }

        val apkPath = "$modulePath/$packageName.apk"
        remoteFS.getFile(patchedApk.absolutePath)
            .also { if (!it.exists()) throw Exception("File doesn't exist") }
            .newInputStream().use { inputStream ->
                remoteFS.getFile(apkPath).newOutputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

        Shell.getShell().newJob()
            .add("chmod 644 \"$apkPath\"")
            .add("chown system:system \"$apkPath\"")
            .add("chcon u:object_r:apk_data_file:s0 \"$apkPath\"")
            .add("chmod +x \"$modulePath/service.sh\"")
            .exec()
            .assertSuccess("Failed to set file permissions")
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
