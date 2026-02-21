package app.revanced.library.installation.command

import app.revanced.library.installation.installer.getDevice
import se.vidstige.jadb.JadbDevice
import se.vidstige.jadb.RemoteFile
import java.io.File
import java.io.InputStream

/**
 * [AdbShellCommandRunner] for running commands on a device remotely using ADB.
 *
 * @see ShellCommandRunner
 */
class AdbShellCommandRunner : ShellCommandRunner {
    private val device: JadbDevice

    /**
     * Creates a [AdbShellCommandRunner] for the given device.
     *
     * @param device The device.
     */
    internal constructor(device: JadbDevice) {
        this.device = device
    }

    /**
     * Creates a [AdbShellCommandRunner] for the device with the given serial.
     *
     * @param deviceSerial deviceSerial The device serial. If null, the first connected device will be used.
     */
    internal constructor(deviceSerial: String?) {
        device = getDevice(deviceSerial, logger)
    }

    override fun runCommand(command: String) = device.shellProcessBuilder(command).start().let { process ->
        object : RunResult {
            override val exitCode by lazy { process.waitFor() }
            override val output by lazy { process.inputStream.bufferedReader().readText() }
            override val error by lazy { process.errorStream.bufferedReader().readText() }

            override fun waitFor() {
                process.waitFor()
            }
        }
    }

    override fun hasRootPermission(): Boolean = invoke("whoami").exitCode == 0

    override fun write(content: InputStream, targetFilePath: String) =
        device.push(content, System.currentTimeMillis(), 644, RemoteFile(targetFilePath))

    /**
     * Moves the given [file] from the local to the target file path on the device.
     *
     * @param file The file to move.
     * @param targetFilePath The target file path.
     */
    override fun move(file: File, targetFilePath: String) = device.push(file, RemoteFile(targetFilePath))
}
