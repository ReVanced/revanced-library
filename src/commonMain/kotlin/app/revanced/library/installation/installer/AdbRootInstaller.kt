package app.revanced.library.installation.installer

import app.revanced.library.installation.command.AdbShellCommandRunner

/**
 * [AdbRootInstaller] for installing and uninstalling [Apk] files with using ADB root permissions by mounting.
 *
 * @param deviceSerial The device serial. If null, the first connected device will be used.
 *
 * @throws NoRootPermissionException If the device does not have root permission.
 *
 * @see RootInstaller
 * @see AdbShellCommandRunner
 */
class AdbRootInstaller(
    deviceSerial: String? = null,
) : RootInstaller({ AdbShellCommandRunner(deviceSerial) }) {
    init {
        logger.fine("Connected to $deviceSerial")
    }
}
