package com.example.a2ui.chat.data.repository

import com.example.a2ui.chat.data.model.AgentResponseDto
import com.example.a2ui.chat.data.model.toDomain
import com.example.a2ui.chat.domain.model.Message
import com.example.a2ui.chat.domain.model.Sender
import com.example.a2ui.chat.domain.repository.ChatRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.UUID

@Serializable
private data class ChatRequest(val message: String)

class RealChatRepository(
    private val baseUrl: String = "http://10.0.2.2:8000"
) : ChatRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }

    override suspend fun sendMessage(userMessage: String): Message {
        return try {
            val response: AgentResponseDto = client.post("$baseUrl/chat") {
                contentType(ContentType.Application.Json)
                setBody(ChatRequest(message = userMessage))
            }.body()

            Message(
                id = UUID.randomUUID().toString(),
                content = response.text,
                sender = Sender.AI,
                timestamp = System.currentTimeMillis(),
                isLoading = false,
                uiDefinition = response.uiDefinition?.toDomain()
            )
        } catch (e: Exception) {
            Message(
                id = UUID.randomUUID().toString(),
                content = "Couldn't reach the agent server. Make sure it's running at $baseUrl.",
                sender = Sender.AI,
                timestamp = System.currentTimeMillis(),
                isLoading = false
            )
        }
    }

    override fun getGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "morning"
            hour < 17 -> "afternoon"
            else -> "evening"
        }
    }
}
