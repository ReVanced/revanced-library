package app.revanced.shizukulibrary.worker

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.EOFException
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import app.revanced.shizukulibrary.adb.AdbStarter
import app.revanced.shizukulibrary.receiver.ShizukuReceiverStarter
import app.revanced.shizukulibrary.receiver.ShizukuReceiverStarter.WorkerState
import app.revanced.shizukulibrary.starter.Starter
import app.revanced.shizukulibrary.utils.EnvironmentUtils
import app.revanced.shizukulibrary.utils.ShizukuStateMachine
import io.github.muntashirakon.adb.android.AdbMdns

/**
 * WorkManager worker that handles the ADB start process.
 *
 * This worker:
 * 1. Force-enables USB debugging via Settings.Global.ADB_ENABLED
 * 2. Force-enables wireless ADB via Settings.Global "adb_wifi_enabled"
 * 3. Discovers the wireless ADB port via mDNS (or uses TCP port)
 * 4. Connects via ADB and starts the Shizuku server
 * 5. Waits for the Shizuku binder to become available
 */
class AdbStartWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "AdbStartWorker"

        /**
         * Enqueues the ADB start worker with appropriate network constraints.
         * If Wi-Fi is required (Android 11+ wireless debugging), adds UNMETERED network constraint.
         */
        fun enqueue(context: Context) {
            val cb = Constraints.Builder()
            if (EnvironmentUtils.isWifiRequired()) {
                cb.setRequiredNetworkType(NetworkType.UNMETERED)
            }
            val constraints = cb.build()

            val request = OneTimeWorkRequestBuilder<AdbStartWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "adb_start_worker",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        try {
            // Initialize EnvironmentUtils so isTelevision() works
            EnvironmentUtils.init(applicationContext)

            ShizukuReceiverStarter.updateNotification(applicationContext, WorkerState.RUNNING)

            val cr = applicationContext.contentResolver

            // Step 1: Force-enable USB debugging (requires WRITE_SECURE_SETTINGS)
            Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
            Settings.Global.putLong(cr, "adb_allowed_connection_time", 0L)

            // Step 2: Check if already in TCP mode from before reboot
            val tcpPort = EnvironmentUtils.getAdbTcpPort()
            if (tcpPort > 0) {
                AdbStarter.stopTcp(applicationContext, tcpPort)
            }

            // Step 3: Discover the wireless ADB port via mDNS or use TCP port
            val port = tcpPort.takeIf { !EnvironmentUtils.isWifiRequired() } ?: callbackFlow {
                val adbMdns = AdbMdns(
                    applicationContext,
                    AdbMdns.SERVICE_TYPE_TLS_CONNECT
                ) { _, p ->
                    if (p > 0) trySend(p)
                }

                var awaitingAuth = false
                var timeoutJob: Job? = null
                var unlockReceiver: BroadcastReceiver? = null

                fun startDiscoveryWithTimeout() {
                    adbMdns.start()
                    timeoutJob?.cancel()
                    timeoutJob = launch {
                        delay(15_000)
                        close(TimeoutException("Timed out during mDNS port discovery"))
                    }
                }

                fun handleAuth() {
                    val km = applicationContext.getSystemService(Context.KEYGUARD_SERVICE)
                            as KeyguardManager
                    if (km.isKeyguardLocked) {
                        // Device is locked — wait for user to unlock before enabling wireless ADB
                        val notification = ShizukuReceiverStarter.buildNotification(
                            applicationContext, null
                        )
                        val foregroundInfo = ForegroundInfo(
                            ShizukuReceiverStarter.NOTIFICATION_ID,
                            notification
                        )
                        setForegroundAsync(foregroundInfo)

                        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
                        unlockReceiver = object : BroadcastReceiver() {
                            override fun onReceive(context: Context, intent: Intent) {
                                if (intent.action == Intent.ACTION_USER_PRESENT) {
                                    context.unregisterReceiver(this)
                                    unlockReceiver = null
                                    Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
                                }
                            }
                        }
                        applicationContext.registerReceiver(unlockReceiver, filter)
                    } else {
                        awaitingAuth = true
                    }
                    timeoutJob?.cancel()
                    adbMdns.stop()
                }

                val observer = object : ContentObserver(null) {
                    override fun onChange(selfChange: Boolean) {
                        when (Settings.Global.getInt(cr, "adb_wifi_enabled", 0)) {
                            0 -> if (awaitingAuth) {
                                close(SecurityException("Network is not authorized for wireless debugging"))
                            } else {
                                handleAuth()
                            }

                            1 -> startDiscoveryWithTimeout()
                        }
                    }
                }

                // Register observer BEFORE enabling wireless ADB to avoid missing the change
                cr.registerContentObserver(
                    Settings.Global.getUriFor("adb_wifi_enabled"),
                    false,
                    observer
                )

                // Force-enable wireless ADB
                Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
                startDiscoveryWithTimeout()

                awaitClose {
                    adbMdns.stop()
                    timeoutJob?.cancel()
                    cr.unregisterContentObserver(observer)
                    unlockReceiver?.let { applicationContext.unregisterReceiver(it) }
                }
            }.first()

            // Step 4: Connect via ADB and start the Shizuku server
            AdbStarter.startAdb(applicationContext, port)

            // Step 5: Wait for the Shizuku binder to become available
            Starter.waitForBinder()

            // Dismiss notification on success
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager
            nm.cancel(ShizukuReceiverStarter.NOTIFICATION_ID)

            return Result.success()
        } catch (e: CancellationException) {
            val state = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                WorkerState.AWAITING_RETRY
            } else {
                when (stopReason) {
                    WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY -> WorkerState.AWAITING_WIFI
                    WorkInfo.STOP_REASON_CANCELLED_BY_APP -> WorkerState.STOPPED
                    else -> WorkerState.AWAITING_RETRY
                }
            }
            ShizukuReceiverStarter.updateNotification(applicationContext, state)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "ADB start failed", e)

            val ignored = listOf(
                EOFException::class,
                SecurityException::class,
                TimeoutException::class
            )
            if (ignored.none { it.isInstance(e) }) {
                Log.e(TAG, "Unexpected error during ADB start", e)
            }

            if (ShizukuStateMachine.update() == ShizukuStateMachine.State.RUNNING) {
                return Result.success()
            } else {
                ShizukuReceiverStarter.updateNotification(
                    applicationContext,
                    WorkerState.AWAITING_RETRY
                )
                return Result.retry()
            }
        }
    }
}

