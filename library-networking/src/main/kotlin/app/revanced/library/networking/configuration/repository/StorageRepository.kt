package app.revanced.library.networking.configuration.repository

import app.revanced.library.networking.models.PatchBundle
import app.revanced.patcher.Patcher
import java.io.File

/**
 * A repository for storage.
 *
 * @param temporaryFilesPath The path to the temporary files for [Patcher].
 * @param outputFilePath The path to the output file to save patched APKs to.
 * @param keystoreFilePath The path to the keystore file to sign patched APKs with.
 * @param aaptBinaryPath The path to the aapt binary to use by [Patcher].
 */
abstract class StorageRepository(
    val temporaryFilesPath: File,
    val outputFilePath: File = File(temporaryFilesPath, "output.apk"),
    val keystoreFilePath: File,
    val aaptBinaryPath: File? = null,
) {
    /**
     * The stored [PatchBundle]s mapped by their name.
     */
    internal lateinit var patchBundles: MutableMap<String, PatchBundle>
        private set

    /**
     * The path to save the patched, but unsigned APK to.
     */
    internal val unsignedApkFilePath = File(temporaryFilesPath, "unsigned.apk")

    init {
        readAndSetPatchBundles()
    }

    /**
     * Read a set of [patchBundles] from a storage.
     *
     * @return The set of [PatchBundle] read.
     */
    internal abstract fun readPatchBundles(): Set<PatchBundle>

    /**
     * Write a set of [patchBundles] to a storage.
     *
     * @param patchBundles The set of patch bundles to write.
     */
    internal abstract fun writePatchBundles(patchBundles: Set<PatchBundle>)

    /**
     * Create a new [PatchBundle] in a storage to write to.
     *
     * @param patchBundleName The name of the patch bundle.
     * @param withIntegrations Whether the patch bundle also has integrations.
     *
     * @return The new [PatchBundle] created.
     */
    internal abstract fun newPatchBundle(patchBundleName: String, withIntegrations: Boolean): PatchBundle

    /**
     * Read the set of [patchBundles] stored and set it to [patchBundles].
     */
    internal fun readAndSetPatchBundles() {
        patchBundles = readPatchBundles().associateBy { it.name }.toMutableMap()
    }

    /**
     * Add a [patchBundle] to the map of the stored [patchBundles] and write the set to a storage using [writePatchBundles].
     *
     * @param patchBundle The patch bundle to add.
     */
    internal fun addPersistentlyPatchBundle(patchBundle: PatchBundle) {
        patchBundles[patchBundle.name] = patchBundle
        writePatchBundles(patchBundles.values.toSet())
    }

    /**
     * Remove a path bundle from the map of [patchBundles] stored and write the set to a storage using [writePatchBundles].
     *
     * @param patchBundleName The name of the patch bundle to remove.
     */
    internal fun removePersistentlyPatchBundle(patchBundleName: String) {
        patchBundles.remove(patchBundleName)
        writePatchBundles(patchBundles.values.toSet())
    }

    /**
     * Delete the temporary files.
     */
    internal fun deleteTemporaryFiles() {
        temporaryFilesPath.deleteRecursively()
    }
}
