package app.revanced.library.installation.command

import java.io.InputStream

/**
 * [CommandRunner] for running commands on a device.
 */
interface CommandRunner {
    /**
     * Runs a command on the device.
     *
     * @param command The command to run.
     * @param root Whether to run the command as root or not.
     * @return The [RunResult].
     */
    operator fun invoke(
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
     * Writes the given [content] to the file at the given [targetFile].
     *
     * @param targetFile The path to the file.
     * @param content The content of the file.
     */
    fun write(
        targetFile: String,
        content: InputStream,
    )

    /**
     * Writes the given [content] to the file at the given [targetFile].
     *
     * @param targetFile The path to the file.
     * @param content The content of the file.
     */
    fun write(
        targetFile: String,
        content: String,
    ) = write(targetFile, content.byteInputStream())
}
