package app.revanced.library.installation.installer

import app.revanced.library.installation.command.ShellCommandRunner
import app.revanced.library.installation.installer.Constants.DELETE
import app.revanced.library.installation.installer.Constants.EXISTS
import app.revanced.library.installation.installer.Constants.INSTALLED_APK_PATH
import app.revanced.library.installation.installer.Constants.KILL
import app.revanced.library.installation.installer.Constants.MODULE_PATH
import app.revanced.library.installation.installer.Constants.MODULE_PROP
import app.revanced.library.installation.installer.Constants.MODULE_UNINSTALL_SCRIPT
import app.revanced.library.installation.installer.Constants.MODULE_PROP_FILE
import app.revanced.library.installation.installer.Constants.MODULE_SERVICE_SCRIPT
import app.revanced.library.installation.installer.Constants.MOUNTED_APK_PATH
import app.revanced.library.installation.installer.Constants.RESTART
import app.revanced.library.installation.installer.Constants.SERVICE_SCRIPT_FILE
import app.revanced.library.installation.installer.Constants.TMP_FILE_PATH
import app.revanced.library.installation.installer.Constants.UMOUNT
import app.revanced.library.installation.installer.Constants.UNINSTALL_SCRIPT_FILE
import app.revanced.library.installation.installer.Constants.invoke

/**
 * [MagiskRootInstaller] for installing and uninstalling [Apk] files using root permissions via Magisk modules.
 *
 * @param shellCommandRunner The [ShellCommandRunner] to use.
 *
 * @throws NoRootPermissionException If the device does not have root permission.
 */
@Suppress("MemberVisibilityCanBePrivate")
abstract class MagiskRootInstaller internal constructor(
    shellCommandRunner: ShellCommandRunner,
) : RootInstaller(shellCommandRunner) {

    /**
     * Installs the given [apk] as a Magisk module.
     *
     * @param apk The [Apk] to install.
     *
     * @throws PackageNameRequiredException If the [Apk] does not have a package name.
     */
    override suspend fun install(apk: Apk): RootInstallerResult {
        logger.info("Installing ${apk.packageName} as a Magisk module")

        val packageName = apk.packageName ?: throw PackageNameRequiredException()
        val formattedPackageName = packageName.replace('.', '_')
        val modulePath = MODULE_PATH(formattedPackageName)

        // Track whether the app was already on-device so uninstall() knows whether to pm uninstall.
        val isPreInstalled = INSTALLED_APK_PATH(packageName)().output.isNotEmpty()

        // Prepare the patched APK at the unified source-of-truth path.
        apk.file.move(TMP_FILE_PATH)
        prepareApk(packageName)

        // Create the Magisk module directory.
        "mkdir -p $modulePath"().waitFor()

        // Write module.prop.
        val moduleProp = MODULE_PROP
            .replace("__FORMATTED_PKG__", formattedPackageName)
            .replace("__PKG_NAME__", packageName)
        "$modulePath/$MODULE_PROP_FILE".write(moduleProp)

        // Write service.sh - Magisk runs this on every boot to install the patched APK.
        val serviceScriptPath = "$modulePath/$SERVICE_SCRIPT_FILE"
        serviceScriptPath.write(MODULE_SERVICE_SCRIPT
            .replace("__PKG_NAME__", packageName)
            .replace("__PATCHED_PKG__", packageName)
            .replace("__USER_ID__", "0"))
        "chmod +x $serviceScriptPath"().waitFor()

        // Write uninstall.sh - Magisk runs this when the module is removed via the Magisk app.
        "$modulePath/$UNINSTALL_SCRIPT_FILE".write(MODULE_UNINSTALL_SCRIPT
            .replace("__PKG_NAME__", packageName)
            .replace("__PATCHED_PKG__", packageName)
            .replace("__FORMATTED_PKG__", formattedPackageName))
        "chmod +x $modulePath/$UNINSTALL_SCRIPT_FILE"()

        // Mark as newly installed so uninstall() also calls pm uninstall.
        if (!isPreInstalled) "$modulePath/.newly_installed".write("")

        // Live trigger: execute service.sh now so the install takes effect without a reboot.
        "sh $serviceScriptPath"().waitFor()

        RESTART(packageName)()

        return RootInstallerResult.SUCCESS
    }

    /**
     * Uninstalls the Magisk module for the given [packageName].
     *
     * Performs an immediate live unmount and removes both the module directory and the
     * unified source APK so the rollback is visible without a reboot.
     */
    override suspend fun uninstall(packageName: String): RootInstallerResult {
        logger.info("Uninstalling $packageName Magisk module")

        val formattedPackageName = packageName.replace('.', '_')
        val modulePath = MODULE_PATH(formattedPackageName)

        // Read the flag before removing the module directory.
        val newlyInstalled = EXISTS("$modulePath/.newly_installed")().exitCode == 0

        // Live unmount so the stock APK is restored immediately.
        UMOUNT(packageName)()

        // Remove the Magisk module directory.
        DELETE(modulePath)().waitFor()

        // Remove the unified source APK.
        DELETE(MOUNTED_APK_PATH(packageName))().waitFor()

        // Clean up any residual tmp file.
        DELETE(TMP_FILE_PATH)()

        // If the app was freshly installed by the module, fully remove it.
        if (newlyInstalled) "pm uninstall $packageName"()

        KILL(packageName)()

        return RootInstallerResult.SUCCESS
    }

    override suspend fun getInstallation(packageName: String): RootInstallation? {
        val formattedPackageName = packageName.replace('.', '_')
        val modulePath = MODULE_PATH(formattedPackageName)

        val moduleExists = EXISTS("$modulePath/module.prop")().exitCode == 0
        if (!moduleExists) return null

        return RootInstallation(
            INSTALLED_APK_PATH(packageName)().output.ifEmpty { null },
            MOUNTED_APK_PATH(packageName),
            false, // Magisk module install uses pm install, not bind-mount.
        )
    }
}
