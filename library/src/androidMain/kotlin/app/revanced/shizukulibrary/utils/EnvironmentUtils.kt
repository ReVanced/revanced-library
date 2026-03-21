package app.revanced.shizukulibrary.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Build

/**
 * Environment utility helpers for detecting ADB TCP mode, Wi-Fi requirements, and TV devices.
 */
object EnvironmentUtils {

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Returns the currently configured ADB TCP port from system property `service.adb.tcp.port`.
     * Returns -1 if ADB is not in TCP mode or the property is not set.
     */
    @SuppressLint("PrivateApi")
    fun getAdbTcpPort(): Int = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getMethod("getInt", String::class.java, Int::class.javaPrimitiveType)
        method.invoke(null, "service.adb.tcp.port", -1) as Int
    }.getOrDefault(-1)

    /**
     * Whether Wi-Fi / network connectivity is required for ADB connection.
     * Wi-Fi is required on Android 11+ (wireless debugging uses mDNS on a random port).
     * TVs always use wireless debugging even on older Android versions.
     * If ADB is already in TCP mode, Wi-Fi is NOT required (localhost connection suffices).
     */
    fun isWifiRequired(): Boolean {
        if (getAdbTcpPort() > 0) return false
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || isTelevision()
    }

    /**
     * Returns true if TLS-based wireless debugging is supported (Android 11+).
     */
    fun isTlsSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * Detects whether the device is a television (Android TV / Google TV).
     */
    fun isTelevision(): Boolean {
        val ctx = appContext ?: return false
        val uiMode = ctx.resources.configuration.uiMode
        return (uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    }
}
