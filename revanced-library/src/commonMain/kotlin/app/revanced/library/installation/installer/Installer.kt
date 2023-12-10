package app.revanced.library.installation.installer

import app.revanced.library.installation.installer.Installer.Apk
import java.io.File
import java.util.logging.Logger

/**
 * [Installer] for installing and uninstalling [Apk] files.
 */
abstract class Installer {
    protected val logger: Logger = Logger.getLogger(this::class.java.name)

    /**
     * Installs the [Apk] file.
     *
     * @param apk The [Apk] file.
     */
    open fun install(apk: Apk) = logger.info("Installed ${apk.file.name}")

    /**
     * Uninstalls the package.
     *
     * @param packageName The package name.
     */
    open fun uninstall(packageName: String) = logger.info("Uninstalled $packageName")

    /**
     * Apk file for [Installer].
     *
     * @param file The [Apk] file.
     * @param packageName The package name of the [Apk] file.
     */
    class Apk(val file: File, val packageName: String? = null)
}
