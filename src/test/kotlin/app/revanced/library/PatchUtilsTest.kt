package app.revanced.library

import app.revanced.patcher.PatchSet
import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.Patch
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal object PatchUtilsTest {
    private val patches =
        arrayOf(
            newPatch("some.package", "a"),
            newPatch("some.package", "a", "b", use = false),
            newPatch("some.package", "a", "b", "c", use = false),
            newPatch("some.other.package", "b", use = false),
            newPatch("some.other.package", "b", "c"),
            newPatch("some.other.package", "b", "c", "d"),
            newPatch("some.other.other.package"),
            newPatch("some.other.other.package", "a"),
            newPatch("some.other.other.package", "b"),
            newPatch("some.other.other.other.package", use = false),
            newPatch("some.other.other.other.package", use = false),
        ).toSet()

    @Test
    fun `return common versions correctly ordered for each package`() {
        assertEqualsVersions(
            expected =
                mapOf(
                    "some.package" to sortedMapOf("a" to 3, "b" to 2, "c" to 1),
                    "some.other.package" to sortedMapOf("b" to 3, "c" to 2, "d" to 1),
                    "some.other.other.package" to sortedMapOf("a" to 1, "b" to 1),
                    "some.other.other.other.package" to sortedMapOf(),
                ),
            patches,
            compatiblePackageNames =
                setOf(
                    "some.package",
                    "some.other.package",
                    "some.other.other.package",
                    "some.other.other.other.package",
                ),
            countUnusedPatches = true,
        )
    }

    @Test
    fun `return common versions correctly ordered for each package without counting unused patches`() {
        assertEqualsVersions(
            expected =
                mapOf(
                    "some.package" to sortedMapOf("a" to 1),
                    "some.other.package" to sortedMapOf("b" to 2, "c" to 2, "d" to 1),
                    "some.other.other.package" to sortedMapOf("a" to 1, "b" to 1),
                ),
            patches,
            compatiblePackageNames =
                setOf(
                    "some.package",
                    "some.other.package",
                    "some.other.other.package",
                    "some.other.other.other.package",
                ),
            countUnusedPatches = false,
        )
    }

    @Test
    fun `return an empty map because no known package was supplied`() {
        assertEqualsVersions(
            expected = emptyMap(),
            patches,
            compatiblePackageNames = setOf("unknown.package"),
        )
    }

    @Test
    fun `return empty set of versions because no compatible package is constrained to a version`() {
        assertEqualsVersions(
            expected = mapOf("some.package" to sortedMapOf()),
            patches = setOf(newPatch("some.package")),
            compatiblePackageNames = setOf("some.package"),
            countUnusedPatches = true,
        )
    }

    @Test
    fun `return 'a' because it is the most common version`() {
        val patches =
            arrayOf("a", "a", "c", "d", "a", "b", "c", "d", "a", "b", "c", "d")
                .map { version -> newPatch("some.package", version) }
                .toSet()

        assertEqualsVersion("a", patches, "some.package")
    }

    @Test
    fun `return null because no patches were supplied`() {
        assertEqualsVersion(null, emptySet<BytecodePatch>(), "some.package")
    }

    @Test
    fun `return null because no patch is compatible with the supplied package name`() {
        val patches = setOf(newPatch("some.package", "a"))

        assertEqualsVersion(null, patches, "other.package")
    }

    @Test
    fun `return null because no compatible package is constrained to a version`() {
        val patches =
            setOf(
                newPatch("other.package"),
                newPatch("other.package"),
            )

        assertEqualsVersion(null, patches, "other.package")
    }

    private fun assertEqualsVersions(
        expected: PackageNameMap,
        patches: PatchSet,
        compatiblePackageNames: Set<String>,
        countUnusedPatches: Boolean = false,
    ) = assertEquals(
        expected,
        PatchUtils.getMostCommonCompatibleVersions(patches, compatiblePackageNames, countUnusedPatches),
    )

    private fun assertEqualsVersion(
        expected: String?,
        patches: PatchSet,
        compatiblePackageName: String,
    ) {
        // Test both the deprecated and the new method.

        assertEquals(
            expected,
            PatchUtils.getMostCommonCompatibleVersion(patches, compatiblePackageName),
        )

        assertEquals(
            expected,
            PatchUtils.getMostCommonCompatibleVersions(patches, setOf(compatiblePackageName))
                .entries.firstOrNull()?.value?.keys?.firstOrNull(),
        )
    }

    private fun newPatch(
        packageName: String,
        vararg versions: String,
        use: Boolean = true,
    ) = object : BytecodePatch() {
        init {
            // Set the compatible packages field to the supplied package name and versions reflectively,
            // because the setter is private but needed for testing.
            val compatiblePackagesField = Patch::class.java.getDeclaredField("compatiblePackages")

            compatiblePackagesField.isAccessible = true
            compatiblePackagesField.set(this, setOf(CompatiblePackage(packageName, versions.toSet())))

            val useField = Patch::class.java.getDeclaredField("use")

            useField.isAccessible = true
            useField.set(this, use)
        }

        override fun execute(context: BytecodeContext) {}

        // Needed to make the patches unique.
        override fun equals(other: Any?) = false
    }
}
