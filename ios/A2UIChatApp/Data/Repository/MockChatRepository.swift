import Foundation

class MockChatRepository: ChatRepository {
    private let financialKeywords = [
        "account", "transaction", "transactions", "activity",
        "portfolio", "balance", "brokerage", "trades", "holdings", "stocks"
    ]

    func sendMessageStream(message: String) -> AsyncThrowingStream<StreamEvent, Error> {
        AsyncThrowingStream { continuation in
            Task {
                try? await Task.sleep(nanoseconds: 800_000_000)

                let lower = message.lowercased()
                let isFinancial = self.financialKeywords.contains { lower.contains($0) }

                if isFinancial {
                    continuation.yield(.textContent("Here's your brokerage activity:"))
                    let uiDef = MockResponseData.buildBrokerageActivityUiDefinition()
                    let msg = Message(
                        id: UUID().uuidString,
                        content: "Here's your brokerage activity:",
                        sender: .ai,
                        isLoading: false,
                        uiDefinition: uiDef,
                        dataModelJson: [:]
                    )
                    continuation.yield(.done(msg))
                } else {
                    let responses = [
                        "I can help you with your financial questions. Try asking about your account balance, transactions, or portfolio.",
                        "Sure! I'm here to assist. You can ask about your brokerage activity, holdings, or recent trades.",
                        "I'm your AI financial assistant. Ask me about transactions, portfolio performance, or account details."
                    ]
                    let response = responses.randomElement()!
                    continuation.yield(.textContent(response))
                    continuation.yield(.done(nil))
                }
                continuation.finish()
            }
        }
    }

    func sendEvent(
        surfaceId: String,
        eventType: String,
        name: String,
        sourceComponentId: String,
        path: String?,
        value: String?,
        context: [[String: String]]?
    ) async {
        // No-op for mock
    }

    func sendFeedbackStream(messageId: String, rating: String, reason: String?) -> AsyncThrowingStream<StreamEvent, Error> {
        AsyncThrowingStream { continuation in
            Task {
                try? await Task.sleep(nanoseconds: 500_000_000)
                continuation.yield(.done(nil))
                continuation.finish()
            }
        }
    }
}
