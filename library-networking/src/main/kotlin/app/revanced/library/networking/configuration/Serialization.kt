package app.revanced.library.networking.configuration

import app.revanced.library.networking.Server
import app.revanced.library.networking.models.Patch
import app.revanced.patcher.patch.options.PatchOption
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.serializer
import java.io.Serializable
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Configures the serialization for the application.
 *
 * @param serializersConfiguration The serializers configuration.
 */
fun Application.configureSerialization(serializersConfiguration: Server.SerializersConfiguration) {
    install(ContentNegotiation) {
        json(
            Json {
                serializersModule = SerializersModule {
                    configurePatchOptionSerializers(serializersConfiguration.patchOptionValueTypes)
                }
            },
        )
    }
}

/**
 * Configures the patch option serializers.
 *
 * @param patchOptionValueTypes A map of [PatchOption.valueType] to [KType] to add serializers for patch options
 * additional to the default ones.
 */
private fun SerializersModuleBuilder.configurePatchOptionSerializers(patchOptionValueTypes: Map<String, KType>) {
    val knownPatchOptionValueTypes = mapOf(
        "String" to typeOf<Patch.PatchOption<String>>(),
        "Int" to typeOf<Patch.PatchOption<Int>>(),
        "Boolean" to typeOf<Patch.PatchOption<Boolean>>(),
        "Long" to typeOf<Patch.PatchOption<Long>>(),
        "Float" to typeOf<Patch.PatchOption<Float>>(),
        "StringArray" to typeOf<Patch.PatchOption<Array<String>>>(),
        "IntArray" to typeOf<Patch.PatchOption<IntArray>>(),
        "BooleanArray" to typeOf<Patch.PatchOption<BooleanArray>>(),
        "LongArray" to typeOf<Patch.PatchOption<LongArray>>(),
        "FloatArray" to typeOf<Patch.PatchOption<FloatArray>>(),
    ) + patchOptionValueTypes

    /**
     * Gets the [KType] for a patch option value type.
     *
     * @param valueType The value type of the patch option.
     *
     * @return The [KType] for the patch option value type.
     */
    fun patchOptionTypeOf(valueType: String) = knownPatchOptionValueTypes[valueType]
        ?: error("Unknown patch option value type: $valueType")

    /**
     * Serializer for [Patch.PatchOption].
     * Uses the [Patch.PatchOption.valueType] to determine the serializer for the generic type.
     */
    val patchOptionSerializer = object : KSerializer<Patch.PatchOption<*>> {
        override val descriptor = serializer(typeOf<Patch.PatchOption<Serializable>>()).descriptor

        override fun serialize(encoder: Encoder, value: Patch.PatchOption<*>) = serializer(
            patchOptionTypeOf(value.valueType),
        ).serialize(encoder, value)

        override fun deserialize(decoder: Decoder) = serializer(
            patchOptionTypeOf(
                decoder.decodeStructure(descriptor) {
                    decodeStringElement(descriptor, descriptor.getElementIndex("valueType"))
                },
            ),
        ).deserialize(decoder) as Patch.PatchOption<*>
    }

    contextual(patchOptionSerializer)
    contextual(SetSerializer(patchOptionSerializer))
}
