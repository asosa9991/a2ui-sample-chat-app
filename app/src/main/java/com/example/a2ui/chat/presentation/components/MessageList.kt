package com.example.a2ui.chat.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.contextable.a2ui4k.model.UiEvent
import com.example.a2ui.chat.domain.model.Message
import kotlinx.collections.immutable.ImmutableList

@Composable
fun MessageList(
    messages: ImmutableList<Message>,
    isAiResponding: Boolean = false,
    onEvent: (UiEvent) -> Unit = {},
    onFeedback: (messageId: String, rating: String, reason: String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Show typing indicator only when AI is responding AND no streaming content is visible yet
    val hasStreamingContent = messages.any { it.isLoading && (it.content.isNotEmpty() || it.uiDefinition != null) }
    val showTypingIndicator = isAiResponding && !hasStreamingContent

    // Include uiDefinition component count so scroll triggers on progressive surface updates
    val scrollTrigger = messages.size.toString() +
            messages.lastOrNull()?.content?.length.toString() +
            messages.lastOrNull()?.uiDefinition?.components?.size.toString() +
            showTypingIndicator.toString()

    LaunchedEffect(scrollTrigger) {
        if (messages.isNotEmpty() || showTypingIndicator) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (showTypingIndicator) {
            item(key = "typing_indicator") {
                TypingIndicator()
            }
        }

        items(
            items = messages.reversed(),
            key = { it.id }
        ) { message ->
            MessageBubble(message = message, onEvent = onEvent, onFeedback = onFeedback)
        }
    }
}
