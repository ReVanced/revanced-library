package app.revanced.library.installation.command

import aidl.app.revanced.library.IRootService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import app.revanced.library.installation.installer.NoRootPermissionException
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.internal.BuilderImpl
import com.topjohnwu.superuser.ipc.RootService
import com.topjohnwu.superuser.nio.FileSystemManager
import java.io.InputStream

/**
 * The [LocalCommandRunner] for running commands locally on the device.
 *
 * @param context The [Context] to use for binding to the [RootService] or writing files.
 */
class LocalCommandRunner(private val context: Context) : CommandRunner, ServiceConnection {
    private var fileSystemManager: FileSystemManager? = null

    init {
        val shellBuilder = BuilderImpl.create().setFlags(Shell.FLAG_MOUNT_MASTER)
        Shell.setDefaultBuilder(shellBuilder)

        // TODO: Wait for the shell to be ready.

        val intent = Intent(context, LocalCommandRunnerRootService::class.java)
        RootService.bind(intent, this)
    }

    override operator fun invoke(
        command: String,
        root: Boolean,
    ): RunResult {
        if (root && !hasRootPermission()) throw NoRootPermissionException()

        return Shell.cmd(command).exec().let {
            object : RunResult {
                override val exitCode = it.code
                override val output = it.out
                override val error = it.err

                override fun waitFor() = exitCode
            }
        }
    }

    override fun hasRootPermission() = Shell.isAppGrantedRoot() ?: false

    override fun write(
        targetFile: String,
        content: InputStream,
    ) {
        if (!hasRootPermission())
            context.getFileStreamPath(targetFile).outputStream().apply(content::copyTo).close()
        else fileSystemManager!!.getFile(targetFile).newOutputStream().use { outputStream ->
            content.copyTo(outputStream)
        }
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val ipc = IRootService.Stub.asInterface(service)
        val binder = ipc.fileSystemService

        fileSystemManager = FileSystemManager.getRemote(binder)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        fileSystemManager = null
    }
}
