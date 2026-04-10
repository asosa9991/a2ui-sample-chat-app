package com.example.a2ui.chat.data.a2ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests for the valueSemantic replacement logic in FinancialCatalog.kt (lines 968–970).
 *
 * Root cause fixed: Kotlin's String.replace(Regex, String) delegates to Java's
 * Matcher.replaceAll(String), where `$` in a replacement string means "group reference".
 * A trailing bare `$` (e.g. "positive $") causes:
 *   java.lang.IllegalArgumentException: Illegal group reference: group index is missing
 *
 * Fix: escape the `$` in the replacement as `\\$` so it is treated as a literal dollar sign.
 */
class ValueSemanticTest {

    /**
     * Mirrors the exact logic from FinancialCatalog.kt lines 968–970.
     * Kept as a private helper here so tests exercise the real replacement strings.
     */
    private fun applyValueSemantic(displayValue: String): String =
        displayValue
            .replace(Regex("^\\+\\$"), "positive \\$")
            .replace(Regex("^-\\$"), "negative \\$")

    @Test
    fun plusDollarPrefix_isReplacedWithPositive() {
        val result = applyValueSemantic("+\$1,234.56")
        assertEquals("positive \$1,234.56", result)
    }

    @Test
    fun minusDollarPrefix_isReplacedWithNegative() {
        val result = applyValueSemantic("-\$50.00")
        assertEquals("negative \$50.00", result)
    }

    @Test
    fun noPrefix_isUnchanged() {
        val input = "\$500.00"
        val result = applyValueSemantic(input)
        assertEquals(input, result)
    }

    @Test
    fun blankValue_isUnchanged() {
        val result = applyValueSemantic("")
        assertEquals("", result)
    }
}
