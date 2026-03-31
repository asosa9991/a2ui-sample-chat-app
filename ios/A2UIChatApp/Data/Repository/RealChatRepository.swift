import Foundation

class RealChatRepository: ChatRepository {
    private let baseURL = "http://127.0.0.1:8000"

    private lazy var sseSession: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 600   // 10 min — LLM can be slow
        config.timeoutIntervalForResource = 600
        return URLSession(configuration: config)
    }()

    func sendMessageStream(message: String) -> AsyncThrowingStream<StreamEvent, Error> {
        print("[A2UI.Repo] sendMessageStream called, message=\(message.prefix(40))")
        return AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    print("[A2UI.Repo] inner Task starting performStream")
                    try await self.performStream(
                        url: URL(string: "\(self.baseURL)/chat/stream")!,
                        body: ["message": message],
                        continuation: continuation
                    )
                } catch {
                    print("[A2UI.Repo] performStream error: \(error)")
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in
                task.cancel()
            }
        }
    }

    private func performStream(
        url: URL,
        body: [String: Any],
        continuation: AsyncThrowingStream<StreamEvent, Error>.Continuation
    ) async throws {
        print("[A2UI.Repo] performStream: \(url)")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        request.setValue("no-cache", forHTTPHeaderField: "Cache-Control")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        print("[A2UI.Repo] opening bytes stream to \(url)")
        let (bytes, response) = try await sseSession.bytes(for: request)
        print("[A2UI.Repo] response received: \(response)")

        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            throw URLError(.badServerResponse)
        }
        print("[A2UI.Repo] HTTP 200 OK, starting line iteration")

        var currentEventType = ""
        var streamDone = false

        for try await line in bytes.lines {
            let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)

            if trimmed.hasPrefix("event: ") {
                currentEventType = String(trimmed.dropFirst("event: ".count))
            } else if trimmed.hasPrefix("data: ") {
                let dataStr = String(trimmed.dropFirst("data: ".count))

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
                    break
                default:
                    if !currentEventType.isEmpty {
                        print("[A2UI.Repo] unknown event: '\(currentEventType)'")
                    }
                }
                currentEventType = ""
                if streamDone { break }
            } else if trimmed.isEmpty {
                // SSE event boundary — reset
                currentEventType = ""
            }
            // Lines starting with ":" are SSE comments (e.g. ping) — ignore
        }

        if !streamDone {
            continuation.yield(.done(nil))
        }
        print("[A2UI.Repo] stream finished, streamDone=\(streamDone)")
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
        // Feedback uses the same /event endpoint which requires surface_id.
        // We send it as a simple fire-and-forget POST (no SSE response needed).
        var context: [String: String] = ["rating": rating]
        if let reason = reason { context["reason"] = reason }

        let body: [String: Any] = [
            "surface_id": "",          // no surface context for feedback
            "event_type": "feedback",
            "name": messageId,
            "context": context
        ]

        return AsyncThrowingStream { continuation in
            let task = Task {
                guard let url = URL(string: "\(self.baseURL)/event") else {
                    continuation.finish()
                    return
                }
                var request = URLRequest(url: url)
                request.httpMethod = "POST"
                request.setValue("application/json", forHTTPHeaderField: "Content-Type")
                request.httpBody = try? JSONSerialization.data(withJSONObject: body)
                _ = try? await URLSession.shared.data(for: request)
                continuation.yield(.done(nil))
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    // MARK: — Spec-compliant JSONL streaming

    /// Set to `true` to hit `/chat/stream/jsonl` instead of `/chat/stream`.
    static let useJsonlEndpoint = false

    /// Spec-compliant JSONL stream from `/chat/stream/jsonl`.
    ///
    /// All SSE events arrive as plain `data:` lines (no custom `event:` type).
    /// Each line is a JSONL object dispatched on its top-level key:
    ///   - `"text"`            → StreamEvent.textContent
    ///   - `"surfaceUpdate"` / `"dataModelUpdate"` / `"beginRendering"` → StreamEvent.a2uiOp
    ///   - `"done"`            → StreamEvent.done
    func sendMessageStreamJsonl(message: String) -> AsyncThrowingStream<StreamEvent, Error> {
        print("[A2UI.Repo] sendMessageStreamJsonl called, message=\(message.prefix(40))")
        return AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    try await self.performJsonlStream(message: message, continuation: continuation)
                } catch {
                    print("[A2UI.Repo] performJsonlStream error: \(error)")
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    private func performJsonlStream(
        message: String,
        continuation: AsyncThrowingStream<StreamEvent, Error>.Continuation
    ) async throws {
        let url = URL(string: "\(baseURL)/chat/stream/jsonl")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        request.setValue("no-cache", forHTTPHeaderField: "Cache-Control")
        request.httpBody = try JSONSerialization.data(withJSONObject: ["message": message])

        print("[A2UI.Repo] opening JSONL stream to \(url)")
        let (bytes, response) = try await sseSession.bytes(for: request)
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            throw URLError(.badServerResponse)
        }
        print("[A2UI.Repo] JSONL HTTP 200 OK")

        var streamDone = false
        for try await line in bytes.lines {
            let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
            guard trimmed.hasPrefix("data: ") else { continue }

            let dataStr = String(trimmed.dropFirst("data: ".count))
            if dataStr.isEmpty || dataStr == "{}" { continue }

            guard let raw = dataStr.data(using: .utf8),
                  let json = try? JSONSerialization.jsonObject(with: raw) as? [String: Any] else {
                print("[A2UI.Repo] JSONL parse error on: \(dataStr.prefix(100))")
                continue
            }

            if let text = (json["text"] as? String) {
                print("[A2UI.Repo] JSONL text: \(text.prefix(60))")
                continuation.yield(.textContent(text))
            } else if json["surfaceUpdate"] != nil || json["dataModelUpdate"] != nil || json["beginRendering"] != nil {
                let key = json.keys.first ?? "?"
                print("[A2UI.Repo] JSONL a2ui op: \(key) len=\(dataStr.count)")
                continuation.yield(.a2uiOp(dataStr))
            } else if json["done"] != nil {
                print("[A2UI.Repo] JSONL done")
                continuation.yield(.done(nil))
                streamDone = true
                break
            } else {
                print("[A2UI.Repo] JSONL unknown key: \(json.keys)")
            }
        }

        if !streamDone {
            continuation.yield(.done(nil))
        }
        print("[A2UI.Repo] JSONL stream finished, streamDone=\(streamDone)")
        continuation.finish()
    }
}
