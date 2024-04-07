package app.revanced.library

/**
 * Utils for the library.
 */
@Suppress("unused")
object Utils {
    /**
     * True if the environment is Android.
     */
    val isAndroidEnvironment =
        try {
            Class.forName("android.app.Application")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
}
