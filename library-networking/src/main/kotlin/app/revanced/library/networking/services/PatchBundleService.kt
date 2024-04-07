package app.revanced.library.networking.services

import app.revanced.library.networking.configuration.repository.PatchSetRepository
import app.revanced.library.networking.configuration.repository.StorageRepository
import app.revanced.library.networking.models.PatchBundle
import java.io.File

/**
 * Service for patch bundles.
 *
 * @property storageRepository The storage repository to get storage paths from.
 * @property patchSetRepository The patch set repository to get patches from.
 * @property httpClientService The HTTP client service to download patch bundles with.
 */
internal class PatchBundleService(
    private val storageRepository: StorageRepository,
    private val patchSetRepository: PatchSetRepository,
    private val httpClientService: HttpClientService,
) {
    /**
     * Get the names of the patch bundles stored.
     *
     * @return The set of patch bundle names.
     */
    internal val patchBundleNames: Set<String>
        get() = storageRepository.patchBundles.keys.toSet()

    /**
     * Add a local patch bundle to storage persistently.
     *
     * @param patchBundleName The name of the patch bundle.
     * @param patchBundleFilePath The path to the patch bundle file.
     * @param patchBundleIntegrationsFilePath The path to the patch bundle integrations file.
     */
    internal fun addPersistentlyLocalPatchBundle(
        patchBundleName: String,
        patchBundleFilePath: String,
        patchBundleIntegrationsFilePath: String?,
    ) = storageRepository.addPersistentlyPatchBundle(
        PatchBundle(
            name = patchBundleName,
            patchBundleFile = File(patchBundleFilePath),
            patchBundleIntegrationsFile = patchBundleIntegrationsFilePath?.let { File(it) },
        ),
    )

    /**
     * Add a patch bundle that needs to be downloaded to storage persistently.
     *
     * @param patchBundleName The name of the patch bundle.
     * @param patchBundleDownloadLink The download link to the patch bundle.
     * @param patchBundleIntegrationsDownloadLink The download link to the patch bundle integrations.
     */
    internal suspend fun addPersistentlyDownloadPatchBundle(
        patchBundleName: String,
        patchBundleDownloadLink: String,
        patchBundleIntegrationsDownloadLink: String?,
    ) {
        val withIntegrations = patchBundleIntegrationsDownloadLink != null

        storageRepository.newPatchBundle(patchBundleName, withIntegrations).apply {
            httpClientService.downloadToFile(patchBundleFile, patchBundleDownloadLink)

            if (withIntegrations) {
                httpClientService.downloadToFile(patchBundleIntegrationsFile!!, patchBundleIntegrationsDownloadLink!!)
            }
        }
    }

    /**
     * Remove a patch bundle from storage persistently.
     *
     * @param name The name of the patch bundle to remove.
     */
    internal fun removePersistentlyPatchBundle(name: String) =
        storageRepository.removePersistentlyPatchBundle(name)

    /**
     * Reload the patch bundles from storage and read the patch set from them.
     */
    internal fun refresh() {
        storageRepository.readAndSetPatchBundles()
        patchSetRepository.readAndSetPatchSet()
    }
}
