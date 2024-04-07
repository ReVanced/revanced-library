package app.revanced.library.installation.installer

import app.revanced.library.installation.installer.Installer.Apk

/**
 * The result of installing or uninstalling an [Apk] with root permissions using [RootInstaller].
 *
 * @see RootInstaller
 */
enum class RootInstallerResult {
    /**
     * The result of installing an [Apk] successfully.
     */
    SUCCESS,

    /**
     * The result of installing an [Apk] unsuccessfully.
     */
    FAILURE,
}
