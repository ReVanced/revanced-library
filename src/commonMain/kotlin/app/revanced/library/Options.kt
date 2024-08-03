@file:Suppress("MemberVisibilityCanBePrivate")

package app.revanced.library

import app.revanced.patcher.patch.OptionException
import app.revanced.patcher.patch.Patch
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.util.logging.Logger

private val logger = Logger.getLogger("Options")

typealias PatchName = String
typealias OptionKey = String
typealias OptionValue = Any?
typealias PatchesOptions = Map<PatchName, Map<OptionKey, OptionValue>>

/**
 * Set the options for a set of patches that have a name.
 *
 * @param options The options to set. The key is the patch name and the value is a map of option keys to option values.
 */
fun Set<Patch<*>>.setOptions(options: PatchesOptions) = filter { it.name != null }.forEach { patch ->
    val patchOptions = options[patch.name] ?: return@forEach

    patch.options.forEach option@{ option ->
        try {
            patch.options[option.key] = patchOptions[option.key] ?: return@option
        } catch (e: OptionException) {
            logger.warning("Could not set option value for the \"${patch.name}\" patch: ${e.message}")
        }
    }
}

@Suppress("unused")
@Deprecated("Functions have been moved to top level.")
object Options {
    private val logger = Logger.getLogger(Options::class.java.name)

    private val mapper = jacksonObjectMapper()

    /**
     * Serializes the options for a set of patches.
     *
     * @param patches The set of patches to serialize.
     * @param prettyPrint Whether to pretty print the JSON.
     * @return The JSON string containing the options.
     */
    @Deprecated("Functions have been moved to the Serialization class.")
    fun serialize(
        patches: Set<app.revanced.patcher.patch.Patch<*>>,
        prettyPrint: Boolean = false,
    ): String =
        patches
            .filter { it.options.any() }
            .map { patch ->
                Patch(
                    patch.name!!,
                    patch.options.values.map { option ->
                        val optionValue =
                            try {
                                option.value
                            } catch (e: OptionException) {
                                logger.warning("Using default option value for the \"${patch.name}\" patch: ${e.message}")
                                option.default
                            }

                        Patch.Option(option.key, optionValue)
                    },
                )
            }
            // See https://github.com/revanced/revanced-patches/pull/2434/commits/60e550550b7641705e81aa72acfc4faaebb225e7.
            .distinctBy { it.patchName }
            .let {
                if (prettyPrint) {
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(it)
                } else {
                    mapper.writeValueAsString(it)
                }
            }

    /**
     * Deserializes the options to a set of patches.
     *
     * @param json The JSON string containing the options.
     * @return A set of [Patch]s.
     * @see Patch
     */
    @Deprecated("Functions have been moved to the Serialization class.")
    fun deserialize(json: String): Array<Patch> = mapper.readValue(json, Array<Patch>::class.java)

    /**
     * Sets the options for a set of patches.
     *
     * @param json The JSON string containing the options.
     */
    @Deprecated("Function has been moved to top level.")
    fun Set<app.revanced.patcher.patch.Patch<*>>.setOptions(json: String) {
        filter { it.options.any() }.let { patches ->
            if (patches.isEmpty()) return

            val jsonPatches = deserialize(json).associate {
                it.patchName to it.options.associate { option -> option.key to option.value }
            }

            setOptions(jsonPatches)
        }
    }

    /**
     * Sets the options for a set of patches.
     *
     * @param file The file containing the JSON string containing the options.
     * @see setOptions
     */
    @Deprecated("Function has been moved to top level.")
    fun Set<app.revanced.patcher.patch.Patch<*>>.setOptions(file: File) = setOptions(file.readText())

    /**
     * Data class for a patch and its [Option]s.
     *
     * @property patchName The name of the patch.
     * @property options The [Option]s for the patch.
     */
    class Patch internal constructor(
        val patchName: String,
        val options: List<Option>,
    ) {
        /**
         * Data class for patch option.
         *
         * @property key The name of the option.
         * @property value The value of the option.
         */
        class Option internal constructor(val key: String, val value: Any?)
    }
}
