package com.example.a2ui.chat.domain.usecase

import com.example.a2ui.chat.domain.model.Message
import com.example.a2ui.chat.domain.repository.ChatRepository

class SendMessageUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(userMessage: String): Message {
        return repository.sendMessage(userMessage)
    }
}
