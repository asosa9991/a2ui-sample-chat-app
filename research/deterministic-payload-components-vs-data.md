---

# Does the Deterministic Response Need Components or Just Data?

## Executive Summary

**Short answer: It depends on WHERE the template lives.**

There are two viable approaches, and the codebase already demonstrates both:

| Approach | Components on wire? | Data on wire? | Template lives | Android changes? |
|----------|-------------------|---------------|----------------|-----------------|
| **A. Server-side templates** | ✅ Yes — server builds them from template | ✅ Yes — from data layer | Server (Python) | **None** |
| **B. Client-side templates** | ❌ No — client builds them locally | ✅ Yes — server sends only data | Client (Kotlin `*Surface.kt`) | **Moderate** |

**Approach A** is the path of least resistance — the server assembles the full component tree from a template + data, sends the normal SSE events, and the Android client is completely unaware that anything changed. This is what the original deterministic architecture report proposed.

**Approach B** is more efficient on the wire (sends only data, not layout) but requires protocol changes and Android client modifications. However, the Android client **already has the building blocks** — the `*Surface.kt` files are client-side templates that produce `UiDefinition` objects directly.

---

## Why the Question Matters

Today, the SSE stream contains **both** layout and data:

```
event: a2ui_op
data: {"dataModelUpdate": {"surfaceId": "...", "contents": [
    {"key": "title", "valueString": "March 2026 Transactions"},
    {"key": "t_action_0", "valueString": "Direct Deposit – Employer Payroll"},
    {"key": "t_amt_0", "valueString": "+$4,250.00"},
    ...
]}}

event: a2ui_op
data: {"surfaceUpdate": {"surfaceId": "...", "components": [
    {"id": "root", "component": {"Column": {"children": {"explicitList": ["hdr_card"]}}}},
    {"id": "hdr_card", "component": {"Card": {"child": "hdr_card_col"}}},
    {"id": "title", "component": {"Text": {"text": {"path": "/title"}, "usageHint": "h5"}}},
    {"id": "t_action_0", "component": {"Text": {"text": {"path": "/t_action_0"}, "usageHint": "body"}}},
    ... (30+ more components)
]}}
```

The `surfaceUpdate` carries the **entire component tree** — every Column, Row, Card, Text, Divider with their structure and relationships. For a transactions list with 14 items, that's ~60 components. This layout is **identical every time** you ask for transactions. Only the data values change.

The question is: can we send just the `dataModelUpdate` (data) and skip the `surfaceUpdate` (components)?

---

## What the Android Client Actually Requires

### The Hard Requirement: `A2UISurface` needs a `UiDefinition`

The rendering chain is[^1][^2][^3]:

```
Message.uiDefinition  ──►  A2UISurface(definition = message.uiDefinition, ...)
         │                         │
         │                         ▼
         │                  Catalog resolves each component by type name
         │                  (Column → financialColumnWidget, Text → financialTextWidget, etc.)
         │
         └── UiDefinition(surfaceId, root, components: Map<String, Component>)
```

`A2UISurface` takes a `UiDefinition` object, which contains[^4]:
- `surfaceId: String` — surface identifier
- `root: String?` — root component ID
- `components: Map<String, Component>` — **the full component map** (every widget, its type, and properties)

Each `Component` contains[^5]:
- `id: String` — component ID
- `componentProperties: Map<String, JsonObject>` — widget type → config (e.g., `"Text" → {"text": {"path": "/title"}, "usageHint": "h5"}`)

**There is no `templateId` or `templateName` field.** The A2UI SDK has no concept of client-side template resolution. `A2UISurface` always traverses `components` starting from `root`.

### The Mock Path Proves Client-Side Templates Work

`MockChatRepository` already bypasses SSE entirely and builds `UiDefinition` on the client[^6]:

```kotlin
// MockChatRepository.kt:29-36
normalized.contains("last") && normalized.contains("transaction") -> Message(
    id = UUID.randomUUID().toString(),
    content = "Here are your transactions from March 2026...",
    sender = Sender.AI,
    uiDefinition = TransactionHistorySurface.build()  // ← Client builds UiDefinition directly
)
```

`TransactionHistorySurface.build()` constructs the entire component tree programmatically with hardcoded data[^7]. This proves that:

1. The client **can** build `UiDefinition` locally without receiving components from the server
2. The `*Surface.kt` files **are** effectively client-side templates
3. The only missing piece is separating the data from the template

---

## Approach A: Server-Side Templates (Recommended First)

### How it works

The server generates the full component tree from a Python template + data layer. The SSE events look **identical** to the LLM path. The Android client doesn't know or care.

```
Server: template_registry.render("transaction_history", data) 
        → full uiDefinition with all components
        → transform_to_operations() (same pipeline)
        → SSE events (same format)

Client: SurfaceStateManager processes events (unchanged)
        → builds UiDefinition from surfaceUpdate (unchanged)
        → A2UISurface renders (unchanged)
```

### What's on the wire

```
event: text
data: {"text": "Here are your transactions..."}

event: a2ui_op
data: {"beginRendering": {"surfaceId": "response_abc", "root": "root"}}

event: a2ui_op
data: {"dataModelUpdate": {"surfaceId": "response_abc", "contents": [...]}}

event: a2ui_op
data: {"surfaceUpdate": {"surfaceId": "response_abc", "components": [...]}}
                                                           ▲
                                              Still sends full components
event: done
data: {}
```

### Pros
- **Zero Android changes** — the client is completely unaware
- Uses existing `transform_to_operations()`, `chunk_components()`, SSE pipeline[^8]
- Easy to implement — just add Python template builders + intent router
- Backward compatible with LLM fallback path

### Cons
- Still sends ~60 components over the wire for a transaction list (identical layout every time)
- Bandwidth overhead: ~4-8 KB per response for the component tree
- On mobile networks, this extra payload adds ~50-200ms latency

### Payload size estimate

For a 14-transaction list:
- `dataModelUpdate`: ~1.5 KB (just values)
- `surfaceUpdate`: ~5 KB (full component tree with structure)
- **Total: ~6.5 KB** per response

---

## Approach B: Client-Side Templates (Data-Only Payload)

### How it works

The server sends a **template identifier** + **data values**. The client resolves the template locally and builds the `UiDefinition` on-device.

```
Server: classify intent → pick template name → fetch data
        → SSE: {"templateRendering": {"template": "transaction_history", "data": {...}}}

Client: TemplateResolver maps "transaction_history" → TransactionHistorySurface
        → TransactionHistorySurface.build(data)  // pass data instead of hardcoding
        → UiDefinition (built locally)
        → A2UISurface renders
```

### What's on the wire (proposed)

```
event: text
data: {"text": "Here are your transactions..."}

event: template                              ◄── NEW event type
data: {
    "template": "transaction_history",       ◄── Template name
    "data": {                                ◄── Only the data values
        "period": {"label": "March 2026", "range": "Mar 1 – Mar 31, 2026"},
        "transactions": [
            {"description": "Direct Deposit – Employer Payroll", "date": "2026-03-28", "amount": "+$4,250.00"},
            {"description": "Buy NVDA · 8 shares", "date": "2026-03-26", "amount": "-$2,184.00"},
            ...
        ]
    }
}

event: done
data: {}
```

### What changes in Android

**1. Modify `*Surface.kt` to accept data parameters**

Currently `TransactionHistorySurface.build()` has hardcoded data[^7]. Change to:

```kotlin
object TransactionHistorySurface {
    // Current: hardcoded data list at lines 29-44
    // Proposed: accept data parameter
    
    fun build(data: TemplateData? = null): UiDefinition {
        val txns = data?.transactions ?: DEFAULT_TRANSACTIONS
        val period = data?.period ?: DEFAULT_PERIOD
        // ... rest of builder uses txns/period instead of hardcoded values
    }
}
```

**2. Add a `TemplateResolver`**

```kotlin
object TemplateResolver {
    private val registry = mapOf(
        "transaction_history" to { data: JsonObject -> TransactionHistorySurface.build(data) },
        "account_balances" to { data: JsonObject -> AccountBalancesSurface.build(data) },
        "brokerage_activity" to { data: JsonObject -> BrokerageActivitySurface.build(data) },
    )
    
    fun resolve(templateName: String, data: JsonObject): UiDefinition? {
        return registry[templateName]?.invoke(data)
    }
}
```

**3. Handle the new `template` SSE event in `RealChatRepository`**

In the SSE parsing loop, add a new case[^9]:

```kotlin
"template" -> {
    val templateData = json.parseToJsonElement(data).jsonObject
    val templateName = templateData["template"]?.jsonPrimitive?.content
    val templateDataObj = templateData["data"]?.jsonObject ?: JsonObject(emptyMap())
    
    if (templateName != null) {
        val uiDef = TemplateResolver.resolve(templateName, templateDataObj)
        if (uiDef != null) {
            emit(StreamEvent.TemplateResolved(uiDef, templateDataObj))
        }
    }
}
```

**4. Handle `TemplateResolved` in `ChatViewModel`**

Add a new `StreamEvent` variant and wire it to `upsertStreamingMessage()`.

### Pros
- **Minimal wire payload**: ~1.5 KB (just data) vs ~6.5 KB (data + components)
- **Instant rendering**: No need to parse/accumulate/chunk components
- **Perfect consistency**: Template is compiled into the app — guaranteed identical layout
- **Offline capable**: Client has templates built-in, only needs data

### Cons
- **Requires Android changes**: TemplateResolver, modified Surface builders, new StreamEvent variant, SSE handler update
- **Template versioning**: Client and server must agree on which templates exist
- **No novel layouts**: If the server wants to send a layout the client doesn't have a template for, needs fallback to full component mode
- **App update required**: New templates require a client release

### Payload size comparison

| Content | Approach A (full) | Approach B (data-only) | Savings |
|---------|-------------------|----------------------|---------|
| 14 transactions | ~6.5 KB | ~1.5 KB | **77%** |
| 5 account balances | ~4 KB | ~0.8 KB | **80%** |
| 5 brokerage trades | ~5 KB | ~1.0 KB | **80%** |

---

## Approach C: Hybrid (Best of Both Worlds)

### How it works

The server sends a **template hint** alongside the normal components. The client checks if it has the template locally — if yes, it uses the local template with the data; if no, it falls back to the server-provided components.

```
event: a2ui_op
data: {"beginRendering": {
    "surfaceId": "response_abc", 
    "root": "root",
    "templateHint": "transaction_history"     ◄── NEW optional field
}}

event: a2ui_op
data: {"dataModelUpdate": {"surfaceId": "response_abc", "contents": [...]}}

event: a2ui_op
data: {"surfaceUpdate": {"surfaceId": "response_abc", "components": [...]}}
                                                           ▲
                                              Still sent as fallback

event: done
data: {}
```

**Client logic:**

```kotlin
fun processBeginRendering(data: JsonObject) {
    surfaceId = data["surfaceId"]?.jsonPrimitive?.contentOrNull
    root = data["root"]?.jsonPrimitive?.contentOrNull
    templateHint = data["templateHint"]?.jsonPrimitive?.contentOrNull
}

fun buildUiDefinition(): UiDefinition? {
    // Try local template first
    if (templateHint != null) {
        val localDef = TemplateResolver.resolve(templateHint!!, buildDataModelJson())
        if (localDef != null) return localDef  // ← Skip server components entirely
    }
    // Fallback: use server-provided components
    return UiDefinition(surfaceId = sid, root = root, components = snapshot)
}
```

### Pros
- **Graceful degradation**: Works even if client doesn't have the template
- **Forward compatible**: New templates can be added server-side and fall back to component mode until the client is updated
- **Incremental adoption**: Existing flows keep working; templates are opt-in optimizations

### Cons
- Components still sent over the wire (unless server skips them when client confirms template support via a capability handshake)
- More complex logic in `SurfaceStateManager`

---

## Recommendation

### Phase 1: Start with Approach A (Server-Side Templates)

- Zero Android changes
- Proves the deterministic intent → template → data pipeline
- Latency improvement: 10-15s → < 100ms
- Cost improvement: ~$0.02/request → $0/request
- Already has all building blocks in the codebase

### Phase 2: Evolve to Approach C (Hybrid)

- Add `templateHint` to `beginRendering` protocol
- Modify `*Surface.kt` to accept data parameters
- Add `TemplateResolver` to the client
- Client uses local templates when available, falls back to server components
- Bandwidth savings: 77-80% for known templates

### Phase 3 (Optional): Approach B (Data-Only)

- Only if bandwidth is a critical concern
- Requires template versioning/negotiation protocol
- Best for known, stable templates (transactions, balances, holdings)

---

## Key Insight: The `dataModelUpdate` Already Separates Data from Layout

The existing `transform_to_path_bindings()` function[^10] already extracts literal values from the component tree into a separate `dataModelUpdate`:

```python
# agent.py:428-458 — transform_to_path_bindings()
# Before: Text component has { "text": {"literalString": "+$4,250.00"} }
# After:  Text component has { "text": {"path": "/t_amt_0"} }
#         DataModel entry:    { "key": "t_amt_0", "valueString": "+$4,250.00" }
```

This means the **data is already separated on the wire** — it's in `dataModelUpdate`, not in `surfaceUpdate`. The `surfaceUpdate` components only contain structural information (which widget type, which children, path references). The actual human-readable values are all in `dataModelUpdate`.

If you adopt Approach B or C with client-side templates, the server only needs to send the `dataModelUpdate` contents — the client template knows the component structure.

---

## Confidence Assessment

| Finding | Confidence | Evidence |
|---------|-----------|---------|
| `A2UISurface` requires full `UiDefinition` with components | **Confirmed** | `MessageBubble.kt:77-83`, no template mechanism in SDK |
| Mock path builds `UiDefinition` client-side | **Confirmed** | `MockChatRepository.kt:35`, `TransactionHistorySurface.build()` |
| `transform_to_path_bindings()` already separates data from layout | **Confirmed** | `agent.py:428-458` |
| Approach A requires zero Android changes | **Confirmed** | SSE format is identical |
| Approach B payload savings of ~80% | **High** | Based on typical component tree sizes |
| Approach C hybrid is forward-compatible | **High** | `templateHint` is additive, non-breaking |

---

## Footnotes

[^1]: `app/src/main/java/com/example/a2ui/chat/presentation/components/MessageBubble.kt:44` — checks `message.uiDefinition != null` to decide rendering path
[^2]: `app/src/main/java/com/example/a2ui/chat/presentation/components/MessageBubble.kt:77-83` — `A2UISurface(definition = message.uiDefinition, dataModel = dataModel, catalog = FinancialCatalog, onEvent = onEvent)`
[^3]: `app/src/main/java/com/example/a2ui/chat/domain/model/Message.kt:14` — `val uiDefinition: UiDefinition? = null`
[^4]: `com.contextable.a2ui4k.model.UiDefinition` — data class with `surfaceId`, `root`, `components` fields
[^5]: `com.contextable.a2ui4k.model.Component` — data class with `id`, `componentProperties` fields
[^6]: `app/src/main/java/com/example/a2ui/chat/data/repository/MockChatRepository.kt:29-36` — intent routing to `TransactionHistorySurface.build()`
[^7]: `app/src/main/java/com/example/a2ui/chat/data/a2ui/TransactionHistorySurface.kt:29-44` — hardcoded transaction data list
[^8]: `agent/agent.py:517-574` — `transform_to_operations()` accepts any parsed response dict and produces SSE operations
[^9]: `app/src/main/java/com/example/a2ui/chat/data/repository/RealChatRepository.kt` — SSE event parsing loop
[^10]: `agent/agent.py:428-458` — `transform_to_path_bindings()` separates `literalString` values into DataModel entries with path references
