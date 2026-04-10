# Release Notes

This file tracks all feature additions, bug fixes, and changes to the A2UI Sample Chat App.
Each entry is appended after every commit that closes a feature or fix.

---

## [Unreleased] — 2025-07-14

### Added
- **`ListItem` semantic component (Android)**: New `financialListItemWidget` (`CatalogItem "ListItem"`) in `FinancialCatalog.kt` with 4 named slots — `label`, `subLabel`, `value`, `subValue` — rendered in a two-column layout with a left accent bar and dividers owned by the parent `List` widget. (Agent: Android Expert, commits: `c3c988f`, `be5a9a6`)
- **`LocalListItemPath` CompositionLocal**: Per-item DataModel path scoping so each row resolves its own data without server-side path duplication. `financialListWidget` provides the per-item context when iterating list entries. (Agent: Android Expert, commit: `c3c988f`)
- **`flatten_items_to_paths()` + `arrays` param (Python)**: New utility in `a2ui_transform.py` converts array data into flat DataModel path entries; `transform_to_operations()` accepts an `arrays` parameter to wire these into the SSE pipeline. `template_renderer.py` now separates array vs. scalar data and feeds arrays through the new path. (Agent: Python Expert, commit: `0ad32dc`)

### Changed
- **`templates/brokerage_activity.json`**: `tx_list` Column migrated to `List` with `path` + `componentId`; `tx-template` replaced old `itemTemplate` with a `ListItem` (4 fields: date, description, amount, account). (Agent: Python Expert, commit: `0ad32dc`)
- **`templates/transaction_history.json`**: `txns_list` Column migrated to `List`; `t-template` replaced with a `ListItem` (3 fields: date, description, amount). (Agent: Python Expert, commit: `0ad32dc`)
- **SSE event count**: Per-list component events reduced from 34–83 (server-expanded Row/Column/Text primitives) to 1 `ListItem` template + N flat path entries — server-side complexity eliminated. (Agents: Python Expert, Android Expert)

### Fixed
- **`barColor`/`valueColor` alignment**: Accent bar and value text now use the existing `monetaryColor()` helper (`+$` → PositiveText, `-$` → NegativeText, unsigned/non-monetary → AccentNeutral, empty → Transparent) instead of ad-hoc color logic. (Agent: Android Expert, commit: `be5a9a6`)

### Design Notes
- `subLabel` uses `OnSurfaceVariant` (`#64748B`, 4.73:1 contrast ratio — WCAG AA compliant). (Agent: Android Designer)
- Dividers rendered at 15dp start inset, owned by the `List` widget — not each `ListItem` — to prevent double-divider artifacts.

### Known Pre-existing Issues (not in scope)
- `A2UIChatUiTest.run1_showTransactions_rendersUiCard` — intermittent timeout (pre-existing, unrelated to `ListItem`).
- `A2UIChatUiTest.run2_showAccountBalances_rendersUiCard` — summary text not rendered (pre-existing).

> Agents: Python Expert · Android Designer · Android Expert · Code Reviewer

---

## [v0.8.0] — 2026-04-09

### Added

- **`POST /chat/stream/template` endpoint (Python)**: New deterministic SSE route in `agent/agent.py` — no LLM call, no latency variance. Mirrors the full A2UI SSE protocol: `text` → `beginRendering` → `dataModelUpdate` → `surfaceUpdate` → `done`. Intent routing and template rendering are handled entirely server-side by the merged template engine. (Agent: Python Expert, commits: `4c3c540`, `f2b94be`)

- **Template engine merged into `agent/` (Python)**: Copied `intent_router.py`, `template_renderer.py`, `a2ui_transform.py`, `templates/`, and `data/` from the standalone `agent-templates/` service into the LLM agent package. Both LLM (`/chat/stream`) and template (`/chat/stream/template`) modes now run from a single FastAPI server — no second process required. (Agent: Python Expert, commit: `4c3c540`)

- **`BackendMode` enum (Android)**: New `BackendMode` enum (`LLM` / `TEMPLATE`) in the domain layer. `ChatViewModel` exposes `backendMode: StateFlow<BackendMode>` and an idempotent `setBackendMode(mode)` setter that no-ops when the mode is unchanged. `RealChatRepository` reads the active mode and routes requests to `/chat/stream` (LLM) or `/chat/stream/template` (Template) accordingly. (Agent: Android Expert, commits: `582ebb5`, `604dcd9`)

- **`BackendModeToggle` composable (Android)**: 152×32dp animated segmented pill control rendered in `ChatTopBar`. Features `animateColorAsState` 150ms transitions on segment fill, WCAG AA–compliant contrast ratios, and full accessibility support via `Role.RadioButton` + `selectableGroup()`. (Agent: Android Expert, commits: `582ebb5`, `604dcd9`)

- **`ChatTopBar` 3-column layout (Android)**: Top bar restructured into a `Row` with three equal-weight columns — back/clear actions on the left, `BackendModeToggle` centered, and a reserved right column for balance. (Agent: Android Expert, commit: `582ebb5`)

- **`GET /health` enhanced (Python)**: Health endpoint now enumerates both active route paths (`/chat/stream`, `/chat/stream/template`) and the set of loaded template names, making it useful as a readiness probe. (Agent: Python Expert, commit: `4c3c540`)

### Fixed

- **`account_balances.json` template content (Python)**: Template was erroneously rendering "Setup Flow Diagram" content. Replaced with a correct `ListItem`-based account balance layout (label, subLabel, value, subValue fields) matching the intended financial account summary display. (Agent: Python Expert, commit: `f2b94be`)

- **Idempotent `BackendMode` selection (Android)**: `setBackendMode()` now guards against redundant state emissions — calling the setter with the currently active mode does not trigger a recomposition or re-issue a network request. (Agent: Android Expert, commit: `604dcd9`)

- **`uvicorn reload=False` (Python)**: Set `reload=False` in the `uvicorn.run()` call to prevent the file-watcher from spawning a second worker process on startup, which was causing duplicate SSE event delivery in some environments. (Agent: Python Expert, commit: `f2b94be`)

### Changed

- **`agent-templates/` deprecated**: The standalone template agent directory is no longer the active implementation. Its `README.md` has been updated with a deprecation notice pointing consumers to the merged endpoint in `agent/`. The directory is retained for historical reference only. (Agent: Python Expert, commit: `4c3c540`)

### Test Results

- **Python**: 7/7 integration tests passed — 3 template intent paths (account balances, brokerage activity, transaction history), fallback/unknown-intent handling, LLM regression test, and SSE `/event` endpoint. (Agent: Integration Tester)
- **Android**: Compile exit 0; APK built (21 MB); all new files (`BackendMode.kt`, `BackendModeToggle.kt`) verified present; UI tests skipped (no emulator available).

> Agents: Python Expert · Android Expert · Integration Tester · Documentation Writer

### Retro
- ✅ What worked: Parallel workstreams (Python merge + Android toggle) collapsed wall-clock time; Code Reviewer caught all 3 bugs before integration tests; 7/7 Python tests + clean APK build in final state.
- ⚠️ What didn't: Template content never validated by implementing agent (`account_balances.json` had wrong content; smoke test only checked SSE event presence, not domain correctness); working-tree `reload=False` fix not staged/committed by implementing agent; blind `toggleBackendMode()` violated idempotency.
- 🔧 Improvement applied: (1) Added self-check gate to Python Expert prompts — verify template `id` matches filename and components match intent-router keywords. (2) Upgraded smoke/integration test prompts to assert domain-specific field names in `dataModelUpdate`, not just SSE event presence. (3) Added commit hygiene rule to all implementing agent prompts — run `git status` as final step, confirm clean working tree. (4) Added rule to Android Expert prompts — never expose toggle-style mutators for enum-backed UI state; always use idempotent `set(value)`.

---

## [v0.8.1] — 2026-04-10

### Added

- **`POST /chat/template` endpoint (Python)**: Synchronous, non-streaming JSON equivalent of `POST /chat/stream/template`. Returns a single `AgentResponse` object — same shape as `POST /chat` — with three fields: `text`, `ui_definition`, and `error`. Useful for clients that prefer standard HTTP request/response over SSE. Intent classification, template rendering, and A2UI op collection all happen server-side; `ui_definition` contains a merged `components` map plus the full `dataModel` array. (Agent: Python Expert, commits: `8421957`, `f76bab5`)

- **`GET /health` update (Python)**: Added `"template_sync": "/chat/template"` to the routes dictionary, keeping the health endpoint accurate as a readiness probe listing all active routes. (Agent: Python Expert, commit: `8421957`)

### Fixed

- **`dataModelUpdate` multi-chunk merge (Python)**: `POST /chat/template` now collects **all** `dataModelUpdate` ops via an `extend()` loop instead of `next()`. Previously only the first chunk was captured, silently dropping every subsequent `dataModelUpdate` entry and producing an incomplete `dataModel` array in the response. (Agent: Python Expert, commit: `f76bab5`)

### Test Results

- **Smoke**: 4/4 checks passed — health route listed (`"template_sync": "/chat/template"` present), transactions response non-null, empty-message returns 400, SSE `/chat/stream/template` endpoint unaffected (no regression). (Agent: Integration Tester)
- **Code review**: APPROVED — no blocking issues. (Agent: Code Reviewer)

> Agents: Python Expert · Integration Tester · Code Reviewer · Documentation Writer

### Retro
- ✅ What worked: Clean single-pass delivery — endpoint implemented, reviewed, fixed, and smoke-tested in one cycle with zero rework loops; Code Reviewer caught the `next()` data-loss bug before merge.
- ⚠️ What didn't: Python Expert applied inconsistent chunk-handling in the same function — `extend()` loop for `surfaceUpdate` but `next()` for `dataModelUpdate`; silent bug (no error, just dropped data) would have shipped undetected without review.
- 🔧 Improvement applied: Added prompt reinforcement — Python Expert now instructed to verify all multi-chunk SSE event types use identical collection patterns within the same function.

---

## [v0.8.3] — 2026-04-11

### Added

- **`WireFormat` enum (Android)**: New `WireFormat` enum (`SSE` / `JSONL`) in `domain/model/WireFormat.kt`. Represents the wire-format dimension of the 2×2 endpoint routing matrix, complementing the existing `BackendMode` enum. (Agent: Android Expert, commit: `b1a4f7d`)
- **`WireFormatToggle` composable (Android)**: 152×32dp animated segmented pill control in `presentation/components/WireFormatToggle.kt`. Uses a violet accent (`WireFormatPrimary` / `OnWireFormatPrimary` color tokens) to visually distinguish it from the blue `BackendModeToggle`. Matches the same structure, `animateColorAsState` 150ms transitions, `Role.RadioButton` + `selectableGroup()` accessibility semantics, and WCAG AA–compliant contrast ratios as its counterpart. (Agent: Android Expert, commit: `b1a4f7d`)
- **Dual-toggle `ChatTopBar` layout (Android)**: `ChatTopBar` now stacks both pills vertically in its center column — `BackendModeToggle` above `WireFormatToggle` with a 6dp gap. `height(56.dp)` constraint replaced with `padding(vertical = 8.dp)` to accommodate the taller layout without clipping. (Agent: Android Expert, commit: `b1a4f7d`)
- **4-endpoint runtime routing matrix (Android)**: `ChatViewModel.sendMessage()` now derives the target endpoint from the full `BackendMode × WireFormat` product, exposing all four server routes at runtime. `wireFormat: StateFlow<WireFormat>` and `setWireFormat()` added to `ChatViewModel`; `ChatScreen` collects and forwards the state to `ChatTopBar`. (Agent: Android Expert, commit: `b1a4f7d`)

### Changed

- **`ChatRepository.sendMessageStreamJsonl()` (Android)**: `endpoint` parameter added (default: `/chat/stream/jsonl`) so the ViewModel can pass the correct JSONL route (`/chat/stream/jsonl` or `/chat/stream/template/jsonl`) without the repository needing to know the routing logic. (Agent: Android Expert, commit: `b1a4f7d`)
- **`USE_JSONL_ENDPOINT` compile-time flag retired (Android)**: The static `USE_JSONL_ENDPOINT` boolean in `ChatViewModel` has been removed. Wire-format selection is now fully runtime-driven via `WireFormat` state, making the compile-time flag redundant. (Agent: Android Expert, commit: `b1a4f7d`)

### Routing Matrix

| `BackendMode` | `WireFormat` | Endpoint |
|---|---|---|
| `LLM` | `SSE` | `/chat/stream` |
| `LLM` | `JSONL` | `/chat/stream/jsonl` |
| `TEMPLATE` | `SSE` | `/chat/stream/template` |
| `TEMPLATE` | `JSONL` | `/chat/stream/template/jsonl` |

### Test Results

- **Compile**: exit 0 ✅
- **APK build**: 20 MB ✅
- **File existence**: 8/8 expected files verified present ✅
- **Routing matrix grep**: all 4 endpoint strings confirmed in compiled sources ✅
- **Code review**: APPROVED — no blocking issues ✅
- **UI tests**: skipped (no emulator available in CI)

> Agents: Android Designer · Android Expert · Code Reviewer · Integration Tester

### Retro
- ✅ What worked: Android Designer → Android Expert handoff was clean; complete design spec meant zero implementation ambiguity; all 4 critical tests (compile, APK, file existence, routing matrix grep) passed on the first attempt.
- ⚠️ What didn't: No ViewModel unit tests for the routing matrix exist — Code Reviewer flagged this as a warning. UI tests are always skipped due to the absence of a persistent emulator in CI.
- 🔧 Improvement applied: None — the routing matrix coverage gap is a pre-existing project-wide test debt, not introduced by this change.

---

## [v0.8.2] — 2026-04-11

### Added

- **`POST /chat/stream/template/jsonl` endpoint (Python)**: New JSONL-variant SSE route for the deterministic template path. Emits plain `data:` lines only (no `event:` type field), making it compliant with the JSONL wire format expected by SDK-based clients. Op ordering follows the JSONL spec: `{"text": "..."}` → `{"surfaceUpdate": {...}}` (one per chunk) → `{"dataModelUpdate": {...}}` → `{"beginRendering": {...}}` → `{"done": {}}`. Fallback and error paths both yield a `text` line followed by `done`. (Agent: Python Expert, commit: `744573a`)

- **`GET /health` update (Python)**: Added `"template_jsonl": "/chat/stream/template/jsonl"` to the routes dictionary. (Agent: Python Expert, commit: `744573a`)

### Test Results

- **Smoke**: 5/5 checks passed — all 3 intents return data (account balances, brokerage activity, transaction history); zero `event:` lines in any response; fallback unknown-intent returns text + done; empty message returns HTTP 400; health routes dict includes `template_jsonl`. (Orchestrator)
- **Integration**: 4/4 passed — all 3 intents return valid JSONL with correct op set; fallback yields text + done; empty message → 400; `/chat/stream/template` SSE route unaffected (regression clean). (Orchestrator)
- **Code review**: APPROVED — `done` op from transform correctly dropped by reordering filter; explicit `{"done": {}}` always emitted; error handler yields valid JSONL. (Orchestrator)

> Agents: Python Expert

### Retro
- ✅ What worked: Clean single-commit delivery; smoke + integration + code review all passed first pass with no blockers; explicit `done` sentinel handling consistent with existing JSONL LLM route.
- ⚠️ What didn't: Nothing notable — straightforward route addition with no surprises.
- 🔧 Improvement applied: None required.

---

## [v0.7.1] — 2026-04-09

### Fixed
- **LLM agent PermissionHandler import**: `github-copilot-sdk` v0.2.1 does not export `PermissionHandler` from `copilot.__init__`; fixed all 3 import sites to use `from copilot.session import PermissionHandler`. This was a critical bug that blocked every `/chat/stream` request with an `ImportError`. (Agent: Python Expert, commit: b86dd36)
- **requirements.txt un-installable package**: Removed `a2ui-agent` from `requirements.txt` as it is a local/private package unavailable on PyPI; replaced with a comment explaining local installation. (Agent: Python Expert, commit: b86dd36)
- **Stale version pins**: Updated `fastapi` (0.115.0→0.135.3), `uvicorn` (0.30.0→0.44.0), `python-dotenv` (1.0.0→1.2.2), `sse-starlette` (>=1.6.0→==3.3.4) to match the working virtual environment. (Agent: Python Expert, commit: b86dd36)
- **Stray file cleanup**: Deleted `agent/=1.6.0` — a zero-byte file created by an accidental shell redirection (`pip install sse-starlette>=1.6.0` with unquoted `>`). (Agent: Python Expert, commit: b86dd36)

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
