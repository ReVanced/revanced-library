package app.revanced.library.installation.command

import android.content.Intent
import app.revanced.library.IRootService
import com.topjohnwu.superuser.ipc.RootService
import com.topjohnwu.superuser.nio.FileSystemManager

/**
 * The [RootService] for the [LocalShellCommandRunner].
 */
internal class LocalShellCommandRunnerRootService : RootService() {
    override fun onBind(intent: Intent) = object : IRootService.Stub() {
        override fun getFileSystemService() =
            FileSystemManager.getService()
    }
}
