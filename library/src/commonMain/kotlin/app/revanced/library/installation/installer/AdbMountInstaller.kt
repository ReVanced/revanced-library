package app.revanced.library.installation.installer

import app.revanced.library.installation.command.AdbShellCommandRunner
import app.revanced.library.installation.installer.Installer.Apk
import app.revanced.library.installation.installer.MountInstaller.NoRootPermissionException

/**
 * [AdbMountInstaller] for installing and uninstalling [Apk] files with using ADB root permissions by mounting.
 *
 * @param deviceSerial The device serial. If null, the first connected device will be used.
 *
 * @throws NoRootPermissionException If the device does not have root permission.
 *
 * @see MountInstaller
 * @see AdbShellCommandRunner
 */
class AdbMountInstaller(
    deviceSerial: String? = null,
) : MountInstaller({ AdbShellCommandRunner(deviceSerial) }) {
    init {
        logger.fine("Connected to $deviceSerial")
    }
}
