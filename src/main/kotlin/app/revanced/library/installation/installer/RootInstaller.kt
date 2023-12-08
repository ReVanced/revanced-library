package app.revanced.library.installation.installer

import app.revanced.library.installation.command.CommandRunner

/**
 * Root [Installer] for rooted devices.
 *
 * @param T The [CommandRunner] to use.
 * @param commandRunner The [CommandRunner] to use.
 */
abstract class RootInstaller<T : CommandRunner> internal constructor(commandRunner: T) :
    Installer<T>(commandRunner) {
    init {
        if (!commandRunner.hasRootPermission()) throw NoRootPermissionException()
    }

    override fun install(apk: Apk) {
        logger.info("Installing by mounting")

        val packageName = apk.packageName ?: throw PackageNameRequiredException()

        // Check if app is installed.
        commandRunner.run(Constants.GET_INSTALLED_PATH, packageName).inputStream!!.bufferedReader().readLine()
            .let { line ->
                if (line != null) return@let
                throw throw FailedToFindInstalledPackageException(packageName)
            }

        // Setup files.
        commandRunner.createFile(Constants.TMP_PATH, apk.file.inputStream())
        commandRunner.run("${Constants.CREATE_DIR} ${Constants.INSTALLATION_PATH}").waitFor()
        commandRunner.run(Constants.INSTALL_PATCHED_APK, packageName).waitFor()
        commandRunner.createFile(
            Constants.TMP_PATH,
            Constants.MOUNT_SCRIPT.applyReplacement(packageName).byteInputStream(),
        )

        // Install and run.
        commandRunner.run(Constants.INSTALL_MOUNT_SCRIPT, packageName).waitFor()
        commandRunner.run(Constants.MOUNT_SCRIPT_PATH, packageName).waitFor()
        commandRunner.run(Constants.RESTART, packageName)
        commandRunner.run(Constants.DELETE, Constants.TMP_PATH)

        super.install(apk)
    }

    override fun uninstall(packageName: String) {
        logger.info("Uninstalling $packageName by unmounting")

        commandRunner.run(Constants.UMOUNT, packageName)
        commandRunner.run(Constants.DELETE.applyReplacement(Constants.PATCHED_APK_PATH), packageName)
        commandRunner.run(Constants.DELETE, Constants.MOUNT_SCRIPT_PATH.applyReplacement(packageName))
        commandRunner.run(Constants.DELETE, Constants.TMP_PATH)
        commandRunner.run(Constants.KILL, packageName)

        super.uninstall(packageName)
    }

    private companion object {
        private fun CommandRunner.run(
            command: String,
            replaceWith: String,
        ) = run(command.applyReplacement(replaceWith))

        private fun String.applyReplacement(with: String) = replace(Constants.PLACEHOLDER, with)
    }

    class FailedToFindInstalledPackageException internal constructor(packageName: String) :
        Exception("Failed to find installed package \"$packageName\" because no activity was found")

    class PackageNameRequiredException internal constructor() : Exception("Package name is required")

    class NoRootPermissionException internal constructor() : Exception("No root permission")
}