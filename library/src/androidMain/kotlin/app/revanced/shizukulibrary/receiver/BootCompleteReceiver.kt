package app.revanced.shizukulibrary.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives ACTION_BOOT_COMPLETED after device reboot and triggers the Shizuku start chain.
 *
 * This receiver is **disabled by default** in the manifest (`android:enabled="false"`)
 * and should be toggled on/off programmatically via `PackageManager.setComponentEnabledSetting`.
 */
class BootCompleteReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompleteReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "Boot completed, starting Shizuku receiver starter")
        ShizukuReceiverStarter.start(context)
    }
}

