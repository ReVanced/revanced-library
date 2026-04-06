package app.revanced.library.installation.installer

class RootInstallOptions(
    patchedApk: Installer.Apk.Patched,
    val stockApk: Installer.Apk.Stock
) : InstallOptions(patchedApk)