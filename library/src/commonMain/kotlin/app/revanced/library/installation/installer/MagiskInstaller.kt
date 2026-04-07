package app.revanced.library.installation.installer

import app.revanced.library.installation.command.ShellCommandRunner
import app.revanced.library.installation.installer.Constants.DELETE
import app.revanced.library.installation.installer.Constants.EXISTS
import app.revanced.library.installation.installer.Constants.INSTALLED_APK_PATH
import app.revanced.library.installation.installer.Constants.KILL
import app.revanced.library.installation.installer.Constants.MAGISK_MODULE_PATH
import app.revanced.library.installation.installer.Constants.MAGISK_MODULE_PROP
import app.revanced.library.installation.installer.Constants.MOVE
import app.revanced.library.installation.installer.Constants.RESTART
import app.revanced.library.installation.installer.Constants.SET_FILE_PERMISSIONS
import app.revanced.library.installation.installer.Constants.TMP_FILE_PATH
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
    shellCommandRunnerSupplier: (RootInstaller) -> ShellCommandRunner,
) : RootInstaller(shellCommandRunnerSupplier) {

    /**
     * Installs the given [apk] as a Magisk module.
     *
     * @param apk The [Apk] to install.
     *
     * @throws PackageNameRequiredException If the [Apk] does not have a package name.
     */
    override suspend fun install(apk: Apk): RootInstallerResult {
        logger.info("Installing ${apk.packageName} as a Magisk module")

        val packageName = apk.packageName?.also { it.assertInstalled() } ?: throw PackageNameRequiredException()

        val sanitizedPackageName = packageName.replace('.', '_')

        // Resolve the stock APK path.
        val stockApkPath = INSTALLED_APK_PATH(packageName)().output
            .lineSequence()
            .firstOrNull { it.startsWith("package:") }
            ?.removePrefix("package:")
            ?: throw FailedToFindInstalledPackageException(packageName)

        // Derive the parent directory relative to /system.
        val stockApkParent = stockApkPath.substringAfter("/")
            .substringBeforeLast("/")

        // Create the Magisk module directory structure.
        val modulePath = MAGISK_MODULE_PATH(sanitizedPackageName)
        val moduleApkDir = "$modulePath/$stockApkParent"
        "mkdir -p $moduleApkDir"().waitFor()

        // Write module.prop.
        apk.file.move(TMP_FILE_PATH)
        "$modulePath/module.prop".write(MAGISK_MODULE_PROP(sanitizedPackageName)(packageName))

        // Move the patched APK into the module and set permissions.
        val targetApkPath = "$moduleApkDir/base.apk"
        MOVE(targetApkPath)().waitFor()
        SET_FILE_PERMISSIONS(targetApkPath)().waitFor()

        RESTART(packageName)()

        return RootInstallerResult.SUCCESS
    }

    override suspend fun uninstall(packageName: String): RootInstallerResult {
        logger.info("Uninstalling $packageName Magisk module")

        val sanitizedPackageName = packageName.replace('.', '_')

        DELETE(MAGISK_MODULE_PATH(sanitizedPackageName))()
        DELETE(TMP_FILE_PATH)()

        KILL(packageName)()

        return RootInstallerResult.SUCCESS
    }

    override suspend fun getInstallation(packageName: String): RootInstallation? {
        val sanitizedPackageName = packageName.replace('.', '_')
        val modulePath = MAGISK_MODULE_PATH(sanitizedPackageName)

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
