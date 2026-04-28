package app.revanced.library.installation.installer

import app.revanced.library.installation.command.ShellCommandRunner
import app.revanced.library.installation.installer.Constants.DELETE
import app.revanced.library.installation.installer.Constants.EXISTS
import app.revanced.library.installation.installer.Constants.INSTALLED_APK_PATH
import app.revanced.library.installation.installer.Constants.PREPARE_MOUNT_SCRIPT
import app.revanced.library.installation.installer.Constants.KILL
import app.revanced.library.installation.installer.Constants.MOUNTED_APK_PATH
import app.revanced.library.installation.installer.Constants.MOUNT_GREP
import app.revanced.library.installation.installer.Constants.PREPARE_APK
import app.revanced.library.installation.installer.Constants.MOUNT_SCRIPT
import app.revanced.library.installation.installer.Constants.MOUNT_SCRIPT_PATH
import app.revanced.library.installation.installer.Constants.RESTART
import app.revanced.library.installation.installer.Constants.TMP_FILE_PATH
import app.revanced.library.installation.installer.Constants.UMOUNT
import app.revanced.library.installation.installer.Constants.invoke
import java.io.File

/**
 * [RootInstaller] for installing and uninstalling [Apk] files using root permissions by mounting.
 *
 * @param shellCommandRunner The [ShellCommandRunner] to use.
 *
 * @throws NoRootPermissionException If the device does not have root permission.
 */
@Suppress("MemberVisibilityCanBePrivate")
abstract class RootInstaller internal constructor(
    protected val shellCommandRunner: ShellCommandRunner,
) : Installer<RootInstallerResult, RootInstallation>() {

    init {
        if (!shellCommandRunner.hasRootPermission()) throw NoRootPermissionException()
    }

    /**
     * Prepares the APK from tmp file path "[TMP_FILE_PATH]" by saving it to a
     * persistent location and applying permissions/SELinux context.
     */
    protected fun prepareApk(packageName: String) = PREPARE_APK(packageName)().waitFor()

    /**
     * Installs the given [apk] by mounting.
     *
     * @param apk The [Apk] to install.
     *
     * @throws PackageNameRequiredException If the [Apk] does not have a package name.
     */
    override suspend fun install(apk: Apk): RootInstallerResult {
        logger.info("Installing ${apk.packageName} by mounting")

        val packageName = apk.packageName?.also { it.assertInstalled() } ?: throw PackageNameRequiredException()

        // Setup files.
        apk.file.move(TMP_FILE_PATH)
        prepareApk(packageName)

        // Install and run.
        TMP_FILE_PATH.write(MOUNT_SCRIPT(packageName))
        PREPARE_MOUNT_SCRIPT(packageName)().waitFor()
        MOUNT_SCRIPT_PATH(packageName)().waitFor()
        RESTART(packageName)()

        DELETE(TMP_FILE_PATH)()

        return RootInstallerResult.SUCCESS
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
    
    override suspend fun uninstall(packageName: String): RootInstallerResult {
        logger.info("Uninstalling $packageName by unmounting")

        UMOUNT(packageName)()

        DELETE(MOUNTED_APK_PATH(packageName))()
        DELETE(MOUNT_SCRIPT_PATH(packageName))()
        DELETE(TMP_FILE_PATH)() // Remove residual.

        KILL(packageName)()

        return RootInstallerResult.SUCCESS
    }

    override suspend fun getInstallation(packageName: String): RootInstallation? {
        val patchedApkPath = MOUNTED_APK_PATH(packageName).takeIf { EXISTS(it)().exitCode == 0 } ?: return null

        return RootInstallation(
            INSTALLED_APK_PATH(packageName)().output.ifEmpty { null },
            patchedApkPath,
            MOUNT_GREP(patchedApkPath)().exitCode == 0,
        )
    }

    /**
     * Runs a command on the device.
     */
    protected operator fun String.invoke() = shellCommandRunner(this)

    /**
     * Moves the given file to the given [targetFilePath].
     *
     * @param targetFilePath The target file path.
     */
    protected fun File.move(targetFilePath: String) = shellCommandRunner.move(this, targetFilePath)

    /**
     * Writes the given [content] to the file.
     *
     * @param content The content of the file.
     */
    protected fun String.write(content: String) = shellCommandRunner.write(content.byteInputStream(), this)

    /**
     * Asserts that the package is installed.
     *
     * @throws FailedToFindInstalledPackageException If the package is not installed.
     */
    protected fun String.assertInstalled() {
        if (INSTALLED_APK_PATH(this)().output.isEmpty()) {
            throw FailedToFindInstalledPackageException(this)
        }
    }

    internal class FailedToFindInstalledPackageException internal constructor(packageName: String) : Exception("Failed to resolve installed APK path for package \"$packageName\"")

    internal class PackageNameRequiredException internal constructor() : Exception("Package name is required")
    internal class NoRootPermissionException internal constructor() : Exception("No root permission")
}
