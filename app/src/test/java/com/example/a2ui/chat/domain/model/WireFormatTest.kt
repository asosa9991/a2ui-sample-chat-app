package com.example.a2ui.chat.domain.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class WireFormatTest {

    @Test
    fun `entries contains exactly three values`() {
        assertEquals(3, WireFormat.entries.size)
    }

    @Test
    fun `SSE entry exists and has correct label`() {
        assertEquals("SSE", WireFormat.SSE.label)
    }

    @Test
    fun `JSONL entry exists and has correct label`() {
        assertEquals("JSONL", WireFormat.JSONL.label)
    }

    @Test
    fun `SYNC entry exists and has correct label`() {
        assertEquals("Sync", WireFormat.SYNC.label)
    }

    @Test
    fun `valueOf returns correct entry for SYNC`() {
        assertEquals(WireFormat.SYNC, WireFormat.valueOf("SYNC"))
    }

    @Test
    fun `all entries have non-empty labels`() {
        WireFormat.entries.forEach { format ->
            assertNotNull(format.label)
            assert(format.label.isNotBlank()) { "${format.name} has blank label" }
        }
    }

    @Test
    fun `SSE is the first entry (default)`() {
        assertEquals(WireFormat.SSE, WireFormat.entries.first())
    }
}
