package app.revanced.library.adb

import app.revanced.library.adb.AdbManager.Apk
import app.revanced.library.installation.installer.Constants.PLACEHOLDER
import app.revanced.library.installation.installer.AdbInstaller
import app.revanced.library.installation.installer.AdbRootInstaller
import app.revanced.library.installation.installer.Installer
import se.vidstige.jadb.JadbDevice
import java.io.File

/**
 * Adb manager. Used to install and uninstall [Apk] files.
 *
 * @param deviceSerial The serial of the device. If null, the first connected device will be used.
 */
@Deprecated("Use an implementation of Installer<*> instead.")
@Suppress("unused")
sealed class AdbManager private constructor(
    @Suppress("UNUSED_PARAMETER") deviceSerial: String?,
) {
    protected abstract val installer: Installer<*>

    /**
     * Installs the [Apk] file.
     *
     * @param apk The [Apk] file.
     */
    open fun install(apk: Apk) = installer.install(Installer.Apk(apk.file, apk.packageName))

    /**
     * Uninstalls the package.
     *
     * @param packageName The package name.
     */
    open fun uninstall(packageName: String) = installer.uninstall(packageName)

    companion object {
        /**
         * Gets an [AdbManager] for the supplied device serial.
         *
         * @param deviceSerial The device serial. If null, the first connected device will be used.
         * @param root Whether to use root or not.
         * @return The [AdbManager].
         * @throws DeviceNotFoundException If the device can not be found.
         */
        @Deprecated("Use an implementation of Installer<*> instead.")
        fun getAdbManager(
            deviceSerial: String? = null,
            root: Boolean = false,
        ): AdbManager = if (root) RootAdbManager(deviceSerial) else UserAdbManager(deviceSerial)
    }

    /**
     * Adb manager for rooted devices.
     *
     * @param deviceSerial The device serial. If null, the first connected device will be used.
     */
    @Deprecated("Use Installer.RootInstaller.AdbRootInstaller instead.")
    class RootAdbManager internal constructor(deviceSerial: String?) : AdbManager(deviceSerial) {
        override val installer = AdbRootInstaller(deviceSerial)

        override fun install(apk: Apk) = installer.install(Installer.Apk(apk.file, apk.packageName))

        override fun uninstall(packageName: String) = installer.uninstall(packageName)

        companion object Utils {
            private fun JadbDevice.run(
                command: String,
                with: String,
            ) = run(command.applyReplacement(with))

            private fun String.applyReplacement(with: String) = replace(PLACEHOLDER, with)
        }
    }

    /**
     * Adb manager for non-rooted devices.
     *
     * @param deviceSerial The device serial. If null, the first connected device will be used.
     */
    @Deprecated("Use Installer.AdbInstaller instead.")
    class UserAdbManager internal constructor(deviceSerial: String?) : AdbManager(deviceSerial) {
        override val installer = AdbInstaller(deviceSerial)

        override fun install(apk: Apk) = installer.install(Installer.Apk(apk.file, apk.packageName))

        override fun uninstall(packageName: String) = installer.uninstall(packageName)
    }

    /**
     * Apk file for [AdbManager].
     *
     * @param file The [Apk] file.
     * @param packageName The package name of the [Apk] file.
     */
    @Deprecated("Use Installer.Apk instead.")
    class Apk(val file: File, val packageName: String? = null)

    @Deprecated("Use CommandRunner.DeviceNotFoundException instead.")
    class DeviceNotFoundException internal constructor(deviceSerial: String? = null) :
        Exception(
            deviceSerial?.let {
                "The device with the ADB device serial \"$deviceSerial\" can not be found"
            } ?: "No ADB device found",
        )

    @Deprecated("Use Installer.FailedToFindInstalledPackageException instead.")
    class FailedToFindInstalledPackageException internal constructor(packageName: String) :
        Exception("Failed to find installed package \"$packageName\" because no activity was found")

    @Deprecated("Use Installer.PackageNameRequiredException instead.")
    class PackageNameRequiredException internal constructor() :
        Exception("Package name is required")
}
