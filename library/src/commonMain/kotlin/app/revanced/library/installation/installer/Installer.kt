package app.revanced.library.installation.installer

import java.io.File
import java.util.logging.Logger

/**
 * [Installer] for installing and uninstalling [Apk] files.
 *
 * @param TInstallerResult The type of the result of the installation.
 * @param TInstallation The type of the installation.
 */
abstract class Installer<TInstallerResult, TInstallation : Installation, TInstallerOptions : InstallerOptions> internal constructor() {
    /**
     * The [Logger].
     */
    protected val logger: Logger = Logger.getLogger(this::class.java.name)

    /**
     * Installs the [Apk] file.
     *
     * @param options The [InstallerOptions].
     *
     * @return The result of the installation.
     */
    abstract suspend fun install(options: TInstallerOptions): TInstallerResult

    /**
     * Uninstalls the package.
     *
     * @param packageName The package name.
     *
     * @return The result of the uninstallation.
     */
    abstract suspend fun uninstall(packageName: String): TInstallerResult

    /**
     * Gets the current installation or null if not installed.
     *
     * @param packageName The package name.
     *
     * @return The installation.
     */
    abstract suspend fun getInstallation(packageName: String): TInstallation?

    open class Apk(val file: File)
}
