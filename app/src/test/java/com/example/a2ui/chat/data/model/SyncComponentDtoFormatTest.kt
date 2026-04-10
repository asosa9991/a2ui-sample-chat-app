package com.example.a2ui.chat.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the sync endpoint component format (Bug #1).
 *
 * The /chat/template sync endpoint previously stored entry["component"] directly
 * without wrapping it in {"componentProperties": ...}. This caused ComponentDto
 * to deserialize with an empty componentProperties map (widgetType = null).
 *
 * Fix: agent.py now stores {"componentProperties": entry["component"]} so Kotlin
 * correctly deserializes it.
 */
class SyncComponentDtoFormatTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `componentProperties key correctly deserializes widget type`() {
        // Correct format after the fix: {"componentProperties": {"Column": {}}}
        val dto = json.decodeFromString(
            ComponentDto.serializer(),
            """{"componentProperties": {"Column": {}}}"""
        )
        assertNotNull("componentProperties should not be null", dto.componentProperties)
        assertTrue("componentProperties should contain Column", dto.componentProperties.containsKey("Column"))
        assertEquals("widgetType should be Column", "Column", dto.componentProperties.keys.firstOrNull())
    }

    @Test
    fun `missing componentProperties key gives empty map — confirms bug scenario`() {
        // Bug scenario: {"Column": {}} stored directly without componentProperties wrapper
        val dto = json.decodeFromString(
            ComponentDto.serializer(),
            """{"Column": {}}"""
        )
        // With ignoreUnknownKeys = true, "Column" key is ignored, componentProperties stays empty
        assertTrue("Bug scenario: componentProperties should be empty", dto.componentProperties.isEmpty())
        assertNull("Bug scenario: widgetType should be null", dto.componentProperties.keys.firstOrNull())
    }

    @Test
    fun `toDomain with componentProperties sets correct id from map key`() {
        val uiDefDto = json.decodeFromString(
            UiDefinitionDto.serializer(),
            """{
                "surfaceId": "s1",
                "root": "root",
                "components": {
                    "root": {"componentProperties": {"Column": {}}}
                }
            }"""
        )
        val uiDef = uiDefDto.toDomain()
        val rootComponent = uiDef.components["root"]
        assertNotNull("root component should exist", rootComponent)
        assertEquals("root component id should be 'root'", "root", rootComponent?.id)
        assertEquals("root widget type should be Column", "Column", rootComponent?.componentProperties?.keys?.firstOrNull())
    }

    @Test
    fun `toDomain with null id uses map key as fallback`() {
        val uiDefDto = json.decodeFromString(
            UiDefinitionDto.serializer(),
            """{
                "surfaceId": "s1",
                "root": "root",
                "components": {
                    "text1": {"componentProperties": {"Text": {}}}
                }
            }"""
        )
        val uiDef = uiDefDto.toDomain()
        val component = uiDef.components["text1"]
        assertEquals("id should fall back to map key 'text1'", "text1", component?.id)
    }
}
