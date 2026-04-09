# iOS Expert — Personal Cheatsheet

> Read at the start of every run. Update after every run with new patterns, gotchas, and fixes.

## Build / Run
```bash
# Uses XcodeGen — regenerate project after adding files
cd ios && xcodegen generate
# Open in Xcode: ios/A2UIChatApp.xcodeproj
```

## FinancialCatalog.swift — Key Patterns
- ALL iOS widget overrides in `ios/A2UIChatApp/Data/A2UI/FinancialCatalog.swift`
- Widgets registered in `init()` as closure-based `WidgetRenderer` typealias
- `WidgetRenderer = (String, [String: Any], @escaping BuildChild, DataContext, @escaping OnEvent) -> AnyView`

## Networking
- iOS Simulator reaches host at `127.0.0.1:8000` (NOT 10.0.2.2 like Android)
- `RealChatRepository.swift` uses `127.0.0.1:8000`
- Physical devices need host LAN IP

## SwiftUI Canvas DonutChart
- `Canvas { context, size in }` (iOS 15+)
- `Path.addArc` for ring segments
- `StrokeStyle(lineWidth: 38, lineCap: .butt)` for ring
- 2° gap between segments: `sweep = pct/100 * 360 - 2`
- Center label via `ZStack` overlay on Canvas

## BarChart Proportional Widths
- `GeometryReader { geo in }` with `frame(width: geo.size.width * fraction)`
- `ZStack(alignment: .leading)` for track (10% opacity) + fill overlay

## AppColors Tokens
- File: `ios/A2UIChatApp/Theme/AppColors.swift`
- Added: `onSurfaceVariant = Color(hex: "#64748B")`
- All colors defined as static lets

## State Management
- ViewModels: `@MainActor`, `@Published` properties
- Async streaming: `AsyncThrowingStream`, `Task { }`
- `@StateObject` for owned VMs, `@ObservedObject` for injected

## SSE Streaming
- `URLSession.shared.bytes(for:)` for line-by-line SSE parsing
- Parse `event:` and `data:` lines manually
- `StreamEvent` enum: `.text`, `.a2uiOp`, `.done`, `.error`

## Known Gotchas
- `USE_REAL_AGENT` flag in `ChatViewModel.swift` — set `false` for MockChatRepository
- `Color(hex:)` extension required for hex color values
- XcodeGen must be re-run after adding new Swift files to `project.yml`

## Session Log
| Date | Pattern Learned |
|---|---|
| 2026-04-09 | DonutChart: Canvas.addArc, 2° gap formula; BarChart: GeometryReader proportional widths |
