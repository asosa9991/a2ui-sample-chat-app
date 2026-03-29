package com.example.a2ui.chat.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.contextable.a2ui4k.data.rememberDataModel
import com.contextable.a2ui4k.model.UiEvent
import com.contextable.a2ui4k.render.A2UISurface
import com.example.a2ui.chat.data.a2ui.FinancialCatalog
import com.example.a2ui.chat.domain.model.Message
import com.example.a2ui.chat.domain.model.Sender
import com.example.a2ui.chat.theme.AiBubble
import com.example.a2ui.chat.theme.SurfaceCardBorder
import com.example.a2ui.chat.theme.UserBubble
import kotlinx.serialization.json.JsonObject

@Composable
fun MessageBubble(
    message: Message,
    onEvent: (UiEvent) -> Unit = {},
    onFeedback: (messageId: String, rating: String, reason: String?) -> Unit = { _, _, _ -> }
) {
    val isUser = message.sender == Sender.USER

    if (!isUser && message.uiDefinition != null) {
        // AI message with an A2UI surface — render full-width card with elevation + border
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.Start
        ) {
            if (message.content.isNotBlank()) {
                Text(
                    text = message.content,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            val dataModel = rememberDataModel(
                initialData = message.dataModelJson ?: JsonObject(emptyMap())
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            border = BorderStroke(1.dp, SurfaceCardBorder),
                            shape = RoundedCornerShape(16.dp)
                        )
                ) {
                    A2UISurface(
                        definition = message.uiDefinition,
                        dataModel = dataModel,
                        catalog = FinancialCatalog,
                        onEvent = onEvent,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (message.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
            if (!message.isLoading) {
                FeedbackBar(
                    onFeedback = { rating, reason -> onFeedback(message.id, rating, reason) },
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    } else {
        val backgroundColor = if (isUser) UserBubble else AiBubble
        val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
        val cornerShape = if (isUser) RoundedCornerShape(18.dp) else RoundedCornerShape(16.dp)

        if (!isUser) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = alignment
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .background(
                                color = backgroundColor,
                                shape = cornerShape
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        if (message.isLoading && message.content.isNotEmpty()) {
                            StreamingText(text = message.content)
                        } else {
                            Text(
                                text = message.content,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
                if (!message.isLoading) {
                    FeedbackBar(
                        onFeedback = { rating, reason -> onFeedback(message.id, rating, reason) },
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = alignment
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .background(
                            color = backgroundColor,
                            shape = cornerShape
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    if (message.isLoading && message.content.isNotEmpty()) {
                        StreamingText(text = message.content)
                    } else {
                        Text(
                            text = message.content,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingText(text: String) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursor_alpha",
    )

    val annotatedString = buildAnnotatedString {
        append(text)
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface.copy(alpha = cursorAlpha))) {
            append("▍")
        }
    }

    Text(
        text = annotatedString,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyLarge,
    )
}
