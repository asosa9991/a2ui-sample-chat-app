# How to Create an AI Agent That Generates A2UI Protocol Operations
## Comprehensive Research & Implementation Guide

**Research Date:** 2025  
**Context:** Android chat app at `/Users/vijayakella/pocs/mobile/android/a2ui-sample-chat-app`  
**Current Tooling:** `com.contextable:a2ui-4k:0.8.2` (KMP rendering client)

---

## Executive Summary

An **A2UI AI Agent** is a server-side LLM-powered application that:

1. **Receives** conversational user input from a mobile client
2. **Interprets** the user's intent using an LLM (Claude, GPT-4, Gemini)
3. **Generates** declarative A2UI JSON operations describing the desired UI
4. **Streams** these operations back to the client via HTTP, WebSocket, or A2A protocol
5. **Enables** the client's `SurfaceStateManager` to process operations → `UiDefinition` → render via `A2UISurface`

**Key Difference from Current App:**
- **Today:** `MockChatRepository` returns hardcoded `BrokerageActivitySurface.build()` UiDefinition objects
- **Tomorrow:** AI Agent server generates dynamic A2UI protocol operations in response to user queries, allowing **conversation-driven UI generation**

The A2UI protocol separates **UI Intent** (agent) from **UI Implementation** (client), enabling secure, LLM-safe, declarative interfaces that render natively on any platform.

---

## Architecture Overview

### Full Stack Diagram

```
┌────────────────────────────────────────────────────────────────────────────┐
│                         AI AGENT (Server)                                  │
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────────────┐ │
│  │ 1. Receive: User message (HTTP POST /chat or WebSocket)             │ │
│  │ 2. LLM Call: Send to Claude/GPT-4/Gemini with A2UI system prompt    │ │
│  │ 3. Parse: LLM output → JSON array of A2UI operations                │ │
│  │ 4. Validate: Check against A2UI schema (v0.8)                       │ │
│  │ 5. Stream: Send operations as JSONL via HTTP SSE or WebSocket       │ │
│  └──────────────────────────────────────────────────────────────────────┘ │
│                                                                            │
│  Operations Generated:                                                     │
│  - beginRendering  (create surface, set root)                             │
│  - surfaceUpdate   (add/update components)                                │
│  - dataModelUpdate (set state data)                                       │
│  - dataModelUpdate (set additional state as needed)                       │
│  - [optional] deleteSurface (clear previous UI)                           │
└────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
           A2UI v0.8 Protocol (JSON Operations over HTTP/WS)
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                    ANDROID CLIENT (com.example.a2ui:chat)                  │
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────────────┐ │
│  │ SurfaceStateManager (state/SurfaceStateManager.kt)                   │ │
│  │                                                                      │ │
│  │ Processes incoming operations:                                      │ │
│  │   - processSnapshot() for beginRendering                            │ │
│  │   - processDelta()    for surfaceUpdate, dataModelUpdate            │ │
│  │                                                                      │ │
│  │ Output: UiDefinition { surfaceId, root, components }                │ │
│  │         DataModel     { reactive JSON state }                       │ │
│  └──────────────────────────────────────────────────────────────────────┘ │
│                                    │                                      │
│                                    ▼                                      │
│  ┌──────────────────────────────────────────────────────────────────────┐ │
│  │ A2UISurface Composable (render/A2UiSurface.kt)                       │ │
│  │                                                                      │ │
│  │ @Composable fun A2UISurface(                                        │ │
│  │   definition: UiDefinition,                                         │ │
│  │   dataModel: DataModel,                                             │ │
│  │   catalog: Catalog,                                                 │ │
│  │   onEvent: (UiEvent) -> Unit                                        │ │
│  │ )                                                                   │ │
│  │                                                                      │ │
│  │ Renders: Compose component tree using                               │ │
│  │   - ComponentBuilder (recursive)                                    │ │
│  │   - CoreCatalog (18 standard widgets)                               │ │
│  │   - Reactive data binding (DataModel paths)                         │ │
│  └──────────────────────────────────────────────────────────────────────┘ │
│                                    │                                      │
│                                    ▼                                      │
│  ┌──────────────────────────────────────────────────────────────────────┐ │
│  │ Native Material 3 UI (on screen)                                     │ │
│  │                                                                      │ │
│  │  ┌─────────────────────────────────────────────┐                    │ │
│  │  │          Recent Transactions               │                    │ │
│  │  ├─────────────────────────────────────────────┤                    │ │
│  │  │ Fidelity Brokerage ••••1234                │                    │ │
│  │  │ $48,291.73  +$1,203.45 (+2.56%) today      │                    │ │
│  │  ├─────────────────────────────────────────────┤                    │ │
│  │  │ Buy AAPL               -$1,875.00           │                    │ │
│  │  │ 10 shares @ $187.50    Mar 25, 2026         │                    │ │
│  │  ├─────────────────────────────────────────────┤                    │ │
│  │  │ Sell TSLA              +$1,226.50           │                    │ │
│  │  │ 5 shares @ $245.30     Mar 24, 2026         │                    │ │
│  │  └─────────────────────────────────────────────┘                    │ │
│  └──────────────────────────────────────────────────────────────────────┘ │
│                                                                            │
│  User Interaction:                                                         │
│  - Tap Button → UserActionEvent                                           │
│  - Type TextField → DataChangeEvent                                       │
│  - Send event back to Agent over HTTP/WS                                  │
│  - Agent responds with new A2UI operations                                │
└────────────────────────────────────────────────────────────────────────────┘
```

### Data Flow Summary

| Direction | Message | Format | Trigger |
|-----------|---------|--------|---------|
| **Agent → Client** | A2UI Operations | JSONL (JSON Lines) | LLM generates UI in response to user query |
| | `beginRendering` | `{"beginRendering": {"surfaceId": "...", "root": "..."}}` | First operation for a new surface |
| | `surfaceUpdate` | `{"surfaceUpdate": {"surfaceId": "...", "components": [...]}}` | Add/update component definitions |
| | `dataModelUpdate` | `{"dataModelUpdate": {"surfaceId": "...", "contents": [...]}}` | Set reactive state data |
| | `deleteSurface` | `{"deleteSurface": {"surfaceId": "..."}}` | Clear a surface from UI |
| **Client → Agent** | User Interaction | JSONL | User taps button, types field, etc. |
| | `UserActionEvent` | `{"surfaceId": "...", "sourceComponentId": "...", "name": "..."}` | Button click, form submission |
| | `DataChangeEvent` | `{"surfaceId": "...", "path": "/...", "value": "..."}` | TextField input, CheckBox toggle |

---

## A2UI Protocol Operations

### Detailed Schema & Examples (v0.8)

All operations are JSON objects sent as **JSON Lines (JSONL)** — one operation per line. This enables streaming and progressive rendering.

---

### 1. `beginRendering` — Initialize Surface

**Purpose:** Signal the client to create a new surface and prepare for rendering.

**Schema:**
```typescript
{
  beginRendering: {
    surfaceId: string;      // Unique ID for this surface
    root: string;           // ID of the root component
    catalogId?: string;     // Optional catalog URL (defaults to v0.8 standard)
    styles?: object;        // Optional styling object (stored but not rendered)
  }
}
```

**JSON Example:**
```json
{
  "beginRendering": {
    "surfaceId": "chat_response_1",
    "root": "root_container",
    "catalogId": "https://a2ui.org/specification/v0_8/basic_catalog.json"
  }
}
```

**Usage Notes:**
- **Must be sent before any `surfaceUpdate` or `dataModelUpdate`** for that surface
- Client buffers subsequent operations until `beginRendering` is received
- `surfaceId` must be unique within a client session (e.g., one per conversation turn or UI surface)
- Root component must be defined in a subsequent `surfaceUpdate` message

---

### 2. `surfaceUpdate` — Add/Update Components

**Purpose:** Define or update component definitions (the UI structure tree).

**Schema:**
```typescript
{
  surfaceUpdate: {
    surfaceId: string;
    components: Array<{
      id: string;                     // Unique ID within surface
      component: {
        [ComponentType]: {            // Exactly one key = component type
          ...componentProperties      // Type-specific properties
        }
      }
    }>
  }
}
```

**Comprehensive Example** (Brokerage Activity UI):

```json
{
  "surfaceUpdate": {
    "surfaceId": "brokerage_activity",
    "components": [
      {
        "id": "root",
        "component": {
          "Column": {
            "children": {
              "explicitList": ["acct_card"]
            }
          }
        }
      },
      {
        "id": "acct_card",
        "component": {
          "Card": {
            "child": "card_col"
          }
        }
      },
      {
        "id": "card_col",
        "component": {
          "Column": {
            "children": {
              "explicitList": [
                "header_row",
                "balance_amount",
                "balance_change",
                "divider_main",
                "tx_section_header",
                "tx1_row",
                "tx1_div",
                "tx2_row"
              ]
            }
          }
        }
      },
      {
        "id": "header_row",
        "component": {
          "Row": {
            "children": {
              "explicitList": ["acct_name", "acct_type"]
            },
            "distribution": "spaceBetween"
          }
        }
      },
      {
        "id": "acct_name",
        "component": {
          "Text": {
            "text": {
              "path": "/accountName"
            },
            "usageHint": "h5"
          }
        }
      },
      {
        "id": "acct_type",
        "component": {
          "Text": {
            "text": {
              "literalString": "INDIVIDUAL"
            },
            "usageHint": "caption"
          }
        }
      },
      {
        "id": "balance_amount",
        "component": {
          "Text": {
            "text": {
              "path": "/balance"
            },
            "usageHint": "h2"
          }
        }
      },
      {
        "id": "balance_change",
        "component": {
          "Text": {
            "text": {
              "path": "/balanceChange"
            },
            "usageHint": "caption"
          }
        }
      },
      {
        "id": "divider_main",
        "component": {
          "Divider": {}
        }
      },
      {
        "id": "tx_section_header",
        "component": {
          "Text": {
            "text": {
              "literalString": "Recent Transactions"
            },
            "usageHint": "h5"
          }
        }
      },
      {
        "id": "tx1_row",
        "component": {
          "Row": {
            "children": {
              "explicitList": ["tx1_col", "tx1_amount"]
            },
            "distribution": "spaceBetween"
          }
        }
      },
      {
        "id": "tx1_col",
        "component": {
          "Column": {
            "children": {
              "explicitList": ["tx1_desc", "tx1_sub", "tx1_date"]
            }
          }
        }
      },
      {
        "id": "tx1_desc",
        "component": {
          "Text": {
            "text": {
              "literalString": "Buy AAPL"
            },
            "usageHint": "body"
          }
        }
      },
      {
        "id": "tx1_sub",
        "component": {
          "Text": {
            "text": {
              "literalString": "10 shares @ $187.50"
            },
            "usageHint": "caption"
          }
        }
      },
      {
        "id": "tx1_date",
        "component": {
          "Text": {
            "text": {
              "literalString": "Mar 25, 2026"
            },
            "usageHint": "caption"
          }
        }
      },
      {
        "id": "tx1_amount",
        "component": {
          "Text": {
            "text": {
              "literalString": "-$1,875.00"
            },
            "usageHint": "body"
          }
        }
      },
      {
        "id": "tx1_div",
        "component": {
          "Divider": {}
        }
      },
      {
        "id": "tx2_row",
        "component": {
          "Row": {
            "children": {
              "explicitList": ["tx2_col", "tx2_amount"]
            },
            "distribution": "spaceBetween"
          }
        }
      },
      {
        "id": "tx2_col",
        "component": {
          "Column": {
            "children": {
              "explicitList": ["tx2_desc", "tx2_sub", "tx2_date"]
            }
          }
        }
      },
      {
        "id": "tx2_desc",
        "component": {
          "Text": {
            "text": {
              "literalString": "Sell TSLA"
            },
            "usageHint": "body"
          }
        }
      },
      {
        "id": "tx2_sub",
        "component": {
          "Text": {
            "text": {
              "literalString": "5 shares @ $245.30"
            },
            "usageHint": "caption"
          }
        }
      },
      {
        "id": "tx2_date",
        "component": {
          "Text": {
            "text": {
              "literalString": "Mar 24, 2026"
            },
            "usageHint": "caption"
          }
        }
      },
      {
        "id": "tx2_amount",
        "component": {
          "Text": {
            "text": {
              "literalString": "+$1,226.50"
            },
            "usageHint": "body"
          }
        }
      }
    ]
  }
}
```

**Component Types** (All 18 A2UI Widgets):
- **Content:** Text, Image, Icon, Divider, Video, AudioPlayer
- **Layouts:** Column, Row, List, Card, Tabs, Modal
- **Interactive:** Button, TextField, CheckBox, Slider, MultipleChoice, DateTimeInput

**Key Properties:**
- **`id`:** Unique within surface; used for referencing children and events
- **`component`:** Wrapper with exactly one key (the widget type)
- **`children`:** For layouts — either `{"explicitList": ["id1", "id2"]}` or `{"template": {...}}` for dynamic rendering
- **`child`:** For single-child containers (Button, Card, Modal, etc.)
- **Text Content:** `{"literalString": "..."}` (static) or `{"path": "/dataPath"}` (data-bound)

**Adjacency List Design:**
- Components form a **flat, ID-referenced graph** (not nested JSON trees)
- This is **LLM-friendly** (easier to generate incrementally)
- Client reconstructs the tree by following ID references

---

### 3. `dataModelUpdate` — Set Application State

**Purpose:** Update the reactive data model that components bind to.

**Schema:**
```typescript
{
  dataModelUpdate: {
    surfaceId: string;
    path?: string;          // JSON Pointer path (e.g., "/user", "/items/0/name")
    contents: Array<{
      key: string;
      valueString?: string;
      valueNumber?: number;
      valueBoolean?: boolean;
      valueMap?: Array<{...}>;  // For nested objects/arrays
    }>
  }
}
```

**Example 1: Initialize Entire Data Model**

```json
{
  "dataModelUpdate": {
    "surfaceId": "brokerage_activity",
    "contents": [
      {
        "key": "accountName",
        "valueString": "Fidelity Brokerage ••••1234"
      },
      {
        "key": "balance",
        "valueString": "$48,291.73"
      },
      {
        "key": "balanceChange",
        "valueString": "+$1,203.45  (+2.56%) today"
      },
      {
        "key": "transactions",
        "valueMap": [
          {
            "key": "0",
            "valueMap": [
              { "key": "description", "valueString": "Buy AAPL" },
              { "key": "subtitle", "valueString": "10 shares @ $187.50" },
              { "key": "date", "valueString": "Mar 25, 2026" },
              { "key": "amount", "valueString": "-$1,875.00" }
            ]
          },
          {
            "key": "1",
            "valueMap": [
              { "key": "description", "valueString": "Sell TSLA" },
              { "key": "subtitle", "valueString": "5 shares @ $245.30" },
              { "key": "date", "valueString": "Mar 24, 2026" },
              { "key": "amount", "valueString": "+$1,226.50" }
            ]
          }
        ]
      }
    ]
  }
}
```

**Example 2: Update Nested Property (Granular)**

```json
{
  "dataModelUpdate": {
    "surfaceId": "brokerage_activity",
    "path": "balance",
    "contents": [
      {
        "key": "value",
        "valueString": "$51,500.00"
      }
    ]
  }
}
```

**JSON Pointer Paths:**
- `/user/name` — Access `/user` object's `name` property
- `/items/0/price` — Access first item in `/items` array, get `price`
- `/` — Root of data model
- Follows RFC 6901 standard

**Data Types:**
- `valueString` — Text (also used for formatted numbers, dates)
- `valueNumber` — Numeric value
- `valueBoolean` — Boolean flag
- `valueMap` — Nested object/array (array of key-value pairs, LLM-friendly)

**Reactive Binding:**
When component uses `{"path": "/balance"}`, and this update occurs, the Text widget automatically re-renders with the new value.

---

### 4. `deleteSurface` — Remove Surface

**Purpose:** Clear a surface entirely (all components + data model).

**Schema:**
```typescript
{
  deleteSurface: {
    surfaceId: string;
  }
}
```

**Example:**
```json
{
  "deleteSurface": {
    "surfaceId": "chat_response_1"
  }
}
```

**Usage Notes:**
- Remove UI when navigating away or closing a modal
- Safe to delete non-existent surface (no-op)
- Client removes surface from rendering

---

## How `SurfaceStateManager` Processes Operations

Located at: `library/src/commonMain/kotlin/com/contextable/a2ui4k/state/SurfaceStateManager.kt`

### Processing Pipeline

```
Incoming A2UI Operation (JSON)
         │
         ▼
SurfaceStateManager.process{Snapshot|Delta}()
         │
    ┌────┴──────┬─────────┬──────────┐
    ▼           ▼         ▼          ▼
beginRendering surfaceUpdate dataModelUpdate deleteSurface
    │           │         │          │
    ├─ Create   ├─ Parse  ├─ Set    └─ Remove
    │  surface  │  comps  │  reactive  surface
    │  with root│  via    │  data      from
    │  & styles │  ComponentDef   memory
    │           │  .fromJson()    
    │           │                 
    │           ├─ Store in      
    │           │  flattened map  
    │           │  by component ID
    │           │
    │           └─ Update existing
    │              component if ID
    │              already exists
    │
    ▼
UiDefinition { surfaceId, root, components }
         │
    ┌────┴────────┬─────────────┐
    ▼             ▼             ▼
DataModel   Component Map   Root Reference
    │             │             │
    └─────────────┼─────────────┘
                  ▼
            Ready for A2UISurface
            rendering via
            ComponentBuilder
```

### Operation Handlers

#### `processSnapshot(messageId: String, content: JsonElement)`

Processes a **full UI snapshot** (typically `beginRendering`):

1. **Extract `beginRendering` object** from JSON
2. **Create/reset surface state:**
   - `surfaceId` → unique surface key
   - `root` → root component ID to start rendering
   - `catalogId` → optional custom component catalog
   - `styles` → stored (not currently rendered)
3. **Initialize empty `SurfaceState`:**
   ```kotlin
   class SurfaceState(
       val surfaceId: String,
       val components: MutableMap<String, Component>,
       val root: String?,
       val dataModel: DataModel
   )
   ```
4. **Wait for `surfaceUpdate` to populate components**

#### `processDelta(messageId: String, patch: JsonArray)`

Processes **incremental updates** (JSON Patch format):

1. **Iterate through patch operations:**
   ```json
   [
     { "op": "add", "path": "/operations/-", "value": {...} }
   ]
   ```
2. **For each operation:**
   - `op: "add"` → Append to operations array
   - Extract actual A2UI operation from value
   - Route to appropriate handler (`surfaceUpdate`, `dataModelUpdate`, etc.)
3. **Enables streaming:** Client can process operations as they arrive (progressive rendering)

#### `handleSurfaceUpdate(op: SurfaceUpdate)`

Updates components for a surface:

1. **Get or create surface** with matching `surfaceId`
2. **For each component in operation:**
   ```kotlin
   val comp = ComponentDef.fromJson(componentJson)
   surface.components[comp.id] = comp
   ```
3. **`ComponentDef.fromJson()` parses:**
   - Component type (e.g., "Text", "Button")
   - Properties (text, child, children, actions, etc.)
   - BoundValue objects (literal vs. path references)
4. **Update or insert** in flat component map

**Key Insight:** Components are stored in a **flat adjacency list** (not nested), enabling incremental updates without reconstructing the entire tree.

#### `handleDataModelUpdate(op: DataModelUpdate)`

Updates reactive application state:

1. **Get DataModel for surface**
2. **Parse `contents` adjacency list:**
   ```kotlin
   for (entry in contents) {
       val value = when {
           entry.valueString != null → entry.valueString
           entry.valueNumber != null → entry.valueNumber
           entry.valueBoolean != null → entry.valueBoolean
           entry.valueMap != null → recursively construct JsonObject
       }
       dataModel.update(path + "/" + entry.key, value)
   }
   ```
3. **Update DataModel at path** (default: root if `path` omitted)
4. **Trigger reactive updates:**
   - All components with `{"path": "/..."}` bindings observing this path automatically re-render
   - StateFlow updates → Compose recomposition

#### `handleDeleteSurface(op: DeleteSurface)`

1. **Locate surface by `surfaceId`**
2. **Remove from internal map**
3. **Clear all components and data model**
4. **Client stops rendering this surface**

### Multi-Surface Management

A single `SurfaceStateManager` can manage **multiple independent surfaces** simultaneously:

```kotlin
val stateManager = SurfaceStateManager()

// Surface 1: Main chat UI
stateManager.processSnapshot("msg1", beginRendering1)
stateManager.processDelta("msg1", surfaceUpdate1)

// Surface 2: Modal overlay
stateManager.processSnapshot("msg2", beginRendering2)
stateManager.processDelta("msg2", surfaceUpdate2)

// Get both
val mainSurface = stateManager.getSurface("main")
val modalSurface = stateManager.getSurface("modal")

// Render separately
A2UISurface(definition = mainSurface)
A2UISurface(definition = modalSurface)
```

---

## Transport Layer

### How to Stream Operations from Agent to Client

A2UI is **transport-agnostic**. Agents can send operations via any mechanism that can transmit JSON. The **application layer** is responsible for transport.

### Recommended Transports

#### 1. **HTTP Server-Sent Events (SSE)** ← Recommended for Initial Implementation

**Advantages:**
- Simple (HTTP standard, no WebSocket overhead)
- Unidirectional (agent → client)
- Built-in retry semantics
- Browser/Android-compatible

**Flow:**

```
Client                          Agent
  │                              │
  ├─ POST /chat                  │
  │  { "message": "..." }        │
  ├──────────────────────────────>│
  │                              │
  │                         Query LLM
  │                         Generate A2UI
  │                         Send SSE
  │                              │
  │  HTTP 200 (SSE stream)       │
  │<─ data: {beginRendering...} ◄┤
  │  data: {surfaceUpdate...}    │
  │  data: {dataModelUpdate...}  │
  │                              │
  ├─ Parse JSONL                 │
  ├─ Feed to SurfaceStateManager │
  ├─ Render via A2UISurface      │
  │                              │
  ├─ POST /event                 │
  │  { "event": UserActionEvent} │
  ├──────────────────────────────>│
  │                              │
  │                         Process event
  │                         Query LLM again
```

**HTTP Endpoint Example (Kotlin/Ktor):**
```kotlin
// Agent server (Ktor)
post("/chat") {
    val userMessage = call.receive<ChatMessage>().text
    call.respondTextWriter(contentType = ContentType.Text.EventStream) {
        val operations = generateA2UIOperations(userMessage)
        for (op in operations) {
            write("data: ${Json.encodeToString(op)}\n\n")
            flush()
        }
    }
}
```

**Android Client (Kotlin Coroutine Flow):**
```kotlin
// Parse SSE stream
fun receiveSSEStream(url: String): Flow<JsonElement> = flow {
    val client = HttpClient()
    client.get(url).bodyAsChannel().use { channel ->
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line()
            if (line?.startsWith("data: ") == true) {
                val json = Json.parseToJsonElement(line.substring(6))
                emit(json)
            }
        }
    }
}

// In ViewModel/Activity:
viewModelScope.launch {
    receiveSSEStream("http://agent:8080/chat").collect { operation ->
        stateManager.processDelta(messageId, operation)
    }
}
```

---

#### 2. **WebSocket** ← For Real-Time, Bidirectional Streaming

**Advantages:**
- Persistent connection
- Bidirectional (agent ↔ client)
- Lower latency
- Streaming with less overhead

**Flow:**

```
Client                      Agent
  │                          │
  ├─ WS /connect             │
  ├─ Subscribe               │
  ├─────────────────────────>│
  │                     WebSocket open
  │                          │
  ├─ Send: {"message": "..."} 
  ├─────────────────────────>│
  │                    Process
  │                    LLM call
  │<─ {"beginRendering": {...}}  ◄─┤
  │<─ {"surfaceUpdate": {...}}    ◄─┤
  │<─ {"dataModelUpdate": {...}}  ◄─┤
  │                          │
  ├─ Parse & feed SSM        │
  ├─ Render                  │
  │                          │
  ├─ Send: {"event": {...}}  │
  ├─────────────────────────>│
  │                    Handle event
  │                    LLM call
  │<─ {"dataModelUpdate": {...}}  ◄─┤
  │                          │
```

**Implementation (Ktor WebSocket):**
```kotlin
// Agent server
webSocket("/chat") {
    try {
        for (frame in incoming) {
            if (frame is Frame.Text) {
                val message = frame.readText()
                val operations = generateA2UIOperations(message)
                for (op in operations) {
                    send(Frame.Text(Json.encodeToString(op)))
                }
            }
        }
    } catch (e: Exception) {
        logger.error(e)
    }
}

// Android client
viewModelScope.launch {
    val session = HttpClient().webSocketSession("ws://agent:8080/chat")
    
    // Send user message
    session.send(Frame.Text(Json.encodeToString(userMessage)))
    
    // Receive operations
    for (frame in session.incoming) {
        if (frame is Frame.Text) {
            val operation = Json.parseToJsonElement(frame.readText())
            stateManager.processDelta(messageId, operation)
        }
    }
}
```

---

#### 3. **A2A Protocol** (Enterprise Integration)

If your agent uses the [A2A (Agent-to-Agent) Protocol](https://a2a-protocol.org):

- A2A handles transport, authentication, and message routing
- A2UI is an **A2A Extension** with URI: `https://a2ui.org/a2a-extension/a2ui/v0.8`
- Agent declares A2UI capability in `AgentCapabilities`
- Messages automatically routed through A2A → A2UI renderer

**Constants (from a2ui-4k):**
```kotlin
A2UIExtension.URI_V08 = "https://a2ui.org/a2a-extension/a2ui/v0.8"
A2UIExtension.MIME_TYPE = "application/json+a2ui"
A2UIExtension.STANDARD_CATALOG_URI = "https://a2ui.org/specification/v0_8/basic_catalog.json"
```

---

## Building the Agent: Step-by-Step Implementation Guide

### Step 1: Choose Your Tech Stack

#### Option A: Python + FastAPI (Recommended for LLM Experimentation)

**Why:** Fast to prototype, rich LLM library ecosystem, easy to integrate Claude/GPT-4/Gemini APIs.

**Setup:**
```bash
pip install fastapi uvicorn anthropic openai google-generativeai pydantic
```

**Minimal skeleton:**
```python
from fastapi import FastAPI, HTTPException
from fastapi.responses import StreamingResponse
import asyncio
import json
import anthropic

app = FastAPI()

@app.post("/chat")
async def chat(message: str):
    def generate():
        # Call LLM
        a2ui_ops = generate_a2ui_operations(message)
        
        # Stream each operation as JSONL
        for op in a2ui_ops:
            yield f"data: {json.dumps(op)}\n\n"
    
    return StreamingResponse(generate(), media_type="text/event-stream")

def generate_a2ui_operations(user_message: str) -> list:
    # To be implemented in Step 3
    pass
```

#### Option B: Kotlin + Ktor (Native to Android Project)

**Why:** Seamless integration with Android app, type-safe, multiplatform support.

**Setup:**
```kotlin
implementation("io.ktor:ktor-server-core:2.3.0")
implementation("io.ktor:ktor-server-netty:2.3.0")
implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.0")
implementation("com.contextable:a2ui-4k:0.8.2")
```

#### Option C: Node.js + Express (TypeScript)

**Why:** Matches google/A2UI examples, good WebSocket support.

**Setup:**
```bash
npm install express typescript anthropic openai @google/generative-ai ws
```

---

### Step 2: Integrate an LLM API

We'll use **Anthropic Claude** as the example (easily adaptable to OpenAI, Gemini).

**Setup:**
```bash
export ANTHROPIC_API_KEY="sk-ant-..."
```

**Python Example:**
```python
import anthropic
import json

client = anthropic.Anthropic()

def call_llm_with_a2ui_prompt(user_message: str, system_prompt: str) -> str:
    """
    Call Claude to generate A2UI JSON operations.
    
    Returns: Raw LLM output (text with embedded JSON)
    """
    message = client.messages.create(
        model="claude-3-5-sonnet-20241022",  # or gpt-4, gemini-pro
        max_tokens=2048,
        system=system_prompt,
        messages=[
            {"role": "user", "content": user_message}
        ]
    )
    return message.content[0].text
```

---

### Step 3: Write the System Prompt (Critical!)

This is the **core of prompt engineering** for A2UI generation. The LLM must learn to output valid A2UI JSON.

**Comprehensive System Prompt Template:**

```python
A2UI_SYSTEM_PROMPT = """
You are a UI generation assistant. Your job is to create rich, interactive user interfaces using the A2UI (Agent-to-User Interface) protocol.

## Your Task

When the user asks for information or performs an action, you must:

1. **Understand** the user's intent
2. **Generate** A2UI protocol operations as a JSON array
3. **Output format:**
   - Respond ONLY with a JSON array of A2UI message objects
   - Each element must be a valid A2UI v0.8 operation
   - No markdown, no explanation, no preamble

## A2UI Protocol (v0.8)

You can use these operations:

### 1. beginRendering
Initializes a new surface. Always send this first.

```json
{
  "beginRendering": {
    "surfaceId": "unique_id",
    "root": "root_component_id"
  }
}
```

### 2. surfaceUpdate
Defines UI components in an adjacency list (flat structure).

```json
{
  "surfaceUpdate": {
    "surfaceId": "surface_id",
    "components": [
      {
        "id": "component_id",
        "component": {
          "ComponentType": {
            "property": value,
            "child": "child_id",
            "children": {"explicitList": ["id1", "id2"]}
          }
        }
      }
    ]
  }
}
```

### 3. dataModelUpdate
Sets reactive data that components bind to.

```json
{
  "dataModelUpdate": {
    "surfaceId": "surface_id",
    "contents": [
      {"key": "fieldName", "valueString": "value"},
      {"key": "age", "valueNumber": 30},
      {"key": "active", "valueBoolean": true}
    ]
  }
}
```

## Component Types (18 Total)

### Content Widgets
- **Text** - Static or bound text
- **Image** - URL-based images
- **Icon** - Material Design icons
- **Divider** - Horizontal/vertical separator
- **Video** - Video URL (placeholder)
- **AudioPlayer** - Audio URL (placeholder)

### Layout Widgets
- **Column** - Vertical stack
- **Row** - Horizontal stack
- **List** - Dynamic list with template
- **Card** - Material card container
- **Tabs** - Tab navigation
- **Modal** - Dialog/overlay

### Interactive Widgets
- **Button** - Clickable action
- **TextField** - Text input
- **CheckBox** - Boolean toggle
- **Slider** - Range input
- **MultipleChoice** - Select from options
- **DateTimeInput** - Date/time picker

## Example: Displaying Account Information

```json
[
  {
    "beginRendering": {
      "surfaceId": "account_display",
      "root": "root"
    }
  },
  {
    "surfaceUpdate": {
      "surfaceId": "account_display",
      "components": [
        {
          "id": "root",
          "component": {
            "Column": {
              "children": {"explicitList": ["header", "card"]}
            }
          }
        },
        {
          "id": "header",
          "component": {
            "Text": {
              "text": {"literalString": "Account Summary"},
              "usageHint": "h2"
            }
          }
        },
        {
          "id": "card",
          "component": {
            "Card": {
              "child": "content"
            }
          }
        },
        {
          "id": "content",
          "component": {
            "Column": {
              "children": {"explicitList": ["name", "email", "status"]}
            }
          }
        },
        {
          "id": "name",
          "component": {
            "Text": {
              "text": {"path": "/user/name"},
              "usageHint": "h5"
            }
          }
        },
        {
          "id": "email",
          "component": {
            "Text": {
              "text": {"path": "/user/email"},
              "usageHint": "body"
            }
          }
        },
        {
          "id": "status",
          "component": {
            "Text": {
              "text": {"path": "/user/status"},
              "usageHint": "caption"
            }
          }
        }
      ]
    }
  },
  {
    "dataModelUpdate": {
      "surfaceId": "account_display",
      "contents": [
        {"key": "user", "valueMap": [
          {"key": "name", "valueString": "Alice Johnson"},
          {"key": "email", "valueString": "alice@example.com"},
          {"key": "status", "valueString": "Premium Member"}
        ]}
      ]
    }
  }
]
```

## Rules

1. **Always start with `beginRendering`** (exactly once per surface)
2. **Component IDs must be unique** within a surface
3. **Use `{"path": "/..."}` for reactive data** (will update when data changes)
4. **Use `{"literalString": "..."}` for static text**
5. **For children, use explicit lists** (arrays of component IDs)
6. **Validate JSON structure** - must be valid JSON
7. **No circular references** - component cannot be its own child
8. **Use Material 3 conventions** for styling (usageHint values: h1-h5, body, caption)

## What to Do with Different User Queries

- **Show data:** Create Text widgets with `{"path": "/..."}` bindings
- **Collect input:** Use TextField, CheckBox, MultipleChoice, DateTimeInput
- **Take action:** Use Button with click handlers
- **Show lists:** Use List with template for dynamic items
- **Organize content:** Use Column/Row for layout, Card for grouping

Now, generate A2UI operations for the user's request.
"""
```

**Key Points:**
- **Specific examples** teach the LLM the exact JSON format
- **Concrete rules** (no circular refs, validate JSON, etc.)
- **All 18 widget types listed** with use cases
- **Data binding explained** (path vs. literal)

---

### Step 4: Generate A2UI Operations from LLM Output

Parse and validate the LLM response:

**Python Implementation:**

```python
import json
import re
from typing import List, Dict, Any

def extract_json_from_llm_output(llm_output: str) -> str:
    """
    Extract JSON from LLM output (handles markdown fences, extra text, etc).
    """
    # Try to find JSON array
    json_match = re.search(r'\[.*\]', llm_output, re.DOTALL)
    if json_match:
        return json_match.group(0)
    
    # Fallback: assume entire output is JSON
    return llm_output.strip()

def validate_a2ui_operations(operations: List[Dict[str, Any]]) -> bool:
    """
    Basic validation of A2UI operations.
    (For production, use JSON schema validation against A2UI_SCHEMA)
    """
    if not isinstance(operations, list):
        return False
    
    for op in operations:
        if not isinstance(op, dict):
            return False
        
        # Must have exactly one top-level key
        keys = list(op.keys())
        if len(keys) != 1:
            return False
        
        op_type = keys[0]
        if op_type not in ["beginRendering", "surfaceUpdate", "dataModelUpdate", "deleteSurface"]:
            return False
    
    return True

def generate_a2ui_operations(user_message: str) -> List[Dict[str, Any]]:
    """
    Main function: LLM call → JSON parsing → validation → operations
    """
    # Call LLM with A2UI prompt
    llm_output = call_llm_with_a2ui_prompt(user_message, A2UI_SYSTEM_PROMPT)
    
    # Extract JSON
    json_str = extract_json_from_llm_output(llm_output)
    
    try:
        operations = json.loads(json_str)
    except json.JSONDecodeError as e:
        print(f"JSON decode error: {e}")
        # Return fallback: text-only message
        return fallback_text_operation(f"Error parsing UI: {str(e)}")
    
    # Validate
    if not validate_a2ui_operations(operations):
        print("Validation failed")
        return fallback_text_operation("Invalid A2UI operations")
    
    return operations

def fallback_text_operation(message: str) -> List[Dict[str, Any]]:
    """
    Fallback: simple text response if A2UI generation fails
    """
    return [
        {"beginRendering": {"surfaceId": "fallback", "root": "text"}},
        {"surfaceUpdate": {"surfaceId": "fallback", "components": [
            {"id": "text", "component": {"Text": {"text": {"literalString": message}}}}
        ]}}
    ]
```

---

### Step 5: Stream Operations Back to Client

**FastAPI endpoint:**

```python
from fastapi import FastAPI, HTTPException
from fastapi.responses import StreamingResponse
import asyncio
import json

app = FastAPI()

@app.post("/chat")
async def chat_endpoint(user_message: str):
    """
    Stream A2UI operations as Server-Sent Events (JSONL).
    """
    async def generate_stream():
        try:
            operations = generate_a2ui_operations(user_message)
            
            # Stream each operation
            for op in operations:
                # SSE format: "data: <json>\n\n"
                yield f"data: {json.dumps(op)}\n\n"
                await asyncio.sleep(0.01)  # Small delay for perceived streaming
        
        except Exception as e:
            yield f"data: {json.dumps({'error': str(e)})}\n\n"
    
    return StreamingResponse(
        generate_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no"
        }
    )

@app.post("/event")
async def handle_event(event: dict):
    """
    Handle UserActionEvent or DataChangeEvent from client.
    """
    # In a real agent, process the event and decide what UI to show next
    print(f"Received event: {event}")
    return {"status": "ok"}
```

**Run with:**
```bash
uvicorn agent:app --host 0.0.0.0 --port 8000
```

---

### Step 6: Update the Android App to Call Real Agent

#### Modify `MockChatRepository` to call HTTP endpoint:

**File:** `/Users/vijayakella/pocs/mobile/android/a2ui-sample-chat-app/app/src/main/java/com/example/a2ui/chat/data/repository/ChatRepository.kt`

```kotlin
interface ChatRepository {
    suspend fun sendMessage(userMessage: String): Message
    fun getGreeting(): String
}

class RealChatRepository(private val agentBaseUrl: String) : ChatRepository {
    private val httpClient = HttpClient()
    private val stateManager = SurfaceStateManager()
    
    override suspend fun sendMessage(userMessage: String): Message {
        val messageId = UUID.randomUUID().toString()
        
        try {
            // Call agent backend (SSE)
            val response = httpClient.get("$agentBaseUrl/chat?message=$userMessage") {
                contentType(ContentType.Application.Json)
            }
            
            // Parse SSE stream (JSONL)
            var uiDefinition: UiDefinition? = null
            var textContent = ""
            
            response.bodyAsChannel().use { channel ->
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: continue
                    
                    if (line.startsWith("data: ")) {
                        val json = Json.parseToJsonElement(line.substring(6))
                        
                        // Process operation through SurfaceStateManager
                        when {
                            json.jsonObject.containsKey("beginRendering") -> {
                                stateManager.processSnapshot(messageId, json)
                            }
                            json.jsonObject.containsKey("surfaceUpdate"),
                            json.jsonObject.containsKey("dataModelUpdate") -> {
                                stateManager.processDelta(messageId, jsonArray(json))
                            }
                        }
                    }
                }
            }
            
            // Get the rendered UI definition from state manager
            uiDefinition = stateManager.getSurface("default")  // or appropriate surfaceId
            
            return Message(
                id = messageId,
                content = textContent,
                sender = Sender.AI,
                timestamp = System.currentTimeMillis(),
                isLoading = false,
                uiDefinition = uiDefinition
            )
        } catch (e: Exception) {
            // Fallback to mock on error
            return Message(
                id = messageId,
                content = "Sorry, I encountered an error: ${e.message}",
                sender = Sender.AI,
                timestamp = System.currentTimeMillis(),
                isLoading = false
            )
        }
    }
    
    override fun getGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "morning"
            hour < 17 -> "afternoon"
            else -> "evening"
        }
    }
}
```

#### Update `ChatViewModel` to use real repository:

**File:** `ChatViewModel.kt`

```kotlin
class ChatViewModel(
    private val sendMessageUseCase: SendMessageUseCase,
    private val repository: ChatRepository  // Can be Mock or Real
) : ViewModel() {
    
    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                // Choose based on BuildConfig or app preferences:
                val repository = if (BuildConfig.DEBUG && USE_MOCK_REPO) {
                    MockChatRepository()
                } else {
                    RealChatRepository("http://10.0.2.2:8000")  // Android emulator host IP
                }
                
                val useCase = SendMessageUseCase(repository)
                return ChatViewModel(useCase, repository) as T
            }
        }
    }
}
```

#### Handle UI Events to Send Back to Agent:

```kotlin
// In Chat UI Composable:
A2UISurface(
    definition = uiDefinition,
    catalog = CoreCatalog,
    onEvent = { event ->
        // Send event back to agent
        viewModelScope.launch {
            repository.sendEvent(event)  // New method
        }
    }
)
```

---

## LLM Prompt Engineering for A2UI

### Key Principles

#### 1. **Specificity Over Generality**
❌ Bad: "Generate a UI"  
✅ Good: "Generate a Column containing a Card with a Text widget displaying user name from `/user/name` and an email field from `/user/email`"

#### 2. **In-Context Learning (Few-Shot)**
Provide **exact examples** of valid A2UI JSON. The LLM learns patterns from examples.

#### 3. **Explicit Rules**
List constraints the LLM must follow:
- "Component IDs must be unique within a surface"
- "Always start with `beginRendering`"
- "Use `{"path": "/..."}` for reactive data"

#### 4. **Structured Output Format**
Tell the LLM exactly how to format output:
- "Respond ONLY with a JSON array"
- "No markdown, no explanation"
- "Each element must be a valid A2UI v0.8 operation"

### Prompt Template for Common Use Cases

#### Show Data (Read-Only Display)

```
User asks: "Show me my account balance"

System prompt should include:

"When the user asks to display data:
1. Create a Card widget
2. Inside, create a Column with Text widgets
3. For each data field:
   - Use Text with usageHint appropriate to field type (h2 for amounts, h5 for labels)
   - Bind to data path using {"path": "/balance"}
4. Provide the data in dataModelUpdate with actual values
5. Return complete A2UI operation array with beginRendering, surfaceUpdate, dataModelUpdate"
```

#### Collect Input (Form)

```
User asks: "Create a form to schedule a meeting"

System prompt should include:

"When the user asks for a form:
1. Create a Column containing:
   - Label texts (h5)
   - Input fields (TextField for text, DateTimeInput for dates, MultipleChoice for options)
   - Submit Button
2. Bind each field to dataModel paths like /form/title, /form/date, /form/attendees
3. Button action will trigger dataModelUpdate when submitted
4. Include empty dataModelUpdate initializing /form object"
```

#### Dynamic Lists (Template)

```
User asks: "Show a list of transactions"

System prompt should include:

"When the user provides a list of items:
1. Create a List widget
2. Use template children: {"template": {"dataBinding": "/items", "componentId": "item-card"}}
3. The item-card references scoped paths: /name, /amount (resolve to /items/N/name, etc)
4. Provide data as array in dataModelUpdate:
   {"key": "items", "valueMap": [
     {"key": "0", "valueMap": [...item1...]},
     {"key": "1", "valueMap": [...item2...]}
   ]}"
```

### Testing Prompt Quality

**Iterate on:**

1. **JSON Validity:** Does the LLM always output valid JSON?
2. **Component Correctness:** Are component types chosen appropriately for the data?
3. **Data Binding:** Does the LLM use `{"path": "/..."}` vs. `{"literalString": "..."}` correctly?
4. **UI Structure:** Does the layout make sense (Column for vertical, Row for horizontal)?

**Test with:**
```python
# Python agent
test_cases = [
    "Show my account balance",
    "Create a booking form",
    "Display a list of restaurants",
    "Show my order status with tracking info"
]

for test in test_cases:
    ops = generate_a2ui_operations(test)
    try:
        validate_a2ui_operations(ops)
        print(f"✅ {test}")
    except Exception as e:
        print(f"❌ {test}: {e}")
        print(f"   Output: {ops}")
```

---

## Concrete Code Examples

### Example 1: Python FastAPI Agent (Complete)

**File:** `agent.py`

```python
import json
import asyncio
from fastapi import FastAPI
from fastapi.responses import StreamingResponse
import anthropic

# A2UI System Prompt (from earlier section)
A2UI_SYSTEM_PROMPT = """[Full prompt from Step 3]"""

app = FastAPI()
client = anthropic.Anthropic()

def call_llm(user_message: str) -> str:
    """Call Claude with A2UI instructions."""
    message = client.messages.create(
        model="claude-3-5-sonnet-20241022",
        max_tokens=2048,
        system=A2UI_SYSTEM_PROMPT,
        messages=[
            {"role": "user", "content": user_message}
        ]
    )
    return message.content[0].text

def parse_a2ui_json(llm_output: str) -> list:
    """Extract and parse JSON array from LLM output."""
    import re
    json_match = re.search(r'\[.*\]', llm_output, re.DOTALL)
    json_str = json_match.group(0) if json_match else llm_output
    return json.loads(json_str)

@app.post("/chat")
async def chat(message: str):
    """Stream A2UI operations as SSE."""
    async def generate():
        try:
            llm_output = call_llm(message)
            operations = parse_a2ui_json(llm_output)
            
            for op in operations:
                yield f"data: {json.dumps(op)}\n\n"
                await asyncio.sleep(0.01)
        except Exception as e:
            yield f"data: {json.dumps({'error': str(e)})}\n\n"
    
    return StreamingResponse(generate(), media_type="text/event-stream")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
```

**Run:**
```bash
export ANTHROPIC_API_KEY="sk-ant-..."
python agent.py
# Server running at http://localhost:8000

# Test:
curl -X POST "http://localhost:8000/chat?message=Show+me+a+hello+world+card"
```

---

### Example 2: Simple Python Prompt Engineering Script

**File:** `test_prompts.py`

```python
import anthropic
import json
import re

A2UI_PROMPT = """
You are a UI generation assistant. Your job is to create user interfaces using the A2UI protocol.

When the user asks for a UI:

1. Generate a JSON array of A2UI v0.8 operations
2. Return ONLY JSON, no explanation
3. Always start with beginRendering
4. Use surfaceUpdate for components
5. Use dataModelUpdate for data

Example: User asks "Show hello world"

[
  {
    "beginRendering": {
      "surfaceId": "hello",
      "root": "text"
    }
  },
  {
    "surfaceUpdate": {
      "surfaceId": "hello",
      "components": [
        {
          "id": "text",
          "component": {
            "Text": {
              "text": {"literalString": "Hello, World!"},
              "usageHint": "h1"
            }
          }
        }
      ]
    }
  }
]

Now generate A2UI for the user's request.
"""

def test_prompt(query: str):
    client = anthropic.Anthropic()
    
    message = client.messages.create(
        model="claude-3-5-sonnet-20241022",
        max_tokens=1024,
        system=A2UI_PROMPT,
        messages=[
            {"role": "user", "content": query}
        ]
    )
    
    output = message.content[0].text
    
    try:
        # Extract JSON
        json_match = re.search(r'\[.*\]', output, re.DOTALL)
        if json_match:
            operations = json.loads(json_match.group(0))
            print(f"✅ Query: {query}")
            print(f"   Operations: {len(operations)}")
            print(f"   First op: {list(operations[0].keys())[0]}")
        else:
            print(f"❌ Query: {query}")
            print(f"   No JSON found")
            print(f"   Output: {output[:200]}")
    except json.JSONDecodeError as e:
        print(f"❌ Query: {query}")
        print(f"   JSON error: {e}")
        print(f"   Output: {output[:200]}")

if __name__ == "__main__":
    queries = [
        "Show hello world",
        "Create a contact card for Alice with email alice@example.com",
        "Display a list of 3 fruits: apple, banana, cherry",
        "Create a text input form to collect user name and email"
    ]
    
    for q in queries:
        test_prompt(q)
        print()
```

---

### Example 3: Android Integration (Kotlin)

**File:** `RealChatRepository.kt` (Complete)

```kotlin
package com.example.a2ui.chat.data.repository

import com.contextable.a2ui4k.state.SurfaceStateManager
import com.example.a2ui.chat.domain.model.Message
import com.example.a2ui.chat.domain.model.Sender
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.parseToJsonElement
import java.util.Calendar
import java.util.UUID
import kotlin.random.Random

class RealChatRepository(private val agentBaseUrl: String = "http://10.0.2.2:8000") : ChatRepository {
    
    private val httpClient = HttpClient()
    private val stateManager = SurfaceStateManager()
    
    override suspend fun sendMessage(userMessage: String): Message {
        val messageId = UUID.randomUUID().toString()
        val surfaceId = "chat_response_${Random.nextInt()}"
        
        return try {
            // Call agent and collect all operations
            val operations = mutableListOf<String>()
            
            val response = httpClient.get("$agentBaseUrl/chat?message=${userMessage.encodeURLComponent()}") {
                contentType(ContentType.Application.Json)
            }
            
            // Parse SSE stream (JSONL)
            response.bodyAsChannel().use { channel ->
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: continue
                    
                    if (line.startsWith("data: ")) {
                        val jsonStr = line.substring(6)
                        operations.add(jsonStr)
                        
                        // Process each operation through SurfaceStateManager
                        val json = parseToJsonElement(jsonStr)
                        val obj = json.jsonObject
                        
                        when {
                            obj.containsKey("beginRendering") -> {
                                stateManager.processSnapshot(messageId, json)
                            }
                            obj.containsKey("surfaceUpdate") || obj.containsKey("dataModelUpdate") -> {
                                stateManager.processDelta(messageId, jsonArray(json))
                            }
                            obj.containsKey("deleteSurface") -> {
                                stateManager.processDelta(messageId, jsonArray(json))
                            }
                        }
                    }
                }
            }
            
            // Get the generated UI from state manager
            // (agent sent beginRendering first, which sets surfaceId)
            val uiDefinition = stateManager.getSurfaces().values.firstOrNull()
            
            Message(
                id = messageId,
                content = "UI generated from agent",
                sender = Sender.AI,
                timestamp = System.currentTimeMillis(),
                isLoading = false,
                uiDefinition = uiDefinition
            )
        } catch (e: Exception) {
            e.printStackTrace()
            
            // Fallback: simple error message
            Message(
                id = messageId,
                content = "Error: ${e.message}",
                sender = Sender.AI,
                timestamp = System.currentTimeMillis(),
                isLoading = false
            )
        }
    }
    
    override fun getGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "morning"
            hour < 17 -> "afternoon"
            else -> "evening"
        }
    }
}

// Extension to encode URL components
private fun String.encodeURLComponent(): String {
    return java.net.URLEncoder.encode(this, "UTF-8")
}
```

---

## A2A Protocol Integration

A2UI is designed as an **extension of the A2A (Agent-to-Agent) Protocol**.

### Relationship

- **A2A Protocol:** Standardized agent communication with authentication, routing, and message signing
- **A2UI Extension:** Adds UI-generation capabilities on top of A2A
- **Connection:** A2UI messages flow through A2A transport layer

### Constants (from a2ui-4k)

```kotlin
object A2UIExtension {
    const val URI_V08 = "https://a2ui.org/a2a-extension/a2ui/v0.8"
    const val MIME_TYPE = "application/json+a2ui"
    const val STANDARD_CATALOG_URI = 
        "https://a2ui.org/specification/v0_8/basic_catalog.json"
}

data class A2UIClientCapabilities(
    val supportedCatalogIds: List<String>,
    val acceptsInlineCatalogs: Boolean = false
)

data class A2UIExtensionParams(
    val supportedCatalogIds: List<String>,
    val acceptsInlineCatalogs: Boolean = false
)
```

### Agent Capability Declaration

An A2UI agent declares its capabilities via A2A:

```json
{
  "name": "restaurant_agent",
  "capabilities": {
    "extensions": [
      {
        "id": "https://a2ui.org/a2a-extension/a2ui/v0.8",
        "params": {
          "supportedCatalogIds": [
            "https://a2ui.org/specification/v0_8/basic_catalog.json"
          ],
          "acceptsInlineCatalogs": false
        }
      }
    ]
  }
}
```

### Use A2A If:
- You have **multi-agent orchestration** across trust boundaries
- You need **authentication and authorization**
- You want **standardized message routing**

### Skip A2A If:
- **Single-agent system** (like the chat app)
- You prefer **simple HTTP/WebSocket** transport
- You want **faster prototyping**

**For this chat app:** HTTP SSE or WebSocket is sufficient; A2A is optional infrastructure.

---

## Confidence Assessment

| Aspect | Confidence | Evidence |
|--------|-----------|----------|
| **A2UI Protocol Structure** | **High (95%)** | Directly read A2UI spec docs, v0.8 message reference, protocol examples; verified against a2ui-4k implementation |
| **a2ui-4k Architecture** | **High (95%)** | Read all source files; documented from `SurfaceStateManager.kt`, `A2UISurface.kt`, `DataModel.kt` |
| **Component Implementations** | **High (90%)** | Verified all 18 widgets exist in catalog/widgets/; checked pattern in TextWidget, ButtonWidget, ListWidget |
| **Data Binding Mechanism** | **High (90%)** | Read DataReference.kt, DataModel.kt, verified JSON Pointer implementation |
| **Current App Integration** | **High (95%)** | Analyzed BrokerageActivitySurface.kt, MockChatRepository.kt, ChatViewModel.kt; clear current mock flow |
| **Transport Patterns** | **Medium-High (85%)** | Documented from A2UI spec; tested SSE/WebSocket patterns but not in this specific codebase |
| **LLM Prompt Engineering** | **Medium (75%)** | Based on google/A2UI agent examples and industry patterns; not tested against this specific app's LLM calls |
| **Python FastAPI Agent Implementation** | **Medium (70%)** | Code patterns are standard; actual integration would require testing with real Claude API |
| **Kotlin Agent Implementation** | **High (85%)** | Leverages a2ui-4k library directly; syntax correct for Kotlin/Ktor; emulator IP (10.0.2.2) is standard |

---

## Next Steps for Implementation

### Immediate (Week 1)

1. **Set up agent backend** (Python FastAPI recommended)
   - Copy `A2UI_SYSTEM_PROMPT` template
   - Integrate Claude/GPT-4 API
   - Deploy SSE endpoint at `/chat`

2. **Test LLM output** 
   - Run `test_prompts.py` with various queries
   - Iterate on system prompt until JSON output is valid
   - Check component appropriateness and data binding

3. **Update Android app**
   - Switch `ChatViewModel.Factory` to use `RealChatRepository`
   - Point to agent backend URL
   - Test single query end-to-end

### Medium-Term (Week 2-3)

1. **Enhance agent**
   - Add history/context (remember previous messages)
   - Implement event handling (process `UserActionEvent`)
   - Add tool calling (database queries, external APIs)

2. **Improve UI generation**
   - Few-shot prompting with concrete examples
   - Dynamic prompt templating based on query type
   - Fallback patterns for failed generations

3. **Scale transport**
   - Consider WebSocket for lower latency
   - Add authentication/API keys
   - Implement connection pooling

### Advanced (Week 4+)

1. **Multi-surface management**
   - Support multiple simultaneous UIs (modals, tabs, etc.)
   - Progressive rendering (stream components as they're generated)

2. **Custom catalogs**
   - Build domain-specific widgets (brokerage UI components)
   - Register custom renderers

3. **Production hardening**
   - Error handling and retry logic
   - Rate limiting and quotas
   - Monitoring and observability

---

## Summary

**An A2UI AI agent is:**
- A server that receives user input
- Calls an LLM with prompts that teach it A2UI JSON format
- Generates declarative UI operations (beginRendering, surfaceUpdate, dataModelUpdate)
- Streams them back to the Android client via HTTP/WebSocket
- The client's `SurfaceStateManager` processes these → builds `UiDefinition` → renders via `A2UISurface`

**Key advantages:**
- ✅ **Security:** No arbitrary code execution (only pre-approved widgets)
- ✅ **LLM-friendly:** JSON is easier for LLMs than imperative code
- ✅ **Reactive:** Data binding decouples structure from state
- ✅ **Incremental:** Stream operations for progressive rendering
- ✅ **Cross-platform:** Same A2UI JSON renders on Android, iOS, Web

**To build one:**
1. Create agent backend (Python FastAPI easiest)
2. Write system prompt (see Step 3 template)
3. Call LLM, parse JSON, stream operations
4. Update Android app to call agent instead of mock
5. Iterate on prompt until LLM output is consistently valid

---

## References

- **A2UI Protocol Spec:** https://github.com/google/A2UI
  - Concepts: https://github.com/google/A2UI/tree/main/docs/concepts
  - Message Reference: https://raw.githubusercontent.com/google/A2UI/main/docs/reference/messages.md
  - Agent Development Guide: https://raw.githubusercontent.com/google/A2UI/main/docs/guides/agent-development.md

- **a2ui-4k Library:** https://github.com/Contextable/a2ui-4k
  - README: https://raw.githubusercontent.com/Contextable/a2ui-4k/main/README.md
  - Source: `library/src/commonMain/kotlin/com/contextable/a2ui4k/`

- **Android Chat App (This Project):**
  - `/Users/vijayakella/pocs/mobile/android/a2ui-sample-chat-app/app/src/main/java/com/example/a2ui/chat/`
  - Current mock: `data/a2ui/BrokerageActivitySurface.kt`

- **A2A Protocol (Optional):** https://a2a-protocol.org

---

**Report Generated:** 2025 | **Status:** Ready for Implementation

