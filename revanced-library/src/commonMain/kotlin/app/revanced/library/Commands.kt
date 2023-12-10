@file:Suppress("DeprecatedCallableAddReplaceWith")

package app.revanced.library

import app.revanced.library.installation.command.AdbCommandRunner
import se.vidstige.jadb.JadbDevice
import se.vidstige.jadb.ShellProcessBuilder
import java.io.File

@Deprecated("Do not use this anymore. Instead use CommandRunner.AdbCommandRunner.")
internal fun JadbDevice.buildCommand(
    command: String,
    su: Boolean = true,
): ShellProcessBuilder {
    if (su) return shellProcessBuilder("su -c \'$command\'")

    val args = command.split(" ") as ArrayList<String>
    val cmd = args.removeFirst()

    return shellProcessBuilder(cmd, *args.toTypedArray())
}

@Deprecated("Use CommandRunner.AdbCommandRunner instead.")
internal fun JadbDevice.run(
    command: String,
    su: Boolean = true,
) = buildCommand(command, su).start()

@Deprecated("Use CommandRunner.AdbCommandRunner instead.")
internal fun JadbDevice.hasSu() = AdbCommandRunner(this).hasRootPermission()

@Deprecated("Use CommandRunner.AdbCommandRunner instead.")
internal fun JadbDevice.push(
    file: File,
    targetFilePath: String,
) = AdbCommandRunner(this).push(file, targetFilePath)

@Deprecated("Use CommandRunner.AdbCommandRunner instead.")
internal fun JadbDevice.createFile(
    targetFile: String,
    content: String,
) = AdbCommandRunner(this).write(targetFile, content)
