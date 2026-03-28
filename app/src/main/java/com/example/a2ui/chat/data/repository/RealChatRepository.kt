package com.example.a2ui.chat.data.repository

import com.example.a2ui.chat.data.model.AgentResponseDto
import com.example.a2ui.chat.data.model.toDomain
import com.example.a2ui.chat.domain.model.Message
import com.example.a2ui.chat.domain.model.Sender
import com.example.a2ui.chat.domain.repository.ChatRepository
import com.example.a2ui.chat.domain.repository.StreamEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit

@Serializable
private data class ChatRequest(val message: String)

@Serializable
private data class TokenData(val token: String)

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

    /** Longer read timeout for streaming — tokens may arrive slowly. */
    private val streamingClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
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

    override fun sendMessageStream(userMessage: String): Flow<StreamEvent> = flow {
        try {
            val requestBody = json.encodeToString(ChatRequest(message = userMessage))
                .toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url("$baseUrl/chat/stream")
                .post(requestBody)
                .header("Accept", "text/event-stream")
                .build()

            val response = withContext(Dispatchers.IO) {
                streamingClient.newCall(request).execute()
            }

            if (!response.isSuccessful) {
                response.close()
                throw IllegalStateException("HTTP ${response.code}: ${response.message}")
            }

            val source = response.body?.source()
            if (source == null) {
                response.close()
                throw IllegalStateException("Empty response body")
            }

            try {
                var eventType = ""
                while (true) {
                    val line = withContext(Dispatchers.IO) {
                        if (source.exhausted()) null else source.readUtf8Line()
                    } ?: break

                    when {
                        line.startsWith("event: ") -> {
                            eventType = line.removePrefix("event: ").trim()
                        }
                        line.startsWith("data: ") -> {
                            val data = line.removePrefix("data: ")
                            when (eventType) {
                                "token" -> {
                                    try {
                                        val tokenData = json.decodeFromString<TokenData>(data)
                                        emit(StreamEvent.Token(tokenData.token))
                                    } catch (_: Exception) {
                                        // Skip malformed token events
                                    }
                                }
                                "done" -> {
                                    val doneMessage = parseDoneEvent(data)
                                    emit(StreamEvent.Done(doneMessage))
                                    return@flow
                                }
                            }
                            eventType = ""
                        }
                    }
                }
            } finally {
                response.close()
            }
        } catch (e: Exception) {
            emit(StreamEvent.Error(e.message ?: "Unknown streaming error"))
        }
    }

    private fun parseDoneEvent(data: String): Message {
        return try {
            val parsed = json.parseToJsonElement(data).jsonObject
            val text = parsed["text"]?.jsonPrimitive?.contentOrNull ?: ""
            val uiDefElement = parsed["ui_definition"]

            val uiDefinitionDto = if (uiDefElement != null && uiDefElement !is JsonNull) {
                json.decodeFromString<com.example.a2ui.chat.data.model.UiDefinitionDto>(
                    uiDefElement.toString()
                )
            } else null

            Message(
                id = UUID.randomUUID().toString(),
                content = text,
                sender = Sender.AI,
                timestamp = System.currentTimeMillis(),
                isLoading = false,
                uiDefinition = uiDefinitionDto?.toDomain()
            )
        } catch (e: Exception) {
            Message(
                id = UUID.randomUUID().toString(),
                content = data,
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
