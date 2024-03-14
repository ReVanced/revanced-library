@file:Suppress("DEPRECATION")

package app.revanced.library

import app.revanced.library.AdbManager.Apk
import app.revanced.library.installation.installer.AdbInstaller
import app.revanced.library.installation.installer.AdbRootInstaller
import app.revanced.library.installation.installer.Constants.PLACEHOLDER
import app.revanced.library.installation.installer.Installer
import se.vidstige.jadb.JadbDevice
import java.io.File

/**
 * [AdbManager] to install and uninstall [Apk] files.
 *
 * @param deviceSerial The serial of the device. If null, the first connected device will be used.
 */
@Deprecated("Use an implementation of Installer instead.")
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
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Use Installer.install instead.")
    open fun install(apk: Apk) = suspend {
        installer.install(Installer.Apk(apk.file, apk.packageName))
    }

    /**
     * Uninstalls the package.
     *
     * @param packageName The package name.
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Use Installer.uninstall instead.")
    open fun uninstall(packageName: String) = suspend {
        installer.uninstall(packageName)
    }

    @Deprecated("Use Installer instead.")
    companion object {
        /**
         * Gets an [AdbManager] for the supplied device serial.
         *
         * @param deviceSerial The device serial. If null, the first connected device will be used.
         * @param root Whether to use root or not.
         * @return The [AdbManager].
         * @throws DeviceNotFoundException If the device can not be found.
         */
        @Suppress("DeprecatedCallableAddReplaceWith")
        @Deprecated("This is deprecated.")
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
    @Deprecated("Use AdbRootInstaller instead.", ReplaceWith("AdbRootInstaller(deviceSerial)"))
    class RootAdbManager internal constructor(deviceSerial: String?) : AdbManager(deviceSerial) {
        override val installer = AdbRootInstaller(deviceSerial)

        @Suppress("DeprecatedCallableAddReplaceWith")
        @Deprecated("Use AdbRootInstaller.install instead.")
        override fun install(apk: Apk) = suspend {
            installer.install(Installer.Apk(apk.file, apk.packageName))
        }

        @Suppress("DeprecatedCallableAddReplaceWith")
        @Deprecated("Use AdbRootInstaller.uninstall instead.")
        override fun uninstall(packageName: String) = suspend {
            installer.uninstall(packageName)
        }

        @Deprecated("This is deprecated.")
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
    @Deprecated("Use AdbInstaller instead.")
    class UserAdbManager internal constructor(deviceSerial: String?) : AdbManager(deviceSerial) {
        override val installer = AdbInstaller(deviceSerial)

        @Suppress("DeprecatedCallableAddReplaceWith")
        @Deprecated("Use AdbInstaller.install instead.")
        override fun install(apk: Apk) = suspend {
            installer.install(Installer.Apk(apk.file, apk.packageName))
        }

        @Suppress("DeprecatedCallableAddReplaceWith")
        @Deprecated("Use AdbInstaller.uninstall instead.")
        override fun uninstall(packageName: String) = suspend {
            installer.uninstall(packageName)
        }
    }

    /**
     * Apk file for [AdbManager].
     *
     * @param file The [Apk] file.
     * @param packageName The package name of the [Apk] file.
     */
    @Deprecated("Use Installer.Apk instead.")
    class Apk(val file: File, val packageName: String? = null)

    @Deprecated("Use AdbCommandRunner.DeviceNotFoundException instead.")
    class DeviceNotFoundException internal constructor(deviceSerial: String? = null) :
        Exception(
            deviceSerial?.let {
                "The device with the ADB device serial \"$deviceSerial\" can not be found"
            } ?: "No ADB device found",
        )

    @Deprecated("Use RootInstaller.FailedToFindInstalledPackageException instead.")
    class FailedToFindInstalledPackageException internal constructor(packageName: String) :
        Exception("Failed to find installed package \"$packageName\" because no activity was found")

    @Deprecated("Use RootInstaller.PackageNameRequiredException instead.")
    class PackageNameRequiredException internal constructor() :
        Exception("Package name is required")
}
