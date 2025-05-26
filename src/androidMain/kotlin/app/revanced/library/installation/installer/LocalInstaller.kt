package app.revanced.library.installation.installer

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import app.revanced.library.installation.installer.Installer.Apk
import java.io.Closeable
import java.io.File

/**
 * [LocalInstaller] for installing and uninstalling [Apk] files locally.
 *
 * @param context The [Context] to use for installing and uninstalling.
 * @param onResult The callback to be invoked when the [Apk] is installed or uninstalled.
 *
 * @see Installer
 */
@Suppress("unused")
class LocalInstaller(
    private val context: Context,
    onResult: (result: LocalInstallerResult) -> Unit,
) : Installer<Unit, Installation>(), Closeable {
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pmStatus = intent.getIntExtra(LocalInstallerService.EXTRA_STATUS, -999)
            val extra = intent.getStringExtra(LocalInstallerService.EXTRA_STATUS_MESSAGE)!!
            val packageName = intent.getStringExtra(LocalInstallerService.EXTRA_PACKAGE_NAME)!!

            onResult.invoke(LocalInstallerResult(pmStatus, extra, packageName))
        }
    }

    private val intentSender
        get() = PendingIntent.getService(
            context,
            0,
            Intent(context, LocalInstallerService::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT,
        ).intentSender

    init {
        ContextCompat.registerReceiver(
            context,
            broadcastReceiver,
            IntentFilter().apply {
                addAction(LocalInstallerService.ACTION)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override suspend fun install(apk: Apk) {
        logger.info("Installing ${apk.file.name}")

        val packageInstaller = context.packageManager.packageInstaller

        packageInstaller.openSession(packageInstaller.createSession(sessionParams)).use { session ->
            session.writeApk(apk.file)
            session.commit(intentSender)
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun uninstall(packageName: String) {
        logger.info("Uninstalling $packageName")

        val packageInstaller = context.packageManager.packageInstaller

        packageInstaller.uninstall(packageName, intentSender)
    }

    override suspend fun getInstallation(packageName: String) = try {
        val packageInfo = context.packageManager.getPackageInfo(packageName, 0)

        Installation(packageInfo.applicationInfo.sourceDir)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    override fun close() = context.unregisterReceiver(broadcastReceiver)

    @SuppressLint("MissingPermission")
    companion object {
        private val sessionParams = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                setRequestUpdateOwnership(true)
            setInstallReason(PackageManager.INSTALL_REASON_USER)
        }

        private fun PackageInstaller.Session.writeApk(apk: File) {
            apk.inputStream().use { inputStream ->
                openWrite(apk.name, 0, apk.length()).use { outputStream ->
                    inputStream.copyTo(outputStream, 1024 * 1024)
                    fsync(outputStream)
                }
            }
        }
    }
}
