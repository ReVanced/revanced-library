package app.revanced.library

import app.revanced.patcher.patch.Option
import app.revanced.patcher.patch.Package
import app.revanced.patcher.patch.Patch
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.InputStream
import java.io.OutputStream
import kotlin.reflect.KType

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
     * Get the count of versions for each compatible package from a supplied set of [patches] ordered by the most common version.
     *
     * @param patches The set of patches to check.
     * @param packageNames The names of the compatible packages to include. If null, all packages will be included.
     * @param countUnusedPatches Whether to count patches that are not used.
     * @return A map of package names to a map of versions to their count.
     */
    fun getMostCommonCompatibleVersions(
        patches: Set<Patch<*>>,
        packageNames: Set<String>? = null,
        countUnusedPatches: Boolean = false,
    ): PackageNameMap =
        buildMap {
            fun filterWantedPackages(compatiblePackages: Iterable<Package>): Iterable<Package> {
                val wantedPackages = packageNames?.toHashSet() ?: return compatiblePackages
                return compatiblePackages.filter { (name, _) -> name in wantedPackages }
            }

            patches
                .filter { it.use || countUnusedPatches }
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

    object Json {
        private val mapper = jacksonObjectMapper()

        /**
         * Serializes a set of [Patch]es to a JSON string and writes it to an output stream.
         *
         * @param patches The set of [Patch]es to serialize.
         * @param transform A function to transform the [Patch]es to [JsonPatch]es.
         * @param prettyPrint Whether to pretty print the JSON.
         * @param outputStream The output stream to write the JSON to.
         */
        fun serialize(
            patches: Set<Patch<*>>,
            transform: (Patch<*>) -> JsonPatch = { patch -> FullJsonPatch.fromPatch(patch) },
            prettyPrint: Boolean = false,
            outputStream: OutputStream,
        ) {
            patches.map(transform).let { transformed ->
                if (prettyPrint) {
                    mapper.writerWithDefaultPrettyPrinter().writeValue(outputStream, transformed)
                } else {
                    mapper.writeValue(outputStream, transformed)
                }
            }
        }

        /**
         * Deserializes a JSON string to a set of [FullJsonPatch]es from an input stream.
         *
         * @param inputStream The input stream to read the JSON from.
         * @param jsonPatchElementClass The class of the [JsonPatch]es to deserialize.
         * @return A set of [JsonPatch]es.
         * @see FullJsonPatch
         */
        fun <T : JsonPatch> deserialize(
            inputStream: InputStream,
            jsonPatchElementClass: Class<T>,
        ): Set<T> =
            mapper.readValue(
                inputStream,
                mapper.typeFactory.constructCollectionType(Set::class.java, jsonPatchElementClass),
            )

        interface JsonPatch

        /**
         * A JSON representation of a [Patch].
         * @see Patch
         */
        class FullJsonPatch internal constructor(
            val name: String?,
            val description: String?,
            val compatiblePackages: Set<Package>?,
            // Cannot serialize dependencies, because they are references to other patches and patch names are nullable.
            // val dependencies: Set<String>,
            val use: Boolean,
            val options: Map<String, FullJsonPatchOption<*>>,
        ) : JsonPatch {
            internal companion object {
                internal fun fromPatch(patch: Patch<*>) =
                    FullJsonPatch(
                        patch.name,
                        patch.description,
                        patch.compatiblePackages,
                        // buildSet { patch.dependencies.forEach { add(it.name) } },
                        patch.use,
                        patch.options.mapValues { FullJsonPatchOption.fromPatchOption(it.value) },
                    )
            }

            /**
             * A JSON representation of a [Option].
             * @see Option
             */
            class FullJsonPatchOption<T> internal constructor(
                val key: String,
                val default: T?,
                val values: Map<String, T?>?,
                val title: String?,
                val description: String?,
                val required: Boolean,
                val type: KType,
            ) {
                internal companion object {
                    internal fun fromPatchOption(option: Option<*>) =
                        FullJsonPatchOption(
                            option.key,
                            option.default,
                            option.values,
                            option.title,
                            option.description,
                            option.required,
                            option.type,
                        )
                }
            }
        }
    }
}
