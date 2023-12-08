package app.revanced.library.installation.command

import java.io.InputStream
import java.io.OutputStream

/**
 * The command runner. Used to run commands on a device.
 */
interface CommandRunner {
    /**
     * Runs a command on the device.
     *
     * @param command The command to run.
     * @param root Whether to run the command as root or not.
     * @return The [RunResult].
     */
    fun run(
        command: String,
        root: Boolean = true,
    ): RunResult

    /**
     * Checks if the device has root permission.
     *
     * @return True if the device has root permission, false otherwise.
     */
    fun hasRootPermission(): Boolean

    /**
     * Creates a file on the device.
     *
     * @param targetFile The path to the file.
     * @param content The content of the file.
     */
    fun createFile(
        targetFile: String,
        content: InputStream,
    )

    /**
     * The result of a command execution
     */
    interface RunResult {
        val exitCode: Int?

        val inputStream: InputStream?
        val errorStream: InputStream?
        val outputStream: OutputStream?

        fun waitFor(): Int?
    }

}
