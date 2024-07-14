package app.revanced.library.networking.configuration.repository

import app.revanced.library.networking.models.App

/**
 * A repository for apps and installers.
 */
abstract class AppRepository {
    /**
     * The set of [App] installed.
     */
    internal lateinit var installedApps: Set<App>
        private set

    init {
        readAndSetInstalledApps()
    }

    /**
     * Read a set of [App] from a storage.
     *
     * @return The set of [App] read.
     */
    internal abstract fun readInstalledApps(): Set<App>

    /**
     * Read a set of [App] using [readInstalledApps] and set [installedApps] to it.
     */
    internal fun readAndSetInstalledApps() {
        this.installedApps = readInstalledApps()
    }
}
