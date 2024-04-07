@file:Suppress("DeprecatedCallableAddReplaceWith")

package app.revanced.library

import app.revanced.library.installation.command.AdbShellCommandRunner
import se.vidstige.jadb.JadbDevice
import se.vidstige.jadb.ShellProcessBuilder
import java.io.File

@Deprecated("Do not use this anymore. Instead use AdbCommandRunner.")
internal fun JadbDevice.buildCommand(
    command: String,
    su: Boolean = true,
): ShellProcessBuilder {
    if (su) return shellProcessBuilder("su -c \'$command\'")

    val args = command.split(" ") as ArrayList<String>
    val cmd = args.removeFirst()

    return shellProcessBuilder(cmd, *args.toTypedArray())
}

@Suppress("DEPRECATION")
@Deprecated("Use AdbShellCommandRunner instead.")
internal fun JadbDevice.run(
    command: String,
    su: Boolean = true,
) = buildCommand(command, su).start()

@Deprecated("Use AdbShellCommandRunner instead.")
internal fun JadbDevice.hasSu() = AdbShellCommandRunner(this).hasRootPermission()

@Deprecated("Use AdbShellCommandRunner instead.")
internal fun JadbDevice.push(
    file: File,
    targetFilePath: String,
) = AdbShellCommandRunner(this).move(file, targetFilePath)

@Deprecated("Use AdbShellCommandRunner instead.")
internal fun JadbDevice.createFile(
    targetFile: String,
    content: String,
) = AdbShellCommandRunner(this).write(content.byteInputStream(), targetFile)
