import SwiftUI

struct MessageList: View {
    let messages: [Message]
    let isAiResponding: Bool
    let onEvent: (UiEvent) -> Void
    let onFeedback: (String, String, String?) -> Void

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(messages) { message in
                        MessageBubble(
                            message: message,
                            onEvent: onEvent,
                            onFeedback: { rating, reason in
                                onFeedback(message.id, rating, reason)
                            }
                        )
                        .id(message.id)
                    }

                    if isAiResponding && (messages.last?.sender == .user || messages.isEmpty) {
                        TypingIndicator()
                            .padding(.vertical, 4)
                    }

                    Color.clear.frame(height: 8).id("bottom")
                }
                .padding(.top, 8)
            }
            .onChange(of: messages.count) { _, _ in
                withAnimation(.easeOut(duration: 0.3)) {
                    proxy.scrollTo("bottom", anchor: .bottom)
                }
            }
            .onChange(of: isAiResponding) { _, _ in
                withAnimation(.easeOut(duration: 0.3)) {
                    proxy.scrollTo("bottom", anchor: .bottom)
                }
            }
        }
    }
}
