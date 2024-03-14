package app.revanced.library.installation.installer

import android.app.Service
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.IBinder

class LocalInstallerService : Service() {
    override fun onStartCommand(
        intent: Intent, flags: Int, startId: Int
    ): Int {
        val extraStatus = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, 0)
        val extraStatusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val extraPackageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)

        when (extraStatus) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                startActivity(
                    if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }

            else -> {
                sendBroadcast(
                    Intent().apply {
                        action = ACTION
                        `package` = packageName

                        putExtra(EXTRA_STATUS, extraStatus)
                        putExtra(EXTRA_STATUS_MESSAGE, extraStatusMessage)
                        putExtra(EXTRA_PACKAGE_NAME, extraPackageName)
                    }
                )
            }
        }

        stopSelf()

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    internal companion object {
        internal const val ACTION = "PACKAGE_INSTALLER_ACTION"

        internal const val EXTRA_STATUS = "EXTRA_STATUS"
        internal const val EXTRA_STATUS_MESSAGE = "EXTRA_STATUS_MESSAGE"
        internal const val EXTRA_PACKAGE_NAME = "EXTRA_PACKAGE_NAME"
    }
}
