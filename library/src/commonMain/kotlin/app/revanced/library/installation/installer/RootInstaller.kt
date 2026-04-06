package app.revanced.library.installation.installer

import app.revanced.library.installation.command.ShellCommandRunner
import app.revanced.library.installation.installer.Constants.CREATE_INSTALLATION_PATH
import app.revanced.library.installation.installer.Constants.DELETE
import app.revanced.library.installation.installer.Constants.EXISTS
import app.revanced.library.installation.installer.Constants.GET_INSTALLED_VERSION_CODE
import app.revanced.library.installation.installer.Constants.INSTALLED_APK_PATH
import app.revanced.library.installation.installer.Constants.INSTALL_MOUNT_SCRIPT
import app.revanced.library.installation.installer.Constants.INSTALL_STOCK_APK
import app.revanced.library.installation.installer.Constants.KILL
import app.revanced.library.installation.installer.Constants.MOUNTED_APK_PATH
import app.revanced.library.installation.installer.Constants.MOUNT_APK
import app.revanced.library.installation.installer.Constants.MOUNT_GREP
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
 * @param shellCommandRunnerSupplier A supplier for the [ShellCommandRunner] to use.
 *
 * @throws NoRootPermissionException If the device does not have root permission.
 */
@Suppress("MemberVisibilityCanBePrivate")
abstract class RootInstaller internal constructor(
    shellCommandRunnerSupplier: (RootInstaller) -> ShellCommandRunner,
) : Installer<RootInstallerResult, RootInstallation, RootInstallOptions>() {

    /**
     * The command runner used to run commands on the device.
     */
    @Suppress("LeakingThis")
    protected val shellCommandRunner = shellCommandRunnerSupplier(this)

    init {
        if (!shellCommandRunner.hasRootPermission()) throw NoRootPermissionException()
    }

    /**
     * Installs the given APK by mounting it over a stock installation.
     *
     * The stock APK is used to ensure a valid base for mounting. If the currently
     * installed version differs from the expected stock version, the stock APK is
     * installed first. If the installed version is newer than the expected version,
     * reinstallation may be required.
     *
     * @param options The install options containing the patched APK to mount and the
     * stock APK to use as the installation base.
     *
     * @throws PackageDowngradeRequiredException If the installed app version is
     * newer than the expected stock version.
     */
    override suspend fun install(options: RootInstallOptions): RootInstallerResult {
        val patchedApk = options.patchedApk
        val stockApk = options.stockApk
        val packageName = stockApk.packageName
        val expectedVersionCode = stockApk.versionCode
        logger.info("Installing $packageName by mounting")

        // Ensure the installed base app matches the stock APK version.
        val installedVersionCode = getInstalledVersionCode(packageName)

        if (installedVersionCode != null && installedVersionCode > expectedVersionCode) {
            throw PackageDowngradeRequiredException(packageName)
        }

        if (installedVersionCode != expectedVersionCode) {
            logger.info("Installing stock APK for $packageName")
            INSTALL_STOCK_APK(stockApk.file.absolutePath)().waitFor()
        }
        packageName.assertInstalled()

        // Setup files.
        patchedApk.file.move(TMP_FILE_PATH)
        CREATE_INSTALLATION_PATH().waitFor()
        MOUNT_APK(packageName)().waitFor()

        // Install and run.
        TMP_FILE_PATH.write(MOUNT_SCRIPT(packageName))
        INSTALL_MOUNT_SCRIPT(packageName)().waitFor()
        MOUNT_SCRIPT_PATH(packageName)().waitFor()
        RESTART(packageName)()

        DELETE(TMP_FILE_PATH)()

        return RootInstallerResult.SUCCESS
    }

    override suspend fun uninstall(packageName: String): RootInstallerResult {
        logger.info("Uninstalling $packageName by unmounting")

        UMOUNT(packageName)()

        DELETE(MOUNTED_APK_PATH)(packageName)()
        DELETE(MOUNT_SCRIPT_PATH)(packageName)()
        DELETE(TMP_FILE_PATH)() // Remove residual.

        KILL(packageName)()

        return RootInstallerResult.SUCCESS
    }

    override suspend fun getInstallation(packageName: String): RootInstallation? {
        val patchedApkPath = MOUNTED_APK_PATH(packageName)

        val patchedApkExists = EXISTS(patchedApkPath)().exitCode == 0
        if (!patchedApkExists) return null

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
    protected fun String.write(content: String) =
        shellCommandRunner.write(content.byteInputStream(), this)

    /**
     * Asserts that the package is installed.
     *
     * @throws FailedToFindInstalledPackageException If the package is not installed.
     */
    private fun String.assertInstalled() {
        if (INSTALLED_APK_PATH(this)().output.isEmpty()) {
            throw FailedToFindInstalledPackageException(this)
        }
    }

    private fun getInstalledVersionCode(packageName: String): Int? {
        val result = GET_INSTALLED_VERSION_CODE(packageName)().waitFor()

        if (result.exitCode != 0) return null

        return result.output.trim().toIntOrNull()
    }

    internal class PackageDowngradeRequiredException internal constructor(packageName: String) :
        Exception("$packageName requires reinstallation for a downgrade, data wipe is likely")

    internal class FailedToFindInstalledPackageException internal constructor(packageName: String) :
        Exception("Failed to resolve installed APK path for package \"$packageName\"")

    internal class NoRootPermissionException internal constructor() :
        Exception("No root permission")
}
