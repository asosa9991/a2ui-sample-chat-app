package com.example.a2ui.chat.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.a2ui.chat.theme.DesignerAmber
import com.example.a2ui.chat.theme.DesignerAmberBorder
import com.example.a2ui.chat.theme.OnDesignerAmber

/**
 * Thin 36dp amber banner displayed below the TopBar when designer mode is active.
 *
 * Animates in/out vertically with [AnimatedVisibility].
 *
 * @param visible       Whether the banner is visible (driven by [DesignerState.isDesignerMode]).
 * @param onExitClick   Called when the user taps "Exit".
 * @param modifier      Optional modifier for the outer [AnimatedVisibility] wrapper.
 */
@Composable
fun DesignerModeBanner(
    visible: Boolean,
    onExitClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(expandFrom = Alignment.Top),
        exit = shrinkVertically(shrinkTowards = Alignment.Top),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(DesignerAmber)
                .border(
                    width = 0.5.dp,
                    color = DesignerAmberBorder,
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "🎨 Designer Mode",
                color = OnDesignerAmber,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(
                onClick = onExitClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp),
            ) {
                Text(
                    text = "Exit",
                    color = OnDesignerAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
