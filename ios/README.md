# A2UI Sample Chat App — iOS

An iOS SwiftUI port of the Android A2UI chat app. It streams AI responses that include both plain text and dynamic financial UI cards, rendered via the A2UI protocol.

## Architecture

```
ChatViewModel → ChatRepository (Real/Mock) → SSE stream → SurfaceStateManager → UiDefinition → A2UISurface → FinancialCatalog
```

- **ChatViewModel** orchestrates messages, streaming state, and event dispatch.
- **RealChatRepository** connects to the Python agent via SSE (Server-Sent Events).
- **MockChatRepository** provides offline responses for development without a running server.
- **SurfaceStateManager** accumulates streaming A2UI protocol operations and builds a `UiDefinition`.
- **A2UISurface** recursively renders `UiDefinition` components using **FinancialCatalog**.
- **FinancialCatalog** provides Fidelity-style financial widget overrides (Text, Row, Column, Card, Button, TextField, List, Divider).

## Generating the Xcode Project

### Option 1: xcodegen (recommended)

```bash
brew install xcodegen
cd /path/to/a2ui-sample-chat-app
xcodegen generate
open A2UIChatApp.xcodeproj
```

### Option 2: Manual

1. Create a new Xcode project (iOS App, SwiftUI interface, Swift language).
2. Delete the generated files.
3. Add all `.swift` files from `A2UIChatApp/` to the project target.
4. Add `A2UIChatApp/Info.plist` and set it as the Info.plist in Build Settings.

## Running the Python Agent

```bash
cd agent
pip install -r requirements.txt
cp .env.example .env   # Add GITHUB_TOKEN or GITHUB_MODELS_TOKEN
python agent.py        # Starts at http://localhost:8000
```

The iOS simulator reaches localhost directly (unlike Android emulators which use `10.0.2.2`).

## Switching Between Real Agent and Mock

In `A2UIChatApp/Presentation/ViewModels/ChatViewModel.swift`:

```swift
static let USE_REAL_AGENT = true   // false = MockChatRepository (no server needed)
```

Set to `false` for offline development with pre-built mock financial cards.

## A2UI Protocol (SSE event types)

The agent streams these SSE event types in order:

| Event | Description |
|---|---|
| `text` | Plain summary text shown above the card |
| `a2ui_op: beginRendering` | Declares surface ID and root component ID |
| `a2ui_op: dataModelUpdate` | Populates DataContext with label strings and initial values |
| `a2ui_op: surfaceUpdate` | Incrementally adds components (may arrive in multiple chunks) |
| `done` | Signals completion |
