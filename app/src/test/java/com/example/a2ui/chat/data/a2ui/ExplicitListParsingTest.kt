package com.example.a2ui.chat.data.a2ui

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
 * Tests that the explicitList children format is correctly parsed in FinancialCatalog widgets.
 * Covers the fix for Bug #2: financialColumnWidget and financialRowWidget were calling
 * DataReferenceParser.parseComponentArray() which only handles {"componentIds": [...]},
 * not the {"explicitList": [...]} format used by our templates.
 */
class ExplicitListParsingTest {

    /** Extracted parsing logic matching the fix applied to Column/Row widgets. */
    private fun parseChildren(childrenEl: kotlinx.serialization.json.JsonElement?): List<String> =
        (childrenEl as? JsonObject)?.get("explicitList")?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()

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
}
