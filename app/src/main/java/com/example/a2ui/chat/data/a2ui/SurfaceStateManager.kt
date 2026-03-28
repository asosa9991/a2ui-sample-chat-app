package com.example.a2ui.chat.data.a2ui

import com.contextable.a2ui4k.model.Component
import com.contextable.a2ui4k.model.UiDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Manages the lifecycle of a single A2UI surface.
 *
 * Protocol operations (`beginRendering`, `surfaceUpdate`, `dataModelUpdate`, `deleteSurface`)
 * are fed in via [processOperation]. The accumulated state can be read at any time through
 * [buildUiDefinition] and [buildDataModelJson].
 */
class SurfaceStateManager {

    private var surfaceId: String? = null
    private var root: String? = null
    private val components = mutableMapOf<String, Component>()
    private val dataContents = mutableListOf<JsonObject>() // raw dataModelUpdate payloads

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ── Public API ─────────────────────────────────────────────────────

    /**
     * Parse a single A2UI protocol operation (JSON) and update internal state.
     */
    fun processOperation(operationJson: String) {
        val obj = json.parseToJsonElement(operationJson).jsonObject

        when {
            "beginRendering" in obj -> processBeginRendering(obj["beginRendering"]!!.jsonObject)
            "surfaceUpdate" in obj -> processSurfaceUpdate(obj["surfaceUpdate"]!!.jsonObject)
            "dataModelUpdate" in obj -> processDataModelUpdate(obj["dataModelUpdate"]!!.jsonObject)
            "deleteSurface" in obj -> processDeleteSurface()
        }
    }

    /**
     * Build the current [UiDefinition] from accumulated operations.
     * Returns `null` if no surface has been initialized or no components exist yet.
     */
    fun buildUiDefinition(): UiDefinition? {
        val sid = surfaceId ?: return null
        if (components.isEmpty()) return null
        return UiDefinition(
            surfaceId = sid,
            root = root,
            components = components.toMap()
        )
    }

    /**
     * Build a [JsonObject] suitable for `DataModel.setData()` from accumulated
     * `dataModelUpdate` operations.
     */
    fun buildDataModelJson(): JsonObject {
        if (dataContents.isEmpty()) return JsonObject(emptyMap())

        val result = mutableMapOf<String, JsonElement>()
        for (updateObj in dataContents) {
            val contents = updateObj["contents"]?.jsonArray ?: continue
            for (entry in contents) {
                val entryObj = entry.jsonObject
                val key = entryObj["key"]?.jsonPrimitive?.contentOrNull ?: continue
                val value = extractValue(entryObj)
                result[key] = value
            }
        }
        return JsonObject(result)
    }

    /** Whether a surface has been initialized and has at least one component. */
    fun hasSurface(): Boolean = surfaceId != null && components.isNotEmpty()

    /** Reset all accumulated state. */
    fun reset() {
        surfaceId = null
        root = null
        components.clear()
        dataContents.clear()
    }

    // ── Operation processors ───────────────────────────────────────────

    private fun processBeginRendering(data: JsonObject) {
        surfaceId = data["surfaceId"]?.jsonPrimitive?.contentOrNull
        root = data["root"]?.jsonPrimitive?.contentOrNull
        components.clear()
        dataContents.clear()
    }

    private fun processSurfaceUpdate(data: JsonObject) {
        val comps = data["components"]?.jsonArray ?: return
        for (compElement in comps) {
            val compObj = compElement.jsonObject
            val id = compObj["id"]?.jsonPrimitive?.contentOrNull ?: continue
            val componentProps = compObj["component"]?.jsonObject ?: continue

            // Transform protocol "component" map → library "componentProperties" map.
            // Each key is a widget type (e.g. "Column", "Card") and its value is the
            // configuration JsonObject.
            val componentProperties = mutableMapOf<String, JsonObject>()
            for ((widgetType, config) in componentProps) {
                if (config is JsonObject) {
                    componentProperties[widgetType] = config
                }
            }

            components[id] = Component(
                id = id,
                componentProperties = componentProperties
            )
        }
    }

    private fun processDataModelUpdate(data: JsonObject) {
        dataContents.add(data)
    }

    private fun processDeleteSurface() {
        surfaceId = null
        root = null
        components.clear()
        dataContents.clear()
    }

    // ── Value extraction helpers ───────────────────────────────────────

    private fun extractValue(entryObj: JsonObject): JsonElement = when {
        "valueString" in entryObj -> JsonPrimitive(entryObj["valueString"]!!.jsonPrimitive.content)
        "valueNumber" in entryObj -> JsonPrimitive(entryObj["valueNumber"]!!.jsonPrimitive.double)
        "valueBoolean" in entryObj -> JsonPrimitive(entryObj["valueBoolean"]!!.jsonPrimitive.boolean)
        "valueMap" in entryObj -> buildNestedObject(entryObj["valueMap"]!!.jsonArray)
        else -> JsonNull
    }

    private fun buildNestedObject(entries: JsonArray): JsonObject {
        val map = mutableMapOf<String, JsonElement>()
        for (entry in entries) {
            val obj = entry.jsonObject
            val key = obj["key"]?.jsonPrimitive?.contentOrNull ?: continue
            map[key] = extractValue(obj)
        }
        return JsonObject(map)
    }
}
