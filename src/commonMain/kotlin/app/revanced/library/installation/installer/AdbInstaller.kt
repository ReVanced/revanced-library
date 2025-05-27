package app.revanced.library.installation.installer

import app.revanced.library.installation.command.AdbShellCommandRunner
import app.revanced.library.installation.command.ShellCommandRunner
import app.revanced.library.installation.installer.Constants.GET_SDK_VERSION
import app.revanced.library.installation.installer.Constants.INSTALLED_APK_PATH
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
    private val shellCommandRunner: ShellCommandRunner
    private val packageManager: PackageManager

    init {
        val device = getDevice(deviceSerial, logger)
        shellCommandRunner = AdbShellCommandRunner(device)
        packageManager = PackageManager(device)

        logger.fine("Connected to $deviceSerial")
    }

    override suspend fun install(apk: Apk): AdbInstallerResult {
        return runPackageManager {
            val sdkVersion = shellCommandRunner(GET_SDK_VERSION).output.toInt()
            if (sdkVersion < 34) install(apk.file)
            else installWithOptions(apk.file, listOf(UPDATE_OWNERSHIP))
        }
    }

    override suspend fun uninstall(packageName: String): AdbInstallerResult {
        logger.info("Uninstalling $packageName")

        return runPackageManager { uninstall(Package(packageName)) }
    }

    override suspend fun getInstallation(packageName: String): Installation? = packageManager.packages.find {
        it.toString() == packageName
    }?.let { Installation(shellCommandRunner(INSTALLED_APK_PATH).output) }

    private fun runPackageManager(block: PackageManager.() -> Unit) = try {
        packageManager.run(block)

        AdbInstallerResult.Success
    } catch (e: JadbException) {
        AdbInstallerResult.Failure(e)
    }
}
