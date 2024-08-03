package app.revanced.library

import app.revanced.patcher.patch.Option
import app.revanced.patcher.patch.Package
import app.revanced.patcher.patch.Patch
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.InputStream
import java.io.OutputStream
import kotlin.reflect.KType

@Deprecated("Functions have been moved to top level.")
object PatchUtils {
    @Deprecated(
        "Function has been moved to top level.",
        ReplaceWith("patches.mostCommonCompatibleVersions(packageNames, countUnusedPatches)"),
    )
    fun getMostCommonCompatibleVersions(
        patches: Set<Patch<*>>,
        packageNames: Set<String>? = null,
        countUnusedPatches: Boolean = false,
    ): PackageNameMap = patches.mostCommonCompatibleVersions(packageNames, countUnusedPatches)

    @Deprecated("Functions have been moved to the Serialization class.")
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
        @Deprecated("Functions have been moved to the Serialization class.")
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
        @Deprecated("This function will be removed in the future.")
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
