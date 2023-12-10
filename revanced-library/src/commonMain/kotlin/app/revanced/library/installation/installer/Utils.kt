package app.revanced.library.installation.installer

import se.vidstige.jadb.JadbConnection
import java.util.logging.Logger

/**
 * Utility functions for [Installer].
 *
 * @see Installer
 */
internal object Utils {
    /**
     * Gets the device with the given serial.
     *
     * @param deviceSerial The device serial. If null, the first connected device will be used.
     * @param logger The logger.
     * @return The device.
     * @throws DeviceNotFoundException If no device with the given serial is found.
     */
    fun getDevice(
        deviceSerial: String? = null,
        logger: Logger,
    ) = with(JadbConnection().devices) {
        if (isEmpty()) throw DeviceNotFoundException()

        deviceSerial?.let {
            firstOrNull { it.serial == deviceSerial } ?: throw DeviceNotFoundException(
                deviceSerial,
            )
        } ?: first().also {
            logger.warning("No device serial supplied. Using device with serial ${it.serial}")
        }
    }!!

    class DeviceNotFoundException internal constructor(deviceSerial: String? = null) : Exception(
        deviceSerial?.let {
            "The device with the ADB device serial \"$deviceSerial\" can not be found"
        } ?: "No ADB device found",
    )
}
