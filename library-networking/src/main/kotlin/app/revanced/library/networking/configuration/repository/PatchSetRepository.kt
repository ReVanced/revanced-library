@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package app.revanced.library.networking.configuration.repository

import app.revanced.library.networking.models.PatchBundle
import app.revanced.patcher.PatchBundleLoader
import app.revanced.patcher.PatchSet
import app.revanced.patcher.patch.Patch

/**
 * A repository for patches from a set of [PatchBundle]s.
 *
 * @param storageRepository The [StorageRepository] to read the [PatchBundle]s from.
 */
abstract class PatchSetRepository(
    private val storageRepository: StorageRepository,
) {
    /**
     * The set of [Patch]es loaded from [StorageRepository.patchBundles].
     */
    internal lateinit var patchSet: PatchSet
        private set

    init {
        readAndSetPatchSet()
    }

    /**
     * Read a [PatchSet] from a set of [patchBundles] using a [PatchBundleLoader].
     *
     * @param patchBundles The set of [PatchBundle]s to read the [PatchSet] from.
     */
    internal abstract fun readPatchSet(patchBundles: Set<PatchBundle>): PatchSet

    /**
     * Read a [PatchSet] from patch bundles from [storageRepository] using [readPatchSet] and set [patchSet] to it.
     */
    internal fun readAndSetPatchSet() {
        this.patchSet = readPatchSet(storageRepository.patchBundles.values.toSet())
    }
}
