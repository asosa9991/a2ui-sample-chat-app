import Foundation

protocol ChatRepository {
    func sendMessageStream(message: String) -> AsyncThrowingStream<StreamEvent, Error>
    func sendEvent(surfaceId: String, eventType: String, name: String, sourceComponentId: String, path: String?, value: String?, context: [[String: String]]?) async
    func sendFeedbackStream(messageId: String, rating: String, reason: String?) -> AsyncThrowingStream<StreamEvent, Error>
}
