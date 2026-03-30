import SwiftUI

struct ChatScreen: View {
    @ObservedObject var viewModel: ChatViewModel
    @State private var inputText: String = ""

    var body: some View {
        VStack(spacing: 0) {
            ChatTopBar()

            if viewModel.messages.isEmpty && !viewModel.isAiResponding {
                EmptyStateGreeting(greeting: viewModel.greeting)
            } else {
                MessageList(
                    messages: viewModel.messages,
                    isAiResponding: viewModel.isAiResponding,
                    onEvent: { event in
                        viewModel.sendUiEvent(event)
                    },
                    onFeedback: { messageId, rating, reason in
                        viewModel.sendFeedback(messageId: messageId, rating: rating, reason: reason)
                    }
                )
            }

            ChatInputBar(inputText: $inputText) {
                let text = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !text.isEmpty else { return }
                viewModel.sendMessage(text)
            }
        }
        .background(AppColors.lightBackground)
        .ignoresSafeArea(edges: .bottom)
    }
}
