package com.example.a2ui.chat.data.a2ui

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the resolveField() path-resolution logic inside financialListItemWidget
 * (FinancialCatalog.kt, lines 936–947).
 *
 * resolveField() reads a field's JsonObject from [data] and resolves it either:
 *   1. `{"literalString": "..."}` → returns the literal string directly
 *   2. `{"path": "rel/path"}` → builds an absolute path by prepending [itemPath]
 *      (if present) and queries DataContext
 *
 * Production crashes in v0.8.5–v0.8.7 included null-pointer exceptions when fields
 * resolved to null unexpectedly, and path-building bugs that produced double-slashes
 * when itemPath was null but the caller assumed it was always set.
 *
 * This test mirrors the resolution logic without requiring the A2UI DataContext or the
 * Compose runtime — exactly the same pattern used in ExplicitListParsingTest.
 *
 * The real implementation in FinancialCatalog.kt:
 *   fun resolveField(key: String): String? {
 *       val fieldEl = data[key] as? JsonObject ?: return null
 *       return when {
 *           fieldEl.containsKey("path") -> {
 *               val rel = fieldEl["path"]?.jsonPrimitive?.content ?: return null
 *               val absPath = if (itemPath != null) "$itemPath/$rel" else "/$rel"
 *               dataContext.getString(absPath)           // ← replaced by returning absPath below
 *           }
 *           fieldEl.containsKey("literalString") -> fieldEl["literalString"]?.jsonPrimitive?.content
 *           else -> null
 *       }
 *   }
 *
 * For path-based fields we return the resolved absPath instead of calling
 * dataContext.getString() so the test runs on the JVM without an A2UI DataContext stub.
 */
class DataContextPathResolutionTest {

    /**
     * Mirrors the path-building half of resolveField() for [key] in [data].
     * - literalString fields: returns the literal value.
     * - path fields: returns the resolved absolute path (absPath) instead of
     *   reading from DataContext, allowing JVM-only assertion.
     * - Missing key or unknown format: returns null.
     */
    private fun resolveFieldPath(
        data: Map<String, kotlinx.serialization.json.JsonElement>,
        key: String,
        itemPath: String?
    ): String? {
        val fieldEl = data[key] as? JsonObject ?: return null
        return when {
            fieldEl.containsKey("path") -> {
                val rel = fieldEl["path"]?.jsonPrimitive?.content ?: return null
                if (itemPath != null) "$itemPath/$rel" else "/$rel"
            }
            fieldEl.containsKey("literalString") ->
                fieldEl["literalString"]?.jsonPrimitive?.contentOrNull
            else -> null
        }
    }

    // ── literalString resolution ───────────────────────────────────────────

    @Test
    fun `literalString field resolves to literal value`() {
        val data = mapOf(
            "label" to buildJsonObject { put("literalString", "Apple Inc.") }
        )
        val result = resolveFieldPath(data, "label", itemPath = null)
        assertEquals("Apple Inc.", result)
    }

    @Test
    fun `literalString with monetary value resolves correctly`() {
        val data = mapOf(
            "value" to buildJsonObject { put("literalString", "+\$1,234.56") }
        )
        val result = resolveFieldPath(data, "value", itemPath = null)
        assertEquals("+\$1,234.56", result)
    }

    @Test
    fun `literalString empty string resolves to empty string`() {
        val data = mapOf(
            "subLabel" to buildJsonObject { put("literalString", "") }
        )
        val result = resolveFieldPath(data, "subLabel", itemPath = null)
        assertEquals("", result)
    }

    // ── path resolution with itemPath prefix ──────────────────────────────

    @Test
    fun `path field with item prefix builds correct absolute path`() {
        val data = mapOf(
            "label" to buildJsonObject { put("path", "description") }
        )
        val result = resolveFieldPath(data, "label", itemPath = "/transactions/0")
        assertEquals("/transactions/0/description", result)
    }

    @Test
    fun `path field with nested item prefix builds correct absolute path`() {
        val data = mapOf(
            "value" to buildJsonObject { put("path", "amount") }
        )
        val result = resolveFieldPath(data, "value", itemPath = "/portfolio/holdings/2")
        assertEquals("/portfolio/holdings/2/amount", result)
    }

    @Test
    fun `path field without item prefix uses slash-prefixed path`() {
        // itemPath=null → absPath = "/" + rel (no item-scope prefix)
        val data = mapOf(
            "label" to buildJsonObject { put("path", "global/key") }
        )
        val result = resolveFieldPath(data, "label", itemPath = null)
        assertEquals("/global/key", result)
    }

    // ── Null / unknown cases ───────────────────────────────────────────────

    @Test
    fun `unknown field format returns null`() {
        val data = mapOf(
            "label" to buildJsonObject { put("unknownKey", "value") }
        )
        val result = resolveFieldPath(data, "label", itemPath = null)
        assertNull(result)
    }

    @Test
    fun `missing field key returns null`() {
        val data = mapOf(
            "label" to buildJsonObject { put("literalString", "Present") }
        )
        val result = resolveFieldPath(data, "nonExistent", itemPath = null)
        assertNull(result)
    }

    @Test
    fun `non-object field value returns null`() {
        // Field is a JsonPrimitive, not a JsonObject — should return null
        val data = mapOf<String, kotlinx.serialization.json.JsonElement>(
            "label" to JsonPrimitive("raw string")
        )
        val result = resolveFieldPath(data, "label", itemPath = null)
        assertNull(result)
    }

    @Test
    fun `empty data map returns null`() {
        val result = resolveFieldPath(emptyMap(), "label", itemPath = "/transactions/0")
        assertNull(result)
    }
}
