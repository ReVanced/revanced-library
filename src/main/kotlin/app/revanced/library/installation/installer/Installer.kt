package app.revanced.library.installation.installer

import app.revanced.library.installation.command.CommandRunner
import app.revanced.library.installation.installer.Installer.Apk
import java.io.File
import java.util.logging.Logger

/**
 * [Installer] for installing and uninstalling [Apk] files.
 *
 * @param T The [CommandRunner] to use.
 * @param commandRunner The [CommandRunner] to use.
 */
@Suppress("unused")
abstract class Installer<T : CommandRunner>(protected val commandRunner: T) {
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

    companion object {
        /**
         * Gets the [Installer] for the device.
         *
         * @param deviceSerial The device serial. If null, the first connected device will be used.
         * @param root Whether to use [RootInstaller] or [Installer].
         * @return The [Installer].
         * @see RootInstaller
         * @see Installer
         * @see AdbRootInstaller
         * @see AdbInstaller
         */
        fun getAdbInstaller(
            deviceSerial: String? = null,
            root: Boolean = false,
        ) = if (root) AdbRootInstaller(deviceSerial) else AdbInstaller(deviceSerial)
    }

    /**
     * Apk file for [Installer].
     *
     * @param file The [Apk] file.
     * @param packageName The package name of the [Apk] file.
     */
    class Apk(val file: File, val packageName: String? = null)
}
