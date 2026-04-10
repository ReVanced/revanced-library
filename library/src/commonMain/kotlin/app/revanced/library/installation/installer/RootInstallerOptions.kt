package app.revanced.library.installation.installer

import java.io.File

class RootInstallerOptions(
    patchedApk: Installer.Apk,
    val stockApk: Apk,
) : InstallerOptions(patchedApk)

class Apk(file: File, val packageName: String, val versionName: String) : Installer.Apk(file)