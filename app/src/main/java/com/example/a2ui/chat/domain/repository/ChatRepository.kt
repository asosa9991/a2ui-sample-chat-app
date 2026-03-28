package com.example.a2ui.chat.domain.repository

import com.example.a2ui.chat.domain.model.Message

interface ChatRepository {
    suspend fun sendMessage(userMessage: String): Message
    fun getGreeting(): String
}
