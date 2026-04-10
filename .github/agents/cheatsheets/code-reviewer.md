# Code Reviewer — Personal Cheatsheet

> Read at the start of every run. Update after every run with new patterns, gotchas, and fixes.

## Review Scope
- Android: `app/src/main/java/com/example/a2ui/` — Kotlin/Compose
- iOS: `ios/A2UIChatApp/` — Swift/SwiftUI
- Python: `agent/` — FastAPI/SSE (unified server)

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
- `String.replace(Regex, replacement)` — bare `$` in replacement string → FATAL `IllegalArgumentException` at runtime. Always verify `$` is escaped as `\\$` in Kotlin source. Search for `.replace(Regex(` in the diff.
- Any new widget override in `FinancialCatalog.kt` must have a companion unit test (or explicit note in PR why not). No widget goes untested.
- All children-parsing code must handle BOTH `{"componentIds": [...]}` AND `{"explicitList": [...]}` formats. Check for `parseComponentArray()` calls without an `explicitList` fallback.
- Sync endpoint component assembly: values stored in `all_components[id]` must be wrapped as `{"componentProperties": entry["component"]}`. If you see `all_components[id] = entry["component"]` (no wrapper) → blocking bug.
- `ComponentDto` fields that are conceptually required but may be absent in some endpoint shapes must have nullable types with fallbacks. Flag any `val x: Type` (non-nullable) on DTO fields that come from the wire.

### iOS (Swift/SwiftUI)
- `WidgetRenderer` closure signature — all 5 params required
- `@MainActor` required on all ViewModel methods that update `@Published` state
- `127.0.0.1:8000` for simulator (not 10.0.2.2)

## Common False Positives (do NOT flag)
- `10.0.2.2:8000` hardcoded in Android — intentional emulator address
- `USE_REAL_AGENT` flag in ChatViewModel — intentional dev toggle
- `a2ui-agent` commented out in requirements.txt — intentional (local package)

## DoD Checklist for Code Reviewer

Run through this list on every review. Raise a BLOCKING issue for any violation.

- [ ] No bare `$` in `String.replace(Regex, String)` replacement strings
- [ ] New FinancialCatalog widgets have unit tests (MonetaryColorTest, ExplicitListParsingTest patterns)
- [ ] Sync endpoint: all_components values wrapped in `{"componentProperties": ...}`
- [ ] Children parsing: explicitList fallback present alongside parseComponentArray()
- [ ] DTO fields that may be absent on wire are nullable with fallback (not non-nullable)
- [ ] Regex replacement: test with inputs containing `$`, `\`, backreference chars
- [ ] Any new string transformation: unit test with at least one `$`-prefixed input
- [ ] Python: template transform output verified with `test_agent.py` assertions

## Session Log
| Date | Pattern Learned |
|---|---|
| 2026-04-09 | PermissionHandler lives in copilot.session, not __init__ |
| 2026-04-10 | Re-review check: sync transport failures must use user-friendly "agent server" copy; tests should assert no raw HTTP code leakage |
