package com.example.a2ui.chat.domain.model

import androidx.compose.runtime.Immutable
import com.contextable.a2ui4k.model.UiDefinition

@Immutable
data class Message(
    val id: String,
    val content: String,
    val sender: Sender,
    val timestamp: Long,
    val isLoading: Boolean = false,
    val uiDefinition: UiDefinition? = null
)
