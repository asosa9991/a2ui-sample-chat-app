package com.example.a2ui.chat.data.repository

import com.example.a2ui.chat.data.model.AgentResponseDto
import com.example.a2ui.chat.data.model.toDomain
import com.example.a2ui.chat.domain.model.Message
import com.example.a2ui.chat.domain.model.Sender
import com.example.a2ui.chat.domain.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit

@Serializable
private data class ChatRequest(val message: String)

class RealChatRepository(
    private val baseUrl: String = "http://10.0.2.2:8000"
) : ChatRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun sendMessage(userMessage: String): Message {
        return try {
            val requestBody = json.encodeToString(ChatRequest(message = userMessage))
                .toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url("$baseUrl/chat")
                .post(requestBody)
                .build()

            val responseBody = withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    response.body?.string()
                        ?: throw IllegalStateException("Empty response body")
                }
            }

            val agentResponse = json.decodeFromString<AgentResponseDto>(responseBody)

            Message(
                id = UUID.randomUUID().toString(),
                content = agentResponse.text,
                sender = Sender.AI,
                timestamp = System.currentTimeMillis(),
                isLoading = false,
                uiDefinition = agentResponse.uiDefinition?.toDomain()
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
