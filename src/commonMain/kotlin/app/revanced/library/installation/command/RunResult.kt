package app.revanced.library.installation.command

import app.revanced.library.installation.command.AdbShellCommandRunner.ShellCmdFailure

/**
 * The result of a command execution.
 */
interface RunResult {
    /**
     * The exit code of the command.
     */
    val exitCode: Int

    /**
     * The output of the command.
     */
    val output: String

    /**
     * The error of the command.
     */
    val error: String

    /**
     * Waits for the command to finish.
     */
    fun waitFor() {}

    /**
     * Verifies whether the command exits with code 0.
     *
     * @throws ShellCmdFailure if given [RunResult] exited unsuccessfully.
     */
    fun ensureSuccess() {}
}
