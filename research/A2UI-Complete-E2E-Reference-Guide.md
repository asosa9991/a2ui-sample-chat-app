# A2UI (Agent-to-UI) — Complete End-to-End Reference Guide

> **Protocol Version:** v0.8 (stable) | v0.9 (preview)
> **Last Updated:** 2026-04-08
> **Repository:** https://github.com/google/A2UI
> **Specification:** https://a2ui.org/specification/v0.8-a2ui/
> **License:** Apache 2.0

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [What is A2UI?](#2-what-is-a2ui)
   - 2.1 [Design Philosophy](#21-design-philosophy)
   - 2.2 [Key Concepts](#22-key-concepts)
   - 2.3 [Protocol vs Library](#23-protocol-vs-library)
3. [System Architecture](#3-system-architecture)
   - 3.1 [High-Level Architecture](#31-high-level-architecture)
   - 3.2 [Repository Structure](#32-repository-structure)
   - 3.3 [Component Overview](#33-component-overview)
   - 3.4 [This Project's Architecture](#34-this-projects-architecture)
4. [A2UI Protocol Specification](#4-a2ui-protocol-specification)
   - 4.1 [Component Model](#41-component-model)
   - 4.2 [Operation Types (v0.8)](#42-operation-types-v08)
   - 4.3 [Operation Sequence and Lifecycle](#43-operation-sequence-and-lifecycle)
   - 4.4 [SSE Event Stream Format](#44-sse-event-stream-format)
   - 4.5 [v0.9 Protocol Changes](#45-v09-protocol-changes)
   - 4.6 [v0.8 → v0.9 Migration](#46-v08--v09-migration)
5. [Widget Catalog](#5-widget-catalog)
   - 5.1 [Content Widgets](#51-content-widgets)
   - 5.2 [Layout Widgets](#52-layout-widgets)
   - 5.3 [Interactive Widgets](#53-interactive-widgets)
   - 5.4 [Widget JSON Examples](#54-widget-json-examples)
6. [Data Binding System](#6-data-binding-system)
   - 6.1 [DataReference Types](#61-datareference-types)
   - 6.2 [JSON Pointer Paths](#62-json-pointer-paths)
   - 6.3 [DataModel and DataContext](#63-datamodel-and-datacontext)
   - 6.4 [Template Lists and Scoped Contexts](#64-template-lists-and-scoped-contexts)
7. [Event System](#7-event-system)
   - 7.1 [UserActionEvent](#71-useractionevent)
   - 7.2 [DataChangeEvent](#72-datachangeevent)
   - 7.3 [Button Action Context Resolution](#73-button-action-context-resolution)
   - 7.4 [Event Flow Back to Agent](#74-event-flow-back-to-agent)
8. [E2E Setup Guide](#8-e2e-setup-guide)
   - 8.1 [Prerequisites](#81-prerequisites)
   - 8.2 [Clone and Explore (google/A2UI)](#82-clone-and-explore-googlea2ui)
   - 8.3 [Python Agent Setup (LLM-backed)](#83-python-agent-setup-llm-backed)
   - 8.4 [Python Template Agent Setup (Deterministic)](#84-python-template-agent-setup-deterministic)
   - 8.5 [Android Client Setup](#85-android-client-setup)
   - 8.6 [iOS Client Setup](#86-ios-client-setup)
   - 8.7 [Running the Full System](#87-running-the-full-system)
   - 8.8 [Testing and Validation](#88-testing-and-validation)
9. [SDK Reference](#9-sdk-reference)
   - 9.1 [Kotlin/Android SDK (com.contextable:a2ui-4k)](#91-kotlinandroid-sdk-comcontextablea2ui-4k)
   - 9.2 [Python Agent SDK (a2ui-agent)](#92-python-agent-sdk-a2ui-agent)
   - 9.3 [TypeScript/Web Renderers](#93-typescriptweb-renderers)
10. [Agent Implementation Guide](#10-agent-implementation-guide)
    - 10.1 [System Prompt Engineering](#101-system-prompt-engineering)
    - 10.2 [LLM-Backed Agent Pattern](#102-llm-backed-agent-pattern)
    - 10.3 [Deterministic Template Agent Pattern](#103-deterministic-template-agent-pattern)
    - 10.4 [Dual-Runner Fallback Pattern](#104-dual-runner-fallback-pattern)
    - 10.5 [Schema Validation and Retry](#105-schema-validation-and-retry)
    - 10.6 [The Transformation Pipeline](#106-the-transformation-pipeline)
11. [Custom Widget Development](#11-custom-widget-development)
    - 11.1 [CatalogItem Pattern](#111-catalogitem-pattern)
    - 11.2 [Catalog Composition](#112-catalog-composition)
    - 11.3 [FinancialCatalog Example (This Project)](#113-financialcatalog-example-this-project)
12. [Multi-Surface and Multi-Agent Patterns](#12-multi-surface-and-multi-agent-patterns)
13. [Security and Production Considerations](#13-security-and-production-considerations)
14. [Version History](#14-version-history)
15. [Key Repositories Summary](#15-key-repositories-summary)
16. [Confidence Assessment](#16-confidence-assessment)
- [Footnotes](#footnotes)

---

## 1. Executive Summary

A2UI (Agent-to-UI) is Google's open standard for enabling AI agents to generate rich, native UI experiences across any platform without writing platform-specific code. Rather than producing HTML, Markdown, or raw JSON for frontends to interpret arbitrarily, agents emit **declarative JSON operations** describing a UI surface — components, layout, and data bindings — which client-side renderers transform into native widgets.

This reference guide covers the full end-to-end picture:

- **The protocol** — the four core operations (`beginRendering`, `surfaceUpdate`, `dataModelUpdate`, `deleteSurface`) and how they compose into a surface
- **The widget catalog** — 18 standard widgets covering content, layout, and interaction
- **The data binding system** — reactive `DataModel` backed by JSON Pointer paths and `MutableStateFlow`
- **The event system** — `UserActionEvent` and `DataChangeEvent` flowing back to the agent
- **SDK implementations** — Kotlin/Android (`com.contextable:a2ui-4k:0.8.2`) and Python (`a2ui-agent`)
- **This project's specific architecture** — dual-agent system (LLM-backed + deterministic template agent), Android + iOS clients, and the complete data flow from SSE stream to rendered Compose UI

Key numbers: 13,697 GitHub stars, Apache 2.0 license, v0.8 stable / v0.9 in active development, 18 standard widgets, 40% token reduction from v0.8 → v0.9.

---

## 2. What is A2UI?

### 2.1 Design Philosophy

Traditional approaches to AI-generated UI suffer from three core problems:

1. **Fragmentation** — agents output HTML for web, but native apps need different representations
2. **Parsing ambiguity** — Markdown and prose require heuristic parsing on the client side
3. **Platform lock-in** — any code the agent generates is tightly coupled to one runtime

A2UI solves this with a single insight: **separate the UI description from the rendering**. The agent emits a platform-neutral *description* of the UI (components, layout, data). The client SDK takes that description and renders it natively — Jetpack Compose on Android, SwiftUI on iOS, React on web, Flutter cross-platform.

The result is that the same agent response renders as a native Material 3 `Card` on Android, a native `SwiftUI.RoundedRectangle` on iOS, and a React `Paper` component on web — all from a single JSON payload.

### 2.2 Key Concepts

| Concept | Definition |
|---------|-----------|
| **Surface** | A named rendering canvas (`surfaceId`). One chat message typically has one surface. |
| **Component** | A named UI element identified by an `id` string, paired with a widget type and its configuration. |
| **UiDefinition** | The complete tree of components accumulated from all `surfaceUpdate` operations for a surface. |
| **DataModel** | A reactive JSON object (`MutableStateFlow<JsonObject>`) holding all dynamic values for a surface. |
| **Catalog** | The registry of known widget types that a client can render. |
| **DataReference** | A typed union value: either a literal (`"Hello"`) or a JSON Pointer path binding (`"/user/name"`). |
| **Operation** | A single JSON message that mutates a surface: `beginRendering`, `surfaceUpdate`, `dataModelUpdate`, or `deleteSurface`. |

### 2.3 Protocol vs Library

A2UI is simultaneously a **protocol specification** and a set of **SDK implementations**:

```
Protocol Layer (platform-neutral JSON spec)
    ├── Operation types and schemas
    ├── Widget catalog specification
    ├── Data binding rules
    └── Event contract

SDK Layer (language-specific)
    ├── Kotlin/Android: com.contextable:a2ui-4k (Kotlin Multiplatform)
    ├── Python: a2ui-agent (agent-side)
    ├── TypeScript: renderers/react, renderers/lit, renderers/angular
    └── Dart/Flutter: renderers/flutter
```

The protocol specification (https://a2ui.org/specification/v0.8-a2ui/) defines the JSON schemas. SDKs implement the client-side renderer and server-side generator — but you can implement either side manually as long as you produce valid A2UI JSON.

---

## 3. System Architecture

### 3.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         AI AGENT (Server Side)                      │
│                                                                     │
│  ┌──────────────┐    ┌──────────────┐    ┌─────────────────────┐  │
│  │  LLM Engine  │    │  a2ui-agent  │    │  Schema Validator   │  │
│  │  (Gemini /   │───▶│  Python SDK  │───▶│  + Retry Logic      │  │
│  │   Copilot)   │    │              │    │                     │  │
│  └──────────────┘    └──────┬───────┘    └─────────┬───────────┘  │
│                             │                      │               │
│                    ┌────────▼──────────────────────▼────────────┐  │
│                    │         Transformation Pipeline             │  │
│                    │  expand → bind → sanitize → chunk → emit   │  │
│                    └────────────────────┬───────────────────────┘  │
└────────────────────────────────────────┼────────────────────────────┘
                                         │ SSE / A2A Transport
                         ┌───────────────▼───────────────┐
                         │       Event Stream (SSE)       │
                         │  event: text                   │
                         │  event: a2ui_op               │
                         │  event: a2ui_op               │
                         │  event: done                   │
                         └──────────────┬────────────────┘
                                        │
              ┌─────────────────────────┼──────────────────────────┐
              │                         │                          │
   ┌──────────▼──────────┐  ┌──────────▼──────────┐  ┌──────────▼──────────┐
   │   Android Client    │  │    iOS Client        │  │    Web Client        │
   │                     │  │                      │  │                      │
   │  SurfaceStateManager│  │  SurfaceStateManager │  │  SurfaceStateManager │
   │  FinancialCatalog   │  │  FinancialCatalog    │  │  (React/Lit/Angular) │
   │  A2UISurface        │  │  A2UISurface         │  │                      │
   │  (Jetpack Compose)  │  │  (SwiftUI)           │  │  (DOM / Shadow DOM)  │
   └─────────────────────┘  └──────────────────────┘  └──────────────────────┘
              │                                                     │
              └─────────────────────────────────────────────────────┘
                              UiEvent (UserActionEvent / DataChangeEvent)
                              POST /event → Agent
```

### 3.2 Repository Structure

The canonical `google/A2UI` repository is organized as follows:

```
google/A2UI/
├── agent_sdks/
│   └── python/                    # a2ui-agent Python SDK
│       ├── pyproject.toml
│       └── src/a2ui/
│           ├── adk/               # ADK integration (A2uiExtension)
│           ├── schema_manager.py  # JSON Schema validation
│           └── assets/            # A2UI specification schemas
├── docs/                          # mkdocs documentation site
├── renderers/
│   ├── angular/                   # Angular renderer
│   ├── flutter/                   # Flutter/Dart renderer
│   ├── lit/                       # Lit (Web Components) renderer
│   └── react/                     # React renderer
├── samples/
│   ├── agent/
│   │   ├── adk/                   # ADK-based agent samples
│   │   │   ├── contact_lookup/
│   │   │   ├── contact_multiple_surfaces/
│   │   │   ├── mcp_app_proxy/
│   │   │   ├── orchestrator/
│   │   │   ├── restaurant_finder/
│   │   │   ├── rizzcharts/
│   │   │   └── migrate_v08_to_v09.py
│   │   └── mcp/
│   ├── client/
│   │   ├── angular/
│   │   ├── lit/
│   │   ├── react/
│   │   └── shared/
│   └── personalized_learning/
├── specification/                 # A2UI protocol specification
└── tools/                         # Development utilities
```

### 3.3 Component Overview

| Layer | Component | Role |
|-------|-----------|------|
| **Agent SDK** | `a2ui-agent` (Python) | Injects A2UI schema into LLM prompts; validates LLM output |
| **Transport** | SSE / A2A | Streams operations from agent to client in real time |
| **Client SDK** | `a2ui-4k` (Kotlin/Multiplatform) | Parses operations, maintains `SurfaceStateManager`, renders via Compose |
| **Catalog** | `CoreCatalog` / custom | Maps widget type names → Composable renderers |
| **DataModel** | `MutableStateFlow<JsonObject>` | Reactive state; Compose auto-recomposes on changes |
| **Event Bus** | `UiEvent` sealed class | Carries user interactions from renderer back to agent |

### 3.4 This Project's Architecture

This project implements A2UI across three components: an LLM-backed Python agent, a deterministic template agent, and dual mobile clients (Android + iOS).

```
┌──────────────────────────────────────────────────────────────────┐
│                      THIS PROJECT                                │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │              PYTHON BACKEND  (:8000)                    │    │
│  │                                                         │    │
│  │  ┌─────────────────────┐  ┌────────────────────────┐   │    │
│  │  │  LLM Agent          │  │  Template Agent         │   │    │
│  │  │  agent/agent.py     │  │  agent-templates/       │   │    │
│  │  │                     │  │  template_agent.py      │   │    │
│  │  │  GitHub Copilot SDK │  │                         │   │    │
│  │  │  (Claude Sonnet 4.6)│  │  intent_router.py       │   │    │
│  │  │                     │  │  template_renderer.py   │   │    │
│  │  │  5-Stage Pipeline:  │  │  a2ui_transform.py      │   │    │
│  │  │  1. System Prompt   │  │                         │   │    │
│  │  │  2. LLM Call        │  │  Pre-approved templates  │   │    │
│  │  │  3. Validation      │  │  + Mock data            │   │    │
│  │  │  4. Transform       │  │  (NO LLM required)      │   │    │
│  │  │  5. SSE Stream      │  │                         │   │    │
│  │  └──────────┬──────────┘  └──────────┬─────────────┘   │    │
│  │             │                        │                  │    │
│  │             └────────────┬───────────┘                  │    │
│  │                          │ SSE  /chat/stream             │    │
│  └──────────────────────────┼──────────────────────────────┘    │
│                             │                                    │
│          ┌──────────────────┼──────────────────┐                │
│          │                  │                  │                │
│  ┌───────▼────────┐         │        ┌─────────▼──────────┐    │
│  │ Android Client │         │        │    iOS Client       │    │
│  │ (Compose)      │         │        │    (SwiftUI)        │    │
│  │ 10.0.2.2:8000  │         │        │    127.0.0.1:8000   │    │
│  │                │         │        │                     │    │
│  │ ChatViewModel  │         │        │  ChatViewModel      │    │
│  │ FinancialCatalog│        │        │  FinancialCatalog   │    │
│  │ A2UISurface    │         │        │  A2UISurface        │    │
│  └───────┬────────┘         │        └────────┬────────────┘    │
│          │                  │                 │                  │
│          └──────────────────▼─────────────────┘                 │
│                    POST /event (UiEvents)                        │
└──────────────────────────────────────────────────────────────────┘
```

**Android Module Structure:**

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

**iOS Module Structure:**

```
ios/A2UIChatApp/
  A2UIChatApp.swift              ← @main entry point
  Data/
    A2UI/
      FinancialCatalog.swift     ← Widget renderers
      SurfaceStateManager.swift  ← Accumulates streaming A2UI ops → UiDefinition
      A2UISurface.swift          ← Recursive component renderer
      DataContext.swift           ← Path-based data resolution
    Repository/
      RealChatRepository.swift   ← SSE streaming to 127.0.0.1:8000
      MockChatRepository.swift   ← Offline mock
  Domain/
    Models/Message.swift
    Repository/ChatRepository.swift
  Presentation/
    ViewModels/ChatViewModel.swift
    Screens/ChatScreen.swift
  Theme/AppColors.swift
```

---

## 4. A2UI Protocol Specification

### 4.1 Component Model

Every A2UI surface is built from a flat list of **components**, each with a unique `id`. Components reference other components by ID (not by nesting), enabling the flat JSON to describe an arbitrarily deep tree.

```
UiDefinition
  ├── surfaceId: String         — unique name for this surface
  ├── rootComponentId: String  — the top-level component to render
  └── components: Map<String, ComponentDefinition>
        └── ComponentDefinition
              ├── id: String               — unique component identifier
              └── component: JsonObject    — widget type + configuration
```

**Component JSON structure (v0.8):**

```json
{
  "id": "card_title",
  "component": {
    "Text": {
      "text": {"literalString": "Account Summary"},
      "usageHint": "h2"
    }
  }
}
```

The outer key of `component` is the **widget type name** (e.g., `"Text"`, `"Column"`, `"Button"`). The value is the widget's configuration object.

### 4.2 Operation Types (v0.8)

There are four operations in v0.8. They must be applied to the client's `SurfaceStateManager` in the order they arrive.

---

#### `beginRendering` — Initialize a Surface

This **must be the first operation** for any surface. It declares the surface ID, which component is the root, and which catalog the client should use.

```json
{
  "beginRendering": {
    "surfaceId": "account_view",
    "root": "root_container",
    "catalogId": "https://a2ui.org/specification/v0_8/basic_catalog.json"
  }
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `surfaceId` | ✅ | Unique identifier for this rendering surface |
| `root` | ✅ | Component ID of the root node in the component tree |
| `catalogId` | ✅ | URL of the component catalog the client must support |

> **Note:** Clients may validate `catalogId` against their supported catalogs and reject surfaces for unsupported catalogs.

---

#### `surfaceUpdate` — Add or Replace Components

Adds new components to the surface's component registry, or replaces existing ones (by ID). Multiple `surfaceUpdate` operations can be sent to build the surface incrementally.

```json
{
  "surfaceUpdate": {
    "surfaceId": "account_view",
    "components": [
      {
        "id": "root_container",
        "component": {
          "Column": {
            "children": {"explicitList": ["account_card", "transactions_card"]}
          }
        }
      },
      {
        "id": "account_card",
        "component": {
          "Card": {
            "child": "card_content"
          }
        }
      },
      {
        "id": "card_content",
        "component": {
          "Column": {
            "children": {"explicitList": ["account_name_text", "balance_text"]}
          }
        }
      },
      {
        "id": "account_name_text",
        "component": {
          "Text": {
            "text": {"path": "/accountName"},
            "usageHint": "h3"
          }
        }
      },
      {
        "id": "balance_text",
        "component": {
          "Text": {
            "text": {"path": "/balance"},
            "usageHint": "body"
          }
        }
      }
    ]
  }
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `surfaceId` | ✅ | Must match a surface previously initialized with `beginRendering` |
| `components` | ✅ | Array of component definitions to add/replace |

---

#### `dataModelUpdate` — Set Reactive State

Populates the DataModel for a surface. The DataModel is a reactive JSON object; components bound via `{"path": "/field"}` DataReferences automatically re-render when data changes.

```json
{
  "dataModelUpdate": {
    "surfaceId": "account_view",
    "path": "/",
    "contents": [
      {"key": "accountName", "valueString": "Fidelity ••••1234"},
      {"key": "balance", "valueString": "$48,291.73"},
      {"key": "rating", "valueDouble": 4.5},
      {"key": "isActive", "valueBoolean": true}
    ]
  }
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `surfaceId` | ✅ | Target surface |
| `path` | ✅ | JSON Pointer root (typically `"/"` or `""` for top-level) |
| `contents` | ✅ | Array of key/value pairs with typed values |

**Typed value fields:**

| Field | Type |
|-------|------|
| `valueString` | String |
| `valueDouble` | Float/Double |
| `valueBoolean` | Boolean |
| `valueArray` | Array (for template list data) |

---

#### `deleteSurface` — Tear Down a Surface

Removes all state associated with a surface from the client. After this, the client should no longer render the surface.

```json
{
  "deleteSurface": {
    "surfaceId": "account_view"
  }
}
```

---

### 4.3 Operation Sequence and Lifecycle

A complete A2UI interaction follows this strict ordering:

```
1. beginRendering       ← Must be first; initializes surface state
2. dataModelUpdate      ← Best sent before surfaceUpdate so bindings resolve immediately
3. surfaceUpdate        ← Can be sent in multiple batches (chunking)
4. surfaceUpdate        ← Additional batches if needed
5. [done signal]        ← Client finalizes rendering (in SSE: event: done)

Later (optional):
6. dataModelUpdate      ← Update reactive data without re-rendering structure
7. surfaceUpdate        ← Add or replace individual components
8. deleteSurface        ← Clean up when surface is no longer needed
```

> **Chunking:** The server may split a large number of components across multiple `surfaceUpdate` operations (e.g., 15 components per batch). The client accumulates all batches and renders the complete surface after the `done` signal.

### 4.4 SSE Event Stream Format

This project uses **Server-Sent Events (SSE)** to stream A2UI operations from the agent to the client. Each SSE event has a named `event` type and a JSON `data` payload.

```
event: text
data: {"text": "Here are your account balances for Q1:"}

event: a2ui_op
data: {"beginRendering": {"surfaceId": "response_abc123", "root": "root", "catalogId": "https://a2ui.org/specification/v0_8/basic_catalog.json"}}

event: a2ui_op
data: {"dataModelUpdate": {"surfaceId": "response_abc123", "path": "/", "contents": [{"key": "accountName", "valueString": "Fidelity ••••1234"}, {"key": "balance", "valueString": "$48,291.73"}]}}

event: a2ui_op
data: {"surfaceUpdate": {"surfaceId": "response_abc123", "components": [{"id": "root", "component": {"Column": {"children": {"explicitList": ["header_card"]}}}}, {"id": "header_card", "component": {"Card": {"child": "header_col"}}}]}}

event: a2ui_op
data: {"surfaceUpdate": {"surfaceId": "response_abc123", "components": [{"id": "header_col", "component": {"Column": {"children": {"explicitList": ["name_text", "balance_text"]}}}}]}}

event: done
data: {}
```

**SSE Event Types:**

| Event Type | Purpose |
|-----------|---------|
| `text` | Plain text content to display alongside or before the UI |
| `a2ui_op` | A single A2UI operation (`beginRendering`, `surfaceUpdate`, `dataModelUpdate`, `deleteSurface`) |
| `done` | Signals end of stream; client finalizes rendering |

**Client-side handling (Kotlin pseudocode):**

```kotlin
sseClient.collect { event ->
    when (event.type) {
        "text" -> appendTextMessage(event.data)
        "a2ui_op" -> surfaceStateManager.processOperation(parseJson(event.data))
        "done" -> finalizeRendering(surfaceStateManager.buildUiDefinition())
    }
}
```

### 4.5 v0.9 Protocol Changes

v0.9 is a significant simplification of the protocol. The key changes:

**1. Renamed operations:**

| v0.8 | v0.9 | Notes |
|------|------|-------|
| `beginRendering` | `createSurface` | Adds `version: "v0.9"` field |
| `surfaceUpdate` | `updateComponents` | Same semantics |
| `dataModelUpdate` | `updateDataModel` | Data format changed |
| `deleteSurface` | `deleteSurface` | Unchanged |

**2. Flat component format (removes nested widget key):**

v0.8 (verbose, nested):
```json
{
  "id": "title",
  "component": {
    "Text": {
      "text": {"literalString": "Hello World"},
      "usageHint": "h1"
    }
  }
}
```

v0.9 (flat, concise):
```json
{
  "id": "title",
  "component": "Text",
  "text": "Hello World",
  "variant": "headline"
}
```

**3. Native JSON DataModel (removes typed value wrapper):**

v0.8:
```json
{
  "dataModelUpdate": {
    "surfaceId": "default",
    "path": "/",
    "contents": [
      {"key": "name", "valueString": "Golden Dragon"},
      {"key": "rating", "valueDouble": 4.5}
    ]
  }
}
```

v0.9:
```json
{
  "updateDataModel": {
    "surfaceId": "default",
    "path": "/",
    "value": {
      "name": "Golden Dragon",
      "rating": 4.5
    }
  }
}
```

**4. Template-driven lists with path binding:**

```json
{
  "id": "item-list",
  "component": "List",
  "direction": "vertical",
  "children": {
    "componentId": "item-card-template",
    "path": "/items"
  }
}
```

**Token efficiency:** v0.9 reduces average payload size by ~40% compared to v0.8, which translates directly to lower LLM costs and faster generation.

### 4.6 v0.8 → v0.9 Migration

The `google/A2UI` repository provides an automated migration script:

```
samples/agent/adk/migrate_v08_to_v09.py
```

Key migration steps for agent-side code:

1. Replace `beginRendering` with `createSurface` + add `"version": "v0.9"`
2. Replace `surfaceUpdate` with `updateComponents`
3. Replace `dataModelUpdate` with `updateDataModel` using native JSON values
4. Flatten component definitions (remove the nested widget-type key)
5. Replace `usageHint` with `variant` for Text widgets
6. Update Python SDK import: `from a2ui.adk.extension import A2uiExtension` (unchanged), update `version="0.9"` in `A2uiSchemaManager`

---

## 5. Widget Catalog

The standard A2UI catalog defines 18 widgets across three categories. Widget names are **case-sensitive** (PascalCase).

### 5.1 Content Widgets

| Widget | Key Properties | Notes |
|--------|---------------|-------|
| `Text` | `text` (BoundValue), `usageHint` (h1/h2/h3/h4/h5/body/caption) | Supports basic markdown. Use `text` not `content`. |
| `Image` | `imageUrl`, `fitMode` | Async loading (Coil 3 on Android). |
| `Icon` | `iconName` | Material Design icon identifier strings. |
| `Divider` | `axis` (horizontal/vertical) | Visual separator line. |
| `Video` | `url` | Placeholder in v0.8.2 — not fully implemented. |
| `AudioPlayer` | `url` | Placeholder in v0.8.2 — not fully implemented. |

**`usageHint` values for Text:**

| Value | Rendered as |
|-------|------------|
| `h1` | Largest heading |
| `h2` | Second-level heading |
| `h3` | Third-level heading |
| `h4` | Fourth-level heading |
| `h5` | Smallest heading |
| `body` | Default body text |
| `caption` | Small, secondary text |

### 5.2 Layout Widgets

| Widget | Key Properties | Notes |
|--------|---------------|-------|
| `Column` | `children`, `distribution`, `alignment` | Vertical stack. |
| `Row` | `children`, `distribution`, `alignment` | Horizontal stack. |
| `List` | `children` (explicit or template) | Scrollable container; supports template rendering for data-driven lists. |
| `Card` | `child` | Material 3 elevated container, 8dp padding. Takes a single child. |
| `Tabs` | `tabs`, `content` | Tab navigation bar + content area. |
| `Modal` | `trigger`, `child` | Dialog overlay triggered by another component. |

**`distribution` values for Column/Row:**

| Value | CSS equivalent | Description |
|-------|---------------|-------------|
| `start` | `flex-start` | Children packed at start |
| `center` | `center` | Children centered |
| `end` | `flex-end` | Children packed at end |
| `spaceBetween` | `space-between` | Equal spacing between children |
| `spaceAround` | `space-around` | Equal spacing around children |
| `spaceEvenly` | `space-evenly` | Equal spacing between and around |

### 5.3 Interactive Widgets

| Widget | Key Properties | Notes |
|--------|---------------|-------|
| `Button` | `child`, `actions`, `style` | Emits `UserActionEvent` with resolved context bindings. |
| `TextField` | `placeholder`, `textFieldType`, `text` | Two-way path binding via `text`. Types: text/email/number/password. |
| `CheckBox` | `label` | Two-way binding; emits `DataChangeEvent` on toggle. |
| `Slider` | `min`, `max` | Two-way binding; emits `DataChangeEvent` on value change. |
| `MultipleChoice` | `options`, `isMultiSelect` | Single-select or multi-select choice list. |
| `DateTimeInput` | `format` (date/time/both) | Invokes platform-native date/time picker. |

**`Button` style values:**

| Style | Visual |
|-------|--------|
| `filled` | Solid background (primary action) |
| `outlined` | Border only (secondary action) |
| `text` | No background or border (tertiary action) |

### 5.4 Widget JSON Examples

**Text with path binding:**
```json
{
  "id": "balance_display",
  "component": {
    "Text": {
      "text": {"path": "/balance"},
      "usageHint": "h3"
    }
  }
}
```

**Image:**
```json
{
  "id": "stock_chart",
  "component": {
    "Image": {
      "imageUrl": {"path": "/chartUrl"},
      "fitMode": "fitWidth"
    }
  }
}
```

**Column with explicit children:**
```json
{
  "id": "main_layout",
  "component": {
    "Column": {
      "children": {
        "explicitList": ["header", "divider_1", "content", "footer"]
      },
      "distribution": "start",
      "alignment": "start"
    }
  }
}
```

**Row with space-between distribution:**
```json
{
  "id": "stats_row",
  "component": {
    "Row": {
      "children": {
        "explicitList": ["price_col", "change_col", "volume_col"]
      },
      "distribution": "spaceBetween"
    }
  }
}
```

**Card with single child:**
```json
{
  "id": "portfolio_card",
  "component": {
    "Card": {
      "child": "portfolio_col"
    }
  }
}
```

**List with template rendering:**
```json
{
  "id": "transactions_list",
  "component": {
    "List": {
      "children": {
        "template": {
          "componentId": "transaction_item_template",
          "dataBinding": "/transactions"
        }
      }
    }
  }
}
```

**Button with actions and context:**
```json
{
  "id": "buy_button",
  "component": {
    "Button": {
      "child": "buy_label",
      "style": "filled",
      "actions": [
        {
          "name": "execute_trade",
          "context": [
            {"key": "ticker", "path": "/ticker"},
            {"key": "quantity", "path": "/quantity"},
            {"key": "action", "path": "/tradeAction"}
          ]
        }
      ]
    }
  }
}
```

**TextField with two-way binding:**
```json
{
  "id": "quantity_input",
  "component": {
    "TextField": {
      "placeholder": {"literalString": "Shares"},
      "textFieldType": "number",
      "text": {"path": "/quantity"}
    }
  }
}
```

---

## 6. Data Binding System

### 6.1 DataReference Types

A `DataReference` is a typed union that represents either a literal value or a path into the DataModel. Every widget property that can be dynamic accepts a `DataReference`.

| Type | JSON Syntax | Purpose |
|------|------------|---------|
| `LiteralString` | `{"literalString": "Hello"}` | Static string — never changes |
| `LiteralNumber` | `{"literalNumber": 42.5}` | Static numeric value |
| `LiteralBoolean` | `{"literalBoolean": true}` | Static boolean value |
| `PathString` | `{"path": "/user/name"}` | Dynamic string bound to DataModel |
| `PathNumber` | `{"path": "/user/age"}` | Dynamic numeric bound to DataModel |
| `PathBoolean` | `{"path": "/user/active"}` | Dynamic boolean bound to DataModel |

**Important:** Widget text properties use `DataReference`, not raw strings. Always wrap values:

```json
// ✅ Correct
"text": {"literalString": "Account Summary"}

// ✅ Correct
"text": {"path": "/accountName"}

// ❌ Wrong — raw string is not a valid DataReference
"text": "Account Summary"
```

### 6.2 JSON Pointer Paths

A2UI uses **RFC 6901 JSON Pointer** syntax for all path references in DataModel bindings.

| Path | Resolves to |
|------|------------|
| `/` | Root of the DataModel |
| `/accountName` | Top-level `accountName` field |
| `/user/name` | Nested object: `dataModel.user.name` |
| `/transactions/0/amount` | First element of `transactions` array, `amount` field |
| `/transactions/5/ticker` | Sixth element, `ticker` field |

**Array data binding for template lists:**

When a `List` uses template-based children with `dataBinding: "/transactions"`, each rendered template item receives a **scoped DataContext** rooted at `/transactions/N`. Within the template, paths like `/amount` resolve relative to the item — e.g., `/transactions/2/amount` for the third item.

### 6.3 DataModel and DataContext

The DataModel is backed by a `MutableStateFlow<JsonObject>` on the client. This means:

1. When the agent sends a `dataModelUpdate`, the DataModel updates reactively
2. Jetpack Compose (Android) automatically recomposes any widget that reads a bound path
3. SwiftUI (iOS) similarly observes changes via `@Published` equivalents

**Kotlin API:**

```kotlin
// Observe a specific path reactively
val name: StateFlow<String> = dataModel.observePath("/accountName")
    .map { it.jsonPrimitive.content }
    .stateIn(scope, SharingStarted.Lazily, "")

// In Compose — auto-recomposes when data changes
val accountName by dataModel.observePath("/accountName").collectAsState()
```

**DataContext scoping for template items:**

When rendering a list item from a template, the DataContext is scoped to the item's path. A template for `/transactions` at index `2` provides paths like:
- `/amount` → resolves to `/transactions/2/amount`
- `/ticker` → resolves to `/transactions/2/ticker`

The `sourceComponentId` in `UserActionEvent` includes an `:itemN` suffix (e.g., `"buy_button:item2"`) to identify which list item triggered the action.

### 6.4 Template Lists and Scoped Contexts

Template lists are the A2UI mechanism for rendering repeating data-driven UI. One template component is defined, and the `List` widget renders it once per item in a DataModel array.

**Complete template list example:**

```json
// DataModel
{
  "dataModelUpdate": {
    "surfaceId": "portfolio",
    "path": "/",
    "contents": [
      {
        "key": "holdings",
        "valueArray": [
          {"ticker": "AAPL", "shares": 10, "value": "$1,823.50"},
          {"ticker": "GOOG", "shares": 5, "value": "$876.25"},
          {"ticker": "MSFT", "shares": 15, "value": "$5,621.75"}
        ]
      }
    ]
  }
}

// List component referencing template
{
  "id": "holdings_list",
  "component": {
    "List": {
      "children": {
        "template": {
          "componentId": "holding_row_template",
          "dataBinding": "/holdings"
        }
      }
    }
  }
}

// Template component (rendered for each array item, data scoped to /holdings/N)
{
  "id": "holding_row_template",
  "component": {
    "Row": {
      "children": {"explicitList": ["ticker_text", "value_text"]},
      "distribution": "spaceBetween"
    }
  }
}
{
  "id": "ticker_text",
  "component": {
    "Text": {
      "text": {"path": "/ticker"},
      "usageHint": "body"
    }
  }
}
{
  "id": "value_text",
  "component": {
    "Text": {
      "text": {"path": "/value"},
      "usageHint": "body"
    }
  }
}
```

---

## 7. Event System

A2UI provides a two-way event system. While operations flow **agent → client**, events flow **client → agent** to report user interactions.

### 7.1 UserActionEvent

Fired when a user taps a `Button` (or other interactive widget with defined `actions`).

```kotlin
data class UserActionEvent(
    val name: String,               // Action identifier (e.g., "buy_stock")
    override val surfaceId: String, // Which surface the event came from
    val sourceComponentId: String,  // Component ID, with ":itemN" suffix for template items
    val timestamp: String,          // ISO 8601 timestamp
    val context: JsonObject? = null // Resolved key/value pairs from action's context bindings
) : UiEvent()
```

**Example payload sent to agent:**

```json
{
  "type": "UserActionEvent",
  "name": "execute_trade",
  "surfaceId": "response_abc123",
  "sourceComponentId": "buy_button",
  "timestamp": "2026-04-08T14:23:45.123Z",
  "context": {
    "ticker": "AAPL",
    "quantity": "10",
    "action": "buy"
  }
}
```

**Template item event (note `:item2` suffix):**

```json
{
  "type": "UserActionEvent",
  "name": "view_transaction",
  "surfaceId": "response_abc123",
  "sourceComponentId": "transaction_row_button:item2",
  "timestamp": "2026-04-08T14:24:01.456Z",
  "context": {
    "transactionId": "TXN-20260401-003",
    "amount": "-$234.50"
  }
}
```

### 7.2 DataChangeEvent

Fired when a user changes a form field (TextField, CheckBox, Slider, MultipleChoice, DateTimeInput).

```kotlin
data class DataChangeEvent(
    override val surfaceId: String,
    val path: String,    // JSON Pointer to the field that changed
    val value: String    // New value as string
) : UiEvent()
```

**Example:**

```json
{
  "type": "DataChangeEvent",
  "surfaceId": "trade_form",
  "path": "/quantity",
  "value": "25"
}
```

The client also immediately applies the change to its local DataModel so the UI reflects the new value without waiting for an agent round-trip.

### 7.3 Button Action Context Resolution

When a Button is tapped, the client resolves all `context` bindings against the current DataModel **at the time of the tap**. This is important for template list items, where the DataContext is scoped to a specific item.

**Button definition with context:**
```json
{
  "Button": {
    "child": "buy_label",
    "style": "filled",
    "actions": [
      {
        "name": "buy_stock",
        "context": [
          {"key": "ticker",   "path": "/ticker"},
          {"key": "quantity", "path": "/quantity"},
          {"key": "price",    "path": "/currentPrice"}
        ]
      }
    ]
  }
}
```

**Resolution process:**
1. Client reads current DataModel values at `/ticker`, `/quantity`, `/currentPrice`
2. Builds `context` JSON object: `{"ticker": "AAPL", "quantity": "10", "price": "$182.35"}`
3. Packages into `UserActionEvent` with `name: "buy_stock"`
4. Sends to agent via `POST /event`

### 7.4 Event Flow Back to Agent

```
User taps Button
    │
    ▼
Client resolves context bindings from DataModel
    │
    ▼
Creates UserActionEvent { name, surfaceId, sourceComponentId, timestamp, context }
    │
    ▼
ChatViewModel.onEvent(uiEvent)
    │
    ▼
RealChatRepository.postEvent(uiEvent)   POST to :8000/event
    │
    ▼
Agent receives event JSON
    │
    ▼
Agent processes action, generates new response
    │
    ▼
New SSE stream → new surface or text response
```

---

## 8. E2E Setup Guide

### 8.1 Prerequisites

**All platforms:**
- Git 2.x+
- Python 3.11+ (for agent)
- pip 23+

**Android:**
- Android Studio Hedgehog or later (2023.1.1+)
- JDK 21 (required by Kotlin 2.1.20+)
- Android SDK with API 24+ (minSdk) and API 36 (compileSdk)
- Android emulator or physical device running Android 7.0+

**iOS:**
- macOS 14+ (Sonoma or later recommended)
- Xcode 15+
- iOS 16+ simulator or device

**Agent (LLM-backed):**
- GitHub Copilot subscription (for GitHub Copilot SDK)
- Or Gemini API key (for google-adk)

**Agent (Template — no LLM):**
- No API key required

### 8.2 Clone and Explore (google/A2UI)

```bash
# Clone the canonical A2UI repository
git clone https://github.com/google/A2UI.git
cd A2UI

# Explore the structure
ls -la
# agent_sdks/  docs/  renderers/  samples/  specification/  tools/

# Install Python SDK locally (editable)
cd agent_sdks/python
pip install -e ".[dev]"

# Run the sample ADK agent (restaurant finder)
cd ../../samples/agent/adk/restaurant_finder
pip install -r requirements.txt
python agent.py

# Run a sample web client
cd ../../../client/react
npm install && npm run dev
```

### 8.3 Python Agent Setup (LLM-backed)

This agent uses the GitHub Copilot SDK (Claude Sonnet 4.6) to generate A2UI responses.

```bash
# Clone this project
git clone <this-repo-url>
cd a2ui-sample-chat-app/agent

# Create virtual environment
python -m venv .venv
source .venv/bin/activate  # Windows: .venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Set credentials (GitHub Copilot SDK)
export GITHUB_TOKEN=<your-github-token>

# Start the agent server
python agent.py
# Server starts at http://0.0.0.0:8000
# Endpoints:
#   POST /chat/stream  — SSE stream for chat messages
#   POST /event        — Receive UiEvents from client
```

**Verify the agent is running:**

```bash
curl -X POST http://localhost:8000/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message": "Show my account balances"}' \
  --no-buffer
```

Expected output: SSE events starting with `event: text` and `event: a2ui_op`.

### 8.4 Python Template Agent Setup (Deterministic)

This agent requires **no LLM** — it matches intents via keyword rules and serves pre-approved A2UI templates with mock data.

```bash
cd a2ui-sample-chat-app/agent-templates

# Create virtual environment
python -m venv .venv
source .venv/bin/activate

# Install dependencies (FastAPI + SSE only — no AI SDKs needed)
pip install -r requirements.txt

# Start the template agent
python template_agent.py
# Server starts at http://0.0.0.0:8000
```

**Template agent architecture:**

```
User message
    │
    ▼
intent_router.py      — Keyword-based intent classification
    │                   (e.g., "balance" → account_balances template)
    ▼
template_renderer.py  — Load JSON template from templates/
    │                   Load mock data from data/
    │                   Substitute {{placeholders}}
    ▼
a2ui_transform.py     — expand → path-bind → sanitize → chunk
    │
    ▼
SSE stream            — emit text, a2ui_op, done events
```

**Available templates:**

| Template File | Intent Keywords | Description |
|--------------|----------------|-------------|
| `account_balances.json` | balance, account, summary | Account balance overview |
| `brokerage_activity.json` | brokerage, activity, trades | Recent brokerage transactions |
| `transaction_history.json` | transaction, history, recent | Transaction list view |

### 8.5 Android Client Setup

```bash
# Open in Android Studio
# File → Open → select a2ui-sample-chat-app/

# Or build from command line
cd a2ui-sample-chat-app
./gradlew assembleDebug
```

**Key configuration in `ChatViewModel.kt`:**

```kotlin
// Switch between real and mock agent
private val USE_REAL_AGENT = true  // false = MockChatRepository (no server needed)
```

**Network configuration:**
- Emulator: `10.0.2.2:8000` (resolves to host machine localhost)
- Physical device: Replace with your machine's LAN IP (e.g., `192.168.1.100:8000`)

**Gradle dependency (already included):**

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.contextable:a2ui-4k:0.8.2")
}
```

**Run on emulator:**

```bash
# Start emulator first, then:
./gradlew installDebug
# Or press ▶ Run in Android Studio
```

### 8.6 iOS Client Setup

```bash
# Open in Xcode
open a2ui-sample-chat-app/ios/A2UIChatApp.xcodeproj
# Or
open a2ui-sample-chat-app/ios/A2UIChatApp.xcworkspace  # if using CocoaPods

# Build and run
# Select iPhone simulator target
# Press ⌘R
```

**Network configuration (iOS simulator):**
- Uses `127.0.0.1:8000` (simulator can reach host machine localhost directly)
- Physical device: Replace with host machine's LAN IP

**Key file: `RealChatRepository.swift`:**

```swift
// Connection to agent
let agentBaseURL = "http://127.0.0.1:8000"
```

### 8.7 Running the Full System

**Step 1: Start the agent (one of two options)**

```bash
# Option A: LLM-backed (requires API key)
cd agent && python agent.py

# Option B: Template/deterministic (no API key needed)
cd agent-templates && python template_agent.py
```

**Step 2: Start Android client**

```bash
./gradlew installDebug
# Tap app icon on emulator
```

**Step 3: Start iOS client**

```bash
# In Xcode: Select iPhone 15 Simulator → ⌘R
```

**Step 4: Test the full flow**

Type any of these in the chat:
- `"Show my account balances"` → triggers account_balances template / LLM response
- `"Show recent transactions"` → triggers transaction_history template
- `"Show brokerage activity"` → triggers brokerage_activity template

**Expected full flow:**

```
Client sends: POST /chat/stream {"message": "Show my account balances"}

Server streams:
  event: text
  data: {"text": "Here are your current account balances:"}

  event: a2ui_op
  data: {"beginRendering": {"surfaceId": "response_001", "root": "root", ...}}

  event: a2ui_op
  data: {"dataModelUpdate": {"surfaceId": "response_001", "path": "/", "contents": [...]}}

  event: a2ui_op
  data: {"surfaceUpdate": {"surfaceId": "response_001", "components": [...]}}

  event: done
  data: {}

Client renders: Native UI with account cards, data bound from DataModel
```

### 8.8 Testing and Validation

**Validate A2UI operations manually:**

```bash
# Test SSE stream directly
curl -N -X POST http://localhost:8000/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message": "Show account balances"}'

# Send a mock button tap event
curl -X POST http://localhost:8000/event \
  -H "Content-Type: application/json" \
  -d '{
    "type": "UserActionEvent",
    "name": "view_details",
    "surfaceId": "response_001",
    "sourceComponentId": "details_button",
    "timestamp": "2026-04-08T10:00:00Z",
    "context": {"accountId": "ACC-001"}
  }'
```

**Android offline testing (MockChatRepository):**

```kotlin
// In ChatViewModel.kt
private val USE_REAL_AGENT = false
// App will use MockChatRepository with pre-defined A2UI responses
// No server required
```

**Python schema validation:**

```python
from a2ui.schema_manager import A2uiSchemaManager, BasicCatalog

schema_manager = A2uiSchemaManager(catalog=BasicCatalog, version="0.8")
is_valid, errors = schema_manager.validate(your_a2ui_json)
print(f"Valid: {is_valid}, Errors: {errors}")
```

---

## 9. SDK Reference

### 9.1 Kotlin/Android SDK (com.contextable:a2ui-4k)

**Library:** [Contextable/a2ui-4k](https://github.com/Contextable/a2ui-4k)
**Version:** 0.8.2 (released 2026-02-10)
**Distribution:** Maven Central

#### Gradle Dependency

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.contextable:a2ui-4k:0.8.2")
}
```

#### Platform-Specific Artifacts

| Platform | Artifact |
|----------|---------|
| Android | `com.contextable:a2ui-4k-android:0.8.2` |
| JVM/Desktop | `com.contextable:a2ui-4k-jvm:0.8.2` |
| iOS ARM64 | `com.contextable:a2ui-4k-iosarm64:0.8.2` |
| JavaScript | `com.contextable:a2ui-4k-js:0.8.2` |

> The base `com.contextable:a2ui-4k` artifact is the Kotlin Multiplatform common artifact that resolves to the appropriate platform artifact automatically.

#### System Requirements

| Requirement | Minimum |
|------------|---------|
| Kotlin | 2.1.20+ |
| minSdk | 24 (Android 7.0) |
| compileSdk | 36 |
| JVM Target | 21 |
| Compose Multiplatform | Required |

#### Core API

**1. Create the state manager:**

```kotlin
val stateManager = SurfaceStateManager()
```

**2. Process incoming operations (call for each `a2ui_op` SSE event):**

```kotlin
// For individual operations
stateManager.processOperation(jsonOperation)

// For complete snapshots (all operations at once)
stateManager.processSnapshot(messageId, activityContent)
```

**3. Get the rendered definition and data:**

```kotlin
val definition: UiDefinition? = stateManager.getSurface("default")
val dataModel: DataModel? = stateManager.getDataModel("default")
```

**4. Render in Jetpack Compose:**

```kotlin
@Composable
fun AgentUI(surfaceId: String) {
    val stateManager = remember { SurfaceStateManager() }
    val definition by remember(stateManager) {
        stateManager.getSurfaceFlow(surfaceId)
    }.collectAsState(null)
    val dataModel = stateManager.getDataModel(surfaceId) ?: rememberDataModel()

    definition?.let { def ->
        A2UISurface(
            definition = def,
            dataModel = dataModel,
            catalog = FinancialCatalog,           // or CoreCatalog
            onEvent = { event -> handleEvent(event) }
        )
    }
}
```

**5. Handle events:**

```kotlin
fun handleEvent(event: UiEvent) {
    when (event) {
        is UserActionEvent -> {
            println("Action: ${event.name}, context: ${event.context}")
            // Send to agent via POST /event
        }
        is DataChangeEvent -> {
            println("Data changed at ${event.path}: ${event.value}")
        }
    }
}
```

#### Custom Catalog

```kotlin
val myWidget = CatalogItem(name = "MyWidget") { componentId, data, buildChild, dataContext, onEvent ->
    val label = DataReferenceParser.parseString(data["label"], dataContext)
    val onClick = data["onTap"]?.let { parseAction(it) }

    CustomButton(
        text = label,
        onClick = { onClick?.let { onEvent(UserActionEvent(it, surfaceId, componentId, ...)) } }
    )
}

// Compose with CoreCatalog
val MyCatalog = CoreCatalog + Catalog.of("my_namespace", myWidget)
```

### 9.2 Python Agent SDK (a2ui-agent)

**Repository:** `agent_sdks/python/` in google/A2UI
**Installation:** `pip install a2ui-agent`

#### Dependencies

```toml
[project.dependencies]
"a2a-sdk>=0.3.0"          # Agent-to-Agent protocol SDK
"google-adk>=1.28.0"      # Google Agent Development Kit
"google-genai>=1.27.0"    # Google GenAI SDK (Gemini models)
"jsonschema>=4.0.0"       # JSON Schema validation
```

#### Key Modules

| Module | Class/Function | Role |
|--------|---------------|------|
| `a2ui.adk.extension` | `A2uiExtension` | Injects A2UI schema + examples into LLM system prompt |
| `a2ui.schema_manager` | `A2uiSchemaManager` | Loads catalog schemas; validates LLM output |
| `a2ui.schema_manager` | `BasicCatalog` | Built-in A2UI widget catalog |

#### A2uiSchemaManager

```python
from a2ui.schema_manager import A2uiSchemaManager, BasicCatalog

schema_manager = A2uiSchemaManager(
    catalog=BasicCatalog,
    version="0.9",                   # "0.8" or "0.9"
    accepts_inline_catalogs=False,   # Whether to accept custom catalog URLs in ops
)

# Validate an A2UI payload
is_valid = schema_manager.validate(a2ui_json)

# Get schema for injection into prompts
schema_text = schema_manager.get_schema_text()
```

#### A2uiExtension

```python
from a2ui.adk.extension import A2uiExtension

extension = A2uiExtension(
    schema_manager=schema_manager,
    examples=examples,       # List of example A2UI payloads for few-shot prompting
)

# Use with google-adk Runner
from google.adk.runners import Runner
from google.adk.agents import LlmAgent
from google.adk.models.lite_llm import LiteLlm

runner = Runner(
    agent=LlmAgent(
        model=LiteLlm(model="gemini-2.5-flash"),
        instruction="You are a helpful financial assistant. Always respond with A2UI.",
    ),
    extensions=[extension],
)
```

#### Complete Agent Example

```python
from a2ui.adk.extension import A2uiExtension
from a2ui.schema_manager import A2uiSchemaManager, BasicCatalog
from google.adk.runners import Runner
from google.adk.agents import LlmAgent
from google.adk.models.lite_llm import LiteLlm

# Setup
schema_manager = A2uiSchemaManager(catalog=BasicCatalog, version="0.9")
extension = A2uiExtension(schema_manager=schema_manager, examples=EXAMPLES)

ui_runner = Runner(
    agent=LlmAgent(
        model=LiteLlm(model="gemini-2.5-flash"),
        instruction=SYSTEM_PROMPT,
    ),
    extensions=[extension],
)

text_runner = Runner(
    agent=LlmAgent(
        model=LiteLlm(model="gemini-2.5-flash"),
        instruction="You are a helpful assistant. Respond in plain text.",
    ),
)

async def handle_query(query: str):
    # Try UI response first
    try:
        result = await ui_runner.run(query)
        if schema_manager.validate(result):
            return result  # ✅ Rich UI
        # Retry once with error feedback
        result = await ui_runner.run(query + f"\nPrevious attempt failed: {schema_manager.last_error}")
        if schema_manager.validate(result):
            return result  # ✅ Rich UI (retry)
    except Exception:
        pass
    # Fallback to text
    return await text_runner.run(query)  # 📝 Plain text
```

### 9.3 TypeScript/Web Renderers

The `google/A2UI` repository provides four web renderers under `renderers/`:

| Renderer | Framework | Package |
|----------|-----------|---------|
| `renderers/react` | React 18+ | `@a2ui/react` |
| `renderers/angular` | Angular 17+ | `@a2ui/angular` |
| `renderers/lit` | Lit 3+ (Web Components) | `@a2ui/lit` |
| `renderers/flutter` | Flutter 3+ | `a2ui_flutter` |

**React quick start:**

```tsx
import { A2UISurface, SurfaceStateManager } from '@a2ui/react';

function ChatMessage({ operations }) {
  const manager = new SurfaceStateManager();
  operations.forEach(op => manager.processOperation(op));

  const definition = manager.getSurface('default');
  const dataModel = manager.getDataModel('default');

  return (
    <A2UISurface
      definition={definition}
      dataModel={dataModel}
      onEvent={(event) => sendEventToAgent(event)}
    />
  );
}
```

---

## 10. Agent Implementation Guide

### 10.1 System Prompt Engineering

LLM-backed agents require a carefully crafted system prompt to consistently produce valid A2UI. This project's prompt is approximately 7,500 characters and includes:

**Required sections:**

1. **Role and objective** — "You are a financial assistant that always responds with A2UI JSON"
2. **Protocol rules** — Must start with `beginRendering`, must include `dataModelUpdate`, component IDs must be unique
3. **Widget catalog** — All 18 widgets with properties, examples, and common mistakes
4. **Layout patterns** — `Column > Card > Column > Text` for cards; `Row` for side-by-side; `List + template` for repeating data
5. **Data binding rules** — Always use `{"path": "/field"}` for dynamic data; use `{"literalString": "..."}` for static labels
6. **Concrete examples** — At minimum 4 full examples covering: simple card, data list, form with inputs, multi-card layout
7. **Error avoidance** — "Never reference a component ID that hasn't been defined", "Card takes exactly one `child` not `children`"

**Prompt engineering tips:**

- Include an example for the exact type of UI you expect (financial → account card examples)
- Explicitly list forbidden patterns (e.g., "Do NOT nest JSON objects inside `literalString` values")
- Show the complete operation sequence (`beginRendering` → `dataModelUpdate` → `surfaceUpdate`)
- Include examples with template lists if your domain has repeating data

### 10.2 LLM-Backed Agent Pattern

```python
# Simplified 5-stage pipeline
async def generate_ui_response(user_message: str) -> AsyncIterator[SSEEvent]:

    # Stage 1: Build prompt
    prompt = SYSTEM_PROMPT + "\n\nUser: " + user_message

    # Stage 2: LLM call (streaming, then accumulate)
    raw_response = ""
    async for chunk in llm_client.stream(prompt):
        raw_response += chunk

    # Stage 3: Validate (with retry)
    a2ui_json = extract_json(raw_response)
    if not schema_validator.validate(a2ui_json):
        # Retry with error context
        prompt_with_error = prompt + f"\n\nError in previous response: {schema_validator.last_error}\nPlease fix."
        raw_response = await llm_client.complete(prompt_with_error)
        a2ui_json = extract_json(raw_response)

    if not schema_validator.validate(a2ui_json):
        # Final fallback: text only
        yield SSEEvent("text", {"text": extract_text_response(raw_response)})
        yield SSEEvent("done", {})
        return

    # Stage 4: Transform
    a2ui_json = expand_templates(a2ui_json)
    a2ui_json = extract_path_bindings(a2ui_json)
    a2ui_json = sanitize_dangling_refs(a2ui_json)
    operation_batches = chunk_surface_updates(a2ui_json, max_per_batch=15)

    # Stage 5: Stream
    if text_content := extract_text(raw_response):
        yield SSEEvent("text", {"text": text_content})

    for batch in operation_batches:
        yield SSEEvent("a2ui_op", batch)
        await asyncio.sleep(0.15)  # 150ms delay between ops

    yield SSEEvent("done", {})
```

### 10.3 Deterministic Template Agent Pattern

The template agent bypasses LLM entirely for known intents, ensuring 100% valid A2UI output with no latency or cost.

```python
# agent-templates/intent_router.py
INTENT_MAP = {
    "account_balances": ["balance", "account", "summary", "how much"],
    "brokerage_activity": ["brokerage", "activity", "trades", "bought", "sold"],
    "transaction_history": ["transaction", "history", "recent", "payments"],
}

def classify_intent(message: str) -> str | None:
    message_lower = message.lower()
    for intent, keywords in INTENT_MAP.items():
        if any(kw in message_lower for kw in keywords):
            return intent
    return None  # Fall through to LLM agent

# agent-templates/template_agent.py (FastAPI)
@app.post("/chat/stream")
async def chat_stream(request: ChatRequest):
    intent = classify_intent(request.message)

    if intent:
        # Deterministic path: template + mock data
        operations = template_renderer.render(intent)
    else:
        # Fallback (text response or hand-off to LLM agent)
        operations = generate_text_response(request.message)

    return StreamingResponse(
        stream_operations(operations),
        media_type="text/event-stream"
    )
```

**Trade-offs:**

| Aspect | Template Agent | LLM Agent |
|--------|---------------|-----------|
| Latency | ~50ms | 2–8 seconds |
| Cost | $0 | ~$0.01–0.05/message |
| Flexibility | Fixed templates only | Handles any query |
| Reliability | 100% valid A2UI | ~95% valid (with retry) |
| Maintenance | Manual template updates | Prompt engineering |

### 10.4 Dual-Runner Fallback Pattern

Google's reference pattern for production LLM agents uses two runners with graceful degradation:

```python
async def query_with_fallback(query: str) -> Response:
    # Attempt 1: Rich UI response
    try:
        result = await ui_runner.run(query)
        if schema_manager.validate(result):
            return result  # ✅ Rich UI

        # Attempt 2: UI with error feedback
        retry_query = f"{query}\n\nPrevious response had errors: {schema_manager.last_error}"
        result = await ui_runner.run(retry_query)
        if schema_manager.validate(result):
            return result  # ✅ Rich UI (retry)

    except Exception as e:
        log.warning(f"UI runner failed: {e}")

    # Attempt 3: Text-only fallback
    return await text_runner.run(query)  # 📝 Always works
```

This pattern ensures users always get *some* response, even if the LLM produces invalid A2UI. The text fallback uses a simpler prompt (no A2UI schema injection) and is far less likely to fail.

### 10.5 Schema Validation and Retry

The Python SDK provides JSON Schema validation against the A2UI specification:

```python
from a2ui.schema_manager import A2uiSchemaManager, BasicCatalog

schema_manager = A2uiSchemaManager(catalog=BasicCatalog, version="0.8")

# Validate a complete response
a2ui_payload = {
    "operations": [
        {"beginRendering": {"surfaceId": "s1", "root": "root", "catalogId": "..."}},
        {"surfaceUpdate": {"surfaceId": "s1", "components": [...]}},
    ]
}

is_valid = schema_manager.validate(a2ui_payload)
```

**Semantic checks (beyond JSON Schema):**

The validation layer in this project also runs semantic checks:
- Root component exists in the component list
- All `children` references point to defined components
- `Card.child` is a string (not an object)
- Template `componentId` references exist in component list
- No orphaned components (defined but not reachable from root)

**Retry strategy:**

```python
MAX_RETRIES = 1  # One retry is usually sufficient

for attempt in range(MAX_RETRIES + 1):
    response = await llm.complete(prompt)
    is_valid, errors = validate(response)

    if is_valid:
        break

    if attempt < MAX_RETRIES:
        prompt += f"\n\nYour previous response had these errors:\n{errors}\nPlease fix them."
    else:
        # Fall back to text
        response = await text_llm.complete(original_prompt)
        break
```

### 10.6 The Transformation Pipeline

Before streaming to the client, A2UI operations go through a 4-step transformation pipeline:

**Step 1 — Template Expansion**

Expand agent-authored shorthand templates. The LLM may use `{field}` or `{i}` placeholders:

```python
# Input (agent output with placeholders)
"text": {"literalString": "Transaction {i}: {amount}"}

# After expansion (for item at index 2 with amount "-$234.50")
"text": {"literalString": "Transaction 2: -$234.50"}
```

**Step 2 — Literal-to-Path Binding Transformation**

The LLM often embeds data values directly as `literalString`. The transformer extracts these into the DataModel and replaces them with path bindings:

```python
# Input (LLM output — data embedded in component)
"text": {"literalString": "$48,291.73"}

# After transformation — data moved to DataModel
DataModel: {"balance": "$48,291.73"}
Component: "text": {"path": "/balance"}
```

This enables reactive updates — if the agent later sends a new `dataModelUpdate`, the UI re-renders without rebuilding the component tree.

**Step 3 — Sanitization**

Remove dangling references that would cause render errors:

```python
# Remove children that reference non-existent component IDs
# Remove Card.child pointing to non-existent components
# Remove template componentId references that don't exist
```

**Step 4 — Chunking**

Split large component lists across multiple `surfaceUpdate` operations (max 15 per batch). This enables progressive rendering — the client can start rendering the top of the surface while lower components are still streaming.

---

## 11. Custom Widget Development

### 11.1 CatalogItem Pattern

A `CatalogItem` is a named factory that maps a widget type name to a Composable function. The factory receives:

| Parameter | Type | Description |
|-----------|------|-------------|
| `componentId` | `String` | Unique ID of this component instance |
| `data` | `JsonObject` | The widget's configuration properties |
| `buildChild` | `(String) -> Unit` | Recursively render a child component by ID |
| `dataContext` | `DataContext` | Resolved DataModel context (for path bindings) |
| `onEvent` | `(UiEvent) -> Unit` | Emit events back to the host |

```kotlin
val stockTickerWidget = CatalogItem(name = "StockTicker") { componentId, data, buildChild, dataContext, onEvent ->
    // Read configuration properties
    val ticker = DataReferenceParser.parseString(data["ticker"], dataContext) ?: return@CatalogItem
    val price = DataReferenceParser.parseString(data["price"], dataContext) ?: ""
    val changePercent = DataReferenceParser.parseDouble(data["changePercent"], dataContext) ?: 0.0

    // Render native Compose widget
    StockTickerRow(
        ticker = ticker,
        price = price,
        changePercent = changePercent,
        isPositive = changePercent >= 0,
        onTap = {
            onEvent(
                UserActionEvent(
                    name = "view_stock",
                    surfaceId = dataContext.surfaceId,
                    sourceComponentId = componentId,
                    timestamp = Instant.now().toString(),
                    context = buildJsonObject { put("ticker", ticker) }
                )
            )
        }
    )
}
```

### 11.2 Catalog Composition

Catalogs are composable. Start with `CoreCatalog` and add custom widgets:

```kotlin
// Single custom widget
val FinancialCatalog = CoreCatalog + Catalog.of("financial", stockTickerWidget)

// Multiple custom widgets
val FinancialCatalog = CoreCatalog + Catalog.of(
    "financial",
    stockTickerWidget,
    portfolioPieChartWidget,
    accountSummaryCardWidget,
    tradeHistoryRowWidget
)

// Compose multiple catalogs
val AppCatalog = CoreCatalog + FinancialCatalog + ChartsWidgetCatalog
```

### 11.3 FinancialCatalog Example (This Project)

This project extends `CoreCatalog` with a `FinancialCatalog` defined in:

```
app/src/main/java/com/example/a2ui/chat/data/a2ui/FinancialCatalog.kt
```

The FinancialCatalog:
- Overrides default widget styles with Material 3 financial app theming
- Adds domain-specific widgets for stock tickers, account summaries, and transaction rows
- Sets custom color tokens from `theme/Color.kt` for positive/negative financial values
- Wires into `A2UISurface` via `MessageBubble.kt`

> **For UI changes:** `FinancialCatalog.kt` is the primary file to edit. All visual customization for agent-generated UI happens here.

The catalog is wired in `MessageBubble.kt`:

```kotlin
@Composable
fun MessageBubble(message: Message, onEvent: (UiEvent) -> Unit) {
    message.uiDefinition?.let { definition ->
        A2UISurface(
            definition = definition,
            dataModel = message.dataModel ?: rememberDataModel(),
            catalog = FinancialCatalog,   // ← Custom catalog with financial overrides
            onEvent = onEvent
        )
    } ?: Text(message.text)
}
```

---

## 12. Multi-Surface and Multi-Agent Patterns

### Multiple Surfaces in One Session

A single chat session can have multiple active surfaces simultaneously:

```json
// Message 1 generates surface "portfolio_summary"
{"beginRendering": {"surfaceId": "portfolio_summary", "root": "root1", ...}}

// Message 2 generates surface "transaction_detail"
{"beginRendering": {"surfaceId": "transaction_detail", "root": "root2", ...}}
```

The `SurfaceStateManager` maintains a map of surfaces by ID. Each `MessageBubble` renders its own surface independently.

### Orchestrator Pattern (Multi-Agent)

Google's ADK supports orchestrator agents that dispatch to specialized sub-agents:

```
User query: "Show my portfolio and recent trades"
     │
     ▼
Orchestrator Agent
     ├── Portfolio Sub-Agent → surfaceId: "portfolio_surface"
     └── Trades Sub-Agent   → surfaceId: "trades_surface"
     │
     ▼
Two parallel SSE streams → Two surfaces rendered side by side
```

See `samples/agent/adk/orchestrator/` in the google/A2UI repository for a reference implementation.

### Cross-Surface Data Sharing

Surfaces can reference a shared DataModel namespace via the path prefix:

```json
// Surface A updates global data
{"dataModelUpdate": {"surfaceId": "shared", "path": "/user", "contents": [...]}}

// Surface B reads from it
{"Text": {"text": {"path": "/user/name"}}}
```

The client-side `SurfaceStateManager` manages cross-surface data references.

### A2A Transport Integration

Google's reference implementation uses the **A2A (Agent-to-Agent) protocol** as the transport layer:

| Feature | A2A Implementation |
|---------|-------------------|
| Transport | `A2AStarletteApplication` (FastAPI-based) |
| Operations encoding | `DataPart` messages containing A2UI JSON |
| Session management | Built-in (per A2A spec) |
| Agent discovery | Agent card declares `a2ui` extension URI |
| Authentication | OAuth2/JWT via A2A auth layer |
| Extension URI | `https://a2ui.org/a2a-extension/a2ui/v0.8` |
| MIME type | `application/json+a2ui` |

**Client capabilities declaration:**

```json
{
  "clientCapabilities": {
    "a2ui": {
      "version": "0.8",
      "catalogs": ["https://a2ui.org/specification/v0_8/basic_catalog.json"]
    }
  }
}
```

---

## 13. Security and Production Considerations

### Network Security

| Concern | Development | Production |
|---------|------------|-----------|
| Transport | HTTP (plain) | HTTPS (TLS required) |
| Auth | None | JWT or OAuth2 |
| CORS | Allow all | Restrict to app origin |
| Rate limiting | None | Per-user limits |

**Android network config for production:**

```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">your-agent.com</domain>
    </domain-config>
</network-security-config>
```

### Input Validation

- **Agent side:** Always validate LLM output against A2UI JSON schema before streaming. Never stream unvalidated LLM output.
- **Client side:** The `a2ui-4k` SDK validates operations before applying them. Invalid operations are silently ignored (logged in debug builds).
- **Event handling:** Validate `UserActionEvent` payloads server-side before acting on them — context values come from the client.

### Prompt Injection

Since LLM agents process user messages to generate A2UI, prompt injection is a risk:

- Sanitize user input before including in the LLM prompt
- Use system prompt position (not user message) for A2UI instructions
- Validate that generated `surfaceId` values don't clash with existing surfaces

### Data Privacy

- `DataChangeEvent` sends form field values to the agent — avoid binding sensitive fields (passwords, SSNs) to DataModel paths that flow through event reporting
- `UserActionEvent.context` is fully client-controlled — validate all values server-side
- `DataModel` is in-memory only — no persistence between sessions unless the agent explicitly re-sends data

### Production Architecture Recommendations

```
                    ┌─────────────────────┐
Internet Client ────│   API Gateway       │────▶ A2UI Agent
                    │   - Auth (JWT)      │
                    │   - Rate limiting   │
                    │   - TLS termination │
                    │   - Request logging │
                    └─────────────────────┘
```

---

## 14. Version History

| Version | Status | Key Changes |
|---------|--------|------------|
| v0.8.2 | **Current stable** | Released 2026-02-10. Current SDK version. `Video` and `AudioPlayer` are placeholders. |
| v0.8.1 | Stable | Bug fixes to DataModel path resolution. |
| v0.8.0 | Stable | Initial stable release. 18 standard widgets. |
| v0.9 | **In development** | Flat component format, renamed operations, native JSON DataModel, ~40% token reduction. |

### v0.8 → v0.9 Summary of Breaking Changes

1. `beginRendering` renamed to `createSurface`
2. `surfaceUpdate` renamed to `updateComponents`
3. `dataModelUpdate` renamed to `updateDataModel` with different `value` format
4. Component definition format flattened (widget type is now a string field, not a nested key)
5. `usageHint` renamed to `variant` in Text widget
6. DataModel `contents` array replaced with native JSON `value` object

---

## 15. Key Repositories Summary

| Repository | URL | Purpose |
|-----------|-----|---------|
| **google/A2UI** | https://github.com/google/A2UI | Canonical protocol spec, Python SDK, web renderers, samples |
| **Contextable/a2ui-4k** | https://github.com/Contextable/a2ui-4k | Kotlin Multiplatform client SDK (Android, iOS, JVM, JS) |
| **A2UI Specification** | https://a2ui.org/specification/v0.8-a2ui/ | v0.8 protocol specification |
| **A2UI Docs** | https://a2ui.org | Official documentation site (mkdocs) |

| Package | Registry | Coordinate |
|---------|----------|-----------|
| `a2ui-agent` | PyPI | `pip install a2ui-agent` |
| `a2ui-4k` | Maven Central | `com.contextable:a2ui-4k:0.8.2` |
| `@a2ui/react` | npm | `npm install @a2ui/react` |
| `@a2ui/angular` | npm | `npm install @a2ui/angular` |
| `@a2ui/lit` | npm | `npm install @a2ui/lit` |

---

## 16. Confidence Assessment

This document is based on verified research. The following table documents confidence levels for each section:

| Section | Source | Confidence |
|---------|--------|-----------|
| Protocol operations (§4.2) | Primary research from protocol spec + SDK source | ✅ High |
| Widget catalog (§5) | SDK source code analysis | ✅ High |
| Data binding (§6) | SDK source + working code | ✅ High |
| Event system (§7) | Kotlin sealed class definitions | ✅ High |
| SSE format (§4.4) | Working implementation | ✅ High |
| v0.9 changes (§4.5) | Migration script + research | ✅ High |
| Python SDK API (§9.2) | SDK source analysis | ✅ High |
| Kotlin SDK API (§9.1) | SDK + GitHub research | ✅ High |
| Repository structure (§3.2) | Direct file tree analysis | ✅ High |
| Template agent (§10.3) | Working implementation in this repo | ✅ High |
| A2A transport (§12) | Google reference implementation research | ⚠️ Medium (A2A spec may have evolved) |
| iOS implementation (§3.4) | File tree analysis | ⚠️ Medium (implementation details inferred) |
| Web renderer APIs (§9.3) | Package names from repo; API is illustrative | ⚠️ Medium (web renderer APIs not deeply verified) |
| v0.9 token savings "~40%" | Research-stated figure | ℹ️ Approximate |

**Not verified / assumed:**
- Exact `A2UISurface` Composable signature parameter order (matches common pattern)
- Precise `CatalogItem` lambda parameter names (illustrative based on pattern)
- Web renderer npm package names (from repo directory names; not confirmed on npm registry)

---

## Footnotes

[^1]: GitHub star count (13,697) is as of research date. The repository continues to grow.

[^2]: `10.0.2.2` is the Android emulator's special IP that routes to the host machine's `localhost`. iOS simulators use `127.0.0.1` directly because they share the host network stack.

[^3]: The "~40% token reduction" for v0.9 is an approximate figure based on the flattened JSON format removing redundant wrapper objects. Actual savings vary with payload complexity.

[^4]: `SurfaceStateManager` contains debug `println` statements (known limitation, Research Block 16). In production builds, these should be removed or gated behind a debug flag.

[^5]: `Video` and `AudioPlayer` in v0.8.2 render placeholder views. Full implementations are expected in v0.9.

[^6]: The 150ms delay between streamed `a2ui_op` events (§10.2) is an implementation choice in this project for visual effect. It can be removed for production latency optimization.

[^7]: JSON Pointer paths follow RFC 6901. A path of `""` (empty string) and `"/"` (slash) have different meanings in RFC 6901: `""` refers to the whole document, `"/"` refers to the key `""` (empty string key). In A2UI's `dataModelUpdate`, both are commonly used to mean "root level" — check the SDK implementation for exact semantics.

---

*This document was generated from verified research. For the latest information, refer to the official specification at https://a2ui.org/specification/v0.8-a2ui/ and the SDK repositories listed in §15.*
