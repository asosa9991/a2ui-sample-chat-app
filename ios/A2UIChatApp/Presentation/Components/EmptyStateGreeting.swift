import SwiftUI

struct EmptyStateGreeting: View {
    let greeting: String

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "sparkles")
                .font(.system(size: 48))
                .foregroundColor(AppColors.primary)

            Text("How can I help you this \(greeting)?")
                .font(.title2)
                .foregroundColor(AppColors.onBackground)
                .multilineTextAlignment(.center)
        }
        .padding(40)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
