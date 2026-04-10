package com.example.a2ui.chat.data.repository

import com.example.a2ui.chat.domain.repository.StreamEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RealChatRepositorySyncTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: RealChatRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = RealChatRepository(baseUrl = server.url("").toString().trimEnd('/'))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `emits TextContent then Done when response has text and ui_definition`() = runTest {
        val json = """
            {
              "text": "Here are your balances",
              "ui_definition": {
                "surfaceId": "s1",
                "root": "c1",
                "components": {
                  "c1": { "id": "c1", "componentProperties": {} }
                }
              }
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(json)
            .addHeader("Content-Type", "application/json"))

        val events = repository.sendMessageSyncAsFlow("show balances", "/chat").toList()

        assertEquals(2, events.size)
        assertIs<StreamEvent.TextContent>(events[0])
        assertEquals("Here are your balances", (events[0] as StreamEvent.TextContent).text)
        assertIs<StreamEvent.Done>(events[1])
        val done = events[1] as StreamEvent.Done
        assertEquals("Here are your balances", done.message.content)
        assertEquals("s1", done.message.uiDefinition?.surfaceId)
    }

    @Test
    fun `emits only Done when response text is empty`() = runTest {
        val json = """{"text": "", "ui_definition": null}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(json)
            .addHeader("Content-Type", "application/json"))

        val events = repository.sendMessageSyncAsFlow("test", "/chat").toList()

        assertEquals(1, events.size)
        assertIs<StreamEvent.Done>(events[0])
        val done = events[0] as StreamEvent.Done
        assertNull(done.message.uiDefinition)
    }

    @Test
    fun `emits Done with null uiDefinition when response has no ui_definition`() = runTest {
        val json = """{"text": "Hello there"}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(json)
            .addHeader("Content-Type", "application/json"))

        val events = repository.sendMessageSyncAsFlow("hi", "/chat/template").toList()

        assertEquals(2, events.size)
        assertIs<StreamEvent.Done>(events[1])
        assertNull((events[1] as StreamEvent.Done).message.uiDefinition)
    }

    @Test
    fun `emits Error on HTTP 500`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val events = repository.sendMessageSyncAsFlow("test", "/chat").toList()

        assertEquals(1, events.size)
        assertIs<StreamEvent.Error>(events[0])
        assertTrue((events[0] as StreamEvent.Error).error.contains("500"))
    }

    @Test
    fun `uses correct endpoint path`() = runTest {
        val json = """{"text": "ok"}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(json)
            .addHeader("Content-Type", "application/json"))

        repository.sendMessageSyncAsFlow("test", "/chat/template").toList()

        val recorded = server.takeRequest()
        assertEquals("/chat/template", recorded.path)
    }

    @Test
    fun `emits Error on network failure after server shutdown`() = runTest {
        server.shutdown()  // Force connection refused

        val events = repository.sendMessageSyncAsFlow("test", "/chat").toList()

        assertEquals(1, events.size)
        assertIs<StreamEvent.Error>(events[0])
    }
}
