package com.example.a2ui.chat.presentation

import com.example.a2ui.chat.domain.model.BackendMode
import com.example.a2ui.chat.domain.model.Message
import com.example.a2ui.chat.domain.model.Sender
import com.example.a2ui.chat.domain.model.WireFormat
import com.example.a2ui.chat.domain.repository.ChatRepository
import com.example.a2ui.chat.domain.repository.StreamEvent
import com.example.a2ui.chat.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelRoutingTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeRepository: FakeChatRepository
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeChatRepository()
        val useCase = SendMessageUseCase(fakeRepository)
        viewModel = ChatViewModel(useCase, fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── LLM mode ────────────────────────────────────────────────────────────

    @Test
    fun `LLM + SSE routes to chat stream`() = runTest {
        viewModel.setBackendMode(BackendMode.LLM)
        viewModel.setWireFormat(WireFormat.SSE)
        viewModel.sendMessage("test")
        advanceUntilIdle()
        assertEquals("/chat/stream", fakeRepository.lastStreamEndpoint)
        assertNull(fakeRepository.lastJsonlEndpoint)
        assertNull(fakeRepository.lastSyncEndpoint)
    }

    @Test
    fun `LLM + JSONL routes to chat stream jsonl`() = runTest {
        viewModel.setBackendMode(BackendMode.LLM)
        viewModel.setWireFormat(WireFormat.JSONL)
        viewModel.sendMessage("test")
        advanceUntilIdle()
        assertEquals("/chat/stream/jsonl", fakeRepository.lastJsonlEndpoint)
        assertNull(fakeRepository.lastStreamEndpoint)
        assertNull(fakeRepository.lastSyncEndpoint)
    }

    @Test
    fun `LLM + SYNC routes to chat`() = runTest {
        viewModel.setBackendMode(BackendMode.LLM)
        viewModel.setWireFormat(WireFormat.SYNC)
        viewModel.sendMessage("test")
        advanceUntilIdle()
        assertEquals("/chat", fakeRepository.lastSyncEndpoint)
        assertNull(fakeRepository.lastStreamEndpoint)
        assertNull(fakeRepository.lastJsonlEndpoint)
    }

    // ── Template mode ────────────────────────────────────────────────────────

    @Test
    fun `TEMPLATE + SSE routes to chat stream template`() = runTest {
        viewModel.setBackendMode(BackendMode.TEMPLATE)
        viewModel.setWireFormat(WireFormat.SSE)
        viewModel.sendMessage("test")
        advanceUntilIdle()
        assertEquals("/chat/stream/template", fakeRepository.lastStreamEndpoint)
        assertNull(fakeRepository.lastJsonlEndpoint)
        assertNull(fakeRepository.lastSyncEndpoint)
    }

    @Test
    fun `TEMPLATE + JSONL routes to chat stream template jsonl`() = runTest {
        viewModel.setBackendMode(BackendMode.TEMPLATE)
        viewModel.setWireFormat(WireFormat.JSONL)
        viewModel.sendMessage("test")
        advanceUntilIdle()
        assertEquals("/chat/stream/template/jsonl", fakeRepository.lastJsonlEndpoint)
        assertNull(fakeRepository.lastStreamEndpoint)
        assertNull(fakeRepository.lastSyncEndpoint)
    }

    @Test
    fun `TEMPLATE + SYNC routes to chat template`() = runTest {
        viewModel.setBackendMode(BackendMode.TEMPLATE)
        viewModel.setWireFormat(WireFormat.SYNC)
        viewModel.sendMessage("test")
        advanceUntilIdle()
        assertEquals("/chat/template", fakeRepository.lastSyncEndpoint)
        assertNull(fakeRepository.lastStreamEndpoint)
        assertNull(fakeRepository.lastJsonlEndpoint)
    }

    // ── Fake repository ────────────────────────────────────────────────────

    class FakeChatRepository : ChatRepository {
        var lastStreamEndpoint: String? = null
        var lastJsonlEndpoint: String? = null
        var lastSyncEndpoint: String? = null

        private fun makeMessage() = Message(
            id = UUID.randomUUID().toString(),
            content = "fake response",
            sender = Sender.AI,
            timestamp = System.currentTimeMillis(),
            isLoading = false,
        )

        override suspend fun sendMessage(userMessage: String): Message = makeMessage()

        override fun getGreeting(): String = "morning"

        override fun sendMessageStream(
            userMessage: String,
            endpoint: String,
        ): Flow<StreamEvent> = flow {
            lastStreamEndpoint = endpoint
            emit(StreamEvent.Done(makeMessage()))
        }

        override fun sendMessageStreamJsonl(
            userMessage: String,
            endpoint: String,
        ): Flow<StreamEvent> = flow {
            lastJsonlEndpoint = endpoint
            emit(StreamEvent.Done(makeMessage()))
        }

        override fun sendMessageSyncAsFlow(
            userMessage: String,
            endpoint: String,
        ): Flow<StreamEvent> = flow {
            lastSyncEndpoint = endpoint
            emit(StreamEvent.Done(makeMessage()))
        }
    }
}
