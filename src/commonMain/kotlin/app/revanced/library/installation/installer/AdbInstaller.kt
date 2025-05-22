package app.revanced.library.installation.installer

import app.revanced.library.installation.command.AdbShellCommandRunner
import app.revanced.library.installation.installer.Constants.INSTALLED_APK_PATH
import app.revanced.library.installation.installer.Installer.Apk
import se.vidstige.jadb.JadbException
import se.vidstige.jadb.managers.Package
import se.vidstige.jadb.managers.PackageManager
import se.vidstige.jadb.managers.PackageManager.UPDATE_OWNERSHIP

/**
 * [AdbInstaller] for installing and uninstalling [Apk] files using ADB.
 *
 * @param deviceSerial The device serial. If null, the first connected device will be used.
 *
 * @see Installer
 */
class AdbInstaller(
    deviceSerial: String? = null,
) : Installer<AdbInstallerResult, Installation>() {
    private val device = getDevice(deviceSerial, logger)
    private val adbShellCommandRunner = AdbShellCommandRunner(device)
    private val packageManager = PackageManager(device)
    private val deviceSdkVersion = device.sdkVersion

    init {
        logger.fine("Connected to $deviceSerial")
    }

    override suspend fun install(apk: Apk): AdbInstallerResult {
        return runPackageManager { 
            if(deviceSdkVersion != null && deviceSdkVersion >= 34) {
                logger.info("Installing ${apk.file.name} with ownership")
                installWithOptions(apk.file, arrayListOf(UPDATE_OWNERSHIP))
            } else {
                logger.info("Installing ${apk.file.name}")
                install(apk.file)
            }
        }
    }

    override suspend fun uninstall(packageName: String): AdbInstallerResult {
        logger.info("Uninstalling $packageName")

        return runPackageManager { uninstall(Package(packageName)) }
    }

    override suspend fun getInstallation(packageName: String): Installation? = packageManager.packages.find {
        it.toString() == packageName
    }?.let { Installation(adbShellCommandRunner(INSTALLED_APK_PATH).output) }

    private fun runPackageManager(block: PackageManager.() -> Unit) = try {
        packageManager.run(block)

        AdbInstallerResult.Success
    } catch (e: JadbException) {
        AdbInstallerResult.Failure(e)
    }
}