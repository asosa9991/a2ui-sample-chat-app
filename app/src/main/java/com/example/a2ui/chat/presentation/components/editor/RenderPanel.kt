package com.example.a2ui.chat.presentation.components.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.contextable.a2ui4k.data.rememberDataModel
import com.contextable.a2ui4k.render.A2UISurface
import com.example.a2ui.chat.data.a2ui.FinancialCatalog
import com.example.a2ui.chat.presentation.editor.ParseResult
import com.example.a2ui.chat.theme.SurfaceCardBorder

/**
 * Live preview panel that renders the parsed [UiDefinition] via [A2UISurface],
 * or shows a placeholder when the JSON is empty or has errors.
 */
@Composable
fun RenderPanel(
    parseResult: ParseResult,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .border(
                border = BorderStroke(1.dp, SurfaceCardBorder),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        when (parseResult) {
            is ParseResult.Success -> {
                val dataModel = rememberDataModel(initialData = parseResult.dataJson)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    A2UISurface(
                        definition = parseResult.definition,
                        dataModel = dataModel,
                        catalog = FinancialCatalog,
                        onEvent = { /* editor preview — events are no-ops */ },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            is ParseResult.Error -> {
                PlaceholderContent(
                    icon = Icons.Default.Error,
                    message = "Fix JSON errors to see preview"
                )
            }

            is ParseResult.Empty -> {
                PlaceholderContent(
                    icon = Icons.Default.Preview,
                    message = "Enter JSON to see preview"
                )
            }
        }
    }
}

@Composable
private fun PlaceholderContent(
    icon: ImageVector,
    message: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
