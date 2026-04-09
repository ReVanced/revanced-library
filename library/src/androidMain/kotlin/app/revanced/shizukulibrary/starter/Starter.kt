package app.revanced.shizukulibrary.starter

import android.content.Context
import android.util.Log
import app.revanced.shizukulibrary.utils.ShizukuStateMachine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.io.File
import java.util.concurrent.TimeoutException

/**
 * Utility for building the Shizuku server start command and waiting for the binder to become available.
 */
object Starter {

    private const val TAG = "ShizukuStarter"

    /**
     * Returns the path to the native libshizuku.so binary.
     */
    fun getStarterFile(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, "libshizuku.so")

    /**
     * The shell command to start the Shizuku server.
     */
    fun getInternalCommand(context: Context): String {
        val starterFile = getStarterFile(context)
        return "${starterFile.absolutePath} --apk=${context.applicationInfo.sourceDir}"
    }

    /**
     * The ADB command that a user would type to start Shizuku manually.
     */
    fun getAdbCommand(context: Context): String {
        val starterFile = getStarterFile(context)
        return "adb shell ${starterFile.absolutePath} --apk=${context.applicationInfo.sourceDir}"
    }

    val serviceStartedMessage =
        "Service started, this window will be automatically closed in 3 seconds"

    /**
     * Waits for the Shizuku server binder to become available by observing [ShizukuStateMachine].
     * Times out after 60 seconds.
     *
     * @param log Optional logging callback for status messages.
     * @throws TimeoutException if the binder does not become available within the timeout.
     */
    suspend fun waitForBinder(log: ((String) -> Unit)? = null) {
        try {
            log?.invoke("\nWaiting for service. This may take up to 1 minute...")
            withTimeout(60_000) {
                ShizukuStateMachine.asFlow()
                    .first { it == ShizukuStateMachine.State.RUNNING }
            }
            log?.invoke("Service is running!")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Timed out waiting for Shizuku binder")
            ShizukuStateMachine.set(ShizukuStateMachine.State.DEAD)
            throw TimeoutException("Timed out waiting for Shizuku binder to become available")
        }
    }
}

