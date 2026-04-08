import SwiftUI

enum AppColors {
    static let lightBackground = Color(hex: "#F4F6FA")
    static let lightSurface = Color(hex: "#FFFFFF")
    static let inputBarBackground = Color(hex: "#EEEEF5")
    static let userBubble = Color(hex: "#D6E4FF")
    static let aiBubble = Color(hex: "#F8FAFD")
    static let onBackground = Color(hex: "#0F172A")
    static let onSurface = Color(hex: "#1E2740")
    static let onSurfaceMuted = Color(hex: "#94A3B8")
    static let onSurfaceVariant = Color(hex: "#64748B")
    static let primary = Color(hex: "#2563EB")
    static let positiveGreen = Color(hex: "#0D7C4F")
    static let negativeRed = Color(hex: "#B91C1C")
    static let negativeText = Color(hex: "#B91C1C")
    static let positiveText = Color(hex: "#0D7C4F")
    static let cardBorderSubtle = Color(hex: "#E2E8F2")
    static let formFieldBackground = Color(hex: "#F9FAFB")
    static let formFieldBorder = Color(hex: "#D0D5DD")
    static let accentNeutral = Color(hex: "#C4C9D4")
    static let sendButtonActive = Color(hex: "#2563EB")
    static let sendButtonInactive = Color(hex: "#BAC4D8")
}

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3:
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6:
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8:
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (255, 0, 0, 0)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue: Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}
