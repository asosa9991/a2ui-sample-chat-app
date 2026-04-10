package com.example.a2ui.chat.data.a2ui

import com.contextable.a2ui4k.model.UiDefinition
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SurfaceStateManager] — the class that accumulates streaming A2UI
 * protocol operations and materialises them into a [UiDefinition].
 *
 * Production crashes in v0.8.5–v0.8.7 included:
 *   - surfaceId not captured until beginRendering arrived, causing hasSurface() to
 *     return false during progressive streaming and dropping partial renders.
 *   - components from multiple surfaceUpdate chunks not accumulating (map replaced
 *     instead of merged).
 *   - dataModelUpdate entries with path-scoped keys not being stored at all.
 *
 * Each test feeds JSON operation strings through [processOperation] and asserts the
 * resulting state via [buildUiDefinition] and [buildDataModelJson].
 */
class SurfaceStateManagerTest {

    private lateinit var manager: SurfaceStateManager

    @Before
    fun setUp() {
        manager = SurfaceStateManager()
    }

    // ── Helper builders ────────────────────────────────────────────────────

    /** Creates a well-formed surfaceUpdate JSON with [surfaceId] and a list of [components].
     *  Each entry in [components] is a Pair(id, widgetType). */
    private fun surfaceUpdateJson(
        surfaceId: String = "surface_1",
        vararg components: Pair<String, String>
    ): String {
        val compsJson = components.joinToString(",") { (id, widget) ->
            """{"id":"$id","component":{"$widget":{}}}"""
        }
        return """{"surfaceUpdate":{"surfaceId":"$surfaceId","components":[$compsJson]}}"""
    }

    /** Creates a well-formed beginRendering JSON. */
    private fun beginRenderingJson(surfaceId: String = "surface_1", root: String): String =
        """{"beginRendering":{"surfaceId":"$surfaceId","root":"$root"}}"""

    /** Creates a dataModelUpdate JSON with a single valueString entry. */
    private fun dataModelUpdateJson(key: String, value: String): String =
        """{"dataModelUpdate":{"contents":[{"key":"$key","valueString":"$value"}]}}"""

    // ── surfaceUpdate ──────────────────────────────────────────────────────

    @Test
    fun `processOperation surfaceUpdate increments component count`() {
        manager.processOperation(surfaceUpdateJson("s1", "comp1" to "Text"))
        val def = manager.buildUiDefinition()
        assertNotNull("UiDefinition must not be null after surfaceUpdate", def)
        assertEquals(1, def!!.components.size)
    }

    @Test
    fun `multiple surfaceUpdate ops accumulate all components`() {
        // Two separate surfaceUpdate messages — components must BOTH appear in the final state.
        manager.processOperation(surfaceUpdateJson("s1", "header" to "Text"))
        manager.processOperation(surfaceUpdateJson("s1", "body" to "Column"))
        val def = manager.buildUiDefinition()
        assertNotNull(def)
        assertEquals(
            "Both components from two separate surfaceUpdate messages must be present",
            2, def!!.components.size
        )
        assertTrue(def.components.containsKey("header"))
        assertTrue(def.components.containsKey("body"))
    }

    @Test
    fun `surfaceUpdate with three components yields component count of three`() {
        manager.processOperation(
            surfaceUpdateJson("s1", "c1" to "Text", "c2" to "Column", "c3" to "Card")
        )
        val def = manager.buildUiDefinition()
        assertNotNull(def)
        assertEquals(3, def!!.components.size)
    }

    @Test
    fun `component protocol format is transformed to componentProperties`() {
        // Protocol wire format: {"id":"c1","component":{"Column":{}}}
        // Expected library format: Component(componentProperties={"Column": ...})
        manager.processOperation(
            """{"surfaceUpdate":{"surfaceId":"s1","components":[{"id":"c1","component":{"Column":{}}}]}}"""
        )
        val def = manager.buildUiDefinition()
        assertNotNull(def)
        val comp = def!!.components["c1"]
        assertNotNull("Component 'c1' must be present", comp)
        assertTrue(
            "componentProperties must contain widget type 'Column'",
            comp!!.componentProperties.containsKey("Column")
        )
    }

    @Test
    fun `surfaceUpdate without surfaceId field still captures provided surfaceId`() {
        // Manager captures surfaceId from the first surfaceUpdate it sees.
        manager.processOperation(surfaceUpdateJson("my_surface", "root" to "Column"))
        val def = manager.buildUiDefinition()
        assertNotNull(def)
        assertEquals("my_surface", def!!.surfaceId)
    }

    // ── dataModelUpdate ────────────────────────────────────────────────────

    @Test
    fun `processOperation dataModelUpdate captures data model`() {
        // surfaceUpdate first so buildUiDefinition() returns non-null
        manager.processOperation(surfaceUpdateJson("s1", "root" to "Column"))
        manager.processOperation(dataModelUpdateJson("greeting", "Hello World"))

        val dataJson = manager.buildDataModelJson()
        assertTrue(
            "buildDataModelJson() must contain at least the seeded key",
            dataJson.isNotEmpty()
        )
        val greeting = dataJson["greeting"]
        assertNotNull("Key 'greeting' must be present in the data model", greeting)
    }

    @Test
    fun `multiple dataModelUpdate ops are accumulated`() {
        manager.processOperation(surfaceUpdateJson("s1", "root" to "Column"))
        manager.processOperation(dataModelUpdateJson("name", "Alice"))
        manager.processOperation(dataModelUpdateJson("balance", "1000"))

        val dataJson = manager.buildDataModelJson()
        assertNotNull(dataJson["name"])
        assertNotNull(dataJson["balance"])
    }

    @Test
    fun `empty manager returns null UiDefinition`() {
        assertNull("Fresh manager with no ops must return null", manager.buildUiDefinition())
    }

    @Test
    fun `empty manager returns empty buildDataModelJson`() {
        val json = manager.buildDataModelJson()
        assertTrue(json.isEmpty())
    }

    // ── beginRendering ─────────────────────────────────────────────────────

    @Test
    fun `processOperation beginRendering sets root on UiDefinition`() {
        // Per protocol: surfaceUpdate (+ components) arrives BEFORE beginRendering.
        // beginRendering then sets root and confirms surfaceId.
        manager.processOperation(surfaceUpdateJson("s1", "root_comp" to "Column"))
        manager.processOperation(beginRenderingJson("s1", root = "root_comp"))

        val def = manager.buildUiDefinition()
        assertNotNull(def)
        assertEquals(
            "beginRendering root must be propagated to UiDefinition",
            "root_comp", def!!.root
        )
    }

    @Test
    fun `processOperation beginRendering sets surfaceId on UiDefinition`() {
        manager.processOperation(surfaceUpdateJson("surface_abc", "comp1" to "Text"))
        manager.processOperation(beginRenderingJson("surface_abc", root = "comp1"))

        val def = manager.buildUiDefinition()
        assertNotNull(def)
        assertEquals("surface_abc", def!!.surfaceId)
    }

    @Test
    fun `beginRendering does not wipe components accumulated before it`() {
        // Critical invariant: the streaming protocol sends components first, then
        // beginRendering. Clearing on beginRendering would cause an empty surface.
        manager.processOperation(surfaceUpdateJson("s1", "c1" to "Text", "c2" to "Column"))
        manager.processOperation(beginRenderingJson("s1", root = "c1"))

        val def = manager.buildUiDefinition()
        assertNotNull(def)
        assertEquals(
            "Components accumulated before beginRendering must survive",
            2, def!!.components.size
        )
    }

    // ── deleteSurface ──────────────────────────────────────────────────────

    @Test
    fun `processOperation deleteSurface clears state`() {
        manager.processOperation(surfaceUpdateJson("s1", "root" to "Column"))
        assertNotNull("State should be populated before deleteSurface", manager.buildUiDefinition())

        manager.processOperation("""{"deleteSurface":{}}""")
        assertNull("buildUiDefinition must return null after deleteSurface", manager.buildUiDefinition())
    }

    // ── reset ──────────────────────────────────────────────────────────────

    @Test
    fun `reset clears all accumulated state`() {
        manager.processOperation(surfaceUpdateJson("s1", "root" to "Column"))
        manager.processOperation(dataModelUpdateJson("key", "value"))
        manager.reset()

        assertNull(manager.buildUiDefinition())
        assertTrue(manager.buildDataModelJson().isEmpty())
    }

    // ── invalid / malformed input ─────────────────────────────────────────

    @Test
    fun `malformed JSON does not throw — is silently ignored`() {
        // processOperation must never propagate exceptions; callers rely on this.
        manager.processOperation("not valid json {{{{")
        // State should remain pristine
        assertNull(manager.buildUiDefinition())
    }

    @Test
    fun `empty JSON object does not throw — is silently ignored`() {
        manager.processOperation("{}")
        assertNull(manager.buildUiDefinition())
    }

    @Test
    fun `component entry missing id is silently skipped`() {
        val json = """{"surfaceUpdate":{"surfaceId":"s1","components":[{"component":{"Text":{}}}]}}"""
        manager.processOperation(json)
        // A surfaceUpdate with no valid components still captures surfaceId but
        // buildUiDefinition() returns null because the components map is empty.
        assertNull(
            "Component without id must be skipped; no components means null UiDefinition",
            manager.buildUiDefinition()
        )
    }
}

private fun kotlinx.serialization.json.JsonObject.isEmpty(): Boolean = this.keys.isEmpty()
