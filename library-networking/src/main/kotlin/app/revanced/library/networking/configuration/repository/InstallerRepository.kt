package app.revanced.library.networking.configuration.repository

import app.revanced.library.installation.installer.Installer
import app.revanced.library.installation.installer.MountInstaller
import app.revanced.library.networking.models.App

abstract class InstallerRepository {
    /**
     * The installer to use for installing and uninstalling [App]s.
     */
    internal abstract val installer: Installer<*, *>

    /**
     * The root installer to use for mounting and unmounting [App]s.
     */
    internal open val mountInstaller: MountInstaller? = null
}
