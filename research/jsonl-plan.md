# Combined Plan: A2UI SDK Integration + JSONL Streaming

---

## How the Reference Agent Actually Works (Key Finding)

The sample agent (`google/A2UI` → `samples/agent/adk/custom-components-example/agent.py`) reveals a fundamentally different architecture from our current implementation.

### Reference agent flow

```
1. LLM receives system prompt (with embedded schema) via A2uiSchemaManager
2. LLM outputs: plain text + <a2ui-json>[...list of JSONL messages...]</a2ui-json>
3. parse_response() extracts text and JSON list from the XML tags
4. catalog.validator.validate(json_list) validates the list against the official schema
5. parse_response_to_parts() wraps text as TextPart, JSON list as DataPart (A2A protocol)
6. Yielded to A2A client as {"is_task_complete": True, "parts": [...]}
```

### The LLM output format is a JSON list of A2UI messages

The LLM generates a **list** of JSONL-style objects — already in spec format — wrapped in XML tags:

```
Here are your trades.
<a2ui-json>
[
  {"surfaceUpdate": {"surfaceId": "...", "components": [...]}},
  {"dataModelUpdate": {"surfaceId": "...", "contents": [...]}},
  {"beginRendering": {"surfaceId": "...", "root": "root"}}
]
</a2ui-json>
```

The LLM produces the protocol messages **directly**, not a high-level `uiDefinition` tree that the server has to transform.

### Key difference from our current agent

| | Current agent | Reference agent |
|---|---|---|
| LLM output | `{"text": "...", "uiDefinition": {nested tree}}` | `text + <a2ui-json>[list of spec messages]</a2ui-json>` |
| Server transform | `transform_to_operations()` converts tree → ops | None — LLM produces ops directly |
| Wire format | Custom `event: a2ui_op` SSE | A2A DataPart (or plain SSE JSONL for our server) |
| Protocol transport | SSE with custom event types | A2A protocol (not applicable to us) |
| Streaming | Op-by-op with `asyncio.sleep` gaps | Full response at once (no partial streaming) |

### What we adapt (we keep our FastAPI + SSE, not A2A)

We adopt the **LLM format and SDK library**, but keep our own HTTP/SSE transport:

1. LLM prompt → `A2uiSchemaManager.generate_system_prompt(include_schema=True)` (SDK)
2. LLM output format → `<a2ui-json>[...]</a2ui-json>` list
3. Parse → `parse_response()` from SDK
4. Validate → `catalog.validator.validate(json_list)` from SDK
5. Stream → emit each JSON list item as a plain SSE `data:` line

---

## Scope: Additive Only — New `/chat/stream/jsonl` Endpoint

**Keep all existing code and endpoints unchanged.**

- **`/chat/stream`** — unchanged (existing SSE with custom event types + `uiDefinition` tree)
- **`/chat/stream/jsonl`** — NEW endpoint, spec-compliant, SDK-powered
- Clients get a **flag** to switch between the two endpoints

No existing files are deleted or modified (except adding the new endpoint and SDK import to `agent.py`).

---

## Part 1: Official A2UI Python SDK (library integration)

### What we use from the SDK

| Module | Replaces |
|---|---|
| `A2uiSchemaManager.generate_system_prompt()` | `system_prompt.py` |
| `BasicCatalog.get_config("0.8")` | `a2ui_schema.py` |
| `parse_response()` | `parse_agent_response()` in agent.py |
| `catalog.validator.validate(json_list)` | custom `validate_ui_definition()` |
| `payload_fixer.parse_and_fix()` | nothing (new — handles smart quotes, trailing commas) |

### Install

```
pip install git+https://github.com/google/A2UI.git#subdirectory=agent_sdks/python
```

Note: `google-adk` and `google-genai` come in as transitive deps but are never called by us.

---

## Part 2: New LLM Output Format

### Old format (current)
```json
{"text": "Summary.", "uiDefinition": {"surfaceId": "...", "root": "root", "components": {...}}}
```

### New format (spec-compliant JSONL list)
```
Summary text here.
<a2ui-json>
[
  {"surfaceUpdate": {"surfaceId": "response_abc", "components": [...]}},
  {"dataModelUpdate": {"surfaceId": "response_abc", "contents": [...]}},
  {"beginRendering": {"surfaceId": "response_abc", "root": "root"}}
]
</a2ui-json>
```

**Impact on agent.py:**
- Delete `parse_agent_response()`, `transform_to_operations()`, `expand_templates()`, `transform_to_path_bindings()`, `sanitize_components()`, `chunk_components()` — no longer needed
- LLM now owns structuring components; we just parse, validate, and stream
- System prompt (from SDK) already instructs the LLM on how to generate the correct list format with the correct order

---

## Part 3: Spec-Compliant JSONL SSE Streaming

### Wire format (per §1.4–1.5)

```
data: {"surfaceUpdate": {"surfaceId": "...", "components": [...]}}
data: {"dataModelUpdate": {"surfaceId": "...", "contents": [...]}}
data: {"beginRendering": {"surfaceId": "...", "root": "root"}}
data: {"done": {}}
```

Plain `data:` lines. No custom `event:` type. `beginRendering` last.

### Changes to agent.py `/chat/stream`

```python
# After parse + validate:
text, jsonl_messages = extract_from_response(final_content)
yield {"data": json.dumps({"text": text})}  # plain text first
for msg in jsonl_messages:                  # each JSONL message
    yield {"data": json.dumps(msg)}
yield {"data": json.dumps({"done": {}})}
```

### Changes to clients (Android + iOS)

Both clients currently switch on the SSE `event:` type field. Change to dispatch on the JSONL top-level key:

| JSONL key | Client action |
|---|---|
| `"text"` | Display plain text summary |
| `"surfaceUpdate"` | Buffer components |
| `"dataModelUpdate"` | Buffer data entries |
| `"beginRendering"` | Set surfaceId + root → trigger render |
| `"done"` | Mark stream complete |

Also: remove `components.clear()` / `dataContents.clear()` from `beginRendering` handler in `SurfaceStateManager` on both platforms (components already buffered when `beginRendering` arrives).

---

## Files to Change

| File | Change |
|---|---|
| `agent/requirements.txt` | Add `a2ui-agent` from GitHub (keep all existing deps) |
| `agent/agent.py` | Add SDK import + `A2uiSchemaManager` init + new `/chat/stream/jsonl` endpoint |
| `app/.../RealChatRepository.kt` | Add `USE_JSONL_ENDPOINT` flag; add `sendMessageStreamJsonl()` that parses plain `data:` JSONL top-level keys |
| `app/.../ChatViewModel.kt` | Wire new repository method when flag is true |
| `ios/.../RealChatRepository.swift` | Same as Android — add `useJsonlEndpoint` flag + new stream method |
| `ios/.../ChatViewModel.swift` | Wire new method when flag is true |
| `ios/...Tests/SurfaceStateManagerTests.swift` | Add new test cases for spec-order JSONL (existing tests stay) |

---

## Testing

- Android: `./gradlew :app:compileDebugKotlin`
- iOS: `xcodebuild` build + `xcodebuild test` (25 unit tests, update for new order)
- Manual: send chat, verify progressive card rendering still works
- Verify server logs show correct JSONL message order
- Push to GitHub


---

## Part 1: Official A2UI Python SDK (library integration)

### What the SDK provides (usable today)

| Module | Replaces |
|---|---|
| `A2uiSchemaManager` → `generate_system_prompt()` | `system_prompt.py` |
| `BasicCatalog` → bundled v0.8 JSON schema | `a2ui_schema.py` |
| `A2uiValidator` → `catalog.validator.validate()` | custom `validate_ui_definition()` |
| `payload_fixer.py` | nothing — new capability |

### What it does NOT provide (we keep as-is)

- HTTP server (FastAPI + uvicorn — unchanged)
- LLM backend — SDK's ADK layer uses Gemini; we stay on GitHub Copilot SDK (Claude)
- SSE streaming, template expansion, chunking, `/chat/stream`, `/event` endpoints

### Changes — Part 1

- `requirements.txt` — add `a2ui-agent` (install from GitHub), drop manual `jsonschema`
- `agent.py` — replace `from a2ui_schema import A2UI_SCHEMA` and `from system_prompt import A2UI_SYSTEM_PROMPT` with SDK calls; replace `validate_ui_definition()` with `catalog.validator.validate()`
- Delete `a2ui_schema.py` and `system_prompt.py`

---

## Part 2: Spec-Compliant JSONL Streaming (§1.4–1.5)

### Problem

Current agent uses **custom SSE event types** (`event: a2ui_op`, `event: text`, `event: done`) and sends `beginRendering` **first**. The spec mandates:
- All messages emitted as default `data:` SSE lines (no custom `event:` type)
- `beginRendering` sent **last** (after all components and data are buffered)

### Spec-Compliant Message Order

```
data: {"text": "..."}                              ← app extension (plain text summary)
data: {"surfaceUpdate": {"surfaceId": "...", "components": [...]}}  ← chunk 1..N
data: {"dataModelUpdate": {"surfaceId": "...", "contents": [...]}}
data: {"beginRendering": {"surfaceId": "...", "root": "root"}}      ← render signal (last)
data: {"done": {}}                                 ← app extension
```

### Changes — Part 2

**`agent/agent.py`**
- `transform_to_operations`: reorder ops; remove custom `"type"` wrapper; emit plain JSONL dicts with op type as top-level key
- `event_generator` + `event_op_generator`: emit all as `{"data": json.dumps(msg)}` (no `event:` field)

**`app/.../RealChatRepository.kt`** (Android)
- Parse JSONL top-level key instead of SSE `event:` type field
- Dispatch on `"text"`, `"beginRendering"`, `"surfaceUpdate"`, `"dataModelUpdate"`, `"done"`

**`app/.../SurfaceStateManager.kt`** (Android)
- Remove `components.clear()` + `dataContents.clear()` from `processBeginRendering` (components already buffered by the time it arrives)

**`ios/.../RealChatRepository.swift`** (iOS) — same as Android repo

**`ios/.../SurfaceStateManager.swift`** (iOS) — same as Android SSM

**iOS unit tests** — update `SurfaceStateManagerTests` to feed ops in spec-compliant order

---

## Files to Change (total)

| File | Change |
|---|---|
| `agent/requirements.txt` | Add `a2ui-agent`, drop bare `jsonschema` |
| `agent/agent.py` | SDK for prompt/schema/validation; JSONL reorder + remove custom event types |
| `agent/a2ui_schema.py` | Delete |
| `agent/system_prompt.py` | Delete |
| `app/.../RealChatRepository.kt` | JSONL top-level key dispatch |
| `app/.../SurfaceStateManager.kt` | Remove clears from beginRendering |
| `ios/.../RealChatRepository.swift` | JSONL top-level key dispatch |
| `ios/.../SurfaceStateManager.swift` | Remove clears from beginRendering |
| `ios/.../SurfaceStateManagerTests.swift` | Update test op order |

---

## Testing

- Android: `./gradlew :app:compileDebugKotlin`
- iOS: `xcodebuild` build + `xcodebuild test` (25 unit tests)
- Manual: send chat, verify progressive card rendering
- Push to GitHub


## Problem

The current agent uses **custom SSE event types** (`event: text`, `event: a2ui_op`, `event: done`) with the A2UI op type wrapped inside the `data:` payload. The spec (§1.4–1.5) mandates:

1. **Pure JSONL** — all messages are default SSE `data:` lines; the A2UI op type is the **top-level JSON key**, not a custom event type.
2. **Correct message order** — `surfaceUpdate` chunks and `dataModelUpdate` must arrive **before** `beginRendering`, which acts as the "all components received, render now" signal (not the first message).

The clients (`SurfaceStateManager` on both Android and iOS) already parse the JSONL payload correctly (top-level key dispatch). The main mismatch is at the SSE event-type layer and the message ordering.

## Spec-Compliant Stream Format (§1.5)

Each SSE event is a default `data:` line (no custom `event:` type). Stream order:

```
data: {"text": "Summary text..."}          ← app extension (not in spec)
data: {"surfaceUpdate": {"surfaceId": "...", "components": [...]}}   ← chunk 1
data: {"surfaceUpdate": {"surfaceId": "...", "components": [...]}}   ← chunk 2..N
data: {"dataModelUpdate": {"surfaceId": "...", "contents": [...]}}
data: {"beginRendering": {"surfaceId": "...", "root": "root"}}       ← triggers render
data: {"done": {}}                          ← app extension (not in spec)
```

## Changes Required

### 1. `agent/agent.py`

**`transform_to_operations`** — change emitted message list:
- Remove custom `"type"` wrapper; each op is the JSONL object itself
- Reorder: text → surfaceUpdate chunks → dataModelUpdate → beginRendering → done
- Old: `{"type": "a2ui_op", "data": {"beginRendering": {...}}}` FIRST
- New: `{"beginRendering": {...}}` LAST

**`event_generator` in `/chat/stream` and `event_op_generator` in `/event`**:
- Remove `{"event": op["type"], "data": ...}` — emit all as `{"data": json.dumps(jsonl_msg)}`

### 2. Android `RealChatRepository.kt`

The SSE parser currently switches on the SSE `event:` type field. Change to:
- Treat all SSE lines as default `message` events (parse `data:` regardless of `event:`)
- Parse the data JSON and dispatch on top-level key:
  - `"text"` → `StreamEvent.TextContent`
  - `"beginRendering"` / `"surfaceUpdate"` / `"dataModelUpdate"` → `StreamEvent.A2UiOp(rawJson)`
  - `"done"` → `StreamEvent.Done`

### 3. Android `SurfaceStateManager.kt`

`processBeginRendering` currently calls `components.clear()` + `dataContents.clear()`. Remove these clears — with spec-compliant order, `beginRendering` arrives AFTER all components and data are buffered; clearing would wipe them. Each `SurfaceStateManager` instance already starts empty.

### 4. iOS `RealChatRepository.swift`

Same change as Android — parse default `message` events, dispatch on JSONL top-level key.

### 5. iOS `SurfaceStateManager.swift`

Same change as Android — remove `components.removeAll()` and `dataContents.removeAll()` from `beginRendering` handler.

## Why `hasSurface` Still Works Correctly

Both clients define `hasSurface = surfaceId != null && components.isNotEmpty()`.

- **Current order** (beginRendering first): `surfaceId` set early → `hasSurface` becomes true after first `surfaceUpdate` chunk
- **Spec order** (beginRendering last): components buffered first → `hasSurface` becomes true at `beginRendering` (surfaceId set, components already present)

Either way `hasSurface` correctly gates the render call. No ViewModel changes needed.

## Files to Change

| File | Change |
|---|---|
| `agent/agent.py` | Reorder ops, remove custom `event:` types, emit pure JSONL `data:` |
| `app/.../RealChatRepository.kt` | Parse JSONL top-level key instead of SSE event type |
| `app/.../SurfaceStateManager.kt` | Remove `clear()` calls in `processBeginRendering` |
| `ios/.../RealChatRepository.swift` | Same as Android repo |
| `ios/.../SurfaceStateManager.swift` | Remove `removeAll()` calls in beginRendering handler |

## Testing

- Build both Android and iOS apps
- Run iOS unit tests (25 tests — SSM and repo tests need updating for new event format)
- Manual: send chat message, verify UI card renders correctly
- Verify server logs show correct message order
- Push to GitHub
