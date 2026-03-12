package app.revanced.library.installation.installer

import app.revanced.library.installation.command.AdbShellCommandRunner
import app.revanced.library.installation.command.ShellCommandRunner
import app.revanced.library.installation.installer.Constants.GET_SDK_VERSION
import app.revanced.library.installation.installer.Constants.INSTALLED_APK_PATH
import app.revanced.library.installation.installer.Constants.TMP_FILE_PATH
import app.revanced.library.installation.installer.Constants.invoke
import app.revanced.library.installation.installer.Constants.splitFileName
import se.vidstige.jadb.JadbDevice
import se.vidstige.jadb.JadbException
import se.vidstige.jadb.RemoteFile
import java.io.IOException
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
    private val device: JadbDevice
    private val shellCommandRunner: ShellCommandRunner
    private val packageManager: PackageManager

    init {
        device = getDevice(deviceSerial, logger)
        shellCommandRunner = AdbShellCommandRunner(device)
        packageManager = PackageManager(device)

        logger.fine("Connected to $deviceSerial")
    }

    override suspend fun install(apk: Apk): AdbInstallerResult {
        return runPackageManager {
            val sdkVersion = shellCommandRunner(GET_SDK_VERSION).output.toInt()
            if (apk.splitFiles.isEmpty()) {
                if (sdkVersion < 34) install(apk.file)
                else installWithOptions(apk.file, listOf(UPDATE_OWNERSHIP))

                return@runPackageManager
            }

            installSplitPackage(apk, sdkVersion >= 34)
        }
    }

    override suspend fun uninstall(packageName: String): AdbInstallerResult {
        logger.info("Uninstalling $packageName")

        return runPackageManager { uninstall(Package(packageName)) }
    }

    override suspend fun getInstallation(packageName: String): Installation? = packageManager.packages.find {
        it.toString() == packageName
    }?.let { Installation(shellCommandRunner(INSTALLED_APK_PATH).output) }

    private fun installSplitPackage(apk: Apk, updateOwnership: Boolean) {
        logger.info("Installing ${apk.file.name} with ${apk.splitFiles.size} split APK(s)")

        val remoteFiles = buildMap {
            put(apk.file, RemoteFile(TMP_FILE_PATH("base.apk")))
            apk.splitFiles.forEach { (splitName, splitFile) ->
                put(splitFile, RemoteFile(TMP_FILE_PATH(splitFileName(splitName))))
            }
        }

        try {
            remoteFiles.forEach { (file, remoteFile) ->
                device.push(file, remoteFile)
            }

            val arguments = mutableListOf("install-multiple")
            if (updateOwnership) {
                arguments += "--update-ownership"
            }
            arguments += remoteFiles.values.map(RemoteFile::getPath)

            val result = device.executeShell("pm", *arguments.toTypedArray()).use { inputStream ->
                inputStream.readBytes().toString(StandardCharsets.UTF_8)
            }

            if (!result.contains("Success")) {
                throw JadbException("Could not install ${apk.file.name}: $result")
            }
        } finally {
            remoteFiles.values.forEach(::removeRemoteFile)
        }
    }

    private fun removeRemoteFile(remoteFile: RemoteFile) {
        device.executeShell("rm", "-f", remoteFile.path).use { }
    }

    private fun runPackageManager(block: PackageManager.() -> Unit) = try {
        packageManager.run(block)

        AdbInstallerResult.Success
    } catch (e: JadbException) {
        AdbInstallerResult.Failure(e)
    } catch (e: IOException) {
        AdbInstallerResult.Failure(e)
    }
}
