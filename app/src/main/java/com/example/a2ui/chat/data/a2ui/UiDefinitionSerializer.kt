package com.example.a2ui.chat.data.a2ui

import com.contextable.a2ui4k.model.Component
import com.contextable.a2ui4k.model.UiDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val prettyJson = Json { prettyPrint = true }

/**
 * Serialize the components map back to the wire-format JSON array:
 * ```json
 * [
 *   {"id": "root", "component": {"Column": {...}}},
 *   {"id": "header", "component": {"Row": {...}}}
 * ]
 * ```
 */
fun UiDefinition.toComponentsJsonString(): String {
    val array = buildJsonArray {
        for ((id, component) in components) {
            add(buildJsonObject {
                put("id", id)
                put("component", JsonObject(component.componentProperties))
            })
        }
    }
    return prettyJson.encodeToString(JsonArray.serializer(), array)
}

/** Pretty-print a [JsonObject]. */
fun JsonObject.toPrettyString(): String =
    prettyJson.encodeToString(JsonObject.serializer(), this)
