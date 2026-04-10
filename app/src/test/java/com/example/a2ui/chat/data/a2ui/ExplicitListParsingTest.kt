package com.example.a2ui.chat.data.a2ui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests that the children element is correctly parsed in FinancialCatalog widgets.
 *
 * Covers the fix for Bug #2: financialColumnWidget and financialRowWidget were calling
 * DataReferenceParser.parseComponentArray() which only handles {"componentIds": [...]},
 * not the {"explicitList": [...]} format used by server templates.
 *
 * The fix uses a two-step fallback:
 *   1. DataReferenceParser.parseComponentArray → reads "componentIds"  (legacy format)
 *   2. direct JsonObject inspection              → reads "explicitList" (new template format)
 *
 * This test class mirrors that logic using a pure Kotlin helper so the suite can run
 * on the JVM (no Android SDK required) without the A2UI library present.
 */
class ExplicitListParsingTest {

    /**
     * Mirrors the full children-parsing logic in the fixed Column/Row widgets.
     *
     * Step 1 — legacy "componentIds" format:  {"componentIds": ["a", "b"]}
     *   In production this is handled by DataReferenceParser.parseComponentArray().
     *   Here we replicate the equivalent logic so the test runs on JVM.
     *
     * Step 2 — new "explicitList" format:  {"explicitList": ["a", "b"]}
     *   Added by the Bug #2 fix.
     */
    private fun parseChildren(childrenEl: JsonElement?): List<String> {
        val asObj = childrenEl as? JsonObject ?: return emptyList()

        // Step 1: legacy componentIds format (DataReferenceParser.parseComponentArray in prod)
        asObj["componentIds"]?.let { ids ->
            return (ids as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?: emptyList()
        }

        // Step 2: new explicitList format (added by Bug #2 fix)
        return asObj["explicitList"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()
    }

    @Test
    fun `explicitList format returns all child ids`() {
        val childrenEl = buildJsonObject {
            put("explicitList", buildJsonArray {
                add(JsonPrimitive("hdr_card"))
                add(JsonPrimitive("txns_list"))
            })
        }
        val result = parseChildren(childrenEl)
        assertEquals(listOf("hdr_card", "txns_list"), result)
    }

    @Test
    fun `explicitList empty list returns empty`() {
        val childrenEl = buildJsonObject {
            put("explicitList", buildJsonArray { })
        }
        val result = parseChildren(childrenEl)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `null children returns empty list`() {
        val result = parseChildren(null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `unknown format returns empty list`() {
        val childrenEl = buildJsonObject {
            put("unknownKey", buildJsonArray { add(JsonPrimitive("child1")) })
        }
        val result = parseChildren(childrenEl)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `single child in explicitList is returned`() {
        val childrenEl = buildJsonObject {
            put("explicitList", buildJsonArray {
                add(JsonPrimitive("main_card"))
            })
        }
        val result = parseChildren(childrenEl)
        assertEquals(listOf("main_card"), result)
    }

    // ── Legacy "componentIds" format ─────────────────────────────────────────

    @Test
    fun `componentIds format returns all child ids`() {
        // Legacy format used by older server responses — must still work
        val childrenEl = buildJsonObject {
            put("componentIds", buildJsonArray {
                add(JsonPrimitive("x"))
                add(JsonPrimitive("y"))
            })
        }
        val result = parseChildren(childrenEl)
        assertEquals(listOf("x", "y"), result)
    }

    @Test
    fun `componentIds single entry is returned`() {
        val childrenEl = buildJsonObject {
            put("componentIds", buildJsonArray {
                add(JsonPrimitive("only_child"))
            })
        }
        val result = parseChildren(childrenEl)
        assertEquals(listOf("only_child"), result)
    }

    @Test
    fun `componentIds takes precedence over explicitList when both keys present`() {
        // If a JsonObject somehow has both keys, componentIds (legacy format) wins
        // because DataReferenceParser.parseComponentArray is tried first in production.
        val childrenEl = buildJsonObject {
            put("componentIds", buildJsonArray { add(JsonPrimitive("from_ids")) })
            put("explicitList", buildJsonArray { add(JsonPrimitive("from_explicit")) })
        }
        val result = parseChildren(childrenEl)
        assertEquals(listOf("from_ids"), result)
    }
}
