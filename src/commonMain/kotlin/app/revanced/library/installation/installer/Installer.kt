package app.revanced.library.installation.installer

import app.revanced.library.installation.installer.Installer.Apk
import java.io.File
import java.util.logging.Logger

/**
 * [Installer] for installing and uninstalling [Apk] files.
 */
abstract class Installer<T> {
    /**
     * The [Logger].
     */
    protected val logger: Logger = Logger.getLogger(this::class.java.name)

    /**
     * Installs the [Apk] file.
     *
     * @param apk The [Apk] file.
     *
     * @return The result of the installation.
     */
    abstract suspend fun install(apk: Apk): T

    /**
     * Uninstalls the package.
     *
     * @param packageName The package name.
     *
     * @return The result of the uninstallation.
     */
    abstract suspend fun uninstall(packageName: String): T

    /**
     * Apk file for [Installer].
     *
     * @param file The [Apk] file.
     * @param packageName The package name of the [Apk] file.
     */
    class Apk(val file: File, val packageName: String? = null)
}
