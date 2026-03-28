package com.example.a2ui.chat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.a2ui.chat.data.repository.MockChatRepository
import com.example.a2ui.chat.domain.model.Message
import com.example.a2ui.chat.domain.model.Sender
import com.example.a2ui.chat.domain.usecase.SendMessageUseCase
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(
    private val sendMessageUseCase: SendMessageUseCase,
    private val repository: MockChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Empty)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val greeting: String = repository.getGreeting()

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        
        val currentUiState = _uiState.value
        val isResponding = (currentUiState as? ChatUiState.Active)?.isAiResponding == true
        if (isResponding) return

        val userMessage = Message(
            id = UUID.randomUUID().toString(),
            content = content,
            sender = Sender.USER,
            timestamp = System.currentTimeMillis()
        )

        val currentMessages = when (currentUiState) {
            is ChatUiState.Empty -> emptyList()
            is ChatUiState.Active -> currentUiState.messages.toList()
        }

        val updatedMessages = currentMessages + userMessage
        _uiState.update { ChatUiState.Active(updatedMessages.toImmutableList(), isAiResponding = true) }

        viewModelScope.launch {
            val response = sendMessageUseCase(content)
            _uiState.update { state ->
                if (state is ChatUiState.Active) {
                    val messagesWithResponse = (state.messages.toList() + response).toImmutableList()
                    ChatUiState.Active(messagesWithResponse, isAiResponding = false)
                } else {
                    state
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repository = MockChatRepository()
                val useCase = SendMessageUseCase(repository)
                return ChatViewModel(useCase, repository) as T
            }
        }
    }
}
