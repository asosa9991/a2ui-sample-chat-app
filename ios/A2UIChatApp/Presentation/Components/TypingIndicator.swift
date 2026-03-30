import SwiftUI

struct TypingIndicator: View {
    @State private var dot1Scale: CGFloat = 0.5
    @State private var dot2Scale: CGFloat = 0.5
    @State private var dot3Scale: CGFloat = 0.5

    var body: some View {
        HStack(alignment: .bottom, spacing: 0) {
            HStack(spacing: 6) {
                Circle()
                    .fill(AppColors.onSurfaceMuted)
                    .frame(width: 8, height: 8)
                    .scaleEffect(dot1Scale)
                Circle()
                    .fill(AppColors.onSurfaceMuted)
                    .frame(width: 8, height: 8)
                    .scaleEffect(dot2Scale)
                Circle()
                    .fill(AppColors.onSurfaceMuted)
                    .frame(width: 8, height: 8)
                    .scaleEffect(dot3Scale)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(AppColors.aiBubble)
            .clipShape(RoundedRectangle(cornerRadius: 18))

            Spacer()
        }
        .padding(.horizontal, 16)
        .onAppear { animateDots() }
    }

    private func animateDots() {
        let animation = Animation.easeInOut(duration: 0.4).repeatForever(autoreverses: true)
        withAnimation(animation) { dot1Scale = 1.0 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
            withAnimation(animation) { dot2Scale = 1.0 }
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.30) {
            withAnimation(animation) { dot3Scale = 1.0 }
        }
    }
}
