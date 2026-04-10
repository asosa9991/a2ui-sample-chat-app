package com.example.a2ui.chat.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.a2ui.chat.domain.model.BackendMode
import com.example.a2ui.chat.domain.model.WireFormat

/**
 * Top app bar with centered [BackendModeToggle] and [WireFormatToggle] stacked vertically.
 *
 * @param selectedMode         Drives the backend mode toggle — hoisted from ViewModel.
 * @param onModeSelected       Forwarded to [BackendModeToggle].
 * @param selectedWireFormat   Drives the wire format toggle — hoisted from ViewModel.
 * @param onWireFormatSelected Forwarded to [WireFormatToggle].
 */
@Composable
fun ChatTopBar(
    selectedMode: BackendMode,
    onModeSelected: (BackendMode) -> Unit,
    selectedWireFormat: WireFormat,
    onWireFormatSelected: (WireFormat) -> Unit,
) {
    Surface(
        shadowElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // LEFT — Hamburger
                IconButton(onClick = { }) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }

                // CENTER — Mode + Wire Format toggles stacked (weight=1f, self-centers)
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        BackendModeToggle(
                            selectedMode = selectedMode,
                            onModeSelected = onModeSelected,
                        )
                        WireFormatToggle(
                            selectedFormat = selectedWireFormat,
                            onFormatSelected = onWireFormatSelected,
                        )
                    }
                }

                // RIGHT — Profile indicator
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

