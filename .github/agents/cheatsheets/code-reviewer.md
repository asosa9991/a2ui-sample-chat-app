# Code Reviewer — Personal Cheatsheet

> Read at the start of every run. Update after every run with new patterns, gotchas, and fixes.

## Review Scope
- Android: `app/src/main/java/com/example/a2ui/` — Kotlin/Compose
- iOS: `ios/A2UIChatApp/` — Swift/SwiftUI
- Python: `agent/` and `agent-templates/` — FastAPI/SSE

## High-Signal Review Areas

### Python
- All `from copilot import X` → verify X is in `copilot.__init__.__all__`
- Template JSON files must be dict with `templateId`, not bare array
- requirements.txt version pins must match working venv
- `UiEventRequest` uses snake_case field names with `surface_id` required

### Android (Kotlin/Compose)
- `FinancialCatalog.kt` val declarations must precede `val FinancialCatalog`
- Explicit imports needed: `Modifier.size`, `Modifier.fillMaxSize` (no wildcard coverage)
- `LocalAccentColorSink` / `LocalBodyEmphasis` CompositionLocals for row signaling

### iOS (Swift/SwiftUI)
- `WidgetRenderer` closure signature — all 5 params required
- `@MainActor` required on all ViewModel methods that update `@Published` state
- `127.0.0.1:8000` for simulator (not 10.0.2.2)

## Common False Positives (do NOT flag)
- `10.0.2.2:8000` hardcoded in Android — intentional emulator address
- `USE_REAL_AGENT` flag in ChatViewModel — intentional dev toggle
- `a2ui-agent` commented out in requirements.txt — intentional (local package)

## Session Log
| Date | Pattern Learned |
|---|---|
| 2026-04-09 | PermissionHandler lives in copilot.session, not __init__ |
| 2026-04-10 | Re-review check: sync transport failures must use user-friendly "agent server" copy; tests should assert no raw HTTP code leakage |
