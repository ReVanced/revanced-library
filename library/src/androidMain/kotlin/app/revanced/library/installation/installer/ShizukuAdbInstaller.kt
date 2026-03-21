package app.revanced.library.installation.installer

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import app.revanced.shizukulibrary.adb.AdbConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * [AdbInstaller] for installing and uninstalling [Installer.Apk] files via ADB.
 *
 * @param context The [Context] to use for string resources.
 * @param adbConnectionManager The [AdbConnectionManager] to use for ADB communication.
 * @param mapErrorMessage The function to map ADB output to a localized error message.
 * @param mapUninstallErrorMessage The function to map ADB uninstall output to a localized error message.
 */
class ShizukuAdbInstaller(
    private val context: Context,
    private val adbConnectionManager: AdbConnectionManager,
    private val mapErrorMessage: (String) -> String = { it },
    private val mapUninstallErrorMessage: (String) -> String = { it }
) : Installer<AdbInstallerResult, Installation>() {

    /**
     * Checks if the ADB connection is active.
     */
    fun isConnected(): Boolean = adbConnectionManager.isConnected

    override suspend fun install(apk: Apk): AdbInstallerResult = withContext(Dispatchers.IO) {
        val size = apk.file.length()
        Log.i("ShizukuAdbInstaller", "Installing ${apk.file.name} via ADB (size: $size bytes)")

        // Use exec:cmd package install for streaming
        try {
            adbConnectionManager.openStream("exec:cmd package install -r -t -S $size").use { stream ->
                Log.i("ShizukuAdbInstaller", "ADB installation stream opened")
                // Write APK bytes
                try {
                    stream.openOutputStream().use { os ->
                        Log.i("ShizukuAdbInstaller", "Writing APK bytes to ADB stream...")
                        apk.file.inputStream().use { fis ->
                            val bytesCopied = fis.copyTo(os)
                            Log.i("ShizukuAdbInstaller", "Successfully wrote $bytesCopied bytes to ADB stream")
                        }
                        os.flush()
                        Log.i("ShizukuAdbInstaller", "ADB stream flushed")
                    }
                } catch (e: Exception) {
                    // Log and continue, as the server might have already started processing
                    Log.w("ShizukuAdbInstaller", "Output stream closed during write: ${e.message}")
                }

                Log.i("ShizukuAdbInstaller", "Waiting for ADB installation response...")
                // Read response
                val output = StringBuilder()
                try {
                    stream.openInputStream().bufferedReader().use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            Log.i("ShizukuAdbInstaller", "ADB Output: $line")
                            output.append(line).append("\n")
                            if (line?.contains("Success", ignoreCase = true) == true || 
                                line?.contains("Failure", ignoreCase = true) == true) {
                                break
                            }
                        }
                    }
                } catch (e: IOException) {
                    // "Stream closed" is common if the server finishes abruptly after sending Success
                    Log.w("ShizukuAdbInstaller", "ADB input stream closed: ${e.message}")
                    if (output.isEmpty()) return@withContext AdbInstallerResult.Failure(e)
                }

                val result = output.toString().trim()
                Log.i("ShizukuAdbInstaller", "ADB Installation summary: $result")
                if (!result.contains("Success")) {
                    AdbInstallerResult.Failure(AdbInstallationException(mapErrorMessage(result), result))
                } else {
                    AdbInstallerResult.Success
                }
            }
        } catch (e: Exception) {
            Log.e("ShizukuAdbInstaller", "Failed to open ADB installation stream: ${e.message}", e)
            AdbInstallerResult.Failure(e)
        }
    }

    override suspend fun uninstall(packageName: String): AdbInstallerResult = withContext(Dispatchers.IO) {
        Log.i("ShizukuAdbInstaller", "Uninstalling $packageName via ADB")

        adbConnectionManager.openStream("shell:pm uninstall $packageName").use { stream ->
            val output = StringBuilder()
            try {
                stream.openInputStream().bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        output.append(line).append("\n")
                        if (line?.contains("Success", ignoreCase = true) == true || 
                            line?.contains("Failure", ignoreCase = true) == true) {
                            break
                        }
                    }
                }
            } catch (e: IOException) {
                // Ignore "Stream closed" if we already have some output
                Log.w("ShizukuAdbInstaller", "ADB uninstall stream error: ${e.message}")
                if (output.isEmpty()) return@withContext AdbInstallerResult.Failure(e)
            }

            val result = output.toString().trim()
            Log.i("ShizukuAdbInstaller", "ADB Uninstall summary: $result")
            if (!result.contains("Success") && result.isNotEmpty()) {
                val message = mapUninstallErrorMessage(result)
                AdbInstallerResult.Failure(AdbInstallationException(message, result))
            } else {
                AdbInstallerResult.Success
            }
        }
    }

    override suspend fun getInstallation(packageName: String): Installation? = try {
        val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
        Installation(packageInfo.applicationInfo!!.sourceDir)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    class AdbInstallationException(message: String, val output: String) : Exception(message)
}
