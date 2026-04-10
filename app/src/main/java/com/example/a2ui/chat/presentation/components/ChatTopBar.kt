package com.example.a2ui.chat.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.a2ui.chat.domain.model.BackendMode
import com.example.a2ui.chat.domain.model.WireFormat
import com.example.a2ui.chat.theme.DesignerAmber

/**
 * Top app bar with centered [BackendModeToggle] and [WireFormatToggle] stacked vertically.
 *
 * The profile avatar supports a **3-tap gesture** that activates designer mode — each
 * tap within a 900ms window increments a counter; on the 3rd tap the haptic fires and
 * [onAvatarTripleTap] is invoked. An amber ring appears around the avatar while
 * [isDesignerMode] is true.
 *
 * @param selectedMode         Drives the backend mode toggle — hoisted from ViewModel.
 * @param onModeSelected       Forwarded to [BackendModeToggle].
 * @param selectedWireFormat   Drives the wire format toggle — hoisted from ViewModel.
 * @param onWireFormatSelected Forwarded to [WireFormatToggle].
 * @param isDesignerMode       When true the profile avatar shows an amber highlight ring.
 * @param onAvatarTripleTap    Called when the user triple-taps the profile avatar.
 */
@Composable
fun ChatTopBar(
    selectedMode: BackendMode,
    onModeSelected: (BackendMode) -> Unit,
    selectedWireFormat: WireFormat,
    onWireFormatSelected: (WireFormat) -> Unit,
    isDesignerMode: Boolean = false,
    onAvatarTripleTap: () -> Unit = {},
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

                // RIGHT — Profile indicator with designer mode 3-tap gesture
                val haptic = LocalHapticFeedback.current
                var tapCount by remember { mutableIntStateOf(0) }
                var lastTapTime by remember { mutableLongStateOf(0L) }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .padding(4.dp)
                        .pointerInput(Unit) {
                            detectTapGestures {
                                val now = System.currentTimeMillis()
                                if (now - lastTapTime > 900L) {
                                    tapCount = 1
                                } else {
                                    tapCount++
                                }
                                lastTapTime = now
                                if (tapCount >= 3) {
                                    tapCount = 0
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onAvatarTripleTap()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // Amber ring animates in/out when designer mode is toggled
                    val ringColor by animateColorAsState(
                        targetValue = if (isDesignerMode) DesignerAmber else Color.Transparent,
                        label = "designerRing",
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .border(2.dp, ringColor, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Profile — triple tap for designer mode",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
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


