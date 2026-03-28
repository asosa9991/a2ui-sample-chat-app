package com.example.a2ui.chat.domain.repository

import com.example.a2ui.chat.domain.model.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed class StreamEvent {
    data class Token(val text: String) : StreamEvent()
    data class Done(val message: Message) : StreamEvent()
    data class Error(val error: String) : StreamEvent()
}

interface ChatRepository {
    suspend fun sendMessage(userMessage: String): Message
    fun getGreeting(): String

    fun sendMessageStream(userMessage: String): Flow<StreamEvent> = flow {
        // Default implementation falls back to non-streaming
        emit(StreamEvent.Done(sendMessage(userMessage)))
    }
}
