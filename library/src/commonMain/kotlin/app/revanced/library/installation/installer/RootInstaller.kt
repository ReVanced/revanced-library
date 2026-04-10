package app.revanced.library.installation.installer

import app.revanced.library.installation.command.ShellCommandRunner
import app.revanced.library.installation.installer.Constants.CREATE_INSTALLATION_PATH
import app.revanced.library.installation.installer.Constants.DELETE
import app.revanced.library.installation.installer.Constants.EXISTS
import app.revanced.library.installation.installer.Constants.GET_INSTALLED_VERSION_CODE
import app.revanced.library.installation.installer.Constants.GET_INSTALLED_VERSION_NAME
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
@Suppress("MemberVisibilityCanBePrivate", "unused")
abstract class RootInstaller internal constructor(
    shellCommandRunnerSupplier: (RootInstaller) -> ShellCommandRunner,
) : Installer<RootInstallerResult, RootInstallation, RootInstallerOptions>() {

    /**
     * The command runner used to run commands on the device.
     */
    protected val shellCommandRunner = shellCommandRunnerSupplier(this)

    init {
        if (!shellCommandRunner.hasRootPermission()) throw NoRootPermissionException()
    }

    /**
     * Installs the given patched APK by mounting it over a regular installation of the stock APK.
     *
     * The stock APK is used to ensure a valid base installation for mounting. If the app is not
     * currently installed, the stock APK is installed first. If the installed app version does not
     * match the expected stock APK version, installation is aborted.
     *
     * @param options The installer options containing the patched APK to mount and the stock APK
     * used as the installation base.
     *
     * @throws PackageVersionMismatchException If the installed app version does not match the
     * expected stock APK version.
     */
    override suspend fun install(options: RootInstallerOptions): RootInstallerResult {
        val stockApk = options.stockApk
        val packageName = stockApk.packageName
        logger.info("Installing $packageName by mounting")

        // Ensure the installed base app matches the stock APK version.
        val installedVersionName = try {
            getInstalledVersionName(packageName)
        } catch (_: PackageNotInstalledException) {
            null
        }

        when {
            installedVersionName == null -> {
                logger.info("Installing stock APK for $packageName")
                INSTALL_STOCK_APK(stockApk.file.absolutePath)().waitFor()
                packageName.assertInstalled()
            }

            installedVersionName != stockApk.versionName -> {
                throw PackageVersionMismatchException(packageName)
            }
        }

        packageName.assertInstalled()

        // Setup files.
        options.stockApk.file.move(TMP_FILE_PATH)
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

    fun getInstalledVersionName(packageName: String): String =
        GET_INSTALLED_VERSION_NAME(packageName)()
            .waitFor()
            .output
            .trim()
            .takeIf { it.isNotEmpty() }
            ?: throw PackageNotInstalledException(packageName)

    fun getInstalledVersionCode(packageName: String): Int =
        GET_INSTALLED_VERSION_CODE(packageName)()
            .waitFor()
            .output
            .trim()
            .toIntOrNull()
            ?: throw PackageNotInstalledException(packageName)

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
     * @throws PackageNotInstalledException If the package is not installed.
     */
    private fun String.assertInstalled() {
        if (INSTALLED_APK_PATH(this)().output.isEmpty()) {
            throw PackageNotInstalledException(this)
        }
    }

    internal class PackageVersionMismatchException internal constructor(packageName: String) :
        Exception("Package $packageName does not match the expected version")

    internal class PackageNotInstalledException internal constructor(packageName: String) :
        Exception("Package $packageName is not installed")

    internal class NoRootPermissionException internal constructor() :
        Exception("Root permission is not granted")
}
