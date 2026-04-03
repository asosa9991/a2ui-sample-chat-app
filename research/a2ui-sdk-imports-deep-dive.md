# Deep Dive: A2UI SDK Imports in agent.py

## Executive Summary

The four A2UI SDK imports in `agent.py` (lines 37-40) — `A2uiSchemaManager`, `VERSION_0_8`, `parse_response`, and `BasicCatalog` — are designed to integrate with an **unreleased A2UI Python SDK** that provides schema-aware system prompt generation, response parsing, and validation for the spec-compliant JSONL endpoint (`/chat/stream/jsonl`). **The SDK is not currently installed** — the `a2ui-agent` package in the venv is an empty placeholder (v0.0.0). All four imports are wrapped in a `try/except` block; when they fail, `_SDK_AVAILABLE` is set to `False` and the agent falls back to its handcrafted `A2UI_SYSTEM_PROMPT` pipeline for all endpoints. This means the `/chat/stream` endpoint works fully today, and `/chat/stream/jsonl` works via a fallback path that reuses the same pipeline.

---

## Architecture: Two Parallel Pipelines

```
                    ┌─────────────────────────────────────────┐
                    │         agent.py startup (lines 36-50)  │
                    │                                         │
                    │  try:                                   │
                    │    import A2uiSchemaManager              │
                    │    import VERSION_0_8                    │
                    │    import parse_response                 │
                    │    import BasicCatalog                   │
                    │    _SDK_AVAILABLE = True                 │
                    │  except:                                 │
                    │    _SDK_AVAILABLE = False  ◄── CURRENT   │
                    └──────────┬──────────────────────────────┘
                               │
              ┌────────────────┴────────────────┐
              │                                 │
    _SDK_AVAILABLE = True               _SDK_AVAILABLE = False
    (Future: SDK installed)             (Current: fallback)
              │                                 │
              ▼                                 ▼
┌──────────────────────────┐   ┌──────────────────────────────┐
│  SDK Pipeline            │   │  Fallback Pipeline           │
│  (/chat/stream/jsonl)    │   │  (/chat/stream + /jsonl)     │
│                          │   │                              │
│  1. BasicCatalog         │   │  1. A2UI_SYSTEM_PROMPT       │
│     .get_config()        │   │     (system_prompt.py)       │
│  2. A2uiSchemaManager    │   │  2. stream_llm_copilot_sdk() │
│     .generate_system_    │   │  3. parse_agent_response()   │
│      prompt()            │   │  4. validate_ui_definition() │
│  3. stream_llm_copilot_  │   │  5. transform_to_operations()│
│     sdk_with_prompt()    │   │  6. SSE emission             │
│  4. sdk_parse_response() │   │                              │
│  5. _sdk_catalog         │   │                              │
│     .validator.validate()│   │                              │
│  6. JSONL emission       │   │                              │
└──────────────────────────┘   └──────────────────────────────┘
```

---

## The Four SDK Imports: Detailed Analysis

### 1. `BasicCatalog` from `a2ui.basic_catalog.provider`

**Purpose**: Provides a pre-built catalog configuration describing the standard A2UI widget set (Text, Row, Column, Card, Button, etc.) for a specific protocol version.

**Usage site** (agent.py line 42):
```python
_sdk_catalog_config = BasicCatalog.get_config(version=VERSION_0_8)
```

**What it does**: `BasicCatalog.get_config()` returns a catalog configuration object that describes which widgets are available, their property schemas, and validation rules. This is passed to `A2uiSchemaManager` so it knows what components are legal in the A2UI output.

**Fallback when unavailable**: The handcrafted `A2UI_SYSTEM_PROMPT` in `system_prompt.py` manually lists all 9 widget types with their JSON property schemas. This duplicates what BasicCatalog would provide programmatically.

---

### 2. `VERSION_0_8` from `a2ui.core.schema.constants`

**Purpose**: A version constant identifying A2UI protocol version 0.8.

**Usage sites** (agent.py lines 42-43):
```python
_sdk_catalog_config = BasicCatalog.get_config(version=VERSION_0_8)
_sdk_manager = A2uiSchemaManager(version=VERSION_0_8, catalogs=[_sdk_catalog_config])
```

**What it does**: Pins both the catalog configuration and the schema manager to A2UI v0.8 semantics. Different versions may have different widget sets, property schemas, or protocol message formats.

**Fallback when unavailable**: The handcrafted system prompt implicitly targets v0.8 through its widget definitions and example JSON structures. The JSON schema in `a2ui_schema.py` is also hand-written for v0.8.

---

### 3. `A2uiSchemaManager` from `a2ui.core.schema.manager`

**Purpose**: Central SDK class that manages schema definitions, generates LLM system prompts, and provides validators.

**Usage sites**:

**(a) Initialization** (agent.py line 43):
```python
_sdk_manager = A2uiSchemaManager(version=VERSION_0_8, catalogs=[_sdk_catalog_config])
```

**(b) Catalog access** (agent.py line 44):
```python
_sdk_catalog = _sdk_manager.get_selected_catalog()
```
Returns a catalog object with:
- `.catalog_id` — logged at startup (line 365)
- `.validator.validate(messages)` — validates JSONL message lists (line 857)

**(c) System prompt generation** (agent.py lines 831-837):
```python
def _build_jsonl_system_prompt() -> str:
    return _sdk_manager.generate_system_prompt(
        role_description=_JSONL_ROLE_DESCRIPTION,
        workflow_description=_JSONL_WORKFLOW_DESCRIPTION,
        ui_description=_JSONL_UI_DESCRIPTION,
        include_schema=True,
        include_examples=False,
    )
```

This is the key differentiator. Instead of the 327-line handcrafted `A2UI_SYSTEM_PROMPT`, the SDK dynamically generates a system prompt that:
- Embeds the full JSON schema for the protocol version
- Includes the available widget catalog definitions
- Merges in the role/workflow/UI descriptions provided by the agent
- Can optionally include examples

**Fallback when unavailable** (agent.py line 829):
```python
def _build_jsonl_system_prompt() -> str:
    if not _SDK_AVAILABLE:
        return _JSONL_ROLE_DESCRIPTION  # ~3 lines vs SDK's schema-rich prompt
```
Without the SDK, the JSONL endpoint falls back to a minimal role description, then on the response side falls through to the `A2UI_SYSTEM_PROMPT` pipeline (line 892).

---

### 4. `sdk_parse_response` (aliased from `parse_response`) from `a2ui.core.parser.parser`

**Purpose**: Parses LLM output that contains `<a2ui-json>` tagged blocks into structured parts.

**Usage site** (agent.py lines 990-1006):
```python
def _parse_jsonl_response(content: str, suffix: str) -> tuple[str, list]:
    if _SDK_AVAILABLE:
        try:
            parts = sdk_parse_response(content)
            text = " ".join(p.text for p in parts if p.text).strip()
            messages = []
            for part in parts:
                if part.a2ui_json:
                    if isinstance(part.a2ui_json, list):
                        messages.extend(part.a2ui_json)
                    else:
                        messages.append(part.a2ui_json)
            surface_id = f"response_{suffix}"
            messages = _inject_surface_id(messages, surface_id)
            return text, messages
        except ValueError:
            pass  # No <a2ui-json> tags -- fall through to text-only
```

**What it does**: When the SDK generates the system prompt, it instructs the LLM to output plain text followed by an `<a2ui-json>[...]</a2ui-json>` block. `sdk_parse_response()` splits that into parts with `.text` and `.a2ui_json` attributes. The JSON inside the tags is a list of A2UI protocol messages (surfaceUpdate, dataModelUpdate, beginRendering).

**Fallback when unavailable** (agent.py lines 1008-1022):
```python
    # No SDK or no tags: extract plain text
    text = content.strip()
    if text.startswith("{"):
        # Old uiDefinition format fallback -- convert to JSONL
        try:
            parsed = json.loads(text)
            text_part = parsed.get("text", "")
            ui_def = parsed.get("uiDefinition") or parsed.get("ui_definition")
            if ui_def:
                messages = _ui_def_to_jsonl(ui_def, suffix)
                return text_part, messages
        except json.JSONDecodeError:
            pass
    return text, []
```

---

## How `_SDK_AVAILABLE` Gates Code Paths

| Location | When `True` (SDK installed) | When `False` (current) |
|----------|---------------------------|----------------------|
| **Startup** (line 46) | Logs `catalog_id` | Logs warning: "A2UI SDK not available" |
| **Startup banner** (line 364) | `SDK available: True` | `SDK available: False` |
| **`_build_jsonl_system_prompt()`** (line 829) | SDK generates schema-rich prompt | Returns minimal `_JSONL_ROLE_DESCRIPTION` |
| **`_validate_jsonl_messages()`** (line 854) | `_sdk_catalog.validator.validate(messages)` | Returns `(True, "")` — skips validation |
| **`/chat/stream/jsonl` handler** (line 889) | Uses `stream_llm_copilot_sdk_with_prompt()` with SDK prompt | Falls back to `A2UI_SYSTEM_PROMPT` path, then reorders ops for JSONL |
| **`_parse_jsonl_response()`** (line 990) | Uses `sdk_parse_response()` for `<a2ui-json>` parsing | Falls back to JSON `uiDefinition` format parsing |

---

## The SDK-Powered JSONL Flow (Future State)

When the SDK is properly installed, the `/chat/stream/jsonl` endpoint will work as follows:

```
1. BasicCatalog.get_config(VERSION_0_8)
   └─> Returns catalog config with widget definitions

2. A2uiSchemaManager(version, catalogs)
   └─> Initializes schema manager with widget knowledge

3. _sdk_manager.generate_system_prompt(role, workflow, ui, include_schema=True)
   └─> Builds rich system prompt embedding:
       - Full A2UI JSON Schema for v0.8
       - Widget catalog definitions
       - Agent's role/workflow/UI descriptions
       - Instructions to output <a2ui-json> blocks

4. LLM receives enriched system prompt
   └─> Responds with: "Plain text summary\n<a2ui-json>[{surfaceUpdate:...}, {dataModelUpdate:...}, {beginRendering:...}]</a2ui-json>"

5. sdk_parse_response(llm_output)
   └─> Returns parts with .text and .a2ui_json attributes

6. _sdk_catalog.validator.validate(messages)
   └─> Validates protocol messages against SDK's schema rules
   └─> On failure: retry once, then fall back to text-only

7. Stream JSONL: surfaceUpdate(s) -> dataModelUpdate -> beginRendering -> done
```

---

## The Fallback Flow (Current State)

Since the SDK is not installed, `/chat/stream/jsonl` currently works via:

```
1. _build_jsonl_system_prompt() returns _JSONL_ROLE_DESCRIPTION (minimal)

2. Falls into SDK-unavailable branch (line 889):
   "SDK unavailable -- using A2UI_SYSTEM_PROMPT fallback path"

3. Uses A2UI_SYSTEM_PROMPT (the handcrafted 327-line prompt)

4. parse_agent_response() extracts {"text": ..., "uiDefinition": ...}

5. transform_to_operations() converts to protocol ops

6. Reorders for JSONL spec: surfaceUpdates -> dataModelUpdate -> beginRendering

7. Streams as plain data: lines
```

---

## Where the SDK Package Should Come From

The `requirements.txt` contains this comment:
```
a2ui-agent
# A2UI Python SDK (schema management, prompt generation, validation)
# Install: pip install /path/to/A2UI/agent_sdks/python
```

The actual SDK source lives at:
`/Users/vijayakella/android/A2UI-Android/a2a_agents/python/a2ui_agent/`

However, this package (v0.1.0) only contains the **A2A extension layer** (`a2ui.extension.*`) — helper functions for creating A2UI parts, checking MIME types, and managing A2A agent extensions. It does NOT contain:
- `a2ui.core.schema.manager` (A2uiSchemaManager)
- `a2ui.core.schema.constants` (VERSION_0_8)
- `a2ui.core.parser.parser` (parse_response)
- `a2ui.basic_catalog.provider` (BasicCatalog)

These core modules appear to be part of a **not-yet-released** portion of the A2UI SDK. The `a2ui-agent` v0.0.0 in the venv is a placeholder name reservation.

---

## Confidence Assessment

| Finding | Confidence | Notes |
|---------|-----------|-------|
| SDK is not installed; `_SDK_AVAILABLE = False` | **Confirmed** | Python import fails; package is empty placeholder |
| All 4 imports are gated by try/except | **Confirmed** | Source code read at lines 36-50 |
| Fallback pipeline works fully without SDK | **Confirmed** | `/chat/stream` uses `A2UI_SYSTEM_PROMPT` directly; `/chat/stream/jsonl` falls back to same |
| SDK's intended API (generate_system_prompt, validate, parse_response) | **High confidence** | Inferred from call sites in agent.py |
| SDK source location / unreleased status | **High confidence** | Searched entire filesystem; only found A2A extension layer, not core modules |
| BasicCatalog provides widget catalog config | **Inferred** | Based on constructor pattern and naming; actual implementation not found |

---

## Footnotes

- agent.py lines 36-50: SDK import block with try/except and _SDK_AVAILABLE flag
- agent.py line 42: `BasicCatalog.get_config(version=VERSION_0_8)`
- agent.py line 43: `A2uiSchemaManager(version=VERSION_0_8, catalogs=[_sdk_catalog_config])`
- agent.py line 44: `_sdk_catalog = _sdk_manager.get_selected_catalog()`
- agent.py lines 827-837: `_build_jsonl_system_prompt()` using SDK
- agent.py lines 852-860: `_validate_jsonl_messages()` using SDK validator
- agent.py lines 886-923: JSONL endpoint SDK-unavailable fallback
- agent.py lines 990-1006: `_parse_jsonl_response()` using `sdk_parse_response()`
- agent.py startup banner line 364: `_SDK_AVAILABLE` status logging
- requirements.txt: `a2ui-agent` with install comment
- A2UI-Android SDK: `/Users/vijayakella/android/A2UI-Android/a2a_agents/python/a2ui_agent/` (contains only extension layer)
