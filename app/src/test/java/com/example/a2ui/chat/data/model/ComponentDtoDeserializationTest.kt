package com.example.a2ui.chat.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Regression tests for Bug 1: MissingFieldException on sync endpoint.
 *
 * The `/chat/template` (sync) endpoint returns component objects that do NOT include
 * an `id` field — the map key IS the id. These tests verify that deserialization
 * succeeds and that [toDomain] falls back to the map key when `id` is absent.
 */
class ComponentDtoDeserializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `ComponentDto without id field falls back to map key in toDomain`() {
        val raw = """
            {
              "surfaceId": "s1",
              "root": "root",
              "components": {
                "root":     {"componentProperties": {}},
                "col1":     {"componentProperties": {}},
                "hdr_card": {"componentProperties": {}}
              }
            }
        """.trimIndent()

        val dto = json.decodeFromString<UiDefinitionDto>(raw)
        val domain = dto.toDomain()

        assertEquals("root",     domain.components["root"]?.id)
        assertEquals("col1",     domain.components["col1"]?.id)
        assertEquals("hdr_card", domain.components["hdr_card"]?.id)
    }

    @Test
    fun `ComponentDto with explicit id uses that id not the map key`() {
        val raw = """
            {
              "surfaceId": "s1",
              "components": {
                "mapKey": {"id": "explicitId", "componentProperties": {}}
              }
            }
        """.trimIndent()

        val dto = json.decodeFromString<UiDefinitionDto>(raw)
        val domain = dto.toDomain()

        assertEquals("explicitId", domain.components["mapKey"]?.id)
    }

    @Test
    fun `UiDefinitionDto deserialization does not throw on missing id field`() {
        val raw = """{"surfaceId":"s1","components":{"c1":{"componentProperties":{}}}}"""
        // Must NOT throw MissingFieldException
        val dto = json.decodeFromString<UiDefinitionDto>(raw)
        assertNotNull(dto)
        assertNotNull(dto.components["c1"])
    }

    @Test
    fun `toDomain produces correct component count when id absent`() {
        val raw = """
            {
              "surfaceId": "surf",
              "root": "root",
              "components": {
                "root":     {"componentProperties": {}},
                "hdr_card": {"componentProperties": {}},
                "body_text":{"componentProperties": {}}
              }
            }
        """.trimIndent()

        val domain = json.decodeFromString<UiDefinitionDto>(raw).toDomain()
        assertEquals(3, domain.components.size)
    }

    @Test
    fun `mixed dto — some components with explicit id some without — all resolve correctly`() {
        val raw = """
            {
              "surfaceId": "s1",
              "components": {
                "key1": {"componentProperties": {}},
                "key2": {"id": "override2", "componentProperties": {}},
                "key3": {"componentProperties": {}}
              }
            }
        """.trimIndent()

        val domain = json.decodeFromString<UiDefinitionDto>(raw).toDomain()
        assertEquals("key1",      domain.components["key1"]?.id)
        assertEquals("override2", domain.components["key2"]?.id)
        assertEquals("key3",      domain.components["key3"]?.id)
    }
}
