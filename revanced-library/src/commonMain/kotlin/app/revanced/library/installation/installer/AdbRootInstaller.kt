package app.revanced.library.installation.installer

import app.revanced.library.installation.command.AdbCommandRunner
import app.revanced.library.installation.installer.Installer.Apk

/**
 * [AdbRootInstaller] for installing and uninstalling [Apk] files with using ADB root permissions by mounting.
 *
 * @param deviceSerial The device serial. If null, the first connected device will be used.
 * @see RootInstaller
 */
class AdbRootInstaller(deviceSerial: String? = null) : RootInstaller<AdbCommandRunner>(AdbCommandRunner(deviceSerial)) {
    init {
        logger.fine("Connected to $deviceSerial")
    }
}
