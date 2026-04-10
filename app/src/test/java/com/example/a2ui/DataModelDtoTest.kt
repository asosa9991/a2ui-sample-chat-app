package com.example.a2ui

import com.example.a2ui.chat.data.model.DataModelEntryDto
import com.example.a2ui.chat.data.model.UiDefinitionDto
import com.example.a2ui.chat.data.model.buildDataModelJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataModelDtoTest {

    private fun makeUiDef(entries: List<DataModelEntryDto>) = UiDefinitionDto(
        surfaceId = "test_surface",
        root = "root",
        components = emptyMap(),
        dataModel = entries
    )

    @Test
    fun buildDataModelJson_withValueArray_expandsToStringKeyedObject() {
        val item0 = buildJsonObject {
            put("action", "Direct Deposit")
            put("date", "2026-03-28")
            put("amount", "+\$4,250.00")
        }
        val item1 = buildJsonObject {
            put("action", "Rent Payment")
            put("date", "2026-03-25")
            put("amount", "-\$1,850.00")
        }
        val uiDef = makeUiDef(listOf(
            DataModelEntryDto(key = "transactions", valueArray = listOf(item0, item1))
        ))

        val result = uiDef.buildDataModelJson()

        val transactions = result["transactions"]
        assertNotNull("transactions key should exist", transactions)
        assertTrue("transactions should be JsonObject", transactions is JsonObject)
        val txObj = transactions as JsonObject
        assertEquals("should have 2 items", 2, txObj.size)
        assertTrue("item 0 should exist", txObj.containsKey("0"))
        assertTrue("item 1 should exist", txObj.containsKey("1"))
        assertEquals("Direct Deposit", (txObj["0"] as JsonObject)["action"]?.jsonPrimitive?.content)
        assertEquals("+\$4,250.00", (txObj["0"] as JsonObject)["amount"]?.jsonPrimitive?.content)
    }

    @Test
    fun buildDataModelJson_withValueArray_preservesAllItemFields() {
        val item = buildJsonObject {
            put("action", "Netflix")
            put("date", "2026-03-18")
            put("amount", "-\$22.99")
        }
        val uiDef = makeUiDef(listOf(
            DataModelEntryDto(key = "transactions", valueArray = listOf(item))
        ))
        val result = uiDef.buildDataModelJson()
        val txObj = result["transactions"] as JsonObject
        val tx0 = txObj["0"] as JsonObject
        assertEquals("Netflix", tx0["action"]?.jsonPrimitive?.content)
        assertEquals("2026-03-18", tx0["date"]?.jsonPrimitive?.content)
        assertEquals("-\$22.99", tx0["amount"]?.jsonPrimitive?.content)
    }

    @Test
    fun buildDataModelJson_scalarEntriesUnchanged() {
        val uiDef = makeUiDef(listOf(
            DataModelEntryDto(key = "title", valueString = "March 2026"),
            DataModelEntryDto(key = "count", valueString = "14 transactions")
        ))
        val result = uiDef.buildDataModelJson()
        assertEquals("March 2026", result["title"]?.jsonPrimitive?.content)
        assertEquals("14 transactions", result["count"]?.jsonPrimitive?.content)
    }

    @Test
    fun buildDataModelJson_mixedScalarsAndValueArray() {
        val item = buildJsonObject {
            put("action", "Test")
            put("date", "2026-01-01")
            put("amount", "+\$1.00")
        }
        val uiDef = makeUiDef(listOf(
            DataModelEntryDto(key = "title", valueString = "Test Title"),
            DataModelEntryDto(key = "transactions", valueArray = listOf(item))
        ))
        val result = uiDef.buildDataModelJson()
        assertEquals("Test Title", result["title"]?.jsonPrimitive?.content)
        assertNotNull(result["transactions"])
        assertTrue(result["transactions"] is JsonObject)
    }

    @Test
    fun buildDataModelJson_emptyValueArray_producesEmptyObject() {
        val uiDef = makeUiDef(listOf(
            DataModelEntryDto(key = "transactions", valueArray = emptyList())
        ))
        val result = uiDef.buildDataModelJson()
        val txObj = result["transactions"]
        assertNotNull(txObj)
        assertTrue(txObj is JsonObject)
        assertEquals(0, (txObj as JsonObject).size)
    }
}
