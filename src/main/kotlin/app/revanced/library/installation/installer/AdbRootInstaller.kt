package app.revanced.library.installation.installer

import app.revanced.library.installation.command.AdbCommandRunner

/**
 * [AdbRootInstaller] for rooted devices.
 *
 * @param deviceSerial The device serial. If null, the first connected device will be used.
 * @see RootInstaller
 */
class AdbRootInstaller(deviceSerial: String? = null) :
    RootInstaller<AdbCommandRunner>(AdbCommandRunner(deviceSerial)) {
    init {
        logger.fine("Connected to ${commandRunner.device.serial}")
    }
}