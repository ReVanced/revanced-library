package app.revanced.library.installation.installer

import android.content.Context
import app.revanced.library.installation.command.LocalShellCommandRunner
import com.topjohnwu.superuser.ipc.RootService
import java.io.Closeable

/**
 * [LocalRootInstaller] for installing and uninstalling [Apk] files locally with using root permissions by mounting.
 *
 * @param context The [Context] to use for binding to the [RootService].
 * @param onReady A callback to be invoked when [LocalRootInstaller] is ready to be used.
 *
 * @throws NoRootPermissionException If the device does not have root permission.
 *
 * @see Installer
 * @see LocalShellCommandRunner
 */
@Suppress("unused")
class LocalRootInstaller private constructor(
    context: Context,
    onReady: LocalRootInstaller.() -> Unit,
    private val readyHook: Array<(() -> Unit)?>,
) : RootInstaller(
    LocalShellCommandRunner(context) { readyHook[0]?.invoke() }
),
    Closeable {

    constructor(
        context: Context,
        onReady: LocalRootInstaller.() -> Unit = {},
    ) : this(context, onReady, arrayOfNulls(1))

    init {
        // `this` doesn't exist as a subclass reference until after super-init, so the
        // ready callback cannot capture it directly in the constructor argument above.
        // Routing through [readyHook] is safe because [LocalShellCommandRunner.onServiceConnected]
        // fires asynchronously after IPC bind — well after this init block completes.
        readyHook[0] = { onReady() }
    }

    override fun close() = (shellCommandRunner as LocalShellCommandRunner).close()
}
