import SwiftUI

enum FeedbackState {
    case idle
    case badPending
    case badReasons
    case good
    case submitted
}

struct FeedbackBar: View {
    let messageId: String
    let onFeedback: (String, String?) -> Void

    @State private var state: FeedbackState = .idle
    @State private var selectedReason: String? = nil

    private let badReasons = ["Not accurate", "Not helpful", "Too complex"]

    var body: some View {
        Group {
            switch state {
            case .idle:
                idleView
            case .badPending:
                badPendingView
            case .badReasons:
                badReasonsView
            case .good, .submitted:
                submittedView
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
    }

    private var idleView: some View {
        HStack(spacing: 16) {
            Spacer()
            Button(action: handleGood) {
                Image(systemName: "hand.thumbsup")
                    .font(.caption)
                    .foregroundColor(AppColors.onSurfaceMuted)
            }
            Button(action: { state = .badPending }) {
                Image(systemName: "hand.thumbsdown")
                    .font(.caption)
                    .foregroundColor(AppColors.onSurfaceMuted)
            }
        }
    }

    private var badPendingView: some View {
        HStack(spacing: 12) {
            Image(systemName: "hand.thumbsdown")
                .font(.caption)
                .foregroundColor(AppColors.onSurfaceMuted)
            Spacer()
            Button(action: { state = .badReasons }) {
                Text("Submit")
                    .font(.caption.weight(.semibold))
                    .foregroundColor(AppColors.negativeRed)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .overlay(
                        RoundedRectangle(cornerRadius: 6)
                            .stroke(AppColors.negativeRed, lineWidth: 1)
                    )
            }
            .buttonStyle(.plain)
        }
    }

    private var badReasonsView: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("What went wrong?")
                .font(.caption.weight(.semibold))
                .foregroundColor(AppColors.onSurface)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(badReasons, id: \.self) { reason in
                        Button(action: { selectReason(reason) }) {
                            Text(reason)
                                .font(.caption)
                                .foregroundColor(selectedReason == reason ? .white : AppColors.onSurface)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(selectedReason == reason ? AppColors.primary : Color.clear)
                                .clipShape(Capsule())
                                .overlay(
                                    Capsule()
                                        .stroke(
                                            selectedReason == reason ? AppColors.primary : AppColors.formFieldBorder,
                                            lineWidth: 1
                                        )
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private var submittedView: some View {
        HStack(spacing: 8) {
            Spacer()
            Image(systemName: "checkmark.circle.fill")
                .font(.caption)
                .foregroundColor(state == .good ? AppColors.positiveGreen : AppColors.onSurfaceMuted)
            Text("Thanks for your feedback")
                .font(.caption)
                .foregroundColor(AppColors.onSurfaceMuted)
        }
    }

    private func handleGood() {
        state = .good
        onFeedback("good", nil)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
            state = .submitted
        }
    }

    private func selectReason(_ reason: String) {
        selectedReason = reason
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            onFeedback("bad", reason)
            state = .submitted
        }
    }
}
