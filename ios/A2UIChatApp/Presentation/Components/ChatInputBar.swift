import SwiftUI

struct ChatInputBar: View {
    @Binding var inputText: String
    let onSend: () -> Void

    private var isActive: Bool {
        !inputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        HStack(spacing: 12) {
            Button(action: {}) {
                Image(systemName: "plus.circle.fill")
                    .font(.title2)
                    .foregroundColor(AppColors.onSurfaceMuted)
            }

            HStack {
                TextField("Chat with Claude", text: $inputText, axis: .vertical)
                    .font(.body)
                    .foregroundColor(AppColors.onBackground)
                    .lineLimit(1...5)
                    .onSubmit {
                        if isActive { onSend() }
                    }
                    .submitLabel(.send)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(AppColors.inputBarBackground)
            .clipShape(RoundedRectangle(cornerRadius: 24))

            Button(action: { if isActive { onSend() } }) {
                Circle()
                    .fill(isActive ? AppColors.sendButtonActive : AppColors.sendButtonInactive)
                    .frame(width: 40, height: 40)
                    .overlay(
                        Image(systemName: "arrow.up")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white)
                    )
            }
            .disabled(!isActive)
            .animation(.easeInOut(duration: 0.2), value: isActive)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(AppColors.lightSurface)
        .overlay(
            Divider(), alignment: .top
        )
    }
}
