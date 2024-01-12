package app.revanced.library.installation.installer

import app.revanced.library.installation.command.ShellCommandRunner
import app.revanced.library.installation.installer.Constants.CREATE_INSTALLATION_PATH
import app.revanced.library.installation.installer.Constants.DELETE
import app.revanced.library.installation.installer.Constants.GET_INSTALLED_PATH
import app.revanced.library.installation.installer.Constants.INSTALL_MOUNT_SCRIPT
import app.revanced.library.installation.installer.Constants.INSTALL_PATCHED_APK
import app.revanced.library.installation.installer.Constants.KILL
import app.revanced.library.installation.installer.Constants.MOUNT_SCRIPT
import app.revanced.library.installation.installer.Constants.MOUNT_SCRIPT_PATH
import app.revanced.library.installation.installer.Constants.PATCHED_APK_PATH
import app.revanced.library.installation.installer.Constants.RESTART
import app.revanced.library.installation.installer.Constants.TMP_FILE_PATH
import app.revanced.library.installation.installer.Constants.UMOUNT
import app.revanced.library.installation.installer.Constants.invoke
import app.revanced.library.installation.installer.Installer.Apk
import app.revanced.library.installation.installer.RootInstaller.NoRootPermissionException
import java.io.File

/**
 * [RootInstaller] for installing and uninstalling [Apk] files using root by mounting.
 *
 * @param shellCommandRunnerSupplier A supplier for the [ShellCommandRunner] to use.
 * @throws NoRootPermissionException If the device does not have root permission.
 */
@Suppress("MemberVisibilityCanBePrivate")
abstract class RootInstaller(
    shellCommandRunnerSupplier: (RootInstaller) -> ShellCommandRunner,
) : Installer() {
    @Suppress("LeakingThis")
    protected val shellCommandRunner = shellCommandRunnerSupplier(this)

    init {
        if (!shellCommandRunner.hasRootPermission()) throw NoRootPermissionException()
    }

    /**
     * Installs the given [apk] by mounting.
     *
     * @param apk The [Apk] to install.
     * @throws PackageNameRequiredException If the [Apk] does not have a package name.
     */
    override fun install(apk: Apk) {
        logger.info("Installing ${apk.packageName} by mounting")

        val packageName = apk.packageName?.also { it.assertInstalled() } ?: throw PackageNameRequiredException()

        // Setup files.
        apk.file.move(TMP_FILE_PATH)
        CREATE_INSTALLATION_PATH().waitFor()
        INSTALL_PATCHED_APK(packageName)().waitFor()

        // Install and run.
        TMP_FILE_PATH.write(MOUNT_SCRIPT(packageName))
        INSTALL_MOUNT_SCRIPT(packageName)().waitFor()
        MOUNT_SCRIPT_PATH(packageName)().waitFor()
        RESTART(packageName)()

        DELETE(TMP_FILE_PATH)()

        super.install(apk)
    }

    override fun uninstall(packageName: String) {
        logger.info("Uninstalling $packageName by unmounting")

        UMOUNT(packageName)()

        DELETE(PATCHED_APK_PATH)(packageName)()
        DELETE(MOUNT_SCRIPT_PATH)(packageName)()
        DELETE(TMP_FILE_PATH)() // Remove residual.

        KILL(packageName)()

        super.uninstall(packageName)
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
    private fun String.assertInstalled() {
        if (GET_INSTALLED_PATH(this)().output.isNotEmpty()) {
            throw FailedToFindInstalledPackageException(this)
        }
    }

    internal class FailedToFindInstalledPackageException internal constructor(packageName: String) :
        Exception("Failed to find installed package \"$packageName\" because no activity was found")

    internal class PackageNameRequiredException internal constructor() : Exception("Package name is required")
    internal class NoRootPermissionException internal constructor() : Exception("No root permission")
}