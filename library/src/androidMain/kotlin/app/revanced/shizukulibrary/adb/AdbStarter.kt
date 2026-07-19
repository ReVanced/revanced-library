package app.revanced.shizukulibrary.adb
import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import app.revanced.shizukulibrary.starter.Starter
import app.revanced.shizukulibrary.utils.EnvironmentUtils
import app.revanced.shizukulibrary.utils.ShizukuStateMachine
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketException
import kotlin.coroutines.cancellation.CancellationException

object AdbStarter {
    private const val TAG = "AdbStarter"

    /**
     * Connects with retry logic, since ADB daemon may be restarting (e.g. after TCP mode switch).
     */
    private suspend fun connectWithRetry(
        manager: AbsAdbConnectionManager,
        host: String,
        port: Int,
        maxAttempts: Int = 5,
    ) {
        var delayTime = 0L
        for (attempt in 1..maxAttempts) {
            try {
                delay(delayTime)
                manager.connect(host, port)
                return
            } catch (e: Exception) {
                if (attempt == maxAttempts || e is CancellationException) throw e
                if (e !is ConnectException && e !is EOFException && e !is SocketException) throw e
                delayTime += 1000
            }
        }
    }

    suspend fun startAdb(
        context: Context,
        port: Int,
        tcpMode: Boolean = true,
        tcpPort: Int = 5555,
        log: ((String) -> Unit)? = null,
    ) {
        try {
            ShizukuStateMachine.set(ShizukuStateMachine.State.STARTING)
            log?.invoke("Starting with wireless adb...\n")
            withContext(Dispatchers.IO) {
                val manager = AdbConnectionManager.getInstance(context)
                var activePort = port
                if (tcpMode && activePort != tcpPort) {
                    log?.invoke("Connecting on port $activePort...")
                    manager.connect("127.0.0.1", activePort)
                    log?.invoke("Successfully connected on port $activePort...")
                    log?.invoke("\nRestarting in TCP mode port: $tcpPort")
                    activePort = tcpPort
                    try {
                        manager.openStream("tcpip:$activePort").use { stream ->
                            stream.openInputStream().bufferedReader().readText()
                        }
                    } catch (_: EOFException) {
                        // Expected when ADB restarts in TCP mode
                    } catch (_: SocketException) {
                        // Expected when ADB restarts in TCP mode
                    }
                    manager.disconnect()
                    delay(2000)
                }
                log?.invoke("Connecting on port $activePort...")
                connectWithRetry(manager, "127.0.0.1", activePort)
                log?.invoke("Successfully connected on port $activePort...\n")
                val command = Starter.getInternalCommand(context)
                manager.openStream("shell:$command").use { stream ->
                    stream.openInputStream().bufferedReader().forEachLine { line ->
                        log?.invoke(line)
                    }
                }
                manager.disconnect()
            }
        } finally {
            if (context.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED) {
                Settings.Global.putInt(context.contentResolver, "adb_wifi_enabled", 0)
            }
        }
    }
    suspend fun stopTcp(context: Context, port: Int) {
        try {
            val cr = context.contentResolver
            if (context.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED) {
                Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
                Settings.Global.putLong(cr, "adb_allowed_connection_time", 0L)
            }
            val adbEnabled = Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0)
            if (adbEnabled == 0) throw IllegalStateException("ADB is not enabled")
            ShizukuStateMachine.set(ShizukuStateMachine.State.IDLE)
            val manager = AdbConnectionManager.getInstance(context)
            withContext(Dispatchers.IO) {
                connectWithRetry(manager, "127.0.0.1", port)
                try {
                    // "usb:" service switches ADB back to USB mode
                    manager.openStream("usb:").use { stream ->
                        stream.openInputStream().bufferedReader().readText()
                    }
                } catch (_: Exception) {}
                manager.disconnect()
            }
        } catch (e: Exception) {
            if (EnvironmentUtils.getAdbTcpPort() > 0) {
                ShizukuStateMachine.update()
            }
            Log.e(TAG, "Failed to stop TCP mode", e)
        }
    }
}
