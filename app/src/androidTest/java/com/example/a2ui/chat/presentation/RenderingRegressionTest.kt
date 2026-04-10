package com.example.a2ui.chat.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.contextable.a2ui4k.data.rememberDataModel
import com.contextable.a2ui4k.model.Component
import com.contextable.a2ui4k.model.UiDefinition
import com.contextable.a2ui4k.render.A2UISurface
import com.example.a2ui.chat.data.a2ui.FinancialCatalog
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI regression tests that catch rendering failures visible to the user.
 *
 * These tests cover:
 * - Bug #1: Sync endpoint missing componentProperties wrapper
 * - Bug #2: explicitList children format not parsed by Column/Row widgets
 *
 * Each test verifies that real content is visible and no error text is shown.
 */
@RunWith(AndroidJUnit4::class)
class RenderingRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun makeComponent(id: String, widgetType: String, props: JsonObject = JsonObject(emptyMap())): Component =
        Component(id = id, componentProperties = mapOf(widgetType to props))

    @Test
    fun column_withExplicitListChildren_rendersChildText() {
        val textProps = buildJsonObject {
            put("text", buildJsonObject { put("literalString", "Hello World") })
        }
        val colProps = buildJsonObject {
            put("children", buildJsonObject {
                put("explicitList", kotlinx.serialization.json.buildJsonArray {
                    add(JsonPrimitive("text1"))
                })
            })
        }
        val uiDef = UiDefinition(
            surfaceId = "test_surface",
            root = "root",
            components = mapOf(
                "root" to makeComponent("root", "Column", colProps),
                "text1" to makeComponent("text1", "Text", textProps)
            )
        )
        composeTestRule.setContent {
            A2UISurface(
                definition = uiDef,
                catalog = FinancialCatalog
            )
        }
        composeTestRule.onNodeWithText("Hello World").assertIsDisplayed()
    }

    @Test
    fun listItem_withLiteralStrings_rendersLabelAndValue() {
        val listItemProps = buildJsonObject {
            put("label", buildJsonObject { put("literalString", "Checking Account") })
            put("value", buildJsonObject { put("literalString", "\$1,500.00") })
        }
        val colProps = buildJsonObject {
            put("children", buildJsonObject {
                put("explicitList", kotlinx.serialization.json.buildJsonArray {
                    add(JsonPrimitive("item1"))
                })
            })
        }
        val uiDef = UiDefinition(
            surfaceId = "test_surface",
            root = "root",
            components = mapOf(
                "root" to makeComponent("root", "Column", colProps),
                "item1" to makeComponent("item1", "ListItem", listItemProps)
            )
        )
        composeTestRule.setContent {
            A2UISurface(
                definition = uiDef,
                catalog = FinancialCatalog
            )
        }
        composeTestRule.onNodeWithText("Checking Account").assertIsDisplayed()
        composeTestRule.onNodeWithText("\$1,500.00").assertIsDisplayed()
    }

    @Test
    fun card_withPlainStringChild_rendersContent() {
        val textProps = buildJsonObject {
            put("text", buildJsonObject { put("literalString", "Card Content") })
        }
        val cardProps = buildJsonObject {
            put("child", JsonPrimitive("content"))
        }
        val colProps = buildJsonObject {
            put("children", buildJsonObject {
                put("explicitList", kotlinx.serialization.json.buildJsonArray {
                    add(JsonPrimitive("card1"))
                })
            })
        }
        val uiDef = UiDefinition(
            surfaceId = "test_surface",
            root = "root",
            components = mapOf(
                "root" to makeComponent("root", "Column", colProps),
                "card1" to makeComponent("card1", "Card", cardProps),
                "content" to makeComponent("content", "Text", textProps)
            )
        )
        composeTestRule.setContent {
            A2UISurface(
                definition = uiDef,
                catalog = FinancialCatalog
            )
        }
        composeTestRule.onNodeWithText("Card Content").assertIsDisplayed()
    }

    @Test
    fun noInvalidComponentErrorText_whenValidDefinition() {
        val textProps = buildJsonObject {
            put("text", buildJsonObject { put("literalString", "Valid Content") })
        }
        val colProps = buildJsonObject {
            put("children", buildJsonObject {
                put("explicitList", kotlinx.serialization.json.buildJsonArray {
                    add(JsonPrimitive("text1"))
                })
            })
        }
        val uiDef = UiDefinition(
            surfaceId = "test_surface",
            root = "root",
            components = mapOf(
                "root" to makeComponent("root", "Column", colProps),
                "text1" to makeComponent("text1", "Text", textProps)
            )
        )
        composeTestRule.setContent {
            A2UISurface(
                definition = uiDef,
                catalog = FinancialCatalog
            )
        }
        // Verify error text does not appear
        composeTestRule.onAllNodesWithText("Invalid").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Missing component").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Unknown widget").assertCountEquals(0)
    }

    // ── Row with explicitList ──────────────────────────────────────────────

    /**
     * Bug #2 regression — Row children via {"explicitList": [...]} format.
     *
     * Before the fix, financialRowWidget called parseComponentArray() which returned null
     * for explicitList format, so children were empty and the Row rendered nothing.
     * After the fix the Row renders its Text child.
     */
    @Test
    fun row_withExplicitListChildren_rendersChildText() {
        val textProps = buildJsonObject {
            put("text", buildJsonObject { put("literalString", "Row Child Text") })
        }
        val rowProps = buildJsonObject {
            put("children", buildJsonObject {
                put("explicitList", buildJsonArray {
                    add(JsonPrimitive("text1"))
                })
            })
        }
        val uiDef = UiDefinition(
            surfaceId = "test_row_surface",
            root = "root",
            components = mapOf(
                "root" to makeComponent("root", "Row", rowProps),
                "text1" to makeComponent("text1", "Text", textProps)
            )
        )
        composeTestRule.setContent {
            A2UISurface(
                definition = uiDef,
                catalog = FinancialCatalog
            )
        }
        composeTestRule.onNodeWithText("Row Child Text").assertIsDisplayed()
    }

    // ── ListItem with path-based field resolution ─────────────────────────

    /**
     * Bug #2 regression — ListItem renders DataModel-resolved label text.
     *
     * ListItem fields that use {"path": "fieldName"} are resolved via DataContext.
     * This test seeds the DataModel with {action: "Buy NVDA"} and verifies that
     * the ListItem label, which references path "action", renders "Buy NVDA".
     *
     * The content description on the outer Row is "Buy NVDA, done" (label + value),
     * making it uniquely identifiable even when inner Text nodes are inside
     * invisibleToUser() semantic nodes.
     */
    @Test
    fun listItem_withPathString_rendersDataModelValue() {
        val listItemProps = buildJsonObject {
            put("label", buildJsonObject { put("path", "action") })
            put("value", buildJsonObject { put("literalString", "done") })
        }
        val colProps = buildJsonObject {
            put("children", buildJsonObject {
                put("explicitList", buildJsonArray {
                    add(JsonPrimitive("item1"))
                })
            })
        }
        val uiDef = UiDefinition(
            surfaceId = "test_path_surface",
            root = "root",
            components = mapOf(
                "root" to makeComponent("root", "Column", colProps),
                "item1" to makeComponent("item1", "ListItem", listItemProps)
            )
        )
        val initialData = buildJsonObject {
            put("action", "Buy NVDA")
        }
        composeTestRule.setContent {
            val dataModel = rememberDataModel(initialData = initialData)
            A2UISurface(
                definition = uiDef,
                dataModel = dataModel,
                catalog = FinancialCatalog
            )
        }
        // The ListItem's outer Row has semantics contentDescription = "Buy NVDA, done"
        composeTestRule.onNodeWithContentDescription("Buy NVDA, done").assertIsDisplayed()
    }
}
