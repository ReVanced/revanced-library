package app.revanced.library.installation.installer

import android.content.Context
import app.revanced.library.installation.command.LocalShellCommandRunner
import com.topjohnwu.superuser.ipc.RootService
import java.io.Closeable

/**
 * [LocalMagiskInstaller] for installing and uninstalling [Apk] files locally with root permissions via Magisk modules.
 *
 * @param context The [Context] to use for binding to the [RootService].
 * @param onReady A callback to be invoked when [LocalMagiskInstaller] is ready to be used.
 *
 * @throws NoRootPermissionException If the device does not have root permission.
 *
 * @see Installer
 * @see LocalShellCommandRunner
 */
@Suppress("unused")
class LocalMagiskInstaller(
    context: Context,
    onReady: LocalMagiskInstaller.() -> Unit = {},
) : MagiskInstaller(
    { installer ->
        LocalShellCommandRunner(context) {
            (installer as LocalMagiskInstaller).onReady()
        }
    },
),
    Closeable {
    override fun close() = (shellCommandRunner as LocalShellCommandRunner).close()
}
