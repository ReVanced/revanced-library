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
            newPatch("some.package", setOf("a")),
            newPatch("some.package", setOf("a", "b"), use = false),
            newPatch("some.package", setOf("a", "b", "c"), use = false),
            newPatch("some.other.package", setOf("b"), use = false),
            newPatch("some.other.package", setOf("b", "c")),
            newPatch("some.other.package", setOf("b", "c", "d")),
            newPatch("some.other.other.package"),
            newPatch("some.other.other.package", setOf("a")),
            newPatch("some.other.other.package", setOf("b")),
            newPatch("some.other.other.other.package", use = false),
            newPatch("some.other.other.other.package", use = false),
        ).toSet()

    @Test
    fun `empty because package is incompatible with any version`() {
        assertEqualsVersions(
            expected = emptyMap(),
            patches = setOf(newPatch("some.package", emptySet(), use = true)),
            compatiblePackageNames = setOf("some.package"),
        )
    }

    @Test
    fun `empty list of versions because package is unconstrained to any version`() {
        assertEqualsVersions(
            expected = mapOf("some.package" to linkedMapOf()),
            patches = setOf(newPatch("some.package")),
            compatiblePackageNames = setOf("some.package"),
            countUnusedPatches = true,
        )
    }

    @Test
    fun `empty because no known package was supplied`() {
        assertEqualsVersions(
            expected = emptyMap(),
            patches,
            compatiblePackageNames = setOf("unknown.package"),
        )
    }

    @Test
    fun `common versions correctly ordered for each package`() {
        fun assertEqualsExpected(compatiblePackageNames: Set<String>?) =
            assertEqualsVersions(
                expected =
                    mapOf(
                        "some.package" to linkedMapOf("a" to 3, "b" to 2, "c" to 1),
                        "some.other.package" to linkedMapOf("b" to 3, "c" to 2, "d" to 1),
                        "some.other.other.package" to linkedMapOf("a" to 1, "b" to 1),
                        "some.other.other.other.package" to linkedMapOf(),
                    ),
                patches,
                compatiblePackageNames,
                countUnusedPatches = true,
            )

        assertEqualsExpected(
            compatiblePackageNames =
                setOf(
                    "some.package",
                    "some.other.package",
                    "some.other.other.package",
                    "some.other.other.other.package",
                ),
        )

        assertEqualsExpected(
            compatiblePackageNames = null,
        )
    }

    @Test
    fun `common versions correctly ordered for each package without counting unused patches`() {
        assertEqualsVersions(
            expected =
                mapOf(
                    "some.package" to linkedMapOf("a" to 1),
                    "some.other.package" to linkedMapOf("b" to 2, "c" to 2, "d" to 1),
                    "some.other.other.package" to linkedMapOf("a" to 1, "b" to 1),
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
    fun `return 'a' because it is the most common version`() {
        val patches =
            arrayOf("a", "a", "c", "d", "a", "b", "c", "d", "a", "b", "c", "d")
                .map { version -> newPatch("some.package", setOf(version)) }
                .toSet()

        assertEqualsVersion("a", patches, "some.package")
    }

    @Test
    fun `return null because no patches were supplied`() {
        assertEqualsVersion(null, emptySet<BytecodePatch>(), "some.package")
    }

    @Test
    fun `return null because no patch is compatible with the supplied package name`() {
        val patches = setOf(newPatch("some.package", setOf("a")))

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
        compatiblePackageNames: Set<String>?,
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

        @Suppress("DEPRECATION")
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
        versions: Set<String>? = null,
        use: Boolean = true,
    ) = object : BytecodePatch() {
        init {
            // Set the compatible packages field to the supplied package name and versions reflectively,
            // because the setter is private but needed for testing.
            val compatiblePackagesField = Patch::class.java.getDeclaredField("compatiblePackages")

            compatiblePackagesField.isAccessible = true
            compatiblePackagesField.set(this, setOf(CompatiblePackage(packageName, versions?.toSet())))

            val useField = Patch::class.java.getDeclaredField("use")

            useField.isAccessible = true
            useField.set(this, use)
        }

        override fun execute(context: BytecodeContext) {}

        // Needed to make the patches unique.
        override fun equals(other: Any?) = false
    }
}
