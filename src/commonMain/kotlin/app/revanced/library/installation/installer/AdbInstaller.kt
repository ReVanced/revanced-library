package app.revanced.library.installation.installer

import app.revanced.library.installation.command.AdbShellCommandRunner
import app.revanced.library.installation.installer.Constants.INSTALLED_APK_PATH
import app.revanced.library.installation.installer.Installer.Apk
import se.vidstige.jadb.JadbException
import se.vidstige.jadb.managers.Package
import se.vidstige.jadb.managers.PackageManager
import se.vidstige.jadb.managers.PackageManager.UPDATE_OWNERSHIP
import java.nio.charset.StandardCharsets

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
    private val device = getDevice(deviceSerial, logger)
    private val adbShellCommandRunner = AdbShellCommandRunner(device)
    private val packageManager = PackageManager(device)

    init {
        logger.fine("Connected to $deviceSerial")
    }

    private fun getSdkVersion(): Int? = runCatching {
        device.executeShell("getprop", "ro.build.version.sdk")
            .bufferedReader(StandardCharsets.UTF_8)
            .use { it.readLine()?.trim()?.toInt() }
    }.getOrNull()

    override suspend fun install(apk: Apk): AdbInstallerResult {
        getSdkVersion().let {
            return runPackageManager {
                if (it == null || it < 34) install(apk.file)
                else installWithOptions(apk.file, arrayListOf(UPDATE_OWNERSHIP))
            }
        }
    }

    override suspend fun uninstall(packageName: String): AdbInstallerResult {
        logger.info("Uninstalling $packageName")

        return runPackageManager { uninstall(Package(packageName)) }
    }

    override suspend fun getInstallation(packageName: String): Installation? = packageManager.packages.find {
        it.toString() == packageName
    }?.let { Installation(adbShellCommandRunner(INSTALLED_APK_PATH).output) }

    private fun runPackageManager(block: PackageManager.() -> Unit) = try {
        packageManager.run(block)

        AdbInstallerResult.Success
    } catch (e: JadbException) {
        AdbInstallerResult.Failure(e)
    }
}