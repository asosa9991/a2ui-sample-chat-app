package com.example.a2ui.chat.presentation.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.a2ui.chat.presentation.components.editor.JsonEditorPanel
import com.example.a2ui.chat.presentation.components.editor.RenderPanel
import com.example.a2ui.chat.presentation.editor.EditorState
import com.example.a2ui.chat.presentation.editor.ParseResult
import com.example.a2ui.chat.presentation.editor.rememberEditorState

/**
 * Full-screen editor for inspecting and live-editing A2UI surface JSON.
 *
 * Receives pre-serialized JSON strings for components and data model.
 * On compact screens (< 840dp wide), uses a vertical layout with preview on top
 * and editors below. On wider screens, uses a side-by-side layout.
 *
 * @param initialComponents Pretty-printed JSON array of components.
 * @param initialData Pretty-printed JSON object of initial data model.
 * @param onBack Callback to navigate back to the chat screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    initialComponents: String,
    initialData: String,
    onBack: () -> Unit
) {
    val state = rememberEditorState(initialComponents, initialData)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Surface") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (maxWidth < 840.dp) {
                CompactEditorLayout(state)
            } else {
                ExpandedEditorLayout(state)
            }
        }
    }
}

/**
 * Portrait / phone layout: preview on top, editors stacked below.
 */
@Composable
private fun CompactEditorLayout(state: EditorState) {
    val componentsError = (state.parseResult as? ParseResult.Error)
        ?.message?.takeIf { it.startsWith("Components") }
    val dataError = (state.parseResult as? ParseResult.Error)
        ?.message?.takeIf { it.startsWith("Data") }

    Column(modifier = Modifier.fillMaxSize()) {
        // Live preview
        RenderPanel(
            parseResult = state.parseResult,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp)
        )

        HorizontalDivider()

        // Components editor
        JsonEditorPanel(
            label = "Components JSON",
            value = state.componentsText,
            onValueChange = state::onComponentsChanged,
            error = componentsError,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp, max = 200.dp)
        )

        HorizontalDivider()

        // Data editor
        JsonEditorPanel(
            label = "Data JSON",
            value = state.dataText,
            onValueChange = state::onDataChanged,
            error = dataError,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp, max = 120.dp)
        )
    }
}

/**
 * Landscape / tablet layout: editors on the left, preview on the right.
 */
@Composable
private fun ExpandedEditorLayout(state: EditorState) {
    val componentsError = (state.parseResult as? ParseResult.Error)
        ?.message?.takeIf { it.startsWith("Components") }
    val dataError = (state.parseResult as? ParseResult.Error)
        ?.message?.takeIf { it.startsWith("Data") }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left panel: JSON editors stacked vertically
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
        ) {
            JsonEditorPanel(
                label = "Components JSON",
                value = state.componentsText,
                onValueChange = state::onComponentsChanged,
                error = componentsError,
                modifier = Modifier
                    .weight(0.65f)
                    .fillMaxWidth()
            )

            HorizontalDivider()

            JsonEditorPanel(
                label = "Data JSON",
                value = state.dataText,
                onValueChange = state::onDataChanged,
                error = dataError,
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxWidth()
            )
        }

        VerticalDivider()

        // Right panel: live preview
        RenderPanel(
            parseResult = state.parseResult,
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .padding(8.dp)
        )
    }
}
