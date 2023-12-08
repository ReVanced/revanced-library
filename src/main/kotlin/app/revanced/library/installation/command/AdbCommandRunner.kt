package app.revanced.library.installation.command

import se.vidstige.jadb.JadbConnection
import se.vidstige.jadb.JadbDevice
import se.vidstige.jadb.RemoteFile
import se.vidstige.jadb.ShellProcessBuilder
import java.io.File
import java.io.InputStream
import java.util.logging.Logger

/**
 * The [CommandRunner] for running commands on a device using ADB.
 *
 * @param device The [JadbDevice] to use.
 */
class AdbCommandRunner(val device: JadbDevice) : CommandRunner {
    /**
     * Creates a [AdbCommandRunner] for the device with the given serial.
     *
     * @param deviceSerial The device serial. If null, the first connected device will be used.
     */
    constructor(deviceSerial: String? = null) : this(
        with(JadbConnection().devices) {
            if (isEmpty()) throw DeviceNotFoundException()

            deviceSerial?.let {
                firstOrNull { it.serial == deviceSerial } ?: throw DeviceNotFoundException(
                    deviceSerial,
                )
            } ?: first().also {
                logger.warning("No device serial supplied. Using device with serial ${it.serial}")
            }
        }!!,
    )

    private fun buildCommand(
        command: String,
        root: Boolean = true,
    ): ShellProcessBuilder {
        if (root) return device.shellProcessBuilder("su -c \'$command\'")

        val args = command.split(" ") as ArrayList<String>
        val cmd = args.removeFirst()

        return device.shellProcessBuilder(cmd, *args.toTypedArray())
    }

    override fun run(
        command: String,
        root: Boolean,
    ) = object : CommandRunner.RunResult {
        private val result = buildCommand(command, root).start()

        override val exitCode get() = result.exitValue()
        override val inputStream = result.inputStream
        override val errorStream = result.errorStream
        override val outputStream = result.outputStream

        override fun waitFor() = result.waitFor()
    }

    override fun hasRootPermission() = run("whoami", true).waitFor() == 0

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

    override fun createFile(
        targetFile: String,
        content: InputStream,
    ) = device.push(
        content,
        System.currentTimeMillis(),
        644,
        RemoteFile(targetFile),
    )

    private companion object {
        private val logger: Logger = Logger.getLogger(AdbCommandRunner::class.java.name)
    }

    class DeviceNotFoundException internal constructor(deviceSerial: String? = null) :
        Exception(
            deviceSerial?.let {
                "The device with the ADB device serial \"$deviceSerial\" can not be found"
            } ?: "No ADB device found",
        )
}