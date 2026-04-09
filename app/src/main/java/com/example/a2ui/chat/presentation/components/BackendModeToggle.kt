package com.example.a2ui.chat.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.a2ui.chat.domain.model.BackendMode
import com.example.a2ui.chat.theme.CardBorderSubtle
import com.example.a2ui.chat.theme.LightSurfaceVariant
import com.example.a2ui.chat.theme.OnPrimary
import com.example.a2ui.chat.theme.OnSurfaceVariant
import com.example.a2ui.chat.theme.Primary

/**
 * Segmented pill toggle for selecting the active backend mode.
 *
 * @param selectedMode   The currently active [BackendMode].
 * @param onModeSelected Callback invoked only when the user taps the *inactive* segment.
 * @param modifier       Optional layout modifier for the outer pill container.
 * @param enabled        When false, both segments are non-interactive and visually dimmed.
 */
@Composable
fun BackendModeToggle(
    selectedMode: BackendMode,
    onModeSelected: (BackendMode) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val modes = BackendMode.entries

    Box(
        modifier = modifier
            .height(32.dp)
            .width(152.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(LightSurfaceVariant)
            .border(0.5.dp, CardBorderSubtle, RoundedCornerShape(16.dp))
            .semantics { contentDescription = "Backend mode selector" },
        contentAlignment = Alignment.Center,
    ) {
        Row(modifier = Modifier.fillMaxSize().selectableGroup()) {
            modes.forEach { mode ->
                val isActive = mode == selectedMode

                val chipBg by animateColorAsState(
                    targetValue = if (isActive) Primary else Color.Transparent,
                    animationSpec = tween(150, easing = FastOutSlowInEasing),
                    label = "chipBg_${mode.name}",
                )
                val labelColor by animateColorAsState(
                    targetValue = if (isActive) OnPrimary else OnSurfaceVariant,
                    animationSpec = tween(150, easing = FastOutSlowInEasing),
                    label = "labelColor_${mode.name}",
                )
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.96f else 1f,
                    label = "pressScale_${mode.name}",
                )

                val segmentDescription = if (isActive) {
                    "${mode.label} mode, selected"
                } else {
                    "${mode.label} mode, tap to switch"
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(chipBg)
                        .scale(scale)
                        .clickable(
                            enabled = enabled && !isActive,
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onModeSelected(mode) },
                        )
                        .semantics {
                            contentDescription = segmentDescription
                            role = Role.RadioButton
                            selected = isActive
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp,
                        ),
                        color = labelColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
