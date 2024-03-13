package app.revanced.library

import app.revanced.patcher.PatcherResult
import com.android.tools.build.apkzlib.zip.AlignmentRules
import com.android.tools.build.apkzlib.zip.StoredEntry
import com.android.tools.build.apkzlib.zip.ZFile
import com.android.tools.build.apkzlib.zip.ZFileOptions
import java.io.File
import java.util.logging.Logger

/**
 * Utility functions to work with APK files.
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
object ApkUtils {
    private val logger = Logger.getLogger(ApkUtils::class.java.name)

    private const val LIBRARY_EXTENSION = ".so"

    // Alignment for native libraries.
    private const val LIBRARY_ALIGNMENT = 1024 * 4

    // Alignment for all other files.
    private const val DEFAULT_ALIGNMENT = 4

    // Prefix for resources.
    private const val RES_PREFIX = "res/"

    private val zFileOptions =
        ZFileOptions().setAlignmentRule(
            AlignmentRules.compose(
                AlignmentRules.constantForSuffix(LIBRARY_EXTENSION, LIBRARY_ALIGNMENT),
                AlignmentRules.constant(DEFAULT_ALIGNMENT),
            ),
        )

    /**
     * Applies the [PatcherResult] to the given [apkFile].
     *
     * The order of operation is as follows:
     * 1. Write patched dex files.
     * 2. Delete all resources in the target APK
     * 3. Merge resources.apk compiled by AAPT.
     * 4. Write raw resources.
     * 5. Delete resources staged for deletion.
     * 6. Realign the APK.
     *
     * @param apkFile The file to apply the patched files to.
     */
    fun PatcherResult.applyTo(apkFile: File) {
        ZFile.openReadWrite(apkFile, zFileOptions).use { targetApkZFile ->
            dexFiles.forEach { dexFile ->
                targetApkZFile.add(dexFile.name, dexFile.stream)
            }

            resources?.let { resources ->
                // Add resources compiled by AAPT.
                resources.resourcesApk?.let { resourcesApk ->
                    ZFile.openReadOnly(resourcesApk).use { resourcesApkZFile ->
                        // Delete all resources in the target APK before merging the new ones.
                        // This is necessary because the resources.apk renames resources.
                        // So unless, the old resources are deleted, there will be orphaned resources in the APK.
                        // It is not necessary, but for the sake of cleanliness, it is done.
                        targetApkZFile.entries().filter { entry ->
                            entry.centralDirectoryHeader.name.startsWith(RES_PREFIX)
                        }.forEach(StoredEntry::delete)

                        targetApkZFile.mergeFrom(resourcesApkZFile) { false }
                    }
                }

                // Add resources not compiled by AAPT.
                resources.otherResources?.let { otherResources ->
                    targetApkZFile.addAllRecursively(otherResources) { file ->
                        file.relativeTo(otherResources).invariantSeparatorsPath !in resources.doNotCompress
                    }
                }

                // Delete resources that were staged for deletion.
                if (resources.deleteResources.isNotEmpty()) {
                    targetApkZFile.entries().filter { entry ->
                        resources.deleteResources.any { shouldDelete -> shouldDelete(entry.centralDirectoryHeader.name) }
                    }.forEach(StoredEntry::delete)
                }
            }

            logger.info("Aligning APK")

            targetApkZFile.realign()

            logger.fine("Writing changes")
        }
    }

    /**
     * Reads an existing or creates a new keystore.
     *
     * @param signingOptions The options to use for signing.
     */
    private fun readOrNewKeyStore(signingOptions: SigningOptions) = if (signingOptions.keyStore.exists()) {
        ApkSigner.readKeyStore(
            signingOptions.keyStore.inputStream(),
            signingOptions.keyStorePassword ?: "",
        )
    } else {
        val entry = ApkSigner.KeyStoreEntry(signingOptions.alias, signingOptions.password)

        // Create a new keystore with a new keypair and saves it.
        ApkSigner.newKeyStore(setOf(entry)).apply {
            store(
                signingOptions.keyStore.outputStream(),
                signingOptions.keyStorePassword?.toCharArray(),
            )
        }
    }

    /**
     * Signs the apk file with the given options.
     *
     * @param signingOptions The options to use for signing.
     */
    @Deprecated("Use sign(File, File, SigningOptions) instead.")
    fun File.sign(signingOptions: SigningOptions) {
        val keyStore = readOrNewKeyStore(signingOptions)

        @Suppress("DEPRECATION")
        ApkSigner.newApkSigner(
            keyStore,
            signingOptions.alias,
            signingOptions.password,
        ).signApk(this)
    }

    /**
     * Signs [inputApkFile] with the given options and saves the signed apk to [outputApkFile].
     *
     * @param inputApkFile The apk file to sign.
     * @param outputApkFile The file to save the signed apk to.
     * @param signingOptions The options to use for signing.
     */
    fun sign(inputApkFile: File, outputApkFile: File, signingOptions: SigningOptions) {
        val keyStore = readOrNewKeyStore(signingOptions)

        ApkSigner.newApkSigner(
            signingOptions.signer,
            keyStore,
            signingOptions.alias,
            signingOptions.password,
        ).signApk(inputApkFile, outputApkFile)
    }

    /**
     * Options for signing an apk.
     *
     * @param keyStore The keystore to use for signing.
     * @param keyStorePassword The password for the keystore.
     * @param alias The alias of the key store entry to use for signing.
     * @param password The password for recovering the signing key.
     * @param signer The name of the signer.
     */
    class SigningOptions(
        val keyStore: File,
        val keyStorePassword: String?,
        val alias: String = "ReVanced Key",
        val password: String = "",
        val signer: String = "ReVanced",
    )
}
