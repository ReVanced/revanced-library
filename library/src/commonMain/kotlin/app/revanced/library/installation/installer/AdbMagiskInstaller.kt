package app.revanced.library.installation.installer

import app.revanced.library.installation.command.AdbShellCommandRunner

/**
 * [AdbMagiskInstaller] for installing and uninstalling [Apk] files using ADB root permissions via Magisk modules.
 *
 * @param deviceSerial The device serial. If null, the first connected device will be used.
 *
 * @throws NoRootPermissionException If the device does not have root permission.
 *
 * @see MagiskInstaller
 * @see AdbShellCommandRunner
 */
class AdbMagiskInstaller(
    deviceSerial: String? = null,
) : MagiskInstaller({ AdbShellCommandRunner(deviceSerial) }) {
    init {
        logger.fine("Connected to $deviceSerial")
    }
}
