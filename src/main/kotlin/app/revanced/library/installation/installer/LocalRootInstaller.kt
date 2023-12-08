package app.revanced.library.installation.installer

import app.revanced.library.installation.command.LocalCommandRunner

/**
 * [LocalRootInstaller] for rooted devices.
 *
 * @param T The [LocalCommandRunner] to use.
 * @param localCommandRunner The [LocalCommandRunner] to use.
 * @see RootInstaller
 */
class LocalRootInstaller<T : LocalCommandRunner>(localCommandRunner: T) :
    RootInstaller<T>(localCommandRunner) {
    init {
        logger.fine("Connected to local device")
    }
}