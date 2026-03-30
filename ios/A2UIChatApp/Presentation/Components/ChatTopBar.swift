import SwiftUI

struct ChatTopBar: View {
    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: {}) {
                    Image(systemName: "line.3.horizontal")
                        .font(.title2)
                        .foregroundColor(AppColors.onBackground)
                }

                Spacer()

                Text("A2UI Chat")
                    .font(.headline)
                    .foregroundColor(AppColors.onBackground)

                Spacer()

                Circle()
                    .fill(AppColors.primary)
                    .frame(width: 36, height: 36)
                    .overlay(
                        Image(systemName: "person.fill")
                            .foregroundColor(.white)
                            .font(.system(size: 16))
                    )
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(AppColors.lightSurface)

            Divider()
        }
    }
}
