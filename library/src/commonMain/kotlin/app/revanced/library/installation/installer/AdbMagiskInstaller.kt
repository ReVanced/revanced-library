package app.revanced.library.installation.installer

import app.revanced.library.installation.command.AdbShellCommandRunner

/**
 * [AdbMagiskRootInstaller] for installing and uninstalling [Apk] files using ADB root permissions via Magisk modules.
 *
 * @param deviceSerial The device serial. If null, the first connected device will be used.
 *
 * @throws NoRootPermissionException If the device does not have root permission.
 *
 * @see MagiskRootInstaller
 * @see AdbShellCommandRunner
 */
class AdbMagiskRootInstaller(
    deviceSerial: String? = null,
) : MagiskRootInstaller(AdbShellCommandRunner(deviceSerial)) {
    init {
        logger.fine("Connected to $deviceSerial")
    }
}
