package com.example.a2ui.chat.domain.repository

import com.example.a2ui.chat.domain.model.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed class StreamEvent {
    /** Legacy token-by-token streaming (backward compat). */
    data class Token(val text: String) : StreamEvent()

    /** A2UI v0.8 protocol operation (JSON payload). */
    data class A2UiOp(val operationJson: String) : StreamEvent()

    /** Text content accompanying a response (displayed alongside or without UI). */
    data class TextContent(val text: String) : StreamEvent()

    /** Stream completed; carries a partially-built [Message] (content may be empty). */
    data class Done(val message: Message) : StreamEvent()

    /** Unrecoverable error during streaming. */
    data class Error(val error: String) : StreamEvent()
}

interface ChatRepository {
    suspend fun sendMessage(userMessage: String): Message
    fun getGreeting(): String

    fun sendMessageStream(userMessage: String): Flow<StreamEvent> = flow {
        // Default implementation falls back to non-streaming
        emit(StreamEvent.Done(sendMessage(userMessage)))
    }

    /**
     * Send a UI event (user action or data change) back to the agent server.
     * Default no-op; real implementations POST to the server's `/event` endpoint.
     */
    suspend fun sendEvent(
        surfaceId: String,
        eventType: String,
        name: String? = null,
        sourceComponentId: String? = null,
        path: String? = null,
        value: String? = null,
        context: Map<String, String>? = null
    ) { /* default no-op */ }
}
