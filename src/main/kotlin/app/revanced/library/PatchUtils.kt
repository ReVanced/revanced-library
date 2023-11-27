package app.revanced.library

import app.revanced.patcher.PatchSet
import java.util.*

private typealias PackageName = String
private typealias Version = String
private typealias Count = Int

private typealias VersionMap = SortedMap<Version, Count>
internal typealias PackageNameMap = Map<PackageName, VersionMap>

/**
 * Utility functions for working with patches.
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
object PatchUtils {
    /**
     * Get the version that is most common for [packageName] in the supplied set of [patches].
     *
     * @param patches The set of patches to check.
     * @param packageName The name of the compatible package.
     * @return The most common version of.
     */
    @Deprecated(
        "Use getMostCommonCompatibleVersions instead.",
        ReplaceWith(
            "getMostCommonCompatibleVersions(patches, setOf(packageName))" +
                ".entries.firstOrNull()?.value?.keys?.firstOrNull()",
        ),
    )
    fun getMostCommonCompatibleVersion(
        patches: PatchSet,
        packageName: String,
    ) = patches
        .mapNotNull {
            // Map all patches to their compatible packages with version constraints.
            it.compatiblePackages?.firstOrNull { compatiblePackage ->
                compatiblePackage.name == packageName && compatiblePackage.versions?.isNotEmpty() == true
            }
        }
        .flatMap { it.versions!! }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }?.key

    /**
     * Get the count of versions for each compatible package from a supplied set of [patches] ordered by the most common version.
     *
     * @param patches The set of patches to check.
     * @param packageNames The names of the compatible packages.
     * @param countUnusedPatches Whether to count patches that are not used.
     * @return A map of package names to a map of versions to their count.
     */
    fun getMostCommonCompatibleVersions(
        patches: PatchSet,
        packageNames: Set<String>,
        countUnusedPatches: Boolean = false,
    ): PackageNameMap {
        val wantedPackages = packageNames.toHashSet()
        return buildMap {
            patches
                .filter { it.use || countUnusedPatches }
                .flatMap { it.compatiblePackages ?: emptyList() }
                .filter { it.name in wantedPackages }
                .forEach { compatiblePackage ->
                    compatiblePackage.versions?.let { versions ->
                        val versionMap = getOrPut(compatiblePackage.name) { sortedMapOf() }

                        versions.forEach { version ->
                            versionMap[version] = versionMap.getOrDefault(version, 0) + 1
                        }
                    }
                }
        }
    }
}
