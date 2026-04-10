package com.example.a2ui.chat.data.model

import com.contextable.a2ui4k.model.Component
import com.contextable.a2ui4k.model.UiDefinition
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class AgentResponseDto(
    val text: String,
    @SerialName("ui_definition")
    val uiDefinition: UiDefinitionDto? = null,
    val error: String? = null
)

@Serializable
data class DataModelEntryDto(
    val key: String,
    @SerialName("valueString") val valueString: String? = null,
    @SerialName("valueNumber") val valueNumber: Double? = null,
    @SerialName("valueBoolean") val valueBoolean: Boolean? = null
)

@Serializable
data class UiDefinitionDto(
    val surfaceId: String,
    val root: String? = null,
    val components: Map<String, ComponentDto> = emptyMap(),
    val dataModel: List<DataModelEntryDto>? = null
)

@Serializable
data class ComponentDto(
    val id: String? = null,   // nullable: map key is the authoritative id when absent
    val componentProperties: Map<String, JsonObject> = emptyMap()
)

fun UiDefinitionDto.toDomain(): UiDefinition = UiDefinition(
    surfaceId = surfaceId,
    root = root,
    components = components.mapValues { (key, dto) ->
        Component(
            id = dto.id ?: key,   // fall back to map key when id is absent in JSON object
            componentProperties = dto.componentProperties
        )
    }
)

/**
 * Converts the flat [dataModel] list into a nested [JsonObject] suitable for
 * use with `rememberDataModel`. Keys may be flat ("summary_text") or path-based
 * ("/fields/x/value") — the leading slash and each "/" segment create nested objects.
 */
fun UiDefinitionDto.buildDataModelJson(): JsonObject {
    val entries = dataModel ?: return JsonObject(emptyMap())
    val result = mutableMapOf<String, JsonElement>()
    for (entry in entries) {
        val value: JsonElement = when {
            entry.valueString != null  -> JsonPrimitive(entry.valueString)
            entry.valueNumber != null  -> JsonPrimitive(entry.valueNumber)
            entry.valueBoolean != null -> JsonPrimitive(entry.valueBoolean)
            else -> JsonNull
        }
        val segments = entry.key.trimStart('/').split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) continue
        if (segments.size == 1) {
            result[segments[0]] = value
        } else {
            val topKey = segments[0]
            val existing = result[topKey]
            result[topKey] = setNestedPath(
                base = if (existing is JsonObject) existing else JsonObject(emptyMap()),
                pathSegments = segments.drop(1),
                value = value
            )
        }
    }
    return JsonObject(result)
}

private fun setNestedPath(
    base: JsonObject,
    pathSegments: List<String>,
    value: JsonElement
): JsonObject {
    val mutable = base.toMutableMap()
    val head = pathSegments.first()
    if (pathSegments.size == 1) {
        mutable[head] = value
    } else {
        val child = mutable[head]
        val childObj = if (child is JsonObject) child else JsonObject(emptyMap())
        mutable[head] = setNestedPath(childObj, pathSegments.drop(1), value)
    }
    return JsonObject(mutable)
}
