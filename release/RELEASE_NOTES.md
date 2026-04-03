# Release Notes

This file tracks all feature additions, bug fixes, and changes to the A2UI Sample Chat App.
Each entry is appended after every commit that closes a feature or fix.

---

## [0.5.0] — 2026-04-03

### Added

- **A2UI Edit View**: Every AI response that contains an A2UI surface now displays a ✏️ edit icon inline with the 👍👎 feedback bar. Tapping it opens a full-screen editor with a live preview of the rendered surface, a Components JSON editor, and a Data JSON editor. Edits update the preview in real-time. The editor is ephemeral (edits do not persist back to the chat). Adaptive layout: compact (preview on top, editors below) on phones, expanded (editors left 40%, preview right 60%) on wider screens. (Agent: Android Expert, commit: f6ecc45)
  - New files: `UiDefinitionSerializer.kt`, `EditorState.kt`, `JsonEditorPanel.kt`, `RenderPanel.kt`, `EditorScreen.kt`
  - Modified: `build.gradle.kts`, `MainActivity.kt`, `MessageBubble.kt`, `MessageList.kt`, `ChatScreen.kt`, `ChatViewModel.kt`
  - Dependency added: `androidx.navigation:navigation-compose:2.8.5`

### Process Notes

> ⚠️ **SDLC gap (backfilled)**: The `Android Designer` and full `Integration Tester` suite were not engaged for this task. Designer review and complete UI test validation (`./run_ui_tests.sh`) should be run before the next release build.

---

## [0.4.0] — 2026-04-02

### Fixed

- **Intent router**: Relaxed keyword matching to use substring matching for better keyword coverage across template agent intents. (Agent: Python Expert, commit: e613364)

---

## [0.3.0] — 2026-04-01

### Changed

- **Agent config**: Enabled real agent in debug builds for template agent testing. (commit: 9492b5f)
- **Integration Tester**: Restricted Integration Tester from LLM agent to avoid token costs. (commit: 6d888c3)

### Added

- **iOS Expert agent**: Added iOS Expert agent definition and updated system for iOS support. (commit: 3ad8366)
