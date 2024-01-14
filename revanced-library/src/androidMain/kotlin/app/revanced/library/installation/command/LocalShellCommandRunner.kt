package app.revanced.library.installation.command

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import app.revanced.library.IRootService
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.internal.BuilderImpl
import com.topjohnwu.superuser.ipc.RootService
import com.topjohnwu.superuser.nio.FileSystemManager
import java.io.Closeable
import java.io.File
import java.io.InputStream

/**
 * The [LocalShellCommandRunner] for running commands locally on the device.
 *
 * @param context The [Context] to use for binding to the [RootService].
 * @param onReady A callback to be invoked when [LocalShellCommandRunner] is ready to be used.
 * @throws IllegalStateException If the main shell was already created
 *
 * @see ShellCommandRunner
 */
class LocalShellCommandRunner(
    private val context: Context,
    private val onReady: () -> Unit
) : ShellCommandRunner(), ServiceConnection, Closeable {
    private var fileSystemManager: FileSystemManager? = null

    init {
        logger.info("Binding to RootService")
        val intent = Intent(context, LocalShellCommandRunnerRootService::class.java)
        RootService.bind(intent, this)
    }

    override fun runCommand(command: String) = shell.newJob().add(command).exec().let {
        object : RunResult {
            override val exitCode = it.code
            override val output by lazy { it.out.joinToString("\n") }
            override val error by lazy { it.err.joinToString("\n") }
        }
    }

    override fun hasRootPermission() = shell.isRoot

    /**
     * Writes the given [content] to the given [targetFilePath].
     *
     * @param content The [InputStream] to write.
     * @param targetFilePath The path to write to.
     * @throws NotReadyException If the [LocalShellCommandRunner] is not ready yet.
     */
    override fun write(content: InputStream, targetFilePath: String) {
        fileSystemManager?.let {
            it.getFile(targetFilePath).newOutputStream().use { outputStream ->
                content.copyTo(outputStream)
            }
        } ?: throw NotReadyException("FileSystemManager service is not ready yet")
    }

    override fun move(file: File, targetFilePath: String) {
        invoke("mv ${file.absolutePath} $targetFilePath")
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val ipc = IRootService.Stub.asInterface(service)
        val binder = ipc.fileSystemService

        fileSystemManager = FileSystemManager.getRemote(binder)

        logger.info("LocalShellCommandRunner service is ready")

        onReady()
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        fileSystemManager = null

        logger.info("LocalShellCommandRunner service is disconnected")
    }

    override fun close() = RootService.unbind(this)

    private companion object {
        private val shell = BuilderImpl.create().setFlags(Shell.FLAG_MOUNT_MASTER).build()
    }

    internal class NotReadyException internal constructor(message: String) : Exception(message)
}
