package com.example.a2ui.chat.data.a2ui

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for Bug 2: FATAL EXCEPTION — JsonObject is not a JsonPrimitive.
 *
 * The transaction-history data model stores each transaction as a JsonObject at
 * `/transactions/<index>`. When the List widget probed `dataContext.getString("/transactions/0")`,
 * the A2UI library's DataModel called `getJsonPrimitive` which throws
 * [IllegalArgumentException] — it does NOT return null.
 *
 * Fix: wrap the probe in try-catch. IllegalArgumentException → item exists (JsonObject);
 * null return → index out of bounds → stop.
 *
 * These tests verify the corrected probe pattern against a simulated DataModelContext.
 */
class ListProbeTest {

    /**
     * Simulates [DataModelContext.getString] behavior:
     * - Missing path → null
     * - JsonObject at path → throws [IllegalArgumentException]
     * - JsonPrimitive at path → returns content string
     */
    private fun mockGetString(data: Map<String, JsonElement>, path: String): String? {
        val value = data[path] ?: return null
        if (value is JsonObject) throw IllegalArgumentException(
            "Element class ${value::class.java.simpleName} is not a JsonPrimitive"
        )
        return (value as? JsonPrimitive)?.content
    }

    /** Run the fixed probe loop over [data] and return the collected item indices. */
    private fun probeItems(data: Map<String, JsonElement>, basePath: String): List<Int> {
        val items = mutableListOf<Int>()
        var index = 0
        while (index < 50) {
            val itemExists = try {
                mockGetString(data, "$basePath/$index") != null
            } catch (_: IllegalArgumentException) {
                true  // path exists but value is a JsonObject — item exists
            }
            if (!itemExists) break
            items.add(index)
            index++
        }
        return items
    }

    @Test
    fun `probe collects indices for all object-valued items and stops at out-of-bounds`() {
        val data = mapOf(
            "/transactions/0" to JsonObject(mapOf(
                "action" to JsonPrimitive("DEPOSIT"),
                "date"   to JsonPrimitive("2024-01-01"),
                "amount" to JsonPrimitive("+$100.00")
            )),
            "/transactions/1" to JsonObject(mapOf(
                "action" to JsonPrimitive("WITHDRAWAL"),
                "date"   to JsonPrimitive("2024-01-02"),
                "amount" to JsonPrimitive("-$50.00")
            ))
        )

        val items = probeItems(data, "/transactions")
        assertEquals(listOf(0, 1), items)
    }

    @Test
    fun `probe handles empty array gracefully — returns empty list`() {
        val items = probeItems(emptyMap(), "/transactions")
        assertTrue(items.isEmpty())
    }

    @Test
    fun `probe stops at correct boundary with gap-free objects`() {
        val data = (0 until 5).associate { i ->
            "/items/$i" to JsonObject(mapOf("v" to JsonPrimitive(i)))
        }
        val items = probeItems(data, "/items")
        assertEquals(listOf(0, 1, 2, 3, 4), items)
    }

    @Test
    fun `probe works for primitive-valued items (string scalars)`() {
        val data = mapOf(
            "/tags/0" to JsonPrimitive("finance"),
            "/tags/1" to JsonPrimitive("banking")
        )
        val items = probeItems(data, "/tags")
        assertEquals(listOf(0, 1), items)
    }

    @Test
    fun `probe never exceeds 50-item safety cap even if data is unbounded`() {
        // Simulate 100 items present; probe must stop at 50
        val data = (0 until 100).associate { i ->
            "/items/$i" to JsonObject(mapOf("n" to JsonPrimitive(i)))
        }
        val items = probeItems(data, "/items")
        assertEquals(50, items.size)
        assertEquals((0 until 50).toList(), items)
    }

    @Test
    fun `probe does not throw when objects and primitives are mixed in the list`() {
        // Index 0 is a JsonObject, index 1 is a primitive string
        val data = mapOf(
            "/mixed/0" to JsonObject(mapOf("a" to JsonPrimitive(1))),
            "/mixed/1" to JsonPrimitive("scalar")
        )
        val items = probeItems(data, "/mixed")
        assertEquals(listOf(0, 1), items)
    }
}
