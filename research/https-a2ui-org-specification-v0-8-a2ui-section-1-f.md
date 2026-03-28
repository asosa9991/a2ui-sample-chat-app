# A2UI Specification v0.8 — Section 1: Foundational Architecture and Data Flow

## Executive Summary

The A2UI (Agent-to-UI) v0.8 specification defines a protocol for AI agents to generate dynamic, native user interfaces at runtime. It uses a **flat adjacency-list component model** (not nested trees), a **reactive data model** with JSON Pointer bindings, and a **catalog-based rendering system** with 18 standard widgets. The protocol streams operations (beginRendering → surfaceUpdate → dataModelUpdate) as JSONL, enabling progressive rendering. Events flow back from client to agent via typed `UiEvent` objects. The reference implementation is **a2ui-4k** (Kotlin Multiplatform, v0.8.2) by Contextable, rendering on Android, iOS, JVM/Desktop, and JavaScript via Compose Multiplatform.

**Specification URL:** https://a2ui.org/specification/v0.8-a2ui/
**Reference Implementation:** [Contextable/a2ui-4k](https://github.com/Contextable/a2ui-4k) (v0.8.2, Maven Central)

---

## 1. Core Data Structures

### 1.1 UiDefinition — The Root Container

The `UiDefinition` is the complete UI state for a single rendering surface[^1]:

```kotlin
@Serializable
data class UiDefinition(
    val surfaceId: String,                              // Unique surface identifier
    val components: Map<String, Component> = emptyMap(), // Flat map: ID → Component
    val root: String? = null,                           // Root component ID (rendering entry point)
    val catalogId: String? = null                       // Which widget catalog to use
)
```

**Key Design Decision:** Components are stored in a **flat map**, not a nested tree. Parent-child relationships are expressed via ID references in `children`/`child` properties. This adjacency-list model is:
- **LLM-friendly:** Easier for language models to generate incrementally
- **Update-efficient:** Single components can be added/replaced without tree reconstruction
- **Stream-compatible:** Components can arrive out of order via JSONL streaming

### 1.2 Component — The Building Block

Each `Component` represents one widget instance[^2]:

```kotlin
@Serializable
data class Component(
    val id: String,                                          // Unique within surface
    val componentProperties: Map<String, JsonObject> = emptyMap(), // Widget type → config
    val weight: Int? = null                                  // Flex weight for layouts
) {
    val widgetType: String? get() = componentProperties.keys.firstOrNull()
    val widgetData: JsonObject? get() = componentProperties.values.firstOrNull()
}
```

The `componentProperties` map always has **exactly one entry** where:
- **Key** = widget type name (e.g., `"Text"`, `"Button"`, `"Column"`)
- **Value** = widget-specific configuration as `JsonObject`

**JSON representation:**
```json
{
  "id": "balance_text",
  "componentProperties": {
    "Text": {
      "text": {"literalString": "$48,291.73"},
      "usageHint": "h2"
    }
  }
}
```

### 1.3 DataReference — Type-Safe Data Binding

Values in component properties can be either **literal** or **path-bound**[^3]:

| Type | JSON | Purpose |
|------|------|---------|
| `LiteralString` | `{"literalString": "Hello"}` | Static text value |
| `LiteralNumber` | `{"literalNumber": 42.5}` | Static numeric value |
| `LiteralBoolean` | `{"literalBoolean": true}` | Static boolean value |
| `PathString` | `{"path": "/user/name"}` | Bound to DataModel via JSON Pointer |
| `PathNumber` | `{"path": "/user/age"}` | Bound to numeric DataModel value |
| `PathBoolean` | `{"path": "/user/active"}` | Bound to boolean DataModel value |

**Children references** support two modes:
- **Explicit:** `{"explicitList": ["child1", "child2"]}` — static list of component IDs
- **Template:** `{"template": {"componentId": "item_tpl", "dataBinding": "/items"}}` — data-driven list rendering

---

## 2. Protocol Operations

The A2UI protocol uses **four operations** streamed as JSONL (one JSON object per line)[^4]:

```
┌──────────────────┐     JSONL Stream      ┌──────────────────┐
│                  │ ─────────────────────▶ │                  │
│   AI Agent       │  beginRendering        │   A2UI Client    │
│   (LLM Server)   │  surfaceUpdate         │   (Android App)  │
│                  │  dataModelUpdate        │                  │
│                  │ ◀───────────────────── │                  │
│                  │  UiEvent (user action)  │                  │
└──────────────────┘                        └──────────────────┘
```

### 2.1 `beginRendering` — Initialize Surface

**Must be the first operation** for any surface. Creates/resets surface state:

```json
{
  "beginRendering": {
    "surfaceId": "account_view",
    "root": "root_container",
    "catalogId": "https://a2ui.org/specification/v0_8/basic_catalog.json"
  }
}
```

### 2.2 `surfaceUpdate` — Define/Update Components

Adds or replaces components in the surface's flat component map:

```json
{
  "surfaceUpdate": {
    "surfaceId": "account_view",
    "components": [
      {
        "id": "root",
        "component": {
          "Column": {
            "children": {"explicitList": ["card"]}
          }
        }
      },
      {
        "id": "card",
        "component": {
          "Card": {"child": "card_content"}
        }
      }
    ]
  }
}
```

### 2.3 `dataModelUpdate` — Set Reactive State

Updates the DataModel that components bind to via JSON Pointer paths:

```json
{
  "dataModelUpdate": {
    "surfaceId": "account_view",
    "contents": [
      {"key": "accountName", "valueString": "Fidelity ••••1234"},
      {"key": "balance", "valueString": "$48,291.73"},
      {
        "key": "transactions",
        "valueMap": [
          {
            "key": "0",
            "valueMap": [
              {"key": "desc", "valueString": "Buy AAPL"},
              {"key": "amount", "valueString": "-$1,875.00"}
            ]
          }
        ]
      }
    ]
  }
}
```

### 2.4 `deleteSurface` — Tear Down

Removes a surface and all associated state:

```json
{"deleteSurface": {"surfaceId": "account_view"}}
```

---

## 3. Widget Catalog (18 Standard Widgets)

### Content Widgets (6)

| Widget | Key Properties | Notes |
|--------|---------------|-------|
| **Text** | `text` (BoundValue), `usageHint` (h1-h5/body/caption) | Supports basic markdown. **Critical: property is `text`, NOT `content`**[^5] |
| **Image** | `imageUrl`, `fitMode` | Coil 3-based async loading |
| **Icon** | `iconName` | Material Design icon set |
| **Divider** | `axis` (horizontal/vertical) | Separator line |
| **Video** | `url` | Placeholder in v0.8.2 |
| **AudioPlayer** | `url` | Placeholder in v0.8.2 |

### Layout Widgets (6)

| Widget | Key Properties | Notes |
|--------|---------------|-------|
| **Column** | `children`, `distribution`, `alignment` | Vertical stack. Distribution: start/center/end/spaceBetween/spaceAround/spaceEvenly |
| **Row** | `children`, `distribution`, `alignment` | Horizontal stack. Same distribution options |
| **List** | `children` (explicit or template) | Scrollable, supports data-bound template rendering |
| **Card** | `child` | Material 3 elevated container, 8dp padding |
| **Tabs** | `tabs` (with titles), `content` | Tab navigation with content switching |
| **Modal** | `trigger`, `child` | Dialog overlay |

### Interactive Widgets (6)

| Widget | Key Properties | Notes |
|--------|---------------|-------|
| **Button** | `child`, `actions`, `style` | Emits `UserActionEvent` with resolved context |
| **TextField** | `placeholder`, `textFieldType`, two-way binding | Types: text/email/number/password. Emits `DataChangeEvent` |
| **CheckBox** | `label`, two-way binding | Boolean toggle, emits `DataChangeEvent` |
| **Slider** | `min`, `max`, two-way binding | Range input |
| **MultipleChoice** | `options`, `isMultiSelect` | Single/multi-select |
| **DateTimeInput** | `format` (date/time/both) | Platform date/time picker |

---

## 4. Rendering Pipeline

The `A2UISurface` composable drives all rendering[^6]:

```kotlin
@Composable
fun A2UISurface(
    definition: UiDefinition,
    modifier: Modifier = Modifier,
    dataModel: DataModel = rememberDataModel(),
    catalog: Catalog,
    onEvent: (UiEvent) -> Unit = {}
)
```

### Rendering Flow

```
A2UISurface receives UiDefinition
    │
    ├─ 1. Observe DataModel (StateFlow → triggers recomposition)
    │
    ├─ 2. Create DataContext(basePath="") from DataModel
    │
    ├─ 3. Provide LocalUiDefinition via CompositionLocal
    │
    └─ 4. Call ComponentBuilder(rootId)
              │
              ├─ Resolve Component by ID from components map
              │
              ├─ Extract widgetType (first key of componentProperties)
              │
              ├─ Look up CatalogItem in catalog[widgetType]
              │
              └─ Call catalogItem.compose(componentId, widgetData, buildChild, dataContext, onEvent)
                    │
                    ├─ Parse DataReferences (literal values or path bindings)
                    │
                    ├─ Resolve paths via DataContext → DataModel
                    │
                    ├─ Render native Compose widget (Text, Card, Column, etc.)
                    │
                    └─ For children/child references:
                          └─ Call buildChild(childId) → recursive ComponentBuilder
                                └─ Template items get scoped DataContext (basePath="/items/0")
```

### Catalog System

```kotlin
class CatalogItem(
    val name: String,  // Widget type identifier
    val compose: @Composable (
        componentId: String,
        data: JsonObject,
        buildChild: ChildBuilder,
        dataContext: DataContext,
        onEvent: EventDispatcher
    ) -> Unit
)

val CoreCatalog: Catalog = Catalog.of(
    id = "standard",
    TextWidget, ImageWidget, IconWidget, DividerWidget, VideoWidget, AudioPlayerWidget,
    ColumnWidget, RowWidget, ListWidget, CardWidget, TabsWidget, ModalWidget,
    ButtonWidget, TextFieldWidget, CheckBoxWidget, SliderWidget,
    MultipleChoiceWidget, DateTimeInputWidget
)
```

Custom widgets can be added by creating a `CatalogItem` and combining with `CoreCatalog + customCatalog`[^7].

---

## 5. DataModel — Reactive State Store

The `DataModel` provides reactive, JSON-pointer-addressable state[^8]:

```kotlin
class DataModel(initialData: JsonObject = JsonObject(emptyMap())) {
    val data: StateFlow<JsonObject>           // Full state, triggers Compose recomposition
    
    fun setData(newData: JsonObject)          // Replace entire state
    fun update(path: String, value: JsonElement) // Update at JSON Pointer path
    
    fun getString(path: String): String?      // Type-safe getters
    fun getNumber(path: String): Double?
    fun getBoolean(path: String): Boolean?
    fun getStringList(path: String): List<String>?
    
    fun createContext(basePath: String): DataContext  // Scoped for template rendering
    fun observePath(path: String): StateFlow<JsonElement?> // Per-path observation
}
```

**Path syntax:** RFC 6901 JSON Pointers (e.g., `/user/name`, `/transactions/0/amount`)

**Compose integration:** Uses `StateFlow` → `collectAsState()` for automatic recomposition when data changes.

---

## 6. Event System

Two event types flow from client back to agent[^9]:

### UserActionEvent (Button clicks, form submissions)

```kotlin
data class UserActionEvent(
    val name: String,               // Action identifier (e.g., "buy_stock")
    override val surfaceId: String,
    val sourceComponentId: String,  // Component ID (includes ":itemN" suffix for template items)
    val timestamp: String,          // ISO 8601
    val context: JsonObject? = null // Resolved data from action's context bindings
) : UiEvent()
```

### DataChangeEvent (Input changes)

```kotlin
data class DataChangeEvent(
    override val surfaceId: String,
    val path: String,               // JSON Pointer to changed field
    val value: String               // New value as string
) : UiEvent()
```

**Two-way binding:** Interactive widgets (TextField, CheckBox, Slider) automatically update the DataModel AND emit `DataChangeEvent` to the agent.

---

## 7. Complete Data Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                           AI AGENT (Server)                         │
│                                                                     │
│  User Query → LLM generates A2UI operations                        │
│  ┌──────────┐   ┌───────────────┐   ┌─────────────────┐           │
│  │ begin    │──▶│ surfaceUpdate │──▶│ dataModelUpdate  │           │
│  │ Rendering│   │ (components)  │   │ (reactive state) │           │
│  └──────────┘   └───────────────┘   └─────────────────┘           │
│       │                │                     │                     │
│       └────────────────┼─────────────────────┘                     │
│                        │  JSONL Stream                              │
└────────────────────────┼───────────────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        ANDROID CLIENT                               │
│                                                                     │
│  SurfaceStateManager                                                │
│       │                                                             │
│       ├─ processSnapshot() → builds UiDefinition                    │
│       └─ processDelta()    → updates existing UiDefinition          │
│              │                                                      │
│              ▼                                                      │
│  ┌─────────────────────────────┐                                   │
│  │       A2UISurface           │                                   │
│  │  ┌────────────┐             │                                   │
│  │  │ DataModel  │──observe──▶ Recomposition                       │
│  │  └────────────┘             │                                   │
│  │  ┌────────────┐             │                                   │
│  │  │  Catalog   │──lookup──▶ Widget rendering                     │
│  │  └────────────┘             │                                   │
│  │  ┌─────────────────────┐    │                                   │
│  │  │ ComponentBuilder    │    │                                   │
│  │  │  root → Column      │    │                                   │
│  │  │    → Card           │    │                                   │
│  │  │      → Text         │    │                                   │
│  │  │      → Row          │    │                                   │
│  │  │        → Text Text  │    │                                   │
│  │  └─────────────────────┘    │                                   │
│  └────────────────┬────────────┘                                   │
│                   │                                                 │
│                   ▼                                                 │
│  User taps Button → UserActionEvent                                 │
│  User types text  → DataChangeEvent                                 │
│       │                                                             │
│       └──────────────────────────────────────────────────────────▶  │
│                          onEvent callback                           │
└─────────────────────────────────────────────────────────────────────┘
                         │
                         ▼
              Back to AI Agent (HTTP/WebSocket)
              Agent processes event → new operations
```

---

## 8. Implementation in This Project

### Agent Server (`agent/agent.py`)

The FastAPI server generates A2UI JSON using a simplified approach — instead of streaming individual operations, it returns a complete `UiDefinition` JSON in the response:

```json
{
  "text": "Here's your account activity:",
  "uiDefinition": {
    "surfaceId": "response_abc123",
    "root": "root",
    "components": { ... }
  }
}
```

The system prompt (`agent/system_prompt.py`) instructs the LLM to output valid A2UI component JSON following the v0.8 schema[^10].

### Android Client (`app/`)

The client deserializes the response into `UiDefinition` via `AgentResponseDto.toDomain()` and renders using `A2UISurface`:

```kotlin
// MessageBubble.kt
A2UISurface(
    definition = message.uiDefinition,
    dataModel = rememberDataModel(),
    catalog = CoreCatalog,
    onEvent = {},
    modifier = Modifier.fillMaxWidth()
)
```

Currently using **snapshot mode** (full UiDefinition per response) rather than incremental operation streaming[^11].

---

## Confidence Assessment

| Aspect | Confidence | Source |
|--------|-----------|--------|
| UiDefinition/Component model | ✅ High | Extracted from a2ui-4k source code |
| 18 widget catalog | ✅ High | CoreCatalog.kt source |
| Protocol operations (beginRendering, surfaceUpdate, dataModelUpdate) | ✅ High | A2UiOperation.kt source + library docs |
| Rendering pipeline | ✅ High | A2UiSurface.kt source |
| DataModel/DataContext | ✅ High | DataModel.kt source |
| Event system | ✅ High | UiEvent.kt source |
| Official spec details beyond source code | ⚠️ Medium | a2ui.org spec URL not directly fetchable; inferred from implementation |

---

## Footnotes

[^1]: `UiDefinition.kt` — `/tmp/a2ui-inspect/commonMain/com/contextable/a2ui4k/model/UiDefinition.kt`
[^2]: `Component.kt` — `/tmp/a2ui-inspect/commonMain/com/contextable/a2ui4k/model/Component.kt`
[^3]: `DataReference.kt` — `/tmp/a2ui-inspect/commonMain/com/contextable/a2ui4k/model/DataReference.kt`
[^4]: `A2UiOperation.kt` — `/tmp/a2ui-inspect/commonMain/com/contextable/a2ui4k/model/A2UiOperation.kt`
[^5]: `TextWidget.kt` — EXPECTED_PROPERTIES = setOf("text", "usageHint"), NOT "content"
[^6]: `A2UiSurface.kt` — `/tmp/a2ui-inspect/commonMain/com/contextable/a2ui4k/render/A2UiSurface.kt`
[^7]: `CoreCatalog.kt` — `/tmp/a2ui-inspect/commonMain/com/contextable/a2ui4k/catalog/CoreCatalog.kt`
[^8]: `DataModel.kt` — `/tmp/a2ui-inspect/commonMain/com/contextable/a2ui4k/data/DataModel.kt`
[^9]: `UiEvent.kt` — `/tmp/a2ui-inspect/commonMain/com/contextable/a2ui4k/model/UiEvent.kt`
[^10]: `agent/system_prompt.py` — lines 1-197
[^11]: `app/src/main/java/com/example/a2ui/chat/presentation/components/MessageBubble.kt` — lines 40-46
