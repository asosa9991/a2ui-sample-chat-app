# Release Notes

This file tracks all feature additions, bug fixes, and changes to the A2UI Sample Chat App.
Each entry is appended after every commit that closes a feature or fix.

---

## [v0.4.0] — 2026-04-22 · Path-Based LLM Output Format with `dataModel`

### Overview

Introduces a first-class `dataModel` field to the LLM response contract, replacing the prior approach of embedding all dynamic values as `{"literalString": "..."}` blobs inside `uiDefinition`. The LLM now emits a clean three-field JSON object (`text`, `uiDefinition`, `dataModel`); components reference dynamic values via `{"path": "/key"}` bindings; and the Python backend maps `dataModel` entries to typed `dataModelUpdate` A2UI operations (`valueString` for scalars, `valueArray` for arrays). All 81 tests pass.

### Commits

| Hash | Description |
|------|-------------|
| `1149367` | feat(agent): path-based LLM output with `dataModel`; `AgentResponse.data_model`; dual-path `transform_to_operations()`; 7 new tests |
| `db2edeb` | fix(agent): explicit `is not None` guards for `data_model`; restore `extra_context` regression fix |

---

### Python Backend (`agent/`)

#### New LLM Response Contract — `{text, uiDefinition, dataModel}` (`1149367`)

The system prompt (`agent/system_prompt.py`) was completely rewritten to codify a three-field output format:

| Field | Purpose |
|-------|---------|
| `text` | Human-readable response text (unchanged) |
| `uiDefinition` | Component tree — dynamic values referenced via `{"path": "/key"}` bindings only |
| `dataModel` | Flat or nested dict of all dynamic values: balances, names, dates, amounts, arrays |

**Binding rules enforced by prompt:**

| Pattern | When to use |
|---------|-------------|
| `{"path": "/key"}` | Any dynamic value — balance, name, date, amount, array element |
| `{"literalString": "..."}` | Truly static UI labels only (e.g., column headers, fixed button text) |

**`ListItem` widget documentation added:**
- Fields: `label`, `value`, `subValue`
- Relative path resolution inside `List` item templates — child paths resolve against each array element
- `List` with `children.path` is the canonical pattern for all repeating financial data (transactions, holdings, activity rows)

**Removed patterns:**
- `itemTemplate` / `items` / `itemListId` template expansion pattern removed entirely from prompt examples
- DonutChart and BarChart retain inline data (not path-based — chart data arrays are self-contained)

**Examples:** 3 new concise examples replace 4 verbose prior examples.

---

#### Backend Processing — `AgentResponse` + `transform_to_operations()` (`1149367` + `db2edeb`)

**Changes in `agent/agent.py`:**

| Change | Detail |
|--------|--------|
| `AgentResponse.data_model` field | New `Optional[dict] = None` field on the `AgentResponse` dataclass |
| `parse_agent_response()` | Now extracts top-level `dataModel` key from LLM JSON response and populates `AgentResponse.data_model` |
| `transform_to_operations()` — dual-path logic | When `data_model is not None`: iterates entries and emits `dataModelUpdate` ops — scalars → `valueString`, arrays → `valueArray`. Falls back to `transform_to_path_bindings()` for backward-compat with `literalString`-only responses. |
| `is not None` guards | Replaced all truthy `data_model` checks with explicit `is not None` — ensures an empty `{}` data model is treated as intentional (LLM-provided) rather than falling back to the legacy path |
| Restored `+ extra_context` | `stream_llm_copilot_sdk` `create_session()` call — recurring regression fixed again (see Retro) |
| Critical comment added | Inline comment above `create_session()` call: `# CRITICAL: must include + extra_context — do not remove` |
| All 3 call sites updated | `chat_stream`, event handler, and JSONL fallback all pass `data_model` through the full pipeline |

**`valueArray` encoding — design decision:**

Arrays in `dataModel` are encoded as structured `valueArray` A2UI operations (not JSON-stringified). This is required for `DataContext` path-probing to resolve `children.path` bindings inside `List` templates — a stringified blob would break `List` children rendering entirely.

---

### Test Suite (`agent/test_agent.py`)

**81/81 tests pass** (74 prior + 7 new)

| New Test Group | Tests | Coverage |
|----------------|-------|---------|
| `TestDataModelExtraction` | 7 | Scalar extraction, array extraction, backward compat with no `dataModel` field, `valueArray` encoding shape, `is not None` guard (empty `{}` does not fall back), fallback to `path_bindings` for `literalString` responses, all 3 call-site pass-through |

---

### A2UI Protocol Impact

| Operation type | Trigger condition |
|----------------|-------------------|
| `dataModelUpdate` (scalar `valueString`) | LLM emits `dataModel` with a string/number entry |
| `dataModelUpdate` (array `valueArray`) | LLM emits `dataModel` with a list entry |
| Path bindings (legacy) | `dataModel` absent from LLM response — backward-compat fallback |

---

### Retro

- ✅ What worked: `valueArray` design decision by Python Expert (over JSON-stringifying arrays) was the right call — `DataContext` path-probing requires structured array storage, not a string blob. Code review caught it would have broken `List` `children.path` rendering entirely.
- ⚠️ What didn't: `extra_context` regression occurred for the **third time** — dropped from `stream_llm_copilot_sdk` during refactor. This is a systemic pattern: any edit touching `create_session()` risks losing the `+ extra_context` append.
- 🔧 Improvement applied: Added inline comment in `agent.py` above `stream_llm_copilot_sdk`'s `create_session()` call: `# CRITICAL: must include + extra_context — do not remove` to prevent future regressions.

---

## [v0.3.1] — 2026-04-14 · Designer Wire-Format & Streaming Context Bug Fixes

### Overview

Two targeted bug fixes resolving a JSON shape mismatch between the Android client and the Python backend in the designer save-template flow, and a silent data-loss defect in the streaming LLM path that caused `extra_context` (mock financial data) to be dropped from the system message.

### Commits

| Hash | Description |
|------|-------------|
| `aa05101` | fix(agent): normalize `uiDefinition.components` list→dict in `designer_save_template`; 8 new tests |
| `3182e56` | fix(agent): restore `extra_context` in `stream_llm_copilot_sdk`; remove redundant `_normalize_components` call |

---

### Python Backend (`agent/`)

#### Fix 1 — Designer save-template wire-format normalization (`aa05101`)

The Android client serializes `uiDefinition.components` as a JSON **array** (`[{id, component}, ...]`), but `transform_to_path_bindings()` expected a **dict** keyed by component ID. This caused a `TypeError` whenever a designer attempted to save a template from the Android app.

**Changes in `agent/agent.py`:**

| Change | Detail |
|--------|--------|
| New `_normalize_components()` helper | Converts a `list` of `{id, component}` objects into a `dict` keyed by `id`; passes an existing `dict` through unchanged — making the function safe to call regardless of which wire format arrives. |
| Applied at both call sites in `designer_save_template` | Both the primary processing path and the metadata extraction path now call `_normalize_components()` before any dict-keyed access. |
| Defensive guard in `transform_to_path_bindings()` | Added an explicit check at function entry: if `components` is still a `list` at that point, it is normalized before iteration — prevents silent failures if other callers pass the raw Android payload in the future. |

**New tests (8 total across 2 groups):**

| Test Group | Count |
|------------|-------|
| `TestNormalizeComponents` — list input, dict passthrough, empty list, empty dict | 4 |
| `TestDesignerSaveTemplateListFormat` — end-to-end save with list-format payload; 409 collision with list format; mixed nested components; round-trip ID preservation | 4 |

---

#### Fix 2 — Restored `extra_context` in streaming LLM path; deduplicated normalize call (`3182e56`)

`stream_llm_copilot_sdk()` was silently dropping `extra_context` from its system message construction. This meant the LLM never received the mock financial context (account balances, transaction history, etc.) during streaming sessions, causing generic or hallucinated responses instead of data-grounded replies.

**Changes in `agent/agent.py`:**

| Change | Detail |
|--------|--------|
| Restored `+ extra_context` in `stream_llm_copilot_sdk` | System message now matches `call_llm_copilot_sdk`: `system_prompt + extra_context`. Mock financial data is correctly injected into every streaming LLM call. |
| Removed redundant `_normalize_components()` call in `designer_save_template` | After fix `aa05101`, the already-normalized `components` dict was being passed through `_normalize_components()` a second time. The redundant call is removed; the single call at the top of the function is sufficient. |

---

### Test Suite

**74/74 tests pass** (66 prior + 8 new)

---

### Bug Impact Summary

| Bug | Symptom | Root Cause |
|-----|---------|------------|
| Save-template `TypeError` | Designer save always failed when triggered from Android | `components` array vs. dict shape mismatch |
| LLM streaming context loss | Streaming responses lacked financial data; LLM gave generic answers | `extra_context` omitted from `stream_llm_copilot_sdk` system message |

---

### Retro
- ✅ What worked: Code review caught 2 blocking issues (silent `extra_context` regression + redundant normalize call) that the initial fix missed — parallel review + test execution surfaced them quickly.
- ⚠️ What didn't: The Python Expert that authored `aa05101` introduced a silent regression in `stream_llm_copilot_sdk` while narrowly focused on the list→dict normalization fix. A broader search for similar `extra_context` usage patterns (one grep) would have caught it immediately. The Android↔Python wire format contract for `components` was also undocumented, which is the root cause of the original 500.
- 🔧 Improvement applied: Added wire format contract note to the Designer API spec comments in `agent.py` (`_normalize_components` docstring now explicitly documents the Android list format). No agent definition changes required — this was a scope discipline issue, not a capability gap.

---

## [v0.3.0] — 2026-04-10 · Designer-First Template Workflow

### Overview

A complete end-to-end designer workflow that bridges UX creation and financial end-user delivery. UX designers can enter a protected **Designer Mode**, craft financial card experiences through the LLM backend, and save them as reusable named templates. Financial end-users then receive instant, deterministic renders at runtime — no LLM call required — triggered by natural-language intent keywords.

### Commits

| Hash | Description |
|------|-------------|
| `e80efa5` | feat(agent): Phase 1+2 — DataAdapter, intentTriggers, dataSchema, designer API |
| `4a555a6` | fix(agent): code review fixes — intent router robustness, template overwrite guard, path bindings generalization |
| `1f9c6b3` | feat(android): Phase 3 — designer mode UI (banner, save-template flow, 3-tap toggle) |
| `d43d307` | fix(android): null-safe errorStream + explicit UTF-8 in DesignerRepository |

---

### Python Backend (`agent/`)

#### New Files

| File | Purpose |
|------|---------|
| `agent/data_adapter.py` | `DataAdapter` ABC with `MockDataAdapter` (reads from `data/`) and `ApiDataAdapter` stub. Enables per-user data injection at render time without coupling templates to a specific data source. |

#### Modified Files

| File | Changes |
|------|---------|
| `agent/templates/transaction_history.json` | Added `intentTriggers` array and `dataSchema` block |
| `agent/templates/account_balances.json` | Added `intentTriggers` array and `dataSchema` block |
| `agent/templates/brokerage_activity.json` | Added `intentTriggers` array and `dataSchema` block |
| `agent/intent_router.py` | Fully rewritten with dynamic trigger loading from template files; `_ExactRule` / `_KeywordRule` dataclasses; `reload()` method; defensive guards for malformed templates |
| `agent/template_renderer.py` | Accepts `DataAdapter`; adds `reload()`; `render()` accepts optional `user_id` |
| `agent/agent.py` | 5 new designer API routes; `user_id` wiring; generalized `transform_to_path_bindings()`; 409 overwrite guard |

#### New Designer API Endpoints

| Method | Route | Description |
|--------|-------|-------------|
| `POST` | `/designer/save-template` | Save a UI definition + metadata as a draft template. Returns `409 Conflict` if a template with the same name already exists in approved status. |
| `GET` | `/designer/templates` | List all templates (draft and approved). |
| `DELETE` | `/designer/templates/{id}` | Delete a draft template by ID. |
| `GET` | `/designer/templates/{id}/preview` | Stream an SSE preview of the named template. |
| `POST` | `/designer/templates/{id}/publish` | Promote a template from `draft` → `approved`, making it reachable by end-user intent matching. |

#### Test Suite

**66/66 tests pass** (58 original + 8 new)

| New Test Group | Count |
|----------------|-------|
| Intent router robustness (malformed templates, missing fields, partial data) | 3 |
| Designer save-template (happy path, 409 collision) | 2 |
| Path bindings generalization (all widget config keys, not just `Text.literalString`) | 3 |

---

### Android App (`app/`)

#### New Files

| File | Purpose |
|------|---------|
| `DesignerRepository.kt` | HTTP client for `POST /designer/save-template`; manual `UiDefinition` serialization; null-safe `errorStream` + explicit `Charsets.UTF_8` |
| `DesignerModeLocal.kt` | `LocalDesignerMode` CompositionLocal — propagates designer mode state through the composition tree without prop drilling |
| `DesignerModeBanner.kt` | 36 dp amber banner with `AnimatedVisibility` expand/shrink animation and an **Exit Designer Mode** button |
| `ChipInput.kt` | Keyword chip input with `FlowRow` layout; Space/comma commits a chip; Backspace removes the last chip; max 10 chips enforced |
| `SaveTemplateDialog.kt` | `ModalBottomSheet` with a 3-state action button: `IDLE` → `SAVING` (spinner) → `SAVED` (checkmark); blocks sheet dismissal during `SAVING` state |

#### Modified Files

| File | Changes |
|------|---------|
| `BackendMode.kt` | Added `DESIGNER` enum value (used internally; filtered from the user-visible toggle) |
| `Color.kt` | 6 new amber designer color tokens: `DesignerAmber`, `DesignerAmberDark`, `DesignerAmberContainer`, `DesignerAmberBorder`, `OnDesignerAmber`, `OnDesignerAmberContainer` |
| `ChatUiState.kt` | Added `DesignerState` data class |
| `ChatViewModel.kt` | Wires `DesignerRepository` + `_designerState` `StateFlow` + 5 designer action methods + exhaustive `DESIGNER` routing in `BackendMode` switch |
| `BackendModeToggle.kt` | Filters `DESIGNER` from segment list — public toggle stays at 2 segments (LLM / Template) |
| `ChatTopBar.kt` | 3-tap `pointerInput` / `detectTapGestures` gesture; triggers haptic feedback + activates designer mode with animated amber ring |
| `MessageBubble.kt` | `BookmarkAdd` `IconButton` visible only when `LocalDesignerMode.current == true` |
| `MessageList.kt` | `onSaveTemplate` lambda threaded down to `MessageBubble` |
| `ChatScreen.kt` | `CompositionLocalProvider(LocalDesignerMode)` + `DesignerModeBanner` overlay + `SaveTemplateDialog` modal sheet |

---

### Designer User Flow

```
Triple-tap avatar  →  Haptic feedback  →  Designer Mode activates
       │
       ▼
Amber "🎨 Designer Mode" banner appears below TopBar
Backend auto-switches to LLM mode
       │
       ▼
Compose a message  →  LLM returns a financial card
       │
       ▼
Tap BookmarkAdd icon on any AI card
       │
       ▼
SaveTemplateDialog opens
  • Enter template name
  • Add intent keywords (chip input: Space/comma to commit)
  • Tap [Save Template]
       │
       ▼
SAVING (spinner)  →  SAVED (checkmark)  →  auto-dismiss after 1.5 s
Template POSTed to Python agent → /designer/save-template
       │
       ▼
Financial end-users trigger template via natural-language keywords
(deterministic render — no LLM call at runtime)
```

---

### Bug Fixes

| Area | Fix |
|------|-----|
| `DesignerRepository` | `conn.errorStream` null safety — falls back to `inputStream` on success responses to avoid NPE |
| `DesignerRepository` | Explicit `Charsets.UTF_8` in stream readers to prevent platform-default encoding issues |
| Intent router | Defensive guards skip malformed or partially-written template files without crashing the router |
| Designer save | `409 Conflict` response prevents overwriting templates that have already been published to `approved` status |
| Path bindings | `transform_to_path_bindings()` generalized to handle all widget config keys, not just `Text.literalString` |

---

### Architecture Notes

- **Designer mode is debug-only accessible.** The 3-tap gesture is the only entry point; the production toggle UI is unaffected (`DESIGNER` filtered from `BackendModeToggle`).
- **`LocalDesignerMode` CompositionLocal** propagates state through the Compose tree without threading a boolean through every intermediate composable.
- **`DESIGNER` BackendMode routes to the existing LLM endpoint** — no new backend route is needed for the design session itself; only the save/preview/publish lifecycle requires new routes.
- **Templates start as `status: "draft"`** and must be explicitly promoted via `POST /designer/templates/{id}/publish` before they are reachable by end-user intent matching. This prevents accidental early exposure.
- **`DataAdapter` abstraction** decouples template rendering from any specific data source. `MockDataAdapter` serves `data/` JSON files for development; `ApiDataAdapter` is a stub ready for a real financial data backend.

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

## [v0.8.4] — 2026-04-10

### Added
- **Sync WireFormat toggle**: Added `WireFormat.SYNC` as a third wire format option alongside `SSE` and `JSONL`. Users can now select any of the 6 server endpoints at runtime via the two pill toggles in the top bar — no recompile required. (Agent: Android Expert, commits: `4c1b76e`, `eccc933`)

### Changed
- **Routing matrix expanded to 2×3**: `ChatViewModel` routing now covers all combinations of `BackendMode (LLM|Template) × WireFormat (SSE|JSONL|Sync)`:
  - LLM + SSE → `POST /chat/stream`
  - LLM + JSONL → `POST /chat/stream/jsonl`
  - LLM + Sync → `POST /chat`
  - Template + SSE → `POST /chat/stream/template`
  - Template + JSONL → `POST /chat/stream/template/jsonl`
  - Template + Sync → `POST /chat/template`
- **Both pill toggles widened to 192dp** (from 152dp) per design review — three 64dp segments for WireFormat pill, two 96dp segments for BackendMode pill; both pills now share identical outer dimensions for visual alignment. (Agent: Android Designer + Android Expert)
- **Accessibility fix — touch targets**: Replaced broken `heightIn(min = 48.dp)` with `minimumInteractiveComponentSize()` in both `WireFormatToggle` and `BackendModeToggle`. The previous approach was clamped by the parent `Box(height(32.dp))` and never actually achieved 48dp touch targets. (Agent: Android Designer + Android Expert)
- **WCAG AA fix — unselected label contrast**: New `ToggleLabelUnselected = Color(0xFF475569)` token (Slate-600, 6.43:1 contrast) replaces `OnSurfaceVariant` (#64748B, 4.10:1) in both toggles. Previous value failed WCAG AA at 11sp. (Agent: Android Designer + Android Expert)

### Infrastructure
- **Unit test directory created** (`app/src/test/`): first unit tests added to the project.
- **MockWebServer added** as a test dependency (`okhttp3:mockwebserver:4.12.0`) enabling HTTP-level repository tests without a live server.
- **Unit test coverage enabled** (`enableUnitTestCoverage = true` in debug buildType).

### Tests added (19 total, all passing)
- `WireFormatTest` — 7 unit tests: enum entries, labels, `valueOf`, SSE as default
- `ChatViewModelRoutingTest` — 6 unit tests: all 6 routing matrix combinations verified with `FakeChatRepository`
- `RealChatRepositorySyncTest` — 6 unit tests via MockWebServer: text+UI response, empty text, missing UI def, HTTP 500, correct endpoint path, network failure
- `WireFormatToggleTest` — 6 instrumented Compose UI tests: 3-segment rendering, default selection, tap callbacks, state update, disabled toggle
- `ChatTopBarTest` — 4 instrumented Compose UI tests: both toggles visible, Sync chip present, callbacks fire correctly

### Retro
- ✅ What worked: Parallel design + codebase exploration cut Phase 2 briefing time significantly; all source files were read before the Android Expert started, producing a single comprehensive brief with zero back-and-forth. MockWebServer pattern for repository testing worked first try.
- ⚠️ What didn't: Initial implementation had a SYNC error-message regression (raw HTTP status leaked to UI). Code review caught it cleanly; a focused 2-file fix resolved it in one pass. Root cause: the implementation brief specified `StreamEvent.Error(e.message)` without explicitly requiring parity with `sendMessage()`'s friendly fallback — brief was updated for future reference.
- 🔧 Improvement applied: none to agent definitions; the fix was a brief-quality issue, not an agent capability gap.

---

## [v0.8.5] — 2026-04-10

### Fixed
- **Sync endpoint deserialization crash**: `ComponentDto.id` was declared as a required `String`, but the `/chat/template` sync endpoint returns components as a map where the key IS the id — no `id` field inside the value object. Changed `id` to `String? = null` and updated `toDomain()` to use `dto.id ?: key` (map key as fallback). This eliminated the `MissingFieldException` that silently swallowed every sync template response. (Agent: Android Expert, commit: 3a0bf7a)
- **Fatal app crash in List widget probe loop**: `financialListWidget` probed array item existence using `dataContext.getString("$path/$index")`. The A2UI library's `DataModel.getString()` throws `IllegalArgumentException` (not returns null) when the value at a path is a JsonObject rather than a primitive. Transaction history data stores each item as an object (`{"action":…,"date":…,"amount":…}`), so the probe threw and crashed the app. Wrapped the probe in try-catch: `IllegalArgumentException` → item exists as object; `null` → end of array. (Agent: Android Expert, commit: 3a0bf7a)

### Tests Added
- `ComponentDtoDeserializationTest` (5 unit tests) — id-absent fallback, explicit-id wins, mixed cases, no-throw validation
- `ListProbeTest` (6 unit tests) — object items, empty list, 50-item cap, primitive items, mixed types
- Total unit tests: 30/30 passing

### Retro
- ✅ What worked: Logcat analysis immediately surfaced both crash sites with exact file + line numbers; parallel research resolved root cause before any implementation began; Android Expert fixed both bugs + tests in a single pass; Code Review + Integration Test caught a non-blocking test-fidelity gap (mock vs real DataModelContext)
- ⚠️ What didn't: Both bugs should have been caught by integration tests before reaching production. Bug 1 (sync deserialization) would be caught by a MockWebServer test that exercises the full `/chat/template` sync response path with a real JSON payload. Bug 2 (List probe crash) would be caught by a UI test that renders a List component backed by object-array data. Neither test existed.
- 🔧 Improvement applied: Added regression tests for both bugs (ComponentDtoDeserializationTest, ListProbeTest). Recommendation: add an instrumented UI test that renders a `List` widget with real nested object data via a mock DataModel — this would have caught Bug 2 at the PR stage.

---

## [v0.8.6] — 2026-04-10

### Fixed
- **Sync endpoint `all_components` value format mismatch (Python)**: `POST /chat/template` was storing component entries as `all_components[id] = entry["component"]`, which placed the raw `{"Column": {...}}` dict directly as the map value. Kotlin's `ComponentDto.componentProperties` maps to the JSON key `"componentProperties"`, so the deserialized `ComponentDto` had empty `componentProperties`, `widgetType = null`, and the A2UI library reported a rendering error for every sync template card. Fixed: `all_components[id] = {"componentProperties": entry["component"]}`. (Agent: Python Expert, `agent/agent.py` — 1 line)
- **`explicitList` children silently dropped in Column/Row widgets (Android)**: Server templates use `{"explicitList": [...]}` for Column and Row children, but `financialColumnWidget` and `financialRowWidget` called `DataReferenceParser.parseComponentArray()` which only understands `{"componentIds": [...]}`. Result: all children were silently dropped → blank cards. Added an `explicitList` fallback path in both widgets so children are resolved directly from the inline list. (Agent: Android Expert, `FinancialCatalog.kt`)
- **Plain-string child not rendered in Card widget (Android)**: Server templates pass a plain string (component id) as Card's `child` field. `financialCardWidget` called `DataReferenceParser.parseComponentRef()` which does not handle bare strings → Card never rendered its child. Added a plain-string fallback so a bare string is treated as a direct component reference. (Agent: Android Expert, `FinancialCatalog.kt`)

### Tests Added
- **`ExplicitListParsingTest.kt`** (5 unit tests) — `explicitList` parsing: non-empty list, empty list, missing key falls back to `componentIds`, mixed valid/invalid ids, null value handled gracefully.
- **`SyncComponentDtoFormatTest.kt`** (4 unit tests) — `ComponentDto` deserialization: `componentProperties` wrapper round-trips correctly, `widgetType` is populated after fix, null `componentProperties` does not throw, full financial card schema deserializes to correct widget type.
- **`RenderingRegressionTest.kt`** (4 instrumented Compose UI tests, emulator required) — Column renders all explicit children, ListItem renders with literal string slots, Card renders its plain-string child, no error text appears in a valid component hierarchy.
- **Test counts**: 22 → 31 unit tests passing. 4 new instrumented UI tests (emulator required).

### Files Changed
- `agent/agent.py` — fix sync endpoint `all_components` value format (1 line)
- `app/src/main/java/com/example/a2ui/chat/data/a2ui/FinancialCatalog.kt` — `explicitList` fallback in `financialColumnWidget` and `financialRowWidget`; plain-string fallback in `financialCardWidget`
- `app/src/test/…/ExplicitListParsingTest.kt` — new (5 unit tests)
- `app/src/test/…/SyncComponentDtoFormatTest.kt` — new (4 unit tests)
- `app/src/androidTest/…/RenderingRegressionTest.kt` — new (4 UI tests)

### Retro
- ✅ What worked: Root causes were clearly separable — Python format mismatch vs. Kotlin parser gap — which allowed targeted, minimal fixes with no cross-cutting side effects. New tests provide permanent regression coverage for both code paths.
- ⚠️ What didn't: Root cause #1 (sync response shape vs. `ComponentDto` schema mismatch) should have been caught by a contract test comparing the Python sync response JSON to the Kotlin DTO schema. Root cause #2 (`explicitList` format) was deliberately designed for templates but was never exercised against `FinancialCatalog` widget parsing — end-to-end template rendering was never tested before shipping.
- 🔧 Improvement applied: (1) Added "sync response format" contract test to the DoD checklist for any sync endpoint changes — a MockWebServer test that deserializes the full Python response into `ComponentDto` and asserts `widgetType != null`. (2) `RenderingRegressionTest` now covers the `explicitList` and plain-string child paths going forward. (3) Recommendation: add a CI gate that renders every template through `FinancialCatalog` against a mock DataModel before merge — silent empty-card regressions should never reach production.

---

## v0.8.7 — 2026-04-10

### Fixed
- **FATAL crash on transaction list render**: `FinancialCatalog.kt:969` used `"positive $"` and `"negative $"` as regex replacement strings. Java's `Matcher.replaceAll()` treats bare `$` as a group-reference — with no group number following it throws `IllegalArgumentException: Illegal group reference`. This fired every time a ListItem value contained a `+$` or `-$` monetary prefix (every transaction row). Fixed by escaping: `"positive \\$"` / `"negative \\$"`. (Agent: Android Expert)

### Tests added
- `ValueSemanticTest` — 4 unit tests covering `+$`, `-$`, plain `$`, and blank value replacement logic

### Retro
- ✅ What worked: Crash was fully self-describing in logcat — exact file + line number + exception type made root cause obvious in under 2 minutes
- ⚠️ What didn't: The accessibility semantic logic was added without a unit test; a test for `+$1,234` input would have caught this at compile/test time before shipping
- 🔧 Improvement applied: `ValueSemanticTest` now covers this path; future monetary string transforms must include a test with `$`-prefixed inputs

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

---

## v0.8.8 — 2026-04-10

### Added — Comprehensive Test Suite & Agent Cheatsheet Updates

**Root cause (retro finding):** Every regression test in this repository was written in the same commit that fixed the bug it covers. Zero tests were proactive. This release addresses that systemic gap.

#### Android unit tests (+38 new, 31 → 84 total Kotlin unit tests)
- **`MonetaryColorTest`** (11 tests) — `monetaryColor()` drives the left accent bar color and value text color for every transaction row. Was completely untested. Covers `+$`, `-$`, neutral, non-monetary, and blank inputs.
- **`DataContextPathResolutionTest`** (10 tests) — `resolveField()` inside `financialListItemWidget` resolves fields from `literalString` or `path` references with `itemPath` prefix. Tests literal resolution, absolute vs relative paths, unknown format, and missing keys.
- **`SurfaceStateManagerTest`** (17 tests) — SSE op accumulation: surfaceUpdate increments component count, dataModelUpdate captures data, beginRendering sets root/surfaceId, multiple chunks accumulate, `componentProperties` wrapper contract enforced.

#### Android instrumented tests (+6)
- **`ListItemRenderTest`** (6 tests) — Compose UI tests that render `ListItem` with `+$` / `-$` monetary values via `FinancialCatalog`. Directly covers the v0.8.7 regex crash scenario end-to-end. Also tests all four fields (label, subLabel, value, subValue) and TalkBack content description semantic.

#### Python unit tests (+33 new — no server required)
- **`agent-templates/test_template_agent.py`** — `agent-templates/` had zero test coverage before this release.
  - `TestIntentRouter` (8) — keyword routing for all 3 intents + case-insensitivity + unknown message
  - `TestTemplateRenderer` (10) — template loading, text field, arrays output
  - `TestA2UiTransform` (10) — all 4 op types present, component `id`+`component` format, no bare widget type keys
  - `TestSyncEndpointFormat` (5) — `componentProperties` wrapper contract; regression for v0.8.6 Bug 1

#### Python integration tests (+5)
- **`agent/test_agent.py: TestTemplateStream`** (5) — `/chat/stream/template` JSONL endpoint protocol contract

#### Agent cheatsheets updated
- **`code-reviewer.md`**: DoD checklist (8 items), bare-`$` regex rule, `explicitList` rule, `componentProperties` wrapper rule, DTO nullability rule
- **`android-expert.md`**: Test DoD section, `monetaryColor()` gotcha, `explicitList` fallback pattern, Card plain-string child pattern
- **`integration-tester.md`**: Regression Checklist, Unit Test Check, Template Agent Unit Tests section
- **`python-expert.md`**: `componentProperties` MANDATORY note, template test commands

### Retro
- ✅ What worked: Parallel delegation (3 specialist agents + 1 researcher simultaneously) cut retro cycle time to a single session; Researcher analysis confirmed all 6 bugs were preventable with standard unit tests
- ⚠️ What didn't: All 6 production bugs reached prod because no tests were written before shipping; `agent-templates/` had 0% coverage for its entire existence
- 🔧 Improvement applied: `code-reviewer.md` now has an 8-item DoD checklist; `integration-tester.md` now has a Regression Checklist run on every release; `android-expert.md` and `python-expert.md` have explicit Test DoD sections. Zero-test shipping is now a reviewer-blocking violation.

---

## v0.9.0 — 2026-04-10

### Changed

- **`agent-templates/` removed — single source of truth is `agent/` (Python)**: Deleted the entire `agent-templates/` directory, which had been dead code since the v0.8.0 merge. All template engine modules (`intent_router.py`, `template_renderer.py`, `a2ui_transform.py`), `templates/`, and `data/` were already identical in both directories. The active implementation has always lived in `agent/` since v0.8.0; this commit eliminates the stale duplicate and any ambiguity about which copy is authoritative. (Agent: Python Expert, commit: `4decb17`)

- **`agent.sh` cleaned up**: Removed stale `TEMPLATE_PID` / `TEMPLATE_LOG` variables and the dead `template` agent branch in `_running_agent()` / `_stop()`. The script now manages a single process — the unified `agent/` server — with no vestigial multi-process scaffolding. (Agent: Python Expert, commit: `4decb17`)

- **Agent definitions and cheatsheets purged of `agent-templates/` references**: Removed all `agent-templates/` path references from 12 agent definition and cheatsheet files. Affected files: `tester.agent.md`, `python-expert.agent.md`, `researcher.agent.md`, `planner.agent.md`, `orchestrator.agent.md`, `debugger.agent.md`, `documentation.agent.md`, `code-reviewer.md`, `integration-tester.md`, `python-expert.md` (cheatsheet), `copilot-instructions.md`, `README.md`. (Agent: Documentation Writer, commit: `6901c01`)

### Added

- **`agent/pytest.ini` — `integration` mark registered (Python)**: Added a `pytest.ini` to `agent/` that registers the `integration` pytest mark, eliminating `PytestUnknownMarkWarning` when running the full test suite. (Agent: Python Expert, commit: `4decb17`)

### Fixed

- **`done` event format in JSONL/SSE template stream (Python)**: `chat_stream_template` was emitting `data: {}` for the terminal done event. Fixed to emit `data: {"done": {}}` so JSONL parsers can detect stream completion by key presence rather than relying on an empty object heuristic. Consistent with the JSONL LLM route and the SSE spec used across all other endpoints. (Agent: Python Expert, commit: `ffc8553`)

- **`UiDefinitionDto` missing `dataModel` field, `Divider` widget unregistered (Android)**: Added `DataModelEntryDto` and a `dataModel: List<DataModelEntryDto>` field to `UiDefinitionDto` so sync responses correctly populate `DataContext` on the Android side. `RealChatRepository`'s sync path now calls `buildDataModelJson()` and passes the result through. Separately, `financialDividerWidget` was missing from `FinancialCatalog`'s registration call — every `Divider` component was silently reported as "Invalid component". Registered it. (Agent: Android Expert, commit: `25a2621`)

### Test Results

- **Python — 55/55 unit + integration tests passing**: 33 tests migrated from `agent-templates/test_template_agent.py` into `agent/test_agent.py` (covering `TestIntentRouter`, `TestTemplateRenderer`, `TestA2UiTransform`, `TestSyncEndpointFormat`); 5 `TestTemplateStream` integration tests already present; `TestChatStream` / `TestJsonlStream` fixed to target template endpoints and corrected `done` format assertion. Suite grew from 22 to 55 tests. (Agent: Python Expert, commit: `ffc8553`)

> Agents: Python Expert · Android Expert · Documentation Writer

### Retro

<!-- TODO: Orchestrator to fill in after retro runs -->

---

## [v0.9.1] — 2026-04-10

### Fixed
- **Clean array DataModel encoding**: Replaced `flatten_items_to_paths()` in `a2ui_transform.py` with `encode_array_entry()`. Arrays are now sent as a single `{"key": "transactions", "valueArray": [...]}` entry instead of N×fields+N sentinel flat entries (56 entries → 1 for 14 transactions). (Agent: Python Expert, commit: `f663846`)
- **Android valueArray support**: Added `valueArray` field to `DataModelEntryDto` and updated `buildDataModelJson()` + `SurfaceStateManager.extractValue()` to expand `valueArray` into a string-keyed object map for DataContext compatibility. (Agent: Android Expert, commit: `ba84ab0`)

### Tests
- Added `DataModelDtoTest.kt` (5 unit tests) covering `buildDataModelJson()` with `valueArray`, scalar regression, and edge cases.
- Added `TestArrayEncoding` class in `test_agent.py` (3 unit tests) covering `encode_array_entry()` format, field preservation, and empty list.

### Retro
- ✅ What worked: Parallel Python + Android workstreams landed independently (`ba84ab0`, `f663846`). GPT-4.1 model fallback recovered from Claude rate-limit within minutes. Smoke test confirmed clean implementation — 58/58 pytest, 30 Kotlin tests passed, zero regression.
- ⚠️ What didn't: Python Expert (Claude) rate-limited mid-session; recovered by relaunching with GPT-4.1. Integration test check 5 false-failed — asserted all 3 templates must emit `valueArray`, but `account_balances` is all-scalar data (`literalString` bindings, no arrays).
- 🔧 Improvement applied: Added "Template Data Structure Awareness" section to `.github/agents/tester.agent.md` with a per-template data shape table and assertion rules. Prevents false-fail assertions on scalar-only templates.

---

## [v0.9.2] — 2026-04-10

### Fixed
- **List widget item-probe — JsonObject/Array detection**: The `financialListWidget` item-probe loop was using `dataContext.getString()` wrapped in a try/catch to detect whether an item existed at a given index path. `getString()` throws `IllegalArgumentException` for any non-primitive value (JsonObject or JsonArray), and the try/catch was fragile — catching exceptions that in other contexts signal programming errors. The probe is now fully non-throwing via a 3-branch check:
  `getObjectKeys() != null || getArraySize() != null || getString() != null`
  covering object-valued items (transaction rows), array-valued items, and primitive items respectively. Only a `null` result on all three branches indicates a missing/out-of-bounds index. (Agent: Android Expert, commits: `f45216c`, `e97417a`)

### Tests
- `ListProbeTest.kt`: expanded from 6 → 13 tests; new cases cover JsonArray-valued items, mixed-type lists, single-item lists, empty lists, and short-circuit branch isolation.
- Full suite: 154 tests passing (96 Android unit + 58 Python).

### Retro
- ✅ What worked: Incremental review catching the JsonArray edge case before release; parallel code-review + test execution kept turnaround tight.
- ⚠️ What didn't: Initial fix only handled JsonObject paths — JsonArray branch was missed on first pass, requiring a second review cycle.
- 🔧 Improvement applied: None needed — review process worked as designed by catching the gap.
