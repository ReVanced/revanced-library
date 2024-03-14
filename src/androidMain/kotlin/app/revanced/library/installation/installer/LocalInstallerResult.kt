package app.revanced.library.installation.installer

import app.revanced.library.installation.installer.Installer.Apk

/**
 * The result of installing or uninstalling an [Apk] locally using [LocalInstaller].
 *
 * @param pmStatus The status code returned by the package manager.
 * @param extra The extra information returned by the package manager.
 * @param packageName The package name of the installed app.
 *
 * @see LocalInstaller
 */
class LocalInstallerResult internal constructor(val pmStatus: Int, val extra: String, val packageName: String)
