package app.revanced.library.installation.installer

import app.revanced.library.installation.command.LocalCommandRunner

/**
 * [LocalInstaller] for non-rooted devices.
 *
 * @param T The [LocalCommandRunner] to use.
 * @param localCommandRunner The [LocalCommandRunner] to use.
 * @see Installer
 */
abstract class LocalInstaller<T : LocalCommandRunner>(localCommandRunner: T) :
    Installer<T>(localCommandRunner)