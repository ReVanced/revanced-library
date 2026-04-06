package app.revanced.library.installation.installer

import java.io.File

class RootInstallOptions(
    patchedApk: Installer.Apk,
    val stockApk: StockApk
) : InstallOptions(patchedApk)

class StockApk(file: File, val packageName: String, val versionCode: Int) : Installer.Apk(file)