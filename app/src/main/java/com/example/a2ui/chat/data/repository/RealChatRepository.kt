package com.example.a2ui.chat.data.repository

import android.util.Log
import com.example.a2ui.chat.data.model.AgentResponseDto
import com.example.a2ui.chat.data.model.toDomain
import com.example.a2ui.chat.domain.model.Message
import com.example.a2ui.chat.domain.model.Sender
import com.example.a2ui.chat.domain.repository.ChatRepository
import com.example.a2ui.chat.domain.repository.StreamEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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

    companion object {
        private const val TAG = "A2UI.Repo"
    }

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
        Log.i(TAG, "sendMessage: \"${userMessage.take(60)}\"")
        return try {
            val requestBody = json.encodeToString(ChatRequest(message = userMessage))
                .toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url("$baseUrl/chat")
                .post(requestBody)
                .build()

            val responseBody = withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    Log.d(TAG, "sendMessage: HTTP ${response.code}")
                    response.body?.string()
                        ?: throw IllegalStateException("Empty response body")
                }
            }

            val agentResponse = json.decodeFromString<AgentResponseDto>(responseBody)
            Log.d(TAG, "sendMessage: response hasUi=${agentResponse.uiDefinition != null}")

            Message(
                id = UUID.randomUUID().toString(),
                content = agentResponse.text,
                sender = Sender.AI,
                timestamp = System.currentTimeMillis(),
                isLoading = false,
                uiDefinition = agentResponse.uiDefinition?.toDomain()
            )
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage: failed", e)
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
        Log.i(TAG, "[stream] start: \"${userMessage.take(60)}\"")

        var attempt = 0
        val maxAttempts = 3
        var lastError: IOException? = null
        var doneReceived = false

        while (attempt < maxAttempts && !doneReceived) {
            if (attempt > 0) {
                val delayMs = (2_000L shl (attempt - 1)) // 2s, 4s, 8s
                Log.w(TAG, "[stream] retry attempt=$attempt after ${delayMs}ms")
                delay(delayMs)
            }
            attempt++
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

                Log.d(TAG, "[stream] HTTP ${response.code}")

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
                                Log.d(TAG, "[stream] event=$eventType dataLen=${data.length}")
                                when (eventType) {
                                    // ── A2UI v0.8 protocol events ──────────────
                                    "a2ui_op" -> {
                                        emit(StreamEvent.A2UiOp(data))
                                    }

                                    "text" -> {
                                        try {
                                            val textObj = json.parseToJsonElement(data).jsonObject
                                            val text = textObj["text"]?.jsonPrimitive?.contentOrNull ?: ""
                                            emit(StreamEvent.TextContent(text))
                                        } catch (e: Exception) {
                                            Log.w(TAG, "[stream] malformed text event: \"${data.take(200)}\"", e)
                                        }
                                    }

                                    "done" -> {
                                        Log.i(TAG, "[stream] done event received")
                                        // Check for backward-compatible "done" with embedded ui_definition
                                        val doneMessage = parseDoneEvent(data)
                                        emit(StreamEvent.Done(doneMessage))
                                        doneReceived = true
                                    }

                                    // ── Legacy token streaming (backward compat) ──
                                    "token" -> {
                                        try {
                                            val tokenData = json.decodeFromString<TokenData>(data)
                                            emit(StreamEvent.Token(tokenData.token))
                                        } catch (e: Exception) {
                                            Log.w(TAG, "[stream] malformed token event: \"${data.take(200)}\"", e)
                                        }
                                    }
                                }
                                eventType = ""
                            }
                        }
                        if (doneReceived) break
                    }
                } finally {
                    try { response.close() } catch (_: Exception) { /* already handled */ }
                }
            } catch (e: IOException) {
                lastError = e
                Log.w(TAG, "[stream] IOException on attempt $attempt", e)
                // loop continues for retry
            } catch (e: Exception) {
                // Non-retryable error (e.g. HTTP 4xx/5xx IllegalStateException)
                Log.e(TAG, "[stream] non-retryable error", e)
                emit(StreamEvent.Error(e.message ?: "Unknown streaming error"))
                return@flow
            }
        }

        if (!doneReceived) {
            emit(StreamEvent.Error("Connection failed after $maxAttempts attempts: ${lastError?.message}"))
        }
    }

    /**
     * Parse a `done` event payload. Handles two formats:
     * 1. **Legacy (snapshot mode):** data contains `text` and optional `ui_definition`.
     * 2. **A2UI v0.8:** data is `{}` — the ViewModel builds the final message from
     *    accumulated operations, so we return a shell message here.
     */
    private fun parseDoneEvent(data: String): Message {
        return try {
            val parsed = json.parseToJsonElement(data).jsonObject
            val text = parsed["text"]?.jsonPrimitive?.contentOrNull ?: ""
            val uiDefElement = parsed["ui_definition"]

            // Legacy format: done event carries an embedded ui_definition
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

    // ── Event sending (Client → Server) ────────────────────────────────

    override suspend fun sendEvent(
        surfaceId: String,
        eventType: String,
        name: String?,
        sourceComponentId: String?,
        path: String?,
        value: String?,
        context: Map<String, String>?
    ) {
        Log.d(TAG, "sendEvent: surfaceId=$surfaceId eventType=$eventType name=$name")
        try {
            val body = buildJsonObject {
                put("surface_id", surfaceId)
                put("event_type", eventType)
                name?.let { put("name", it) }
                sourceComponentId?.let { put("source_component_id", it) }
                path?.let { put("path", it) }
                value?.let { put("value", it) }
                context?.let { ctx ->
                    put("context", buildJsonObject { ctx.forEach { (k, v) -> put(k, v) } })
                }
            }

            val requestBody = body.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$baseUrl/event")
                .post(requestBody)
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().close()
            }
            Log.d(TAG, "sendEvent: success")
        } catch (e: Exception) {
            // Event sending is best-effort — don't crash the app
            Log.e(TAG, "sendEvent: failed", e)
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
