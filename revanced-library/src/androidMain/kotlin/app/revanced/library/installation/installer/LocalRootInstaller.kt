package app.revanced.library.installation.installer

import android.content.Context
import app.revanced.library.installation.command.LocalCommandRunner
import app.revanced.library.installation.installer.Installer.Apk

/**
 * [LocalRootInstaller] for installing and uninstalling [Apk] files locally with using root permissions by mounting.
 *
 * @param context The [Context] to use.
 * @see Installer
 */
@Suppress("unused")
class LocalRootInstaller(context: Context) : RootInstaller<LocalCommandRunner>(LocalCommandRunner(context))
