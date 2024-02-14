package app.revanced.library.installation.installer

import app.revanced.library.installation.installer.Installer.Apk
import se.vidstige.jadb.managers.Package
import se.vidstige.jadb.managers.PackageManager

/**
 * [AdbInstaller] for installing and uninstalling [Apk] files using ADB.
 *
 * @param deviceSerial The device serial. If null, the first connected device will be used.
 * @see Installer
 */
class AdbInstaller(deviceSerial: String? = null) : Installer() {
    private val packageManager = PackageManager(Utils.getDevice(deviceSerial, logger))

    init {
        logger.fine("Connected to $deviceSerial")
    }

    override fun install(apk: Apk) {
        logger.info("Installing ${apk.file.name}")

        packageManager.install(apk.file)

        super.install(apk)
    }

    override fun uninstall(packageName: String) {
        logger.info("Uninstalling $packageName")

        packageManager.uninstall(Package(packageName))

        super.uninstall(packageName)
    }
}
