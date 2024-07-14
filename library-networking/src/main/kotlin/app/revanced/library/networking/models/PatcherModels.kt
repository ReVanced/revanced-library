@file:Suppress("unused")

package app.revanced.library.networking.models

import kotlinx.serialization.*
import java.io.File

private typealias PackageName = String
private typealias PackageVersion = String
private typealias PackageVersions = Set<PackageVersion>
private typealias CompatiblePackages = Map<PackageName, PackageVersions?>

@Serializable
open class App(
    internal val name: String,
    internal val version: String,
    internal val packageName: String,
)

@Serializable
class Patch internal constructor(
    internal val name: String,
    internal val description: String?,
    internal val use: Boolean,
    internal val compatiblePackages: CompatiblePackages?,
) {
    @Serializable
    class PatchOption<T> internal constructor(
        internal val key: String,
        internal val default: T?,
        internal val values: Map<String, T?>?,
        internal val title: String?,
        internal val description: String?,
        internal val required: Boolean,
        internal val valueType: String,
    )

    class KeyValuePatchOption<T>(
        val key: String,
        val value: T?,
        val valueType: String,
    ) {
        // Abuse serialization capabilities of Patch.PatchOption which is used in request bodies.
        // Use Patch.PatchOption.default as Patch.KeyValuePatchOption.value.
        internal constructor(patchOption: PatchOption<T>) : this(
            patchOption.key,
            patchOption.default,
            patchOption.valueType,
        )
    }
}

class PatchBundle internal constructor(
    val name: String,
    val patchBundleFile: File,
    val patchBundleIntegrationsFile: File? = null,
)
