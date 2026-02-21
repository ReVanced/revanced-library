package app.revanced.library.installation.installer

/**
 * [Installation] of an apk file.
 *
 * @param apkFilePath The apk file path.
 */
@Suppress("MemberVisibilityCanBePrivate")
open class Installation internal constructor(
    val apkFilePath: String,
)
