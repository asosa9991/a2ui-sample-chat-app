import Foundation
import SwiftUI

@MainActor
class ChatViewModel: ObservableObject {
    @Published var messages: [Message] = []
    @Published var isAiResponding: Bool = false

    static let USE_REAL_AGENT = true

    private let repository: ChatRepository
    private var streamingTask: Task<Void, Never>?

    var greeting: String {
        let hour = Calendar.current.component(.hour, from: Date())
        if hour < 12 { return "morning" }
        else if hour < 17 { return "afternoon" }
        else { return "evening" }
    }

    init() {
        repository = ChatViewModel.USE_REAL_AGENT ? RealChatRepository() : MockChatRepository()
    }

    func sendMessage(_ content: String) {
        let userMessage = Message(id: UUID().uuidString, content: content, sender: .user)
        messages.append(userMessage)
        isAiResponding = true

        if ChatViewModel.USE_REAL_AGENT {
            sendMessageStreaming(content)
        } else {
            sendMessageNonStreaming(content)
        }
    }

    private func sendMessageStreaming(_ content: String) {
        let aiMessageId = UUID().uuidString
        let placeholder = Message(id: aiMessageId, content: "", sender: .ai, isLoading: true)
        messages.append(placeholder)

        let surfaceManager = SurfaceStateManager()

        streamingTask = Task {
            do {
                let stream = repository.sendMessageStream(message: content)
                for try await event in stream {
                    guard !Task.isCancelled else { break }
                    switch event {
                    case .textContent(let text):
                        if let idx = messages.firstIndex(where: { $0.id == aiMessageId }) {
                            messages[idx].content = text
                            messages[idx].isLoading = false
                        }
                    case .a2uiOp(let json):
                        surfaceManager.processOperation(json)
                        if surfaceManager.hasSurface {
                            if let idx = messages.firstIndex(where: { $0.id == aiMessageId }) {
                                messages[idx].uiDefinition = surfaceManager.buildUiDefinition()
                                messages[idx].dataModelJson = surfaceManager.buildDataModelJson()
                                messages[idx].isLoading = true
                            }
                        }
                    case .token(let token):
                        if let idx = messages.firstIndex(where: { $0.id == aiMessageId }) {
                            messages[idx].content += token
                            messages[idx].isLoading = false
                        }
                    case .done(let maybeMsg):
                        if let completedMsg = maybeMsg {
                            if let idx = messages.firstIndex(where: { $0.id == aiMessageId }) {
                                messages[idx] = Message(
                                    id: aiMessageId,
                                    content: completedMsg.content,
                                    sender: .ai,
                                    isLoading: false,
                                    uiDefinition: completedMsg.uiDefinition,
                                    dataModelJson: completedMsg.dataModelJson
                                )
                            }
                        } else {
                            if let idx = messages.firstIndex(where: { $0.id == aiMessageId }) {
                                messages[idx].isLoading = false
                                messages[idx].uiDefinition = surfaceManager.buildUiDefinition()
                                messages[idx].dataModelJson = surfaceManager.buildDataModelJson()
                            }
                        }
                        isAiResponding = false
                    case .error(let error):
                        print("[A2UI.VM] Stream error: \(error)")
                        if let idx = messages.firstIndex(where: { $0.id == aiMessageId }) {
                            messages.remove(at: idx)
                        }
                        sendMessageNonStreaming(content)
                    }
                }
            } catch {
                print("[A2UI.VM] Streaming failed: \(error)")
                if let idx = messages.firstIndex(where: { $0.id == aiMessageId }) {
                    messages[idx].content = "Sorry, I encountered an error. Please try again."
                    messages[idx].isLoading = false
                }
                isAiResponding = false
            }
        }
    }

    private func sendMessageNonStreaming(_ content: String) {
        Task {
            do {
                let stream = repository.sendMessageStream(message: content)
                var accumulatedText = ""
                var finalMessage: Message? = nil
                let surfaceManager = SurfaceStateManager()

                for try await event in stream {
                    switch event {
                    case .textContent(let text): accumulatedText = text
                    case .token(let t): accumulatedText += t
                    case .a2uiOp(let json): surfaceManager.processOperation(json)
                    case .done(let msg):
                        if let m = msg { finalMessage = m }
                    case .error: break
                    }
                }

                let aiMessage: Message
                if let fm = finalMessage {
                    aiMessage = Message(
                        id: UUID().uuidString,
                        content: fm.content.isEmpty ? accumulatedText : fm.content,
                        sender: .ai,
                        isLoading: false,
                        uiDefinition: fm.uiDefinition,
                        dataModelJson: fm.dataModelJson
                    )
                } else {
                    aiMessage = Message(
                        id: UUID().uuidString,
                        content: accumulatedText,
                        sender: .ai,
                        isLoading: false,
                        uiDefinition: surfaceManager.buildUiDefinition(),
                        dataModelJson: surfaceManager.buildDataModelJson()
                    )
                }
                messages.append(aiMessage)
                isAiResponding = false
            } catch {
                let errMessage = Message(id: UUID().uuidString, content: "Sorry, I encountered an error.", sender: .ai)
                messages.append(errMessage)
                isAiResponding = false
            }
        }
    }

    func sendUiEvent(_ event: UiEvent) {
        Task {
            await repository.sendEvent(
                surfaceId: event.surfaceId,
                eventType: event.eventType,
                name: event.name,
                sourceComponentId: event.sourceComponentId,
                path: event.path,
                value: event.value,
                context: event.context
            )
        }
    }

    func sendFeedback(messageId: String, rating: String, reason: String?) {
        Task {
            let stream = repository.sendFeedbackStream(messageId: messageId, rating: rating, reason: reason)
            for try await _ in stream { }
        }
    }
}
