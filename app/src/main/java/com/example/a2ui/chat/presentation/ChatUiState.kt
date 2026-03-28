package com.example.a2ui.chat.presentation

import com.example.a2ui.chat.domain.model.Message
import kotlinx.collections.immutable.ImmutableList

sealed interface ChatUiState {
    data object Empty : ChatUiState
    data class Active(
        val messages: ImmutableList<Message>,
        val isAiResponding: Boolean
    ) : ChatUiState
}
