package app.revanced.library

import app.revanced.patcher.patch.Package
import app.revanced.patcher.patch.Patch

typealias PackageName = String
typealias Version = String
typealias Count = Int

typealias VersionMap = LinkedHashMap<Version, Count>
typealias PackageNameMap = Map<PackageName, VersionMap>

/**
 * Get the count of versions for each compatible package from the set of [Patch] ordered by the most common version.
 *
 * @param packageNames The names of the compatible packages to include. If null, all packages will be included.
 * @param countUnusedPatches Whether to count patches that are not used.
 * @return A map of package names to a map of versions to their count.
 */
fun Set<Patch>.mostCommonCompatibleVersions(
    packageNames: Set<String>? = null,
    countUnusedPatches: Boolean = false,
): PackageNameMap = buildMap {
    fun filterWantedPackages(compatiblePackages: List<Package>): List<Package> {
        val wantedPackages = packageNames?.toHashSet() ?: return compatiblePackages
        return compatiblePackages.filter { (name, _) -> name in wantedPackages }
    }

    this@mostCommonCompatibleVersions.filter { it.use || countUnusedPatches }
        .flatMap { it.compatiblePackages ?: emptyList() }
        .let(::filterWantedPackages)
        .forEach { (name, versions) ->
            if (versions?.isEmpty() == true) {
                return@forEach
            }

            val versionMap = getOrPut(name) { linkedMapOf() }

            versions?.forEach { version ->
                versionMap[version] = versionMap.getOrDefault(version, 0) + 1
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
