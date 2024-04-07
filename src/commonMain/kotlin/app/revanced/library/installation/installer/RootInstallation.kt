package app.revanced.library.installation.installer

/**
 * [RootInstallation] of the apk file that is mounted to [installedApkFilePath] with root permissions.
 *
 * @param installedApkFilePath The installed apk file path or null if the apk is not installed.
 * @param apkFilePath The mounting apk file path.
 * @param mounted Whether the apk is mounted to [installedApkFilePath].
 */
@Suppress("MemberVisibilityCanBePrivate")
class RootInstallation internal constructor(
    val installedApkFilePath: String?,
    apkFilePath: String,
    val mounted: Boolean,
) : Installation(apkFilePath)
