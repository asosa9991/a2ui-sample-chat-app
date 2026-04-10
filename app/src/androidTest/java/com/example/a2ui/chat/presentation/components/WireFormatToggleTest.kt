package com.example.a2ui.chat.presentation.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit4.runners.AndroidJUnit4
import com.example.a2ui.chat.domain.model.WireFormat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class WireFormatToggleTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun renders_all_three_segment_labels() {
        composeTestRule.setContent {
            WireFormatToggle(selectedFormat = WireFormat.SSE, onFormatSelected = {})
        }
        composeTestRule.onNodeWithText("SSE").assertExists()
        composeTestRule.onNodeWithText("JSONL").assertExists()
        composeTestRule.onNodeWithText("Sync").assertExists()
    }

    @Test
    fun SSE_is_selected_by_default() {
        composeTestRule.setContent {
            WireFormatToggle(selectedFormat = WireFormat.SSE, onFormatSelected = {})
        }
        composeTestRule.onNodeWithContentDescription("SSE wire format, selected").assertExists()
        composeTestRule.onNodeWithContentDescription("JSONL wire format, tap to switch").assertExists()
        composeTestRule.onNodeWithContentDescription("Sync wire format, tap to switch").assertExists()
    }

    @Test
    fun tapping_JSONL_fires_callback_with_JSONL() {
        var selected: WireFormat? = null
        composeTestRule.setContent {
            WireFormatToggle(
                selectedFormat = WireFormat.SSE,
                onFormatSelected = { selected = it },
            )
        }
        composeTestRule.onNodeWithText("JSONL").performClick()
        assertEquals(WireFormat.JSONL, selected)
    }

    @Test
    fun tapping_Sync_fires_callback_with_SYNC() {
        var selected: WireFormat? = null
        composeTestRule.setContent {
            WireFormatToggle(
                selectedFormat = WireFormat.SSE,
                onFormatSelected = { selected = it },
            )
        }
        composeTestRule.onNodeWithText("Sync").performClick()
        assertEquals(WireFormat.SYNC, selected)
    }

    @Test
    fun tapping_already_selected_SSE_does_not_fire_callback() {
        var callCount = 0
        composeTestRule.setContent {
            WireFormatToggle(
                selectedFormat = WireFormat.SSE,
                onFormatSelected = { callCount++ },
            )
        }
        composeTestRule.onNodeWithText("SSE").performClick()
        assertEquals(0, callCount)
    }

    @Test
    fun selection_updates_visually_when_state_changes() {
        var format by mutableStateOf(WireFormat.SSE)
        composeTestRule.setContent {
            WireFormatToggle(selectedFormat = format, onFormatSelected = { format = it })
        }
        composeTestRule.onNodeWithText("Sync").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Sync wire format, selected").assertExists()
    }

    @Test
    fun disabled_toggle_does_not_respond_to_taps() {
        var selected: WireFormat? = null
        composeTestRule.setContent {
            WireFormatToggle(
                selectedFormat = WireFormat.SSE,
                onFormatSelected = { selected = it },
                enabled = false,
            )
        }
        composeTestRule.onNodeWithText("JSONL").performClick()
        assertEquals(null, selected)
    }
}
