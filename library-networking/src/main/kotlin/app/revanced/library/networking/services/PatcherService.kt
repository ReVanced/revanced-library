package app.revanced.library.networking.services

import app.revanced.library.ApkUtils
import app.revanced.library.ApkUtils.applyTo
import app.revanced.library.installation.installer.Installer
import app.revanced.library.networking.configuration.repository.AppRepository
import app.revanced.library.networking.configuration.repository.InstallerRepository
import app.revanced.library.networking.configuration.repository.PatchSetRepository
import app.revanced.library.networking.configuration.repository.StorageRepository
import app.revanced.library.networking.models.App
import app.revanced.library.networking.models.Patch
import app.revanced.patcher.Patcher
import app.revanced.patcher.PatcherConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.logging.Logger

/**
 * Service for patching and installing apps.
 *
 * @property storageRepository The storage repository to get storage paths from.
 * @property patchSetRepository The patch set repository to get patches from.
 * @property appRepository The app repository to get installed apps from.
 * @property installerRepository The installer repository to install apps with.
 */
internal class PatcherService(
    private val storageRepository: StorageRepository,
    private val patchSetRepository: PatchSetRepository,
    private val appRepository: AppRepository,
    private val installerRepository: InstallerRepository,
) {
    private val logger = Logger.getLogger(PatcherService::class.simpleName)

    /**
     * Get installed apps.
     *
     * @param universal Whether to show apps that only have universal patches.
     *
     * @return The installed apps.
     */
    internal fun getInstalledApps(universal: Boolean = true): Set<App> {
        // TODO: Show apps, that only have universal patches, only if universal is true.
        return appRepository.installedApps
    }

    /**
     * Get patches.
     *
     * @param app The app to get patches for.
     * @param version The version of the app to get patches for.
     * @param universal Whether to show patches that are compatible with all apps.
     *
     * @return The patches.
     */
    internal fun getPatches(
        app: String? = null,
        version: String? = null,
        universal: Boolean = true,
    ) = if (app != null) {
        patchSetRepository.patchSet.filter { patch ->
            patch.compatiblePackages?.any { pkg ->
                pkg.name == app && (version == null || pkg.versions?.contains(version) ?: false)
            } ?: universal
        }
    } else {
        patchSetRepository.patchSet.filter { patch ->
            patch.compatiblePackages != null || universal
        }
    }.map { patch ->
        Patch(
            patch.name!!,
            patch.description,
            patch.use,
            patch.compatiblePackages?.associate { pkg -> pkg.name to pkg.versions },
        )
    }.toSet()

    /**
     * Patch an app.
     * Due to the likely-hood, that patches for the same app have the same name, duplicates are unhandled.
     *
     * @param patchNames The names of the patches to apply.
     * @param multithreading Whether to use multi-threading for dex file writing.
     * @param apkFile The APK file to patch.
     */
    internal suspend fun patch(
        patchNames: Set<String>,
        multithreading: Boolean = false,
        apkFile: File,
    ) = Patcher(
        PatcherConfig(
            apkFile = apkFile,
            temporaryFilesPath = storageRepository.temporaryFilesPath,
            aaptBinaryPath = storageRepository.aaptBinaryPath?.absolutePath,
            frameworkFileDirectory = storageRepository.temporaryFilesPath.absolutePath,
            multithreadingDexFileWriter = multithreading,
        ),
    ).use { patcher ->
        val packageName = patcher.context.packageMetadata.packageName

        patcher.apply {
            acceptPatches(
                patchSetRepository.patchSet.filter { patch ->
                    patch.name in patchNames && patch.compatiblePackages?.any { it.name == packageName } ?: true
                }.toSet(),
            )

            // TODO: Only accept integrations from patch bundles that contain selected patches.
            acceptIntegrations(
                storageRepository.patchBundles.values.mapNotNull {
                    it.patchBundleIntegrationsFile
                }.toSet(),
            )
        }

        patcher.apply(false).collect { patchResult ->
            patchResult.exception?.let {
                StringWriter().use { writer ->
                    it.printStackTrace(PrintWriter(writer))
                    logger.severe("${patchResult.patch.name} failed:\n$writer")
                }
            } ?: logger.info("${patchResult.patch.name} succeeded")
        }

        patcher.get()
    }.let { patcherResult ->
        apkFile.copyTo(storageRepository.unsignedApkFilePath, overwrite = true).apply {
            patcherResult.applyTo(this)
        }
    }

    /**
     * Sign an APK.
     *
     * @param signer The signer to use.
     * @param keyStorePassword The password of the keystore.
     * @param keyStoreEntryAlias The alias of the keystore entry.
     * @param keyStoreEntryPassword The password of the keystore entry.
     */
    internal fun sign(
        signer: String,
        keyStorePassword: String?,
        keyStoreEntryAlias: String,
        keyStoreEntryPassword: String,
    ) = ApkUtils.signApk(
        storageRepository.unsignedApkFilePath,
        storageRepository.outputFilePath,
        signer,
        ApkUtils.KeyStoreDetails(
            storageRepository.keystoreFilePath,
            keyStorePassword,
            keyStoreEntryAlias,
            keyStoreEntryPassword,
        ),
    )

    /**
     * Install an APK.
     *
     * @param mount The package name to mount the APK to.
     */
    internal suspend fun install(mount: String?) {
        if (mount != null) {
            if (installerRepository.mountInstaller == null) {
                throw IllegalArgumentException("Mount installer not available")
            }

            installerRepository.mountInstaller!! to Installer.Apk(
                storageRepository.unsignedApkFilePath,
                packageName = mount,
            )
        } else {
            installerRepository.installer to Installer.Apk(storageRepository.outputFilePath)
        }.let { (installer, apk) ->
            installer.install(apk)
        }
    }

    /**
     * Uninstall an APK.
     *
     * @param packageName The package name of the APK to uninstall.
     * @param unmount Whether to uninstall a mounted APK.
     */
    internal suspend fun uninstall(packageName: String, unmount: Boolean) = if (unmount) {
        installerRepository.mountInstaller!!
    } else {
        installerRepository.installer
    }.uninstall(packageName)

    /**
     * Get patch options from [PatchSetRepository.patchSet].
     * The [app] parameter is necessary in case there are patches with the same name.
     * Due to the likely-hood, that patches for the same app have the same name, duplicates are unhandled.
     *
     * @param patchName The name of the patch to get options for.
     * @param app The app to get options for.
     *
     * @return The patch options for the patch.
     */
    internal fun getPatchOptions(patchName: String, app: String) = patchSetRepository.patchSet.single { patch ->
        patch.name == patchName && patch.compatiblePackages?.any { it.name == app } ?: true
    }.options.map { (key, option) ->
        Patch.PatchOption(
            key,
            option.default,
            option.values,
            option.title,
            option.description,
            option.required,
            option.valueType,
        )
    }.toSet()

    /**
     * Set patch options.
     * The [app] parameter is necessary in case there are patches with the same name.
     * Due to the likely-hood, that patches for the same app have the same name, duplicates are unhandled.
     *
     * @param patchOptions The options to set.
     * @param patchName The name of the patch to set options for.
     * @param app The app to set options for.
     */
    internal fun setPatchOptions(
        patchOptions: Set<Patch.KeyValuePatchOption<*>>,
        patchName: String,
        app: String,
    ) = patchSetRepository.patchSet.single { patch ->
        patch.name == patchName && patch.compatiblePackages?.any { it.name == app } ?: true
    }.options.let { options ->
        patchOptions.forEach { option ->
            options[option.key] = option.value
        }
    }

    /**
     * Reset patch options and persist them to the storage.
     *
     * @param patchName The name of the patch to reset options for.
     * @param app The app to reset options for.
     */
    internal fun resetPatchOptions(patchName: String, app: String) {
        patchSetRepository.patchSet.single { patch ->
            patch.name == patchName && patch.compatiblePackages?.any { it.name == app } ?: true
        }.options.forEach { (_, option) -> option.reset() }
    }

    internal fun deleteTemporaryFiles() = storageRepository.deleteTemporaryFiles()
}
