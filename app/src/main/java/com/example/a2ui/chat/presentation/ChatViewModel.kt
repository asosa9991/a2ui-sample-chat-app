package com.example.a2ui.chat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.contextable.a2ui4k.model.DataChangeEvent
import com.contextable.a2ui4k.model.UiEvent
import com.contextable.a2ui4k.model.UserActionEvent
import com.example.a2ui.chat.data.a2ui.SurfaceStateManager
import com.example.a2ui.chat.data.repository.MockChatRepository
import com.example.a2ui.chat.data.repository.RealChatRepository
import com.example.a2ui.chat.domain.model.Message
import java.util.Calendar
import com.example.a2ui.chat.domain.model.Sender
import com.example.a2ui.chat.domain.repository.ChatRepository
import com.example.a2ui.chat.domain.repository.StreamEvent
import com.example.a2ui.chat.domain.usecase.SendMessageUseCase
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.util.UUID

class ChatViewModel(
    private val sendMessageUseCase: SendMessageUseCase,
    private val repository: ChatRepository,
    private val mockRepository: MockChatRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Empty)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val greeting: String = mockRepository?.getGreeting() ?: run {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "morning"
            hour < 17 -> "afternoon"
            else -> "evening"
        }
    }

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

        if (USE_REAL_AGENT) {
            sendMessageStreaming(content)
        } else {
            sendMessageNonStreaming(content)
        }
    }

    private fun sendMessageStreaming(content: String) {
        val streamingMessageId = UUID.randomUUID().toString()
        val surfaceManager = SurfaceStateManager()
        var summaryText = ""

        viewModelScope.launch {
            repository.sendMessageStream(content).collect { event ->
                when (event) {
                    is StreamEvent.A2UiOp -> {
                        // Feed each A2UI protocol operation to the surface manager
                        surfaceManager.processOperation(event.operationJson)

                        // Progressive render: update the streaming message after each
                        // operation that produces visible changes (surfaceUpdate / dataModelUpdate)
                        if (surfaceManager.hasSurface()) {
                            upsertStreamingMessage(
                                streamingMessageId = streamingMessageId,
                                content = summaryText,
                                surfaceManager = surfaceManager,
                                isLoading = true
                            )
                        }
                    }

                    is StreamEvent.TextContent -> {
                        summaryText = event.text
                        // Update message with the summary text
                        upsertStreamingMessage(
                            streamingMessageId = streamingMessageId,
                            content = summaryText,
                            surfaceManager = surfaceManager,
                            isLoading = true
                        )
                    }

                    is StreamEvent.Done -> {
                        // Build final message from accumulated state
                        val uiDef = surfaceManager.buildUiDefinition()
                            ?: event.message.uiDefinition  // fallback: legacy done event
                        val dataJson = surfaceManager.buildDataModelJson()
                        val finalText = summaryText.ifEmpty { event.message.content }

                        val message = Message(
                            id = streamingMessageId,
                            content = finalText,
                            sender = Sender.AI,
                            timestamp = System.currentTimeMillis(),
                            uiDefinition = uiDef,
                            dataModelJson = if (dataJson.size > 0) dataJson else null,
                            isLoading = false
                        )

                        _uiState.update { state ->
                            if (state is ChatUiState.Active) {
                                val updated = state.messages.map {
                                    if (it.id == streamingMessageId) message else it
                                }.toImmutableList()
                                ChatUiState.Active(updated, isAiResponding = false)
                            } else state
                        }
                    }

                    is StreamEvent.Token -> {
                        // Backward compat: buffer silently
                    }

                    is StreamEvent.Error -> {
                        // Fallback: try non-streaming
                        sendMessageFallback(content, streamingMessageId)
                    }
                }
            }
        }
    }

    /**
     * Insert or update the streaming message in the message list.
     * This enables progressive rendering — UI updates as each A2UI operation arrives.
     */
    private fun upsertStreamingMessage(
        streamingMessageId: String,
        content: String,
        surfaceManager: SurfaceStateManager,
        isLoading: Boolean
    ) {
        val uiDef = surfaceManager.buildUiDefinition()
        val dataJson = surfaceManager.buildDataModelJson()

        val message = Message(
            id = streamingMessageId,
            content = content,
            sender = Sender.AI,
            timestamp = System.currentTimeMillis(),
            uiDefinition = uiDef,
            dataModelJson = if (dataJson.size > 0) dataJson else null,
            isLoading = isLoading
        )

        _uiState.update { state ->
            if (state is ChatUiState.Active) {
                val existingIndex = state.messages.indexOfFirst { it.id == streamingMessageId }
                if (existingIndex >= 0) {
                    // Update existing message in-place
                    val mutable = state.messages.toMutableList()
                    mutable[existingIndex] = message
                    ChatUiState.Active(mutable.toImmutableList(), isAiResponding = true)
                } else {
                    // Insert new streaming message
                    ChatUiState.Active(
                        (state.messages + message).toImmutableList(),
                        isAiResponding = true
                    )
                }
            } else state
        }
    }

    // ── Event sending (Client → Server) ────────────────────────────────

    /**
     * Forward a [UiEvent] from an A2UISurface back to the agent server.
     */
    fun sendUiEvent(event: UiEvent) {
        viewModelScope.launch {
            when (event) {
                is UserActionEvent -> {
                    repository.sendEvent(
                        surfaceId = event.surfaceId,
                        eventType = "userAction",
                        name = event.name,
                        sourceComponentId = event.sourceComponentId
                    )
                }
                is DataChangeEvent -> {
                    repository.sendEvent(
                        surfaceId = event.surfaceId,
                        eventType = "dataChange",
                        path = event.path,
                        value = event.value
                    )
                }
                else -> { /* Unknown event type — ignore */ }
            }
        }
    }

    // ── Fallback helpers ───────────────────────────────────────────────

    private fun sendMessageFallback(content: String, streamingMessageId: String) {
        viewModelScope.launch {
            val response = sendMessageUseCase(content)
            _uiState.update { state ->
                if (state is ChatUiState.Active) {
                    val messagesWithoutStreaming = state.messages
                        .filter { it.id != streamingMessageId }
                    ChatUiState.Active(
                        (messagesWithoutStreaming + response).toImmutableList(),
                        isAiResponding = false,
                    )
                } else state
            }
        }
    }

    private fun sendMessageNonStreaming(content: String) {
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
        // Set to true when your agent server is running at localhost:8000
        private const val USE_REAL_AGENT = true

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repository = if (USE_REAL_AGENT) {
                    RealChatRepository()
                } else {
                    MockChatRepository()
                }
                val useCase = SendMessageUseCase(repository)
                return ChatViewModel(
                    useCase,
                    repository,
                    if (USE_REAL_AGENT) null else repository as? MockChatRepository
                ) as T
            }
        }
    }
}
