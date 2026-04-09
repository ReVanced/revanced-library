package app.revanced.shizukulibrary.receiver

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import app.revanced.shizukulibrary.utils.EnvironmentUtils
import app.revanced.shizukulibrary.utils.ShizukuStateMachine
import app.revanced.shizukulibrary.utils.UserHandleCompat
import app.revanced.shizukulibrary.worker.AdbStartWorker

/**
 * Decides how to start Shizuku based on the last launch mode and available permissions.
 */
object ShizukuReceiverStarter {

    private const val TAG = "ShizukuReceiverStarter"
    const val NOTIFICATION_ID = 1447
    private const val CHANNEL_ID = "AdbStartWorker"

    enum class WorkerState {
        AWAITING_WIFI,
        AWAITING_RETRY,
        RUNNING,
        STOPPED
    }

    /**
     * Entry point for starting Shizuku in the background.
     * Called from [BootCompleteReceiver] or any manual trigger.
     *
     * @param context Application context.
     * @param forceStart If true, skips checks for running state and user ID.
     */
    fun start(context: Context, forceStart: Boolean = false) {
        // Skip if not the primary user or already running
        if ((UserHandleCompat.myUserId() > 0 || ShizukuStateMachine.isRunning()) && !forceStart) return

        val launchMode =
            context.getSharedPreferences("revanced_prefs", Context.MODE_PRIVATE)
                .getInt("last_launch_mode", 0)

        if (launchMode == 0) {
            // ADB mode requires Android 11+ OR TV OR existing TCP port
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                || EnvironmentUtils.isTelevision()
                || EnvironmentUtils.getAdbTcpPort() > 0
            ) {
                if (context.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED) {
                    AdbStartWorker.enqueue(context)
                    updateNotification(context, WorkerState.AWAITING_WIFI)
                } else {
                    Log.w(TAG, "WRITE_SECURE_SETTINGS not granted, cannot auto-start ADB")
                    showPermissionErrorNotification(context)
                }
            } else {
                Log.w(TAG, "Background ADB start not supported on this device/Android version")
            }
        } else {
            Log.w(TAG, "Last launch mode was not ADB, background start not supported by this library")
        }
    }

    /**
     * Builds a notification for the ADB start progress.
     */
    fun buildNotification(context: Context, msg: String? = null): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Shizuku ADB Start",
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)

        val nb = NotificationCompat.Builder(context, CHANNEL_ID)

        if (msg != null) nb.setContentText(msg)

        return nb
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Starting Shizuku")
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    /**
     * Updates the ongoing notification based on the current worker state.
     */
    fun updateNotification(context: Context, state: WorkerState) {
        if (state == WorkerState.STOPPED) return

        val msg = when (state) {
            WorkerState.AWAITING_WIFI -> "Waiting for Wi-Fi connection..."
            WorkerState.AWAITING_RETRY -> "Retrying..."
            WorkerState.RUNNING -> "Starting Shizuku service..."
            else -> null
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(context, msg))
    }

    /**
     * Shows a notification indicating that WRITE_SECURE_SETTINGS is missing.
     */
    private fun showPermissionErrorNotification(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Shizuku ADB Start",
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)

        val msg = "WRITE_SECURE_SETTINGS permission is not granted. " +
                "Run: adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Shizuku: Permission Required")
            .setContentText(msg)
            .setSilent(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }
}

