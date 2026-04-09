package app.revanced.shizukulibrary.utils

import android.os.Process

/**
 * Compat wrapper to obtain the current user ID.
 * On multi-user devices, user 0 is the primary user (owner).
 */
object UserHandleCompat {

    /**
     * Returns the user ID of the current process.
     * Uses reflection to call the hidden `UserHandle.myUserId()` method,
     * falling back to deriving it from the UID.
     */
    fun myUserId(): Int = runCatching {
        android.os.UserHandle::class.java
            .getMethod("myUserId")
            .invoke(null) as Int
    }.getOrElse {
        // Fallback: userId = uid / 100000
        Process.myUid() / 100_000
    }
}

