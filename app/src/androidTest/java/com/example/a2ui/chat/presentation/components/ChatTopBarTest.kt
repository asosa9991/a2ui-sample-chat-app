package com.example.a2ui.chat.presentation.components

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit4.runners.AndroidJUnit4
import com.example.a2ui.chat.domain.model.BackendMode
import com.example.a2ui.chat.domain.model.WireFormat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class ChatTopBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun both_toggles_are_visible() {
        composeTestRule.setContent {
            ChatTopBar(
                selectedMode = BackendMode.LLM,
                onModeSelected = {},
                selectedWireFormat = WireFormat.SSE,
                onWireFormatSelected = {},
            )
        }
        composeTestRule.onNodeWithContentDescription("Backend mode selector").assertExists()
        composeTestRule.onNodeWithContentDescription("Wire format selector").assertExists()
    }

    @Test
    fun sync_chip_is_visible_in_top_bar() {
        composeTestRule.setContent {
            ChatTopBar(
                selectedMode = BackendMode.LLM,
                onModeSelected = {},
                selectedWireFormat = WireFormat.SSE,
                onWireFormatSelected = {},
            )
        }
        composeTestRule.onNodeWithText("Sync").assertExists()
    }

    @Test
    fun tapping_Sync_invokes_setWireFormat_with_SYNC() {
        var received: WireFormat? = null
        composeTestRule.setContent {
            ChatTopBar(
                selectedMode = BackendMode.LLM,
                onModeSelected = {},
                selectedWireFormat = WireFormat.SSE,
                onWireFormatSelected = { received = it },
            )
        }
        composeTestRule.onNodeWithText("Sync").performClick()
        assertEquals(WireFormat.SYNC, received)
    }

    @Test
    fun tapping_Template_invokes_setBackendMode_with_TEMPLATE() {
        var received: BackendMode? = null
        composeTestRule.setContent {
            ChatTopBar(
                selectedMode = BackendMode.LLM,
                onModeSelected = { received = it },
                selectedWireFormat = WireFormat.SSE,
                onWireFormatSelected = {},
            )
        }
        composeTestRule.onNodeWithText("Template").performClick()
        assertEquals(BackendMode.TEMPLATE, received)
    }
}
