package com.example.a2ui.chat.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.a2ui.chat.theme.NegativeText
import com.example.a2ui.chat.theme.OnSurfaceMuted
import com.example.a2ui.chat.theme.PositiveGreen
import kotlinx.coroutines.delay

private enum class FeedbackState { IDLE, BAD_PENDING, BAD_REASONS, SUBMITTED, GOOD }

private val badReasons = listOf("Not accurate", "Not helpful", "Too complex")

@Composable
fun FeedbackBar(
    onFeedback: (rating: String, reason: String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var state by remember { mutableStateOf(FeedbackState.IDLE) }

    // Auto-collapse GOOD → SUBMITTED after 800ms
    if (state == FeedbackState.GOOD) {
        LaunchedEffect(Unit) {
            delay(800)
            state = FeedbackState.SUBMITTED
        }
    }

    AnimatedContent(
        targetState = state,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "FeedbackBarState",
        modifier = modifier
    ) { targetState ->
        when (targetState) {
            FeedbackState.IDLE -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    IconButton(
                        onClick = { onFeedback("positive", null); state = FeedbackState.GOOD },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = "Helpful",
                            tint = OnSurfaceMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { state = FeedbackState.BAD_PENDING },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbDown,
                            contentDescription = "Not helpful",
                            tint = OnSurfaceMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            FeedbackState.BAD_PENDING -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ThumbDown,
                        contentDescription = null,
                        tint = NegativeText,
                        modifier = Modifier.size(16.dp)
                    )
                    OutlinedButton(
                        onClick = { state = FeedbackState.BAD_REASONS },
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NegativeText
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NegativeText),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp, vertical = 0.dp
                        )
                    ) {
                        Text(
                            text = "Submit",
                            style = MaterialTheme.typography.labelMedium,
                            color = NegativeText
                        )
                    }
                }
            }

            FeedbackState.BAD_REASONS -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "What went wrong?",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceMuted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        badReasons.forEach { reason ->
                            SuggestionChip(
                                onClick = { onFeedback("negative", reason); state = FeedbackState.SUBMITTED },
                                label = {
                                    Text(
                                        text = reason,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            )
                        }
                    }
                }
            }

            FeedbackState.SUBMITTED -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = OnSurfaceMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Thanks for your feedback",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceMuted
                    )
                }
            }

            FeedbackState.GOOD -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = PositiveGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Thanks for your feedback",
                        style = MaterialTheme.typography.bodySmall,
                        color = PositiveGreen
                    )
                }
            }
        }
    }
}
