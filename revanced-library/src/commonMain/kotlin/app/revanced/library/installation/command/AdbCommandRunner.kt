package app.revanced.library.installation.command

import app.revanced.library.installation.installer.Utils
import se.vidstige.jadb.JadbDevice
import se.vidstige.jadb.RemoteFile
import se.vidstige.jadb.ShellProcessBuilder
import java.io.File
import java.io.InputStream
import java.util.logging.Logger

/**
 * [AdbCommandRunner] for running commands on a device remotely using ADB.
 *
 * @see CommandRunner
 */
class AdbCommandRunner : CommandRunner {
    private val logger: Logger = Logger.getLogger(this::class.java.name)

    private val device: JadbDevice

    /**
     * Creates a [AdbCommandRunner] for the given device.
     *
     * @param device The device.
     */
    constructor(device: JadbDevice) {
        this.device = device
    }

    /**
     * Creates a [AdbCommandRunner] for the device with the given serial.
     *
     * @param deviceSerial deviceSerial The device serial. If null, the first connected device will be used.
     */
    constructor(deviceSerial: String?) {
        device = Utils.getDevice(deviceSerial, logger)
    }

    private fun buildCommand(
        command: String,
        root: Boolean = true,
    ): ShellProcessBuilder {
        if (root) return device.shellProcessBuilder("su -c \'$command\'")

        val args = command.split(" ") as ArrayList<String>
        val cmd = args.removeFirst()

        return device.shellProcessBuilder(cmd, *args.toTypedArray())
    }

    override operator fun invoke(
        command: String,
        root: Boolean,
    ) = buildCommand(command, root).start().let {
        object : RunResult {
            override val exitCode by lazy { it.exitValue() }
            override val output by lazy { it.inputStream.bufferedReader().readLines() }
            override val error by lazy { it.errorStream.bufferedReader().readLines() }

            override fun waitFor() = it.waitFor()
        }
    }

    override fun hasRootPermission() = invoke("whoami", true).exitCode == 0

    /**
     * Pushes a file to the device.
     *
     * @param file The file to push.
     * @param targetFilePath The path to push the file to.
     */
    fun push(
        file: File,
        targetFilePath: String,
    ) = device.push(file, RemoteFile(targetFilePath))

    override fun write(
        targetFile: String,
        content: InputStream,
    ) = device.push(
        content,
        System.currentTimeMillis(),
        644,
        RemoteFile(targetFile),
    )
}
