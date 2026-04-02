## Jetpack Compose
For Compose/Android UI work, follow the skill instructions in
`jetpack-compose-expert-skill/SKILL.md`. Consult reference files in
`jetpack-compose-expert-skill/references/` for patterns, pitfalls,
and source-code-backed guidance.

## Android Kotlin
For Android Kotlin work, follow the skill instructions in
`android-skill/SKILL.md`.

---

# A2UI Sample Chat App — Copilot Instructions

## Build Commands

```bash
# Required before every Gradle call on this machine:
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# Compile check (fast — catches all Kotlin errors without full APK):
./gradlew :app:compileDebugKotlin --no-daemon -q

# Full debug build:
./gradlew :app:assembleDebug --no-daemon
```

Android UI tests can be run with `./run_ui_tests.sh` (requires a running emulator).

## Agent Server (Python)

```bash
cd agent
pip install -r requirements.txt
cp .env.example .env   # add GITHUB_TOKEN or GITHUB_MODELS_TOKEN

python agent.py        # starts at http://localhost:8000
```

## Template Agent Server (Python — Deterministic)

```bash
cd agent-templates
pip install -r requirements.txt

python template_agent.py   # starts at http://localhost:8000
```

The template agent is a deterministic alternative to the LLM agent. It uses pre-approved JSON templates and mock data instead of LLM inference. Same SSE protocol, same A2UI operations format, no API keys needed.

## Architecture Overview

This is a two-part system with two backend modes:

**Android app** — Jetpack Compose chat UI that receives AI responses containing both plain text and A2UI protocol operations (SSE stream). A2UI operations are accumulated by `SurfaceStateManager`, which builds a `UiDefinition` rendered by `A2UISurface` using `FinancialCatalog`.

**Python agent** (`agent/agent.py`) — FastAPI server that calls an LLM (GitHub Copilot SDK or GitHub Models API) using a system prompt (`system_prompt.py`) that instructs it to respond with A2UI protocol operations. The agent streams SSE events back to the app.

**Python template agent** (`agent-templates/template_agent.py`) — Deterministic FastAPI server that classifies user intent via keywords, renders pre-approved A2UI templates with mock data, and streams the same SSE protocol. No LLM dependency. Instant responses.

**iOS app** (`ios/A2UIChatApp/`) — SwiftUI port of the Android chat UI. Same architecture (MVVM + Clean), same A2UI protocol consumption. Uses `127.0.0.1:8000` (simulator reaches localhost directly, unlike Android's `10.0.2.2`). Built with XcodeGen (`project.yml`).

### Data flow

```
User types → ChatViewModel.sendMessage()
           → RealChatRepository.sendMessageStream()  [SSE to :8000/chat/stream]
           → StreamEvent.A2UiOp  →  SurfaceStateManager.processOperation()
           → StreamEvent.Done    →  surfaceManager.buildUiDefinition()
                                 →  MessageBubble → A2UISurface(catalog = FinancialCatalog)
```

User interactions (button taps, text input) fire as `UiEvent` → `ChatViewModel.onEvent()` → `RealChatRepository` posts to `:8000/event`.

### A2UI Protocol (SSE event types)

The agent streams these SSE event types in order:
- `text` — plain summary text shown above the card
- `a2ui_op: beginRendering` — declares surface ID and root component ID
- `a2ui_op: dataModelUpdate` — populates `DataContext` with label strings and initial values
- `a2ui_op: surfaceUpdate` — incrementally adds components (may arrive in multiple chunks)
- `done` — signals completion

### Android module structure

```
app/src/main/java/com/example/a2ui/chat/
  data/
    a2ui/
      FinancialCatalog.kt    ← ALL widget overrides — primary file for UI changes
      SurfaceStateManager.kt ← accumulates streaming A2UI protocol ops
    repository/
      RealChatRepository.kt  ← SSE streaming, event posting (10.0.2.2:8000)
      MockChatRepository.kt  ← offline mock for testing without agent
  domain/model/Message.kt    ← holds UiDefinition + dataModelJson alongside text
  presentation/
    ChatViewModel.kt         ← USE_REAL_AGENT flag, streaming state machine
    components/
      MessageBubble.kt       ← wires FinancialCatalog into A2UISurface
  theme/
    Color.kt                 ← all color tokens
```

### Template agent module structure

```
agent-templates/
  template_agent.py        ← FastAPI server, SSE streaming, intent → template → A2UI ops
  intent_router.py         ← Keyword-based intent classification (no LLM)
  template_renderer.py     ← Loads templates + data, caches, placeholder substitution
  a2ui_transform.py        ← Reusable transform pipeline (expand, path bindings, sanitize, chunk)
  templates/               ← Pre-approved A2UI JSON templates
    account_balances.json
    brokerage_activity.json
    transaction_history.json
  data/                    ← Mock data files
    account_balances.json
    brokerage_activity.json
    transaction_history.json
```

### iOS module structure

```
ios/A2UIChatApp/
  A2UIChatApp.swift              ← @main entry point
  ContentView.swift              ← Root view, creates ChatViewModel
  Data/
    A2UI/
      FinancialCatalog.swift     ← Widget renderers (closure-based WidgetRenderer typealias)
      SurfaceStateManager.swift  ← Accumulates streaming A2UI ops → UiDefinition
      A2UISurface.swift          ← Recursive component renderer
      DataContext.swift           ← Path-based data resolution
    Repository/
      RealChatRepository.swift   ← SSE streaming to 127.0.0.1:8000
      MockChatRepository.swift   ← Offline mock with keyword routing
  Domain/
    Models/Message.swift         ← Message, Sender, StreamEvent
    Repository/ChatRepository.swift ← Protocol (interface)
  Presentation/
    ViewModels/ChatViewModel.swift  ← USE_REAL_AGENT flag, @MainActor
    Components/                  ← ChatInputBar, MessageBubble, FeedbackBar, etc.
    Screens/ChatScreen.swift
  Theme/AppColors.swift          ← Color tokens
```

## Key Conventions

### FinancialCatalog — the central override file

`FinancialCatalog.kt` overrides A2UI's `CoreCatalog` to render with a Fidelity-style financial UX. **All widget UI changes go here.** The pattern:

```kotlin
private val financialFooWidget = CatalogItem(name = "Foo") { componentId, data, buildChild, dataContext, onEvent ->
    // read data with DataReferenceParser:
    val labelRef = DataReferenceParser.parseString(data["label"])
    val label = when (labelRef) {
        is LiteralString -> labelRef.value
        is PathString    -> dataContext.getString(labelRef.path) ?: ""
        else             -> ""
    }
    // render Composable
}

// Registration — rightmost catalog wins; ORDER of val declarations matters
val FinancialCatalog: Catalog = CoreCatalog + Catalog.of(
    "financial",
    financialFooWidget,
    // ...
)
```

**Critical initialization order**: all `private val` widget declarations must appear before `val FinancialCatalog` in the file. Kotlin top-level vals initialize in declaration order.

### Row ↔ Text accent bar signaling

Transaction rows use two `CompositionLocal`s to communicate across the composition tree without prop drilling:

- `LocalAccentColorSink` — a `(Color) -> Unit` lambda written by the amount `Text` via `SideEffect` to color the left accent bar in its parent `Row`. Initial color is `Color.Transparent` (header rows stay uncolored).
- `LocalBodyEmphasis` — a `Boolean` that upgrades `body`-hinted `Text` to `SemiBold` inside transaction rows.

The outer `Row` uses `IntrinsicSize.Min` so the accent bar's `fillMaxHeight()` works correctly.

### TextField form data binding

TextFields **must** include an explicit `text: { path: "..." }` binding that exactly matches the path referenced in the button's `actions` context array. The Android client reads `text.path` first and uses it for both reading initial values from DataContext and writing updated values on each keystroke.

Example — server TextField definition:
```json
{"TextField": {"placeholder": {"literalString": "First name"}, "textFieldType": "text", "text": {"path": "/fields/first_field/value"}}}
```
Button context:
```json
{"key": "first_name", "path": "/fields/first_field/value"}
```

Without the explicit `text` binding, the client falls back to `/{componentId}/value`, which will NOT match if the server nests fields under a parent container component. A `WARN` log from tag `FinancialCatalog` will fire in this case.

Values are seeded into DataContext via `LaunchedEffect` on composition so the button always has values to read, even for untouched fields. Button context entries use the flat format `{"key": "X", "path": "/Y"}`.

### Button style detection

The server sends `style: "filled"` / `style: "outlined"` (not `primary: true/false`). The button override checks `style` first and falls back to the `primary` bool for backward compatibility.

### Column spacing token

The `Column` override parses an optional `spacing` string property:
- `"form"` → 16dp (between form field groups)
- `"fieldGroup"` → 4dp (between a label Text and its TextField)
- omitted → 2dp (default, used for transaction row label→metadata)

### Switching between real agent and mock

In `ChatViewModel.kt`:
```kotlin
private const val USE_REAL_AGENT = true   // false = MockChatRepository (no server needed)
```

### Android emulator networking

The agent runs on the host at port 8000. Emulator reaches it via `10.0.2.2:8000` (hardcoded in `RealChatRepository`). Physical devices need the host's LAN IP.

### Logcat tags

| Tag | Source |
|---|---|
| `A2UI.VM` | ChatViewModel |
| `A2UI.Repo` | RealChatRepository |
| `A2UI.Surface` | SurfaceStateManager |
| `FinancialCatalog` | Widget overrides (TextField seed/change, Button context) |
 