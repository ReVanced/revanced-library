package app.revanced.library

import app.revanced.patcher.PatchSet
import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.Patch
import app.revanced.patcher.patch.options.PatchOption.PatchExtensions.booleanPatchOption
import app.revanced.patcher.patch.options.PatchOption.PatchExtensions.intArrayPatchOption
import app.revanced.patcher.patch.options.PatchOption.PatchExtensions.stringPatchOption
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals

internal object PatchUtilsTest {
    private val patches =
        arrayOf(
            newPatch("some.package", setOf("a")) { stringPatchOption("string", "value") },
            newPatch("some.package", setOf("a", "b"), use = false),
            newPatch("some.package", setOf("a", "b", "c"), use = false),
            newPatch("some.other.package", setOf("b"), use = false),
            newPatch("some.other.package", setOf("b", "c")) { booleanPatchOption("bool", true) },
            newPatch("some.other.package", setOf("b", "c", "d")),
            newPatch("some.other.other.package") { intArrayPatchOption("intArray", arrayOf(1, 2, 3)) },
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

    @Test
    fun `serializes to and deserializes from JSON string correctly`() {
        val out = ByteArrayOutputStream()
        PatchUtils.Json.serialize(patches, outputStream = out)

        val deserialized =
            PatchUtils.Json.deserialize(
                ByteArrayInputStream(out.toByteArray()),
                PatchUtils.Json.FullJsonPatch::class.java,
            )

        assert(patches.size == deserialized.size)
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
        options: Patch<*>.() -> Unit = {},
    ) = object : BytecodePatch(
        name = "test",
        compatiblePackages = setOf(CompatiblePackage(packageName, versions?.toSet())),
        use = use,
    ) {
        init {
            options()
        }

        override fun execute(context: BytecodeContext) {}

        // Needed to make the patches unique.
        override fun equals(other: Any?) = false
    }
}
