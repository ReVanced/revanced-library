package app.revanced.library.installation.command

import java.io.File
import java.io.InputStream
import java.util.logging.Logger

/**
 * [ShellCommandRunner] for running commands on a device.
 */
abstract class ShellCommandRunner internal constructor() {
    protected val logger: Logger = Logger.getLogger(this::class.java.name)

    /**
     * Writes the given [content] to the file at the given [targetFilePath] path.
     *
     * @param content The content of the file.
     * @param targetFilePath The target file path.
     */
    internal abstract fun write(
        content: InputStream,
        targetFilePath: String,
    )

    /**
     * Moves the given [file] to the given [targetFilePath] path.
     *
     * @param file The file to move.
     * @param targetFilePath The target file path.
     */
    internal abstract fun move(
        file: File,
        targetFilePath: String,
    )

    /**
     * Runs the given [command] on the device as root.
     *
     * @param command The command to run.
     * @return The [RunResult].
     */
    protected abstract fun runCommand(command: String): RunResult

    /**
     * Checks if the device has root permission.
     *
     * @return True if the device has root permission, false otherwise.
     */
    internal abstract fun hasRootPermission(): Boolean

    /**
     * Runs a command on the device as root.
     *
     * @param command The command to run.
     * @return The [RunResult].
     */
    internal operator fun invoke(
        command: String,
    ) = runCommand("su -c \'$command\'")
}
