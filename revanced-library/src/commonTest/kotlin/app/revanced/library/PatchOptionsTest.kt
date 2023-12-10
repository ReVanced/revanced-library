package app.revanced.library

import app.revanced.library.Options.setOptions
import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.patch.options.PatchOption.PatchExtensions.booleanPatchOption
import app.revanced.patcher.patch.options.PatchOption.PatchExtensions.stringPatchOption
import kotlin.test.Test

class PatchOptionsTest {
    private var patches = setOf(PatchOptionsTestPatch)

    private val serializedJson =
        "[{\"patchName\":\"PatchOptionsTestPatch\",\"options\":[{\"key\":\"key1\",\"value\":null},{\"key\":\"key2\"," +
            "\"value\":true}]}]"

    private val changedJson =
        "[{\"patchName\":\"PatchOptionsTestPatch\",\"options\":[{\"key\":\"key1\",\"value\":\"test\"},{\"key\":\"key2" +
            "\",\"value\":false}]}]"

    @Test
    fun `serializes and deserializes`() {
        assert(serializedJson == Options.serialize(patches))

        patches.setOptions(changedJson)

        assert(PatchOptionsTestPatch.option1 == "test")
        assert(PatchOptionsTestPatch.option2 == false)
    }

    @Patch("PatchOptionsTestPatch")
    object PatchOptionsTestPatch : BytecodePatch() {
        var option1 by stringPatchOption("key1", null, null, "title1", "description1")
        var option2 by booleanPatchOption("key2", true, null, "title2", "description2")

        override fun execute(context: BytecodeContext) {
            // Do nothing
        }
    }
}
