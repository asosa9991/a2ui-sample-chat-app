# Release Notes

This file tracks all feature additions, bug fixes, and changes to the A2UI Sample Chat App.
Each entry is appended after every commit that closes a feature or fix.

---

## [0.7.0] — 2026-04-09

### Added

- **Chart Visualization Widgets — Android + iOS**: Two new A2UI widget types (`DonutChart`, `BarChart`) are now rendered natively in both the Android and iOS clients. The agent can now respond to chart-intent queries (e.g., "show my portfolio breakdown") with visual charts instead of plain tables.
  - **`DonutChart`**: Segmented ring drawn with `Canvas`/`drawArc` (Android: Compose Canvas; iOS: SwiftUI Canvas). Configurable center label and sublabel. Two-column legend with color swatches and percentage labels. 11-color `colorHint` palette (`blue`, `teal`, `green`, `indigo`, `amber`, `slate`, `rose`, `cyan`, `violet`, `orange`, `lime`).
  - **`BarChart`**: Horizontal proportional bars, scaled to the absolute max value. Positive/negative direction color coding (green/red). Optional value labels (88pt right-aligned). Track background at 10% opacity. Supports gain/loss, account balance, and comparison layouts.
  - Registered in `FinancialCatalog.kt` (Android) and `FinancialCatalog.swift` (iOS). Commits: `0752741` (Android), `d623600` (iOS).

- **Four pre-aggregated chart display JSON files** in `mockdata/`:
  - `portfolio_allocation_chart.json` — 11-segment donut by asset class (total $1,642,068.09 across all positions)
  - `account_balance_chart.json` — 6-bar breakdown by account type (Brokerage, 401k, IRA, HSA, Savings, Individual)
  - `gain_loss_chart.json` — 12-bar P&L chart: top 8 unrealized winners + 4 losers by position
  - `performance_chart.json` — 12-month fabricated portfolio performance line ($1.42M → $1.87M, +31.5%)
  - Commit: `81e161f`

- **`charts` intent** added to `detect_intent` in `agent/agent.py`. Keywords: `chart`, `graph`, `breakdown`, `visualize`, `pie`, `allocation chart`, `performance chart`, `compare`. Maps to `portfolio_allocation_chart.json` by default.

- **`DonutChart` + `BarChart` widget schemas** documented in `agent/system_prompt.py` with embedded-array approach (segments/bars are inline JSON, not path references). Commit: `0752741`.

- **`AppColors.onSurfaceVariant`** (`#64748B`) added to `ios/A2UIChatApp/Theme/AppColors.swift` for legend text. Commit: `d623600`.

### Technical Notes
- Segments/bars are embedded directly in `componentProperties` — not DataContext path bindings — so the LLM can populate them in a single JSON response without a `dataModelUpdate` SSE op.
- Canvas `Modifier.size()` and `Modifier.fillMaxSize()` require explicit imports in Android (`androidx.compose.foundation.layout.*`) — these were added.
- Android registration order: chart widgets appear first in `Catalog.of(...)` to ensure they take priority over any CoreCatalog defaults.
- iOS `GeometryReader` drives bar widths; `ZStack(alignment: .leading)` handles the track + fill overlay.

---

## [0.6.0] — 2026-04-08

### Added
- **Mock data injection (Python agent)**: The LLM agent (`agent/agent.py`) now injects pre-built display-model customer data into the system prompt before every LLM call, replacing the previous behaviour of asking the LLM to invent financial values on each request. (Agent: Python Expert, commit: 3c0cf69)
  - `detect_intent(message)` — zero-latency keyword classifier maps user messages to one of four intent categories: `accounts`, `positions`, `transactions`, `activities`
  - `load_mock_data(intent)` — loads the matching `*_display.json` file from `mockdata/` and injects it as a `[CUSTOMER DATA]` block into the system prompt
  - All three endpoints (`/chat`, `/chat/stream`, `/event`) and their retry paths now receive the injected context
  - Display files chosen over raw domain-model files: all amounts are pre-formatted strings (`"$487,234.56"`), account numbers pre-masked (`"••••4821"`), direction signals explicit — LLM copies values verbatim into `literalString` fields, no formatting math required
  - Customer identity is now consistent across all sessions: Michael Hartwell (CUST-7842931)

### Changed
- **`system_prompt.py`**: Removed "invent realistic simulated data" and "never refuse — invent plausible demo numbers" instructions. Replaced with explicit instruction to use only injected `[CUSTOMER DATA]` values verbatim. (Agent: Python Expert, commit: 3c0cf69)

### Documentation
- **`agent/README.md`**: Added *Mock Data Injection* section documenting intent categories, file mappings, how to add new intents, and the rationale for using display files over raw domain-model files. (Agent: Documentation Writer, commit: 0861002)

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
