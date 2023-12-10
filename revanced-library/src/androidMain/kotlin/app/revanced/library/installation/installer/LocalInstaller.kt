package app.revanced.library.installation.installer

import android.content.Context
import app.revanced.library.installation.installer.Installer.Apk

/**
 * [LocalInstaller] for installing and uninstalling [Apk] files locally.
 *
 * @see Installer
 */
@Suppress("unused")
class LocalInstaller(private val context: Context) : Installer() {
    override fun install(apk: Apk) {
        logger.info("Installing ${apk.file.name}")

        // TODO: Implement

        super.install(apk)
    }

    override fun uninstall(packageName: String) {
        logger.info("Uninstalling $packageName")

        // TODO: Implement

        super.uninstall(packageName)
    }
}
