package com.example.a2ui.chat.data.repository

import com.example.a2ui.chat.data.a2ui.AccountBalancesSurface
import com.example.a2ui.chat.data.a2ui.BrokerageActivitySurface
import com.example.a2ui.chat.data.a2ui.TransactionHistorySurface
import com.example.a2ui.chat.data.model.MockResponseData
import com.example.a2ui.chat.domain.model.Message
import com.example.a2ui.chat.domain.model.Sender
import com.example.a2ui.chat.domain.repository.ChatRepository
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.UUID
import kotlin.random.Random

private val BROKERAGE_TRIGGERS = setOf(
    "account", "transaction", "transactions", "activity", "portfolio",
    "balance", "brokerage", "trades", "holdings", "stocks"
)

class MockChatRepository : ChatRepository {
    override suspend fun sendMessage(userMessage: String): Message {
        val delayMs = Random.nextLong(800, 2000)
        delay(delayMs)

        val normalized = userMessage.lowercase().trim()

        return when {
            // ── Specific test scenarios (Run 1 & Run 2) ──────────────────────────
            normalized.contains("last") && normalized.contains("transaction") -> Message(
                id = UUID.randomUUID().toString(),
                content = "Here are your transactions from March 2026 — 14 transactions total.",
                sender = Sender.AI,
                timestamp = System.currentTimeMillis(),
                isLoading = false,
                uiDefinition = TransactionHistorySurface.build()
            )
            normalized.contains("account") && normalized.contains("balance") -> Message(
                id = UUID.randomUUID().toString(),
                content = "Here's a summary of all your accounts with current balances as of today.",
                sender = Sender.AI,
                timestamp = System.currentTimeMillis(),
                isLoading = false,
                uiDefinition = AccountBalancesSurface.build()
            )
            // ── Generic brokerage query (existing behaviour) ──────────────────────
            else -> {
                val words = normalized.split(Regex("\\W+")).toSet()
                if (words.any { it in BROKERAGE_TRIGGERS }) {
                    Message(
                        id = UUID.randomUUID().toString(),
                        content = "Here's your recent account activity:",
                        sender = Sender.AI,
                        timestamp = System.currentTimeMillis(),
                        isLoading = false,
                        uiDefinition = BrokerageActivitySurface.build()
                    )
                } else {
                    Message(
                        id = UUID.randomUUID().toString(),
                        content = MockResponseData.responses.random(),
                        sender = Sender.AI,
                        timestamp = System.currentTimeMillis(),
                        isLoading = false
                    )
                }
            }
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
