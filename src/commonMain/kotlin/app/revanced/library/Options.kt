@file:Suppress("MemberVisibilityCanBePrivate")

package app.revanced.library

import app.revanced.patcher.patch.OptionException
import app.revanced.patcher.patch.Patch
import java.util.logging.Logger

typealias PatchName = String
typealias OptionKey = String
typealias OptionValue = Any?
typealias PatchesOptions = Map<PatchName, Map<OptionKey, OptionValue>>

private val logger = Logger.getLogger("Options")

/**
 * Set the options for a set of patches that have a name.
 *
 * @param options The options to set. The key is the patch name and the value is a map of option keys to option values.
 */
fun Set<Patch<*>>.setOptions(options: PatchesOptions) = filter { it.name != null }.forEach { patch ->
    options[patch.name]?.forEach setOption@{ (optionKey, optionValue) ->
        if (optionKey !in patch.options) {
            return@setOption logger.warning(
                "Could not set option for the \"${patch.name}\" patch because " +
                    "option with key \"${optionKey}\" does not exist",
            )
        }

        try {
            patch.options[optionKey] = optionValue
        } catch (e: OptionException) {
            logger.warning("Could not set option value for the \"${patch.name}\" patch: ${e.message}")
        }
    }
}
