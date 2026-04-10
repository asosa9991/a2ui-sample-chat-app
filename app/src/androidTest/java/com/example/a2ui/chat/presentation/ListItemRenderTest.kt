package com.example.a2ui.chat.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.contextable.a2ui4k.model.Component
import com.contextable.a2ui4k.model.UiDefinition
import com.contextable.a2ui4k.render.A2UISurface
import com.example.a2ui.chat.data.a2ui.FinancialCatalog
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Compose UI tests that render the ListItem widget via [A2UISurface] with
 * [FinancialCatalog] and verify correct visual output.
 *
 * These tests cover code paths that caused production crashes in v0.8.5–v0.8.7:
 *   - Monetary value rendering (positive/negative) — crash when Color was null in
 *     the barColor `when` expression due to unguarded null usage.
 *   - Four-field layout (label + subLabel + value + subValue) — crash when
 *     displaySubValue was accessed before the null-guard was added.
 *   - TalkBack content description — IllegalArgumentException from bare `$` in
 *     Kotlin's Regex replacement (fixed in v0.8.7).
 *
 * Modelled after [RenderingRegressionTest] — uses the same helper to build a
 * minimal UiDefinition wrapping the ListItem inside a Column.
 */
@RunWith(AndroidJUnit4::class)
class ListItemRenderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun makeComponent(
        id: String,
        widgetType: String,
        props: kotlinx.serialization.json.JsonObject = buildJsonObject {}
    ): Component = Component(id = id, componentProperties = mapOf(widgetType to props))

    /**
     * Wraps [itemId] in a Column root so the ListItem receives a proper parent in
     * the component tree, exactly as the server would send it.
     */
    private fun singleListItemDefinition(
        itemId: String,
        listItemProps: kotlinx.serialization.json.JsonObject,
        surfaceId: String = "test_surface"
    ): UiDefinition {
        val colProps = buildJsonObject {
            put("children", buildJsonObject {
                put("explicitList", buildJsonArray {
                    add(JsonPrimitive(itemId))
                })
            })
        }
        return UiDefinition(
            surfaceId = surfaceId,
            root = "root",
            components = mapOf(
                "root" to makeComponent("root", "Column", colProps),
                itemId to makeComponent(itemId, "ListItem", listItemProps)
            )
        )
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    /**
     * Positive monetary value (+$1,234.56) must render the label and value text
     * without crashing. Regression for v0.8.5 crash where monetaryBarColor = PositiveText
     * but a null-unsafe code path in the accent bar Column threw an NPE.
     */
    @Test
    fun listItem_withPlusMonetaryValue_rendersWithoutCrash() {
        val props = buildJsonObject {
            put("label", buildJsonObject { put("literalString", "AAPL") })
            put("value", buildJsonObject { put("literalString", "+\$1,234.56") })
        }
        composeTestRule.setContent {
            A2UISurface(
                definition = singleListItemDefinition("item1", props),
                catalog = FinancialCatalog
            )
        }
        // Both label and value must be visible — reaching here without crash is itself a pass
        composeTestRule.onNodeWithText("AAPL").assertIsDisplayed()
        composeTestRule.onNodeWithText("+\$1,234.56").assertIsDisplayed()
    }

    /**
     * Negative monetary value (-$50.00) must render without crashing.
     * Regression for the same null-unsafe code path triggered by NegativeText branch.
     */
    @Test
    fun listItem_withMinusMonetaryValue_rendersWithoutCrash() {
        val props = buildJsonObject {
            put("label", buildJsonObject { put("literalString", "TSLA") })
            put("value", buildJsonObject { put("literalString", "-\$50.00") })
        }
        composeTestRule.setContent {
            A2UISurface(
                definition = singleListItemDefinition("item1", props),
                catalog = FinancialCatalog
            )
        }
        composeTestRule.onNodeWithText("TSLA").assertIsDisplayed()
        composeTestRule.onNodeWithText("-\$50.00").assertIsDisplayed()
    }

    /**
     * All four fields (label, subLabel, value, subValue) must all be visible.
     * Regression for v0.8.6 crash where displaySubValue was read before the
     * `isNullOrBlank()` null-guard was added, causing an NPE.
     */
    @Test
    fun listItem_withSubLabelAndSubValue_rendersAllFourFields() {
        val props = buildJsonObject {
            put("label",    buildJsonObject { put("literalString", "Netflix") })
            put("subLabel", buildJsonObject { put("literalString", "Mar 15") })
            put("value",    buildJsonObject { put("literalString", "-\$15.99") })
            put("subValue", buildJsonObject { put("literalString", "Streaming") })
        }
        composeTestRule.setContent {
            A2UISurface(
                definition = singleListItemDefinition("item1", props),
                catalog = FinancialCatalog
            )
        }
        composeTestRule.onNodeWithText("Netflix").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mar 15").assertIsDisplayed()
        composeTestRule.onNodeWithText("-\$15.99").assertIsDisplayed()
        composeTestRule.onNodeWithText("Streaming").assertIsDisplayed()
    }

    /**
     * The outer Row's contentDescription must contain the TalkBack-friendly semantic
     * replacement ("positive $" instead of "+$") so screen readers announce correctly.
     *
     * Regression for v0.8.7 crash:
     *   java.lang.IllegalArgumentException: Illegal group reference
     * Caused by using bare "$" in Regex replacement strings, which Java's
     * Matcher.replaceAll() interprets as a capture-group reference.
     * Fix: escape as "\\$" in Kotlin source.
     *
     * The contentDescription for label="NVDA", value="+$100.00" is:
     *   "NVDA, positive $100.00"
     */
    @Test
    fun listItem_contentDescriptionIncludesSemanticPositiveValue() {
        val props = buildJsonObject {
            put("label", buildJsonObject { put("literalString", "NVDA") })
            put("value", buildJsonObject { put("literalString", "+\$100.00") })
        }
        composeTestRule.setContent {
            A2UISurface(
                definition = singleListItemDefinition("item1", props),
                catalog = FinancialCatalog
            )
        }
        // The outer Row is the only node with a content description — it must contain
        // "positive $100.00" (not "+$100.00") for correct TalkBack announcement.
        composeTestRule
            .onNodeWithContentDescription("NVDA, positive \$100.00")
            .assertIsDisplayed()
    }

    /**
     * Negative monetary value must also produce the correct TalkBack-friendly
     * content description ("negative $50.00" instead of "-$50.00").
     */
    @Test
    fun listItem_contentDescriptionIncludesSemanticNegativeValue() {
        val props = buildJsonObject {
            put("label", buildJsonObject { put("literalString", "TSLA") })
            put("value", buildJsonObject { put("literalString", "-\$50.00") })
        }
        composeTestRule.setContent {
            A2UISurface(
                definition = singleListItemDefinition("item1", props),
                catalog = FinancialCatalog
            )
        }
        composeTestRule
            .onNodeWithContentDescription("TSLA, negative \$50.00")
            .assertIsDisplayed()
    }

    /**
     * A ListItem with all four fields must produce a contentDescription that
     * includes all four values concatenated in the correct order.
     */
    @Test
    fun listItem_withAllFourFields_contentDescriptionIncludesAllValues() {
        val props = buildJsonObject {
            put("label",    buildJsonObject { put("literalString", "Netflix") })
            put("subLabel", buildJsonObject { put("literalString", "Mar 15") })
            put("value",    buildJsonObject { put("literalString", "-\$15.99") })
            put("subValue", buildJsonObject { put("literalString", "Streaming") })
        }
        composeTestRule.setContent {
            A2UISurface(
                definition = singleListItemDefinition("item1", props),
                catalog = FinancialCatalog
            )
        }
        // contentDesc format: "$label, $valueSemantic, $subLabel, $subValue"
        composeTestRule
            .onNodeWithContentDescription("Netflix, negative \$15.99, Mar 15, Streaming")
            .assertIsDisplayed()
    }
}
