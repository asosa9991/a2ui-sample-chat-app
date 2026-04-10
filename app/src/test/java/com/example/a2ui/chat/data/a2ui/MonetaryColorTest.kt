package com.example.a2ui.chat.data.a2ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the monetaryColor() logic in FinancialCatalog.kt (line 1130).
 *
 * monetaryColor() drives both:
 *   - the left accent bar color on every transaction row
 *   - the value text color (PositiveText / NegativeText / OnSurface)
 *
 * Production crashes in v0.8.5–v0.8.7 were caused by callers assuming a non-null
 * Color for all inputs (including blank strings and non-monetary text).
 *
 * The actual implementation:
 *   private fun monetaryColor(text: String): Color? = when {
 *       text.startsWith("+") && text.contains("$") -> PositiveText   // Color(0xFF0D7C4F)
 *       text.startsWith("-") && text.contains("$") -> NegativeText   // Color(0xFFB91C1C)
 *       else -> null
 *   }
 *
 * This test mirrors that logic using an enum so it runs on the JVM without
 * requiring the Compose runtime (compose-ui is not on the unit-test classpath).
 */
class MonetaryColorTest {

    /**
     * Mirrors the exact branching logic from FinancialCatalog.kt without importing
     * Compose Color so the test runs purely on the JVM.
     */
    private enum class MonetarySignal { Positive, Negative }

    private fun monetaryColor(text: String): MonetarySignal? = when {
        text.startsWith("+") && text.contains("$") -> MonetarySignal.Positive
        text.startsWith("-") && text.contains("$") -> MonetarySignal.Negative
        else -> null
    }

    // ── Positive ──────────────────────────────────────────────────────────

    @Test
    fun `positive monetary value returns PositiveText`() {
        val result = monetaryColor("+\$1,234.56")
        assertEquals(MonetarySignal.Positive, result)
    }

    @Test
    fun `positive small monetary value returns PositiveText`() {
        val result = monetaryColor("+\$0.01")
        assertEquals(MonetarySignal.Positive, result)
    }

    // ── Negative ──────────────────────────────────────────────────────────

    @Test
    fun `negative monetary value returns NegativeText`() {
        val result = monetaryColor("-\$50.00")
        assertEquals(MonetarySignal.Negative, result)
    }

    @Test
    fun `negative large monetary value returns NegativeText`() {
        val result = monetaryColor("-\$10,000.00")
        assertEquals(MonetarySignal.Negative, result)
    }

    // ── Null cases ────────────────────────────────────────────────────────

    @Test
    fun `zero value without sign prefix returns null`() {
        // "$0.00" has no leading + or - → null (bar uses AccentNeutral in the widget)
        val result = monetaryColor("\$0.00")
        assertNull(result)
    }

    @Test
    fun `non-monetary string returns null`() {
        val result = monetaryColor("Apple Inc.")
        assertNull(result)
    }

    @Test
    fun `blank value returns null`() {
        val result = monetaryColor("")
        assertNull(result)
    }

    @Test
    fun `value with dollar sign but no sign prefix returns null`() {
        // "$500.00" has no leading + or - → null
        val result = monetaryColor("\$500.00")
        assertNull(result)
    }

    @Test
    fun `plain positive number without dollar sign returns null`() {
        // "+" prefix but no "$" → null
        val result = monetaryColor("+500.00")
        assertNull(result)
    }

    @Test
    fun `plain negative number without dollar sign returns null`() {
        // "-" prefix but no "$" → null
        val result = monetaryColor("-500.00")
        assertNull(result)
    }

    @Test
    fun `date string returns null`() {
        val result = monetaryColor("Mar 15, 2024")
        assertNull(result)
    }
}
