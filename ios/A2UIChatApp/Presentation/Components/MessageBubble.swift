import SwiftUI

struct MessageBubble: View {
    let message: Message
    let onEvent: (UiEvent) -> Void
    let onFeedback: (String, String?) -> Void

    @StateObject private var dataContext = DataContext()
    @State private var cursorVisible: Bool = true
    @State private var cursorTimer: Timer? = nil

    private var isUser: Bool { message.sender == .user }
    private var isStreaming: Bool {
        message.isLoading && message.uiDefinition == nil && !message.content.isEmpty
    }

    var body: some View {
        if isUser {
            userBubble
        } else {
            aiBubble
        }
    }

    private var userBubble: some View {
        HStack {
            Spacer(minLength: 60)
            Text(message.content)
                .font(.body)
                .foregroundColor(AppColors.onBackground)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(AppColors.userBubble)
                .clipShape(RoundedRectangle(cornerRadius: 20))
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 4)
    }

    @ViewBuilder
    private var aiBubble: some View {
        if let uiDef = message.uiDefinition {
            aiCardBubble(uiDef: uiDef)
        } else {
            plainTextBubble
        }
    }

    private var plainTextBubble: some View {
        HStack(alignment: .bottom) {
            HStack(spacing: 0) {
                Text(message.content)
                    .font(.body)
                    .foregroundColor(AppColors.onBackground)
                if isStreaming {
                    Text("▍")
                        .foregroundColor(AppColors.primary)
                        .opacity(cursorVisible ? 1 : 0)
                        .onAppear { startCursorBlink() }
                        .onDisappear { stopCursorBlink() }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(AppColors.aiBubble)
            .clipShape(RoundedRectangle(cornerRadius: 18))

            Spacer(minLength: 60)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 4)
    }

    private func aiCardBubble(uiDef: UiDefinition) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            if !message.content.isEmpty {
                Text(message.content)
                    .font(.body)
                    .foregroundColor(AppColors.onBackground)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            VStack(spacing: 0) {
                A2UISurface(
                    uiDefinition: uiDef,
                    dataContext: dataContext,
                    onEvent: { event in
                        let enriched = UiEvent(
                            surfaceId: uiDef.surfaceId,
                            eventType: event.eventType,
                            name: event.name,
                            sourceComponentId: event.sourceComponentId,
                            path: event.path,
                            value: event.value,
                            context: event.context
                        )
                        onEvent(enriched)
                    }
                )

                if message.isLoading {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                }
            }
            .background(AppColors.lightSurface)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(AppColors.cardBorderSubtle, lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.06), radius: 4, x: 0, y: 2)
            .padding(.horizontal, 16)

            if !message.isLoading {
                FeedbackBar(messageId: message.id, onFeedback: onFeedback)
                    .padding(.horizontal, 16)
            }
        }
        .padding(.vertical, 4)
        .onAppear {
            if let dataModel = message.dataModelJson {
                dataContext.setData(dataModel)
            }
        }
        .onChange(of: message.uiDefinition?.components.count ?? 0) { _, _ in
            if let dataModel = message.dataModelJson {
                dataContext.setData(dataModel)
            }
        }
    }

    private func startCursorBlink() {
        cursorTimer?.invalidate()
        cursorTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { _ in
            cursorVisible.toggle()
        }
    }

    private func stopCursorBlink() {
        cursorTimer?.invalidate()
        cursorTimer = nil
    }
}
