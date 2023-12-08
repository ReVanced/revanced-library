package app.revanced.library.installation.installer

import app.revanced.library.installation.command.AdbCommandRunner
import se.vidstige.jadb.managers.Package
import se.vidstige.jadb.managers.PackageManager

/**
 * [AdbInstaller] for non-rooted devices.
 *
 * @param deviceSerial The device serial. If null, the first connected device will be used.
 * @see Installer
 */
class AdbInstaller(deviceSerial: String? = null) :
    Installer<AdbCommandRunner>(AdbCommandRunner(deviceSerial)) {
    private val packageManager = PackageManager(commandRunner.device)

    init {
        logger.fine("Connected to ${commandRunner.device.serial}")
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