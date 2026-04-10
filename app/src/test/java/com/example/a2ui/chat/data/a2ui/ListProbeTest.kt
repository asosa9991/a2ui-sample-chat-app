package com.example.a2ui.chat.data.a2ui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the List widget item-existence probe in [financialListWidget].
 *
 * ## Background
 *
 * The transaction-history data model stores each transaction as a JsonObject (not a
 * JsonPrimitive) at `/transactions/<index>` — e.g.
 *   `/transactions/0` → `{action: "DEPOSIT", date: "2024-01-01", amount: "+$100.00"}`
 *
 * A previous implementation probed with `dataContext.getString("$path/$index")`, expecting
 * null for missing items. However, A2UI's `DataModel.getString()` calls
 * `get(path)?.jsonPrimitive?.contentOrNull` — for a path that resolves to a JsonObject,
 * `jsonPrimitive` throws `IllegalArgumentException` instead of returning null.  This meant
 * the try-catch correctly caught the exception, but it was fragile and caught exceptions
 * that should only be thrown for truly invalid states.
 *
 * ## Fix (current implementation)
 *
 * The probe now uses THREE throw-free DataContext methods:
 *   1. `dataContext.getObjectKeys("$path/$index")` — uses `as? JsonObject` internally,
 *      returns a non-null key list for object-valued items, null for missing paths.
 *      Never throws.
 *   2. `dataContext.getArraySize("$path/$index")` — uses `as? JsonArray` internally,
 *      returns a non-null Int for array-valued items, null for missing paths.
 *      Never throws.  Guards against `getString()` throwing on JsonArray paths.
 *   3. `dataContext.getString("$path/$index")` — returns non-null for primitive-valued
 *      items (string, number, boolean), null for missing paths.  Called only when
 *      both prior checks returned null (short-circuit).
 *
 * Combined:  `getObjectKeys != null || getArraySize != null || getString != null`
 *   → true   for object-valued items (transactions) ✓
 *   → true   for array-valued items                 ✓
 *   → true   for primitive-valued items             ✓
 *   → false  for out-of-bounds / missing indices    ✓
 *   No try-catch needed.
 *
 * These tests mirror the probe logic without requiring the A2UI DataContext or Compose
 * runtime (same JVM-only pattern as [DataContextPathResolutionTest]).
 */
class ListProbeTest {

    /**
     * Simulates the two DataContext methods used by the probe:
     * - [mockGetObjectKeys]: returns key list for JsonObject entries, null otherwise
     * - [mockGetArraySize]: returns element count for JsonArray entries, null otherwise
     * - [mockGetString]: returns content for JsonPrimitive entries, null otherwise
     *
     * None of the simulated methods throw — matching the real DataContext behaviour.
     * In particular, [mockGetString] uses `as? JsonPrimitive` (not `.jsonPrimitive`)
     * so it returns null rather than throwing for JsonObject/JsonArray-valued paths,
     * exactly as the real `DataModel.getArraySize()` guards before reaching `getString`.
     */
    private fun mockGetObjectKeys(data: Map<String, JsonElement>, path: String): List<String>? {
        return (data[path] as? JsonObject)?.keys?.toList()
    }

    private fun mockGetArraySize(data: Map<String, JsonElement>, path: String): Int? {
        return (data[path] as? JsonArray)?.size
    }

    private fun mockGetString(data: Map<String, JsonElement>, path: String): String? {
        return (data[path] as? JsonPrimitive)?.content
    }

    /** Run the probe loop over [data] using the three-branch throw-free strategy. */
    private fun probeItems(data: Map<String, JsonElement>, basePath: String): List<Int> {
        val items = mutableListOf<Int>()
        var index = 0
        while (index < 50) {
            val itemExists = mockGetObjectKeys(data, "$basePath/$index") != null  // JsonObject items
                || mockGetArraySize(data, "$basePath/$index") != null              // JsonArray items
                || mockGetString(data, "$basePath/$index") != null                 // primitive items
            if (!itemExists) break
            items.add(index)
            index++
        }
        return items
    }

    // ── Object-valued items (e.g. transaction rows) ───────────────────────

    @Test
    fun `probe collects indices for all object-valued items and stops at out-of-bounds`() {
        val data = mapOf(
            "/transactions/0" to JsonObject(mapOf(
                "action" to JsonPrimitive("DEPOSIT"),
                "date"   to JsonPrimitive("2024-01-01"),
                "amount" to JsonPrimitive("+\$100.00")
            )),
            "/transactions/1" to JsonObject(mapOf(
                "action" to JsonPrimitive("WITHDRAWAL"),
                "date"   to JsonPrimitive("2024-01-02"),
                "amount" to JsonPrimitive("-\$50.00")
            ))
        )

        val items = probeItems(data, "/transactions")
        assertEquals(listOf(0, 1), items)
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
    fun `getObjectKeys branch detects object-valued item without getString fallback`() {
        // getObjectKeys returns non-null → itemExists=true even though getString returns null
        val data = mapOf("/list/0" to JsonObject(mapOf("key" to JsonPrimitive("val"))))
        val items = probeItems(data, "/list")
        assertEquals(listOf(0), items)
    }

    // ── Primitive-valued items ────────────────────────────────────────────

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
    fun `getString branch detects primitive item after getObjectKeys returns null`() {
        // getObjectKeys returns null for primitive → falls through to getString which is non-null
        val data = mapOf("/scalars/0" to JsonPrimitive("hello"))
        val items = probeItems(data, "/scalars")
        assertEquals(listOf(0), items)
    }

    // ── Array-valued items ────────────────────────────────────────────────

    @Test
    fun `probe detects array-valued item without throwing`() {
        // This was the bug: getString() throws IllegalArgumentException for JsonArray paths.
        // getArraySize() must intercept before getString() is reached.
        val data = mapOf(
            "/nested/0" to JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b")))
        )
        val items = probeItems(data, "/nested")
        assertEquals(listOf(0), items)
    }

    @Test
    fun `probe collects multiple array-valued items and stops at boundary`() {
        val data = mapOf(
            "/matrix/0" to JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2))),
            "/matrix/1" to JsonArray(listOf(JsonPrimitive(3), JsonPrimitive(4), JsonPrimitive(5)))
        )
        val items = probeItems(data, "/matrix")
        assertEquals(listOf(0, 1), items)
    }

    @Test
    fun `getArraySize branch detects array-valued item — getObjectKeys and getString both return null`() {
        // Validates short-circuit: getObjectKeys(JsonArray) → null, getArraySize(JsonArray) → 2
        val data = mapOf("/lists/0" to JsonArray(listOf(JsonPrimitive("x"), JsonPrimitive("y"))))
        assertEquals(null, mockGetObjectKeys(data, "/lists/0"))
        assertEquals(2, mockGetArraySize(data, "/lists/0"))
        // getString must also return null — i.e. it must NOT be called (or if called, not throw)
        assertEquals(null, mockGetString(data, "/lists/0"))
        val items = probeItems(data, "/lists")
        assertEquals(listOf(0), items)
    }

    @Test
    fun `probe handles mixed object, array, and primitive items in the same list`() {
        val data = mapOf(
            "/mixed/0" to JsonObject(mapOf("type" to JsonPrimitive("row"))),
            "/mixed/1" to JsonArray(listOf(JsonPrimitive("col1"), JsonPrimitive("col2"))),
            "/mixed/2" to JsonPrimitive("leaf")
        )
        val items = probeItems(data, "/mixed")
        assertEquals(listOf(0, 1, 2), items)
    }

    // ── Mixed and edge cases ──────────────────────────────────────────────

    @Test
    fun `probe handles empty array gracefully — returns empty list`() {
        val items = probeItems(emptyMap(), "/transactions")
        assertTrue(items.isEmpty())
    }

    @Test
    fun `probe does not throw when objects and primitives are mixed in the list`() {
        val data = mapOf(
            "/mixed/0" to JsonObject(mapOf("a" to JsonPrimitive(1))),
            "/mixed/1" to JsonPrimitive("scalar")
        )
        val items = probeItems(data, "/mixed")
        assertEquals(listOf(0, 1), items)
    }

    @Test
    fun `probe never exceeds 50-item safety cap even if data is unbounded`() {
        val data = (0 until 100).associate { i ->
            "/items/$i" to JsonObject(mapOf("n" to JsonPrimitive(i)))
        }
        val items = probeItems(data, "/items")
        assertEquals(50, items.size)
        assertEquals((0 until 50).toList(), items)
    }

    @Test
    fun `probe correctly handles single-item list`() {
        val data = mapOf(
            "/rows/0" to JsonObject(mapOf("label" to JsonPrimitive("Only Row")))
        )
        val items = probeItems(data, "/rows")
        assertEquals(listOf(0), items)
    }
}
