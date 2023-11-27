package app.revanced.library

import app.revanced.patcher.PatchSet
import app.revanced.patcher.patch.Patch

typealias PackageName = String
typealias Version = String
typealias Count = Int

typealias VersionMap = LinkedHashMap<Version, Count>
typealias PackageNameMap = Map<PackageName, VersionMap>

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
     * @param packageNames The names of the compatible packages to include. If null, all packages will be included.
     * @param countUnusedPatches Whether to count patches that are not used.
     * @return A map of package names to a map of versions to their count.
     */
    fun getMostCommonCompatibleVersions(
        patches: PatchSet,
        packageNames: Set<String>? = null,
        countUnusedPatches: Boolean = false,
    ): PackageNameMap =
        buildMap {
            fun filterWantedPackages(compatiblePackages: Iterable<Patch.CompatiblePackage>): Iterable<Patch.CompatiblePackage> {
                val wantedPackages = packageNames?.toHashSet() ?: return compatiblePackages
                return compatiblePackages.filter { it.name in wantedPackages }
            }

            patches
                .filter { it.use || countUnusedPatches }
                .flatMap { it.compatiblePackages ?: emptyList() }
                .let(::filterWantedPackages)
                .forEach { compatiblePackage ->
                    if (compatiblePackage.versions?.isEmpty() == true) {
                        return@forEach
                    }

                    val versionMap = getOrPut(compatiblePackage.name) { linkedMapOf() }

                    compatiblePackage.versions?.let { versions ->
                        versions.forEach { version ->
                            versionMap[version] = versionMap.getOrDefault(version, 0) + 1
                        }
                    }
                }

            // Sort the version maps by the most common version.
            forEach { (packageName, versionMap) ->
                this[packageName] =
                    versionMap
                        .asIterable()
                        .sortedWith(compareByDescending { it.value })
                        .associate { it.key to it.value } as VersionMap
            }
        }
}
