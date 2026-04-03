# How the A2UI Agent Generates Spec-Aligned Output

## Executive Summary

The A2UI agent is a Python FastAPI server that acts as a translation layer between a general-purpose LLM (Claude Sonnet 4.6 via the Copilot SDK) and the A2UI protocol understood by the Android client. The agent achieves spec-alignment through a **four-stage pipeline**: (1) a detailed system prompt instructs the LLM to produce a JSON object with a `text` field and a `uiDefinition` tree of A2UI components; (2) the raw LLM output is parsed and validated against a JSON Schema with semantic checks; (3) the validated `uiDefinition` is transformed from a literal-value format into a path-binding format with a separated `DataModel`, and template lists are expanded; (4) the transformed output is decomposed into ordered A2UI protocol operations (`text`, `beginRendering`, `dataModelUpdate`, `surfaceUpdate`, `done`) and streamed as SSE events to the Android client. The Android client's `SurfaceStateManager` accumulates these operations and builds a `UiDefinition` that the `A2UISurface` Compose component renders using `FinancialCatalog`.

Two streaming endpoints exist: `/chat/stream` (custom SSE event types) and `/chat/stream/jsonl` (spec-compliant JSONL, optionally using the A2UI SDK for system prompt generation and validation).

---

## Architecture Overview

```
PYTHON AGENT (FastAPI)

  System      Copilot SDK     Parse &      Transform
  Prompt  --> (LLM Call)  --> Validate --> to Ops
                                           |
  Widgets,    claude-         JSON parse    Path bindings
  layout      sonnet-4.6     Schema val    Template exp
  guide,      streaming      Semantic      Chunking
  template                   checks        Sanitization
  examples                   Retry(1x)
                                           |
                                    SSE Stream
                                    event: text
                                    event: a2ui_op (x N)
                                    event: done
                                           |
                                           v (HTTP SSE)

ANDROID CLIENT

  RealChat       SurfaceState      ChatViewModel
  Repository --> Manager       --> 
                                   buildUiDefinition()
  SSE parsing    processOperation  buildDataModelJson()
  StreamEvent    accumulates:
  emission       - components      Message(uiDefinition,
                 - dataContents      dataModelJson)
                 - surfaceId/root         |
                                          v
  MessageBubble -> A2UISurface(catalog=FinancialCatalog)
                   DataModel(initialData=dataModelJson)
```

---

## Stage 1: System Prompt — Instructing the LLM

The primary mechanism for spec alignment is a ~7,500-character system prompt defined in `system_prompt.py`. This prompt constrains the LLM's output format via several interlocking sections:

### 1.1 Response Format Contract

The prompt mandates the LLM respond with a JSON object in exactly this shape:

```json
{
  "text": "A brief human-readable summary (1-2 sentences)",
  "uiDefinition": { ... } or null
}
```

The `uiDefinition` object follows the A2UI component tree schema:

```json
{
  "surfaceId": "response_<unique_id>",
  "root": "<root_component_id>",
  "components": {
    "<component_id>": {
      "id": "<component_id>",
      "componentProperties": {
        "<WidgetType>": { ... }
      }
    }
  }
}
```

### 1.2 Widget Catalog

The system prompt defines **9 widget types** with their exact JSON property schemas:

| Widget | Purpose | Key Properties |
|--------|---------|---------------|
| `Text` | Display text | `text.literalString`, `usageHint` (h1-h5, body, caption) |
| `Column` | Vertical layout | `children.explicitList` |
| `Row` | Horizontal layout | `children.explicitList`, `distribution` |
| `Card` | Elevated container | `child` (single component ID) |
| `Divider` | Visual separator | (empty config) |
| `Button` | Interactive action | `child`, `actions[]`, `style` (filled/outlined/text) |
| `TextField` | Text input | `placeholder.literalString`, `textFieldType`, `text.path` |
| `CheckBox` | Toggle | `label.literalString` |
| `List` | Scrollable container | `children.explicitList` |

### 1.3 Financial Layout Guide

The prompt includes detailed layout patterns for two key financial scenarios:

- **Account/Portfolio Views**: Section header Row (spaceBetween) with category label (h5, UPPERCASE) + total (h4), then Card with Column containing two-line account rows
- **Transaction/Trade History**: Summary header Card with Column containing title/period/count, then trades in Card with Column with Row(spaceBetween) for each

### 1.4 Template Pattern for Large Lists

For datasets with 5+ items, the prompt instructs the LLM to use a template pattern instead of defining each item individually:

```json
{
  "itemTemplate": {
    "components": { "row_{i}": {...}, "left_{i}": {...}, ... },
    "rootId": "row_{i}",
    "dividerId": "div_{i}"
  },
  "items": [{"action": "...", "date": "...", "amount": "..."}, ...],
  "itemListId": "txns_list"
}
```

Placeholders `{i}` in component IDs are replaced with the item index, and `{field_name}` in `literalString` values are replaced with data from the items array.

### 1.5 Concrete Examples

The prompt includes **4 complete JSON examples**:
1. Account/portfolio query (inline components)
2. Transaction/trade query (inline components)
3. Transaction query with template (10+ items)
4. Conversational reply (null `uiDefinition`)

### 1.6 Rules

10+ explicit rules including unique component IDs, all children must exist, root must reference a valid component, Card wrapping for financial data, JSON-only output, template pattern for 5+ items, and a character limit of 8,000 characters.

---

## Stage 2: LLM Call & Response Parsing

### 2.1 LLM Invocation

The agent uses the **Copilot SDK** to call Claude Sonnet 4.6 in streaming mode (`agent.py:101-141`):

```python
async def stream_llm_copilot_sdk(message: str) -> AsyncGenerator[str, None]:
    client = CopilotClient()
    await client.start()
    session = await client.create_session(
        model="claude-sonnet-4.6",
        streaming=True,
        system_message={"mode": "replace", "content": A2UI_SYSTEM_PROMPT},
    )
```

Tokens are yielded via an `asyncio.Queue` — `ASSISTANT_MESSAGE_DELTA` events push tokens, and `SESSION_IDLE` signals completion. The streaming endpoint (`/chat/stream`) accumulates all tokens into `full_content` before parsing.

> **Key insight**: Despite streaming from the LLM, the agent **does not** forward raw tokens to the client. It accumulates the full response, then emits structured A2UI protocol operations. This is a "stream-then-transform" architecture.

### 2.2 Response Parsing

`parse_agent_response()` (`agent.py:148-191`) handles three cases with progressive fallback:

1. **Valid JSON**: Parse with `json.loads()`, extract `text` and `uiDefinition`/`ui_definition`, inject `surfaceId`
2. **Truncated JSON**: Regex extraction of the `"text"` field from malformed JSON (recovery)
3. **Plain text**: If the response starts with `{` but can't be parsed, return a friendly error message; otherwise treat as plain text

---

## Stage 3: Validation & Retry

### 3.1 JSON Schema Validation

The A2UI schema (`a2ui_schema.py`) validates that every component has `id` and `componentProperties`:

```python
A2UI_SCHEMA = {
    "type": "object",
    "required": ["root", "components"],
    "properties": {
        "components": {
            "additionalProperties": {
                "required": ["id", "componentProperties"],
                "properties": {
                    "componentProperties": {"minProperties": 1}
                }
            }
        }
    },
    "additionalProperties": True  # Allow template fields
}
```

### 3.2 Semantic Validation

Beyond schema compliance, `validate_ui_definition()` (`agent.py:200-236`) performs three semantic checks:

1. **Root existence**: `root` must reference a key in `components`
2. **Child existence**: Every child ID in Column/Row/List `explicitList` must exist in `components` (template placeholders containing `{i}` are skipped)
3. **Card child existence**: Every Card's `child` must exist in `components`

### 3.3 Retry on Failure

If validation fails, the agent retries **once** by appending the error to the prompt:

```python
retry_message = (
    f"{request.message}\n\n"
    f"[SYSTEM: Your previous response had a UI validation error: {error}. "
    f"Please fix the issue and try again.]"
)
```

If the retry also fails, the response falls back to text-only (no UI card).

---

## Stage 4: Transformation Pipeline

After validation, the `uiDefinition` goes through a multi-step transformation pipeline.

### 4.1 Template Expansion

`expand_templates()` (`agent.py:269-334`) processes the `itemTemplate` / `items` / `itemListId` fields:

1. For each item in the `items` array, clones every template component
2. Replaces `{i}` with the item index in all component IDs and string values
3. Replaces `{field_name}` in `literalString` values with corresponding item data
4. Inserts optional Divider components between items
5. Updates the target List/Column's `children.explicitList` with expanded IDs
6. Removes `itemTemplate`, `items`, `itemListId` from the `uiDefinition`

A `MAX_TEMPLATE_ITEMS = 200` cap prevents unbounded expansion.

### 4.2 Literal-to-Path-Binding Transformation

`transform_to_path_bindings()` (`agent.py:428-458`) converts inline `literalString` values to `DataModel` path references:

**Before (LLM output):**
```json
{"Text": {"text": {"literalString": "$4,250.00"}, "usageHint": "h3"}}
```

**After (protocol output):**
```json
{"Text": {"text": {"path": "/balance_text"}, "usageHint": "h3"}}
```
Plus a data entry: `{"key": "balance_text", "valueString": "$4,250.00"}`

This separation is fundamental to A2UI — it allows the client's `DataModel` to hold all values reactively, enabling dynamic updates without re-sending the entire component tree.

### 4.3 Sanitization

`sanitize_components()` (`agent.py:461-504`) removes **dangling child references** — children IDs in Column/Row/List that don't exist in the component map. This handles truncated LLM output where the JSON was cut off before all components were defined.

### 4.4 Chunking

`chunk_components()` (`agent.py:507-514`) splits the component list into batches of 15 (default for `/chat/stream`) or 1 (for `/chat/stream/jsonl`) for progressive `surfaceUpdate` emissions.

### 4.5 Operation Assembly

`transform_to_operations()` (`agent.py:517-574`) assembles the final ordered list:

```
1. text            -> {"type": "text", "data": {"text": "summary..."}}
2. beginRendering  -> {"type": "a2ui_op", "data": {"beginRendering": {...}}}
3. dataModelUpdate -> {"type": "a2ui_op", "data": {"dataModelUpdate": {...}}}
4. surfaceUpdate   -> {"type": "a2ui_op", "data": {"surfaceUpdate": {...}}}  (x N chunks)
5. done            -> {"type": "done", "data": {}}
```

> **Order difference**: `/chat/stream` emits `beginRendering` before `surfaceUpdate`. `/chat/stream/jsonl` reverses this: `surfaceUpdate` -> `dataModelUpdate` -> `beginRendering` (spec section 1.5 order). The Android client handles both orderings.

---

## Stage 5: SSE Streaming to Client

### 5.1 `/chat/stream` — Custom Event Types

```
event: text
data: {"text": "Here are your transactions..."}

event: a2ui_op
data: {"beginRendering": {"surfaceId": "response_abc123", "root": "root"}}

event: a2ui_op
data: {"dataModelUpdate": {"surfaceId": "response_abc123", "path": "", "contents": [...]}}

event: a2ui_op
data: {"surfaceUpdate": {"surfaceId": "response_abc123", "components": [...]}}

event: done
data: {}
```

A 150ms delay between `a2ui_op` events enables **visible progressive rendering** on the client.

### 5.2 `/chat/stream/jsonl` — Spec-Compliant JSONL

Uses plain `data:` lines (no custom `event:` type). When the A2UI SDK is available, generates the system prompt via `A2uiSchemaManager.generate_system_prompt()` and validates responses with `_sdk_catalog.validator.validate()`.

---

## Stage 6: Android Client Consumption

### 6.1 SSE Parsing (RealChatRepository)

| SSE `event:` | Emitted as | Handler |
|---|---|---|
| `a2ui_op` | `StreamEvent.A2UiOp(data)` | Raw JSON forwarded to `SurfaceStateManager` |
| `text` | `StreamEvent.TextContent(text)` | Extracted from `{"text": "..."}` |
| `done` | `StreamEvent.Done(message)` | Signals completion |

### 6.2 State Accumulation (SurfaceStateManager)

- **`processBeginRendering`**: Sets `surfaceId` and `root` (does NOT clear accumulated components)
- **`processSurfaceUpdate`**: Parses `components` array into `Component(id, componentProperties)`, stores in `LinkedHashMap`
- **`processDataModelUpdate`**: Stores raw `JsonObject` for later expansion
- **`buildUiDefinition()`**: Returns snapshot `UiDefinition(surfaceId, root, components)`
- **`buildDataModelJson()`**: Merges all entries into nested `JsonObject` tree with slash-path support

### 6.3 Progressive Rendering (ChatViewModel)

Creates a fresh `SurfaceStateManager` per message. After each `A2UiOp`, calls `upsertStreamingMessage()` which rebuilds the `UiDefinition` snapshot and updates the Compose state flow. This means the UI card visibly builds up as each `surfaceUpdate` chunk arrives.

### 6.4 Rendering (MessageBubble -> A2UISurface)

```kotlin
val dataModel = rememberDataModel(
    initialData = message.dataModelJson ?: JsonObject(emptyMap())
)
A2UISurface(
    definition = message.uiDefinition,
    dataModel = dataModel,
    catalog = FinancialCatalog,
    onEvent = onEvent,
)
```

---

## Event Handling: Client to Server

When users interact with A2UI components (Button taps, TextField edits):

1. `FinancialCatalog` fires a `UiEvent` (`UserActionEvent` or `DataChangeEvent`)
2. `ChatViewModel.sendUiEvent()` forwards to `RealChatRepository.sendEvent()`
3. Repository POSTs to `/event` on the agent
4. Agent's `/event` handler constructs a follow-up prompt and streams a new response back

---

## Key Files Summary

| File | Purpose | Lines |
|------|---------|-------|
| `agent/system_prompt.py` | LLM system prompt — widget catalog, layout guide, templates, examples | 327 |
| `agent/a2ui_schema.py` | JSON Schema for uiDefinition validation | 25 |
| `agent/agent.py` | FastAPI server — LLM calls, parsing, validation, transformation, SSE | ~1100 |
| `agent/test_agent.py` | Integration tests for both SSE and JSONL endpoints | 257 |
| `app/.../SurfaceStateManager.kt` | Accumulates A2UI ops into UiDefinition + DataModel | 287 |
| `app/.../RealChatRepository.kt` | SSE/JSONL parsing, StreamEvent emission, event posting | ~400 |
| `app/.../ChatViewModel.kt` | Streaming state machine, progressive rendering | 433 |
| `app/.../MessageBubble.kt` | Wires A2UISurface with FinancialCatalog and DataModel | 196 |

---

## Confidence Assessment

| Aspect | Confidence | Notes |
|--------|-----------|-------|
| System prompt to LLM contract | High | Full source read of system_prompt.py |
| Parsing and validation pipeline | High | Full source read of agent.py |
| Template expansion mechanics | High | Full source read with deep_replace() and expand_templates() |
| Transform to path bindings | High | Full source read of transform_to_path_bindings() |
| SSE streaming format | High | Confirmed in both agent and client source |
| JSONL/SDK path | Medium-High | SDK internals (a2ui.core.*) are imported but not in this repo |
| Android client consumption | High | Full source read of SurfaceStateManager, RealChatRepository, ChatViewModel |
| A2UISurface/FinancialCatalog rendering | Medium | FinancialCatalog.kt not fully read, but wiring confirmed in MessageBubble |
