package com.example.a2ui.chat.data.a2ui

import android.util.Log
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

    companion object {
        private const val TAG = "A2UI.Surface"
        private const val MAX_COMPONENTS = 1000
        private const val MAX_DATA_ENTRIES = 10_000
    }

    private var surfaceId: String? = null
    private var root: String? = null
    // LinkedHashMap preserves insertion order for deterministic rendering.
    // Wrapped with synchronizedMap so IO-thread writes and main-thread reads don't race.
    private val components = java.util.Collections.synchronizedMap(LinkedHashMap<String, Component>())
    // Wrapped with synchronizedList for the same reason.
    private val dataContents = java.util.Collections.synchronizedList(mutableListOf<JsonObject>())

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ── Public API ─────────────────────────────────────────────────────

    /**
     * Parse a single A2UI protocol operation (JSON) and update internal state.
     */
    fun processOperation(operationJson: String) {
        try {
            val obj = json.parseToJsonElement(operationJson).jsonObject

            when {
                "beginRendering" in obj -> {
                    Log.d(TAG, "processOperation: beginRendering")
                    processBeginRendering(obj["beginRendering"]!!.jsonObject)
                }
                "surfaceUpdate" in obj -> {
                    Log.d(TAG, "processOperation: surfaceUpdate")
                    processSurfaceUpdate(obj["surfaceUpdate"]!!.jsonObject)
                }
                "dataModelUpdate" in obj -> {
                    Log.d(TAG, "processOperation: dataModelUpdate")
                    processDataModelUpdate(obj["dataModelUpdate"]!!.jsonObject)
                }
                "deleteSurface" in obj -> {
                    Log.d(TAG, "processOperation: deleteSurface")
                    processDeleteSurface()
                }
                else -> {
                    Log.w(TAG, "processOperation: unknown operation keys=${obj.keys}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "processOperation: JSON parse error, raw=\"${operationJson.take(200)}\"", e)
        }
    }

    /**
     * Build the current [UiDefinition] from accumulated operations.
     * Returns `null` if no surface has been initialized or no components exist yet.
     */
    fun buildUiDefinition(): UiDefinition? {
        val sid = surfaceId ?: return null
        // Snapshot under the monitor to prevent ConcurrentModificationException during toMap().
        val snapshot = synchronized(components) { components.toMap() }
        if (snapshot.isEmpty()) return null
        Log.d(TAG, "buildUiDefinition: surfaceId=$sid components=${snapshot.size} hasDataModel=${dataContents.isNotEmpty()}")
        return UiDefinition(
            surfaceId = sid,
            root = root,
            components = snapshot
        )
    }

    /**
     * Build a [JsonObject] suitable for `DataModel.setData()` from accumulated
     * `dataModelUpdate` operations.
     *
     * Keys in `contents` entries may be slash-delimited paths (e.g. `/fields/first_field/value`)
     * which are expanded into a nested JSON tree and deep-merged across all updates.
     * Flat keys (no `/`, e.g. `greeting`) are written directly at the top level.
     * Later updates win for scalar values; object nodes are merged recursively.
     */
    fun buildDataModelJson(): JsonObject {
        if (dataContents.isEmpty()) return JsonObject(emptyMap())

        // Work on a snapshot to avoid holding the list monitor across potentially heavy work.
        val snapshot = synchronized(dataContents) { dataContents.toList() }

        val result = mutableMapOf<String, JsonElement>()
        for (updateObj in snapshot) {
            val contents = updateObj["contents"]?.jsonArray ?: continue
            for (entry in contents) {
                val entryObj = entry.jsonObject
                val rawKey = entryObj["key"]?.jsonPrimitive?.contentOrNull ?: continue
                val value = extractValue(entryObj)

                // Strip leading '/' and split into path segments.
                val segments = rawKey.trimStart('/').split('/')
                    .filter { it.isNotEmpty() }

                if (segments.isEmpty()) continue

                if (segments.size == 1) {
                    // Flat key — merge at top level.
                    result[segments[0]] = deepMerge(result[segments[0]], value)
                } else {
                    // Path key — set (or merge) into the nested tree.
                    val topKey = segments[0]
                    val existing = result[topKey]
                    result[topKey] = setNestedPath(
                        base = if (existing is JsonObject) existing else JsonObject(emptyMap()),
                        pathSegments = segments.drop(1),
                        value = value
                    )
                }
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
        Log.i(TAG, "beginRendering: surfaceId=$surfaceId root=$root components=${components.size}")
        // NOTE: Do NOT clear components or dataContents here.
        // With per-component JSONL streaming (chunk_size=1), all surfaceUpdate and
        // dataModelUpdate messages arrive BEFORE beginRendering. Clearing here would
        // wipe every accumulated component and leave an empty surface.
        // The SurfaceStateManager is created fresh per-message in the ViewModel, so
        // there is no stale cross-message state to worry about.
    }

    private fun processSurfaceUpdate(data: JsonObject) {
        // With JSONL streaming, surfaceUpdate arrives before beginRendering, so we
        // capture surfaceId here on first occurrence. This makes hasSurface() return
        // true during component streaming, enabling progressive rendering in the ViewModel.
        val sid = data["surfaceId"]?.jsonPrimitive?.contentOrNull
        if (sid != null && surfaceId == null) {
            surfaceId = sid
            Log.d(TAG, "processSurfaceUpdate: captured surfaceId=$sid (beginRendering not yet received)")
        }

        val comps = data["components"]?.jsonArray ?: return
        Log.d(TAG, "surfaceUpdate: ${comps.size} component(s) received")
        for (compElement in comps) {
            if (components.size >= MAX_COMPONENTS) {
                Log.w(TAG, "processSurfaceUpdate: component limit ($MAX_COMPONENTS) reached, skipping remaining")
                break
            }
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
        val contents = data["contents"]?.jsonArray
        val path = data["path"]?.jsonPrimitive?.contentOrNull
        val newEntries = contents?.size ?: 0
        Log.d(TAG, "dataModelUpdate: entries=$newEntries path=$path")

        // Guard against unbounded growth.
        val currentTotal = synchronized(dataContents) {
            dataContents.sumOf { it["contents"]?.jsonArray?.size ?: 0 }
        }
        if (currentTotal + newEntries > MAX_DATA_ENTRIES) {
            Log.w(TAG, "processDataModelUpdate: data entry limit ($MAX_DATA_ENTRIES) would be exceeded, skipping update")
            return
        }
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
        "valueString"  in entryObj -> JsonPrimitive(entryObj["valueString"]!!.jsonPrimitive.content)
        "valueNumber"  in entryObj -> JsonPrimitive(entryObj["valueNumber"]!!.jsonPrimitive.double)
        "valueBoolean" in entryObj -> JsonPrimitive(entryObj["valueBoolean"]!!.jsonPrimitive.boolean)
        "valueMap"     in entryObj -> buildNestedObject(entryObj["valueMap"]!!.jsonArray)
        "valueArray"   in entryObj -> {
            val arr = entryObj["valueArray"]!!.jsonArray
            JsonObject(arr.mapIndexed { i, item -> i.toString() to item }.toMap())
        }
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

    /**
     * Recursively set [value] at [pathSegments] inside [base], deep-merging any
     * intermediate object nodes that already exist.
     */
    private fun setNestedPath(
        base: JsonObject,
        pathSegments: List<String>,
        value: JsonElement
    ): JsonObject {
        if (pathSegments.isEmpty()) return base
        val mutable = base.toMutableMap()
        val head = pathSegments.first()
        if (pathSegments.size == 1) {
            mutable[head] = deepMerge(mutable[head], value)
        } else {
            val child = mutable[head]
            val childObj = if (child is JsonObject) child else JsonObject(emptyMap())
            mutable[head] = setNestedPath(childObj, pathSegments.drop(1), value)
        }
        return JsonObject(mutable)
    }

    /**
     * Deep-merge [incoming] into [base].
     * - If both are [JsonObject], their keys are merged recursively (incoming wins on conflict).
     * - Otherwise [incoming] replaces [base].
     */
    private fun deepMerge(base: JsonElement?, incoming: JsonElement): JsonElement {
        if (base is JsonObject && incoming is JsonObject) {
            val merged = base.toMutableMap()
            for ((key, value) in incoming) {
                merged[key] = deepMerge(merged[key], value)
            }
            return JsonObject(merged)
        }
        return incoming
    }
}
