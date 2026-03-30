import Foundation

class RealChatRepository: ChatRepository {
    private let baseURL = "http://127.0.0.1:8000"

    func sendMessageStream(message: String) -> AsyncThrowingStream<StreamEvent, Error> {
        AsyncThrowingStream { continuation in
            Task {
                do {
                    try await self.performStream(
                        url: URL(string: "\(self.baseURL)/chat/stream")!,
                        body: ["message": message],
                        continuation: continuation
                    )
                } catch {
                    var lastError = error
                    let delays: [UInt64] = [2_000_000_000, 4_000_000_000]
                    for delay in delays {
                        do {
                            try await Task.sleep(nanoseconds: delay)
                            try await self.performStream(
                                url: URL(string: "\(self.baseURL)/chat/stream")!,
                                body: ["message": message],
                                continuation: continuation
                            )
                            return
                        } catch let e {
                            lastError = e
                        }
                    }
                    continuation.finish(throwing: lastError)
                }
            }
        }
    }

    private func performStream(
        url: URL,
        body: [String: Any],
        continuation: AsyncThrowingStream<StreamEvent, Error>.Continuation
    ) async throws {
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (bytes, response) = try await URLSession.shared.bytes(for: request)

        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            throw URLError(.badServerResponse)
        }

        var currentEventType = ""
        var buffer = ""
        var streamDone = false

        outer: for try await byte in bytes {
            let char = String(bytes: [byte], encoding: .utf8) ?? ""
            buffer += char

            while let newlineRange = buffer.range(of: "\n") {
                let line = String(buffer[buffer.startIndex..<newlineRange.lowerBound])
                buffer = String(buffer[newlineRange.upperBound...])

                if line.hasPrefix("event: ") {
                    currentEventType = String(line.dropFirst("event: ".count))
                        .trimmingCharacters(in: .whitespaces)
                } else if line.hasPrefix("data: ") {
                    let dataStr = String(line.dropFirst("data: ".count))
                        .trimmingCharacters(in: .whitespaces)

                    switch currentEventType {
                    case "a2ui_op":
                        print("[A2UI.Repo] a2ui_op: \(dataStr.prefix(80))")
                        continuation.yield(.a2uiOp(dataStr))
                    case "text":
                        print("[A2UI.Repo] text event")
                        if let raw = dataStr.data(using: .utf8),
                           let json = try? JSONSerialization.jsonObject(with: raw) as? [String: Any],
                           let text = json["text"] as? String {
                            continuation.yield(.textContent(text))
                        }
                    case "token":
                        if let raw = dataStr.data(using: .utf8),
                           let json = try? JSONSerialization.jsonObject(with: raw) as? [String: Any],
                           let token = json["token"] as? String {
                            continuation.yield(.token(token))
                        }
                    case "done":
                        print("[A2UI.Repo] done")
                        continuation.yield(.done(nil))
                        streamDone = true
                        break outer
                    default:
                        if !currentEventType.isEmpty {
                            print("[A2UI.Repo] ⚠️ Unknown event type: '\(currentEventType)'")
                        }
                        if currentEventType.isEmpty,
                           let raw = dataStr.data(using: .utf8),
                           let json = try? JSONSerialization.jsonObject(with: raw) as? [String: Any],
                           let text = json["text"] as? String {
                            continuation.yield(.textContent(text))
                        }
                    }
                    currentEventType = ""
                }
            }
        }

        if !streamDone {
            continuation.yield(.done(nil))
        }
        continuation.finish()
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
        guard let url = URL(string: "\(baseURL)/event") else { return }
        var body: [String: Any] = [
            "surface_id": surfaceId,
            "event_type": eventType,
            "name": name,
            "source_component_id": sourceComponentId
        ]
        if let path = path { body["path"] = path }
        if let value = value { body["value"] = value }
        if let context = context { body["context"] = context }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        _ = try? await URLSession.shared.data(for: request)
    }

    func sendFeedbackStream(messageId: String, rating: String, reason: String?) -> AsyncThrowingStream<StreamEvent, Error> {
        var body: [String: Any] = [
            "event_type": "feedback",
            "message_id": messageId,
            "rating": rating
        ]
        if let reason = reason { body["reason"] = reason }

        return AsyncThrowingStream { continuation in
            Task {
                do {
                    try await self.performStream(
                        url: URL(string: "\(self.baseURL)/event")!,
                        body: body,
                        continuation: continuation
                    )
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }
}
