package app.revanced.library.installation.installer

import app.revanced.library.installation.command.ShellCommandRunner
import app.revanced.library.installation.installer.Constants.DELETE
import app.revanced.library.installation.installer.Constants.EXISTS
import app.revanced.library.installation.installer.Constants.INDUCTION_SERVICE_SCRIPT
import app.revanced.library.installation.installer.Constants.INSTALLED_APK_PATH
import app.revanced.library.installation.installer.Constants.KILL
import app.revanced.library.installation.installer.Constants.MAGISK_MODULE_PATH
import app.revanced.library.installation.installer.Constants.MAGISK_MODULE_PROP
import app.revanced.library.installation.installer.Constants.MAGISK_UNINSTALL_SCRIPT
import app.revanced.library.installation.installer.Constants.MOUNTED_APK_PATH
import app.revanced.library.installation.installer.Constants.MOUNT_APK
import app.revanced.library.installation.installer.Constants.RESTART
import app.revanced.library.installation.installer.Constants.TMP_FILE_PATH
import app.revanced.library.installation.installer.Constants.UMOUNT
import app.revanced.library.installation.installer.Constants.invoke

/**
 * [MagiskInstaller] for installing and uninstalling [Apk] files using root permissions via Magisk modules.
 *
 * @param shellCommandRunnerSupplier A supplier for the [ShellCommandRunner] to use.
 *
 * @throws NoRootPermissionException If the device does not have root permission.
 */
@Suppress("MemberVisibilityCanBePrivate")
abstract class MagiskInstaller internal constructor(
    shellCommandRunnerSupplier: () -> ShellCommandRunner,
) : RootInstaller(shellCommandRunnerSupplier) {

    /**
     * Installs the given [apk] as a Magisk module.
     *
     * The patched APK is staged at the unified source-of-truth path
     * `/data/adb/revanced/<packageName>/base.apk` (the same location used by the
     * non-Magisk root installer), and the module ships a `service.sh` that
     * bind-mounts that file over the stock APK on every boot. After provisioning,
     * `service.sh` is executed inline so the install takes effect immediately,
     * without requiring a reboot.
     *
     * @param apk The [Apk] to install.
     *
     * @throws PackageNameRequiredException If the [Apk] does not have a package name.
     */
    override suspend fun install(apk: Apk): RootInstallerResult {
        logger.info("Installing ${apk.packageName} as a Magisk module")

        val packageName = apk.packageName?.also { it.assertInstalled() } ?: throw PackageNameRequiredException()
        val formattedPackageName = packageName.replace('.', '_')
        val modulePath = MAGISK_MODULE_PATH(formattedPackageName)

        // Stage the patched APK at the unified source-of-truth path.
        // MOUNT_APK moves the file from TMP_FILE_PATH and applies permissions/SELinux context.
        apk.file.move(TMP_FILE_PATH)
        MOUNT_APK(packageName)().waitFor()

        // Create the Magisk module directory.
        "mkdir -p $modulePath"().waitFor()

        // Write module.prop.
        val moduleProp = MAGISK_MODULE_PROP
            .replace("__PKG_NAME__", packageName)
            .replace("__VERSION__", apk.version ?: "1.0")
            .replace("__LABEL__", apk.label ?: packageName)
        "$modulePath/module.prop".write(moduleProp)

        // Write service.sh — Magisk runs this on every boot to bind-mount the patched APK.
        val serviceScript = INDUCTION_SERVICE_SCRIPT
            .replace("__PKG_NAME__", packageName)
            .replace("__VERSION__", apk.version ?: "1.0")
            .replace("__LABEL__", apk.label ?: packageName)
        val serviceScriptPath = "$modulePath/service.sh"
        serviceScriptPath.write(serviceScript)
        "chmod +x $serviceScriptPath"().waitFor()

        // Write uninstall.sh — Magisk runs this when the module is removed via the Magisk app,
        // cleaning up the unified source APK directory that lives outside the module.
        val uninstallScript = MAGISK_UNINSTALL_SCRIPT.replace("__PKG_NAME__", packageName)
        val uninstallScriptPath = "$modulePath/uninstall.sh"
        uninstallScriptPath.write(uninstallScript)
        "chmod +x $uninstallScriptPath"().waitFor()

        // Live trigger: execute service.sh now so the bind-mount is active without a reboot.
        "sh $serviceScriptPath"().waitFor()

        RESTART(packageName)()

        return RootInstallerResult.SUCCESS
    }

    /**
     * Uninstalls the Magisk module for the given [packageName].
     *
     * Performs an immediate live unmount and removes both the module directory and the
     * unified source APK so the rollback is visible without a reboot. The module also
     * ships an `uninstall.sh` for the case where the user removes it from the Magisk
     * app instead of going through the installer.
     */
    override suspend fun uninstall(packageName: String): RootInstallerResult {
        logger.info("Uninstalling $packageName Magisk module")

        val formattedPackageName = packageName.replace('.', '_')

        // Live unmount so the stock APK is restored immediately.
        UMOUNT(packageName)()

        // Remove the Magisk module directory.
        DELETE(MAGISK_MODULE_PATH(formattedPackageName))().waitFor()

        // Remove the unified source APK.
        DELETE(MOUNTED_APK_PATH(packageName))().waitFor()

        // Clean up any residual tmp file.
        DELETE(TMP_FILE_PATH)()

        KILL(packageName)()

        return RootInstallerResult.SUCCESS
    }

    override suspend fun getInstallation(packageName: String): RootInstallation? {
        val formattedPackageName = packageName.replace('.', '_')
        val modulePath = MAGISK_MODULE_PATH(formattedPackageName)

        val moduleExists = EXISTS("$modulePath/module.prop")().exitCode == 0
        if (!moduleExists) return null

        return RootInstallation(
            INSTALLED_APK_PATH(packageName)().output.ifEmpty { null },
            modulePath,
            moduleExists,
        )
    }

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
}
