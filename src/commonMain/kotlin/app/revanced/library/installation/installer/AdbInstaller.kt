package app.revanced.library.installation.installer

import app.revanced.library.installation.installer.Installer.Apk
import se.vidstige.jadb.JadbException
import se.vidstige.jadb.managers.Package
import se.vidstige.jadb.managers.PackageManager

/**
 * [AdbInstaller] for installing and uninstalling [Apk] files using ADB.
 *
 * @param deviceSerial The device serial. If null, the first connected device will be used.
 *
 * @see Installer
 */
class AdbInstaller(
    deviceSerial: String? = null,
) : Installer<AdbInstallerResult>() {
    private val packageManager = PackageManager(Utils.getDevice(deviceSerial, logger))

    init {
        logger.fine("Connected to $deviceSerial")
    }

    override suspend fun install(apk: Apk): AdbInstallerResult {
        logger.info("Installing ${apk.file.name}")

        return runPackageManager { install(apk.file) }
    }

    override suspend fun uninstall(packageName: String): AdbInstallerResult {
        logger.info("Uninstalling $packageName")

        return runPackageManager { uninstall(Package(packageName)) }
    }

    private fun runPackageManager(block: PackageManager.() -> Unit): AdbInstallerResult {
        try {
            packageManager.run(block)

            return AdbInstallerResult.Success
        } catch (e: JadbException) {
            return AdbInstallerResult.Failure(e)
        }
    }
}
