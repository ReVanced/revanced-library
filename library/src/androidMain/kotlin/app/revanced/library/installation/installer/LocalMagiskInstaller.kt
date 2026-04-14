package app.revanced.library.installation.installer

import android.content.Context
import app.revanced.library.installation.command.LocalShellCommandRunner
import com.topjohnwu.superuser.ipc.RootService
import java.io.Closeable

/**
 * [LocalMagiskRootInstaller] for installing and uninstalling [Apk] files locally with root permissions via Magisk modules.
 *
 * @param context The [Context] to use for binding to the [RootService].
 * @param onReady A callback to be invoked when [LocalMagiskRootInstaller] is ready to be used.
 *
 * @throws NoRootPermissionException If the device does not have root permission.
 *
 * @see Installer
 * @see LocalShellCommandRunner
 */
@Suppress("unused")
class LocalMagiskRootInstaller private constructor(
    context: Context,
    onReady: LocalMagiskRootInstaller.() -> Unit,
    private val readyHook: Array<(() -> Unit)?>,
) : MagiskRootInstaller(
    { LocalShellCommandRunner(context) { readyHook[0]?.invoke() } },
),
    Closeable {

    constructor(
        context: Context,
        onReady: LocalMagiskRootInstaller.() -> Unit = {},
    ) : this(context, onReady, arrayOfNulls(1))

    init {
        // The supplier passed to [MagiskRootInstaller] runs during super-init, before `this`
        // exists as a subclass reference, so the ready callback cannot capture it directly.
        // Instead we route through [readyHook], which is populated here — safe because
        // [LocalShellCommandRunner.onServiceConnected] fires asynchronously after IPC bind.
        readyHook[0] = { onReady() }
    }

    override fun close() = (shellCommandRunner as LocalShellCommandRunner).close()
}
