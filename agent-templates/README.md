# Template-Based A2UI Agent

A **deterministic, no-LLM** agent that serves pre-approved financial UI templates over SSE. Drop-in replacement for the LLM-based agent in [`agent/agent.py`](../agent/agent.py) — same `/chat/stream` and `/event` endpoints, same A2UI protocol, zero generative AI dependencies.

### Why?

- **Legal compliance** — Every UI surface is pre-approved; no model hallucinations or unapproved copy.
- **Deterministic** — Same input always produces the same output. Easy to test and audit.
- **Fast & lightweight** — No model inference. Sub-millisecond rendering, instant SSE streaming.
- **No API keys** — No `GITHUB_TOKEN`, no `.env` file, no external service dependencies.

---

## Quick Start

```bash
cd agent-templates
pip install -r requirements.txt
python template_agent.py          # Starts on http://localhost:8000
```

Verify it's running:

```bash
curl http://localhost:8000/health
```

---

## Architecture

```
User message
  │
  ▼
intent_router.classify(message)
  │  Keyword matching → IntentMatch(template_id, data_id, confidence)
  │
  ▼
template_renderer.render(template_id, data_id)
  │  Load template + data JSON → substitute ${placeholders} → expand items
  │
  ▼
a2ui_transform.transform_to_operations(parsed_response, surface_suffix)
  │  Expand itemTemplate × items → literalString→path bindings
  │  → sanitize dangling refs → chunk components (15 per batch)
  │
  ▼
SSE stream
  text → beginRendering → dataModelUpdate → surfaceUpdate ×N → done
```

### Files

| File | Role |
|------|------|
| `template_agent.py` | FastAPI server — `/chat/stream` (SSE), `/event` (POST), `/health` (GET) on port 8000 |
| `intent_router.py` | Keyword-based intent classifier → `IntentMatch(template_id, data_id, confidence)` |
| `template_renderer.py` | Loads template + data JSON, substitutes `${placeholders}`, expands `itemTemplate` × items |
| `a2ui_transform.py` | Transform pipeline: template expansion → path bindings → sanitization → chunking |
| `templates/` | 3 pre-approved A2UI template JSON files |
| `data/` | 3 mock data JSON files |
| `requirements.txt` | `fastapi`, `uvicorn`, `sse-starlette`, `pydantic` — no LLM dependencies |

---

## Supported Intents

| Intent | Template ID | Sample Queries | Confidence |
|--------|-------------|----------------|------------|
| Transaction history | `transaction_history` | "Show my recent transactions", "What were my last purchases?" | `exact` — requires **"last"** + **"transaction"** |
| Account balances | `account_balances` | "What are my account balances?", "Show account summary" | `exact` — requires **"account"** + **"balance"** |
| Brokerage activity | `brokerage_activity` | "Show brokerage trades", "Recent stock activity" | `keyword` — any word in trigger set¹ |

¹ Brokerage triggers: `account`, `transaction`, `transactions`, `activity`, `portfolio`, `balance`, `brokerage`, `trades`, `holdings`, `stocks`

**Priority:** Exact matches (transaction history, account balances) are tested first. Brokerage activity is the broadest fallback. If nothing matches, the agent returns a plain-text help message.

---

## Template Authoring Guide

### Adding a New Template

**Step 1 — Create `templates/my_template.json`:**

```json
{
  "templateId": "my_template",
  "version": "1.0.0",
  "approvedBy": "Legal Team",
  "approvedDate": "2026-01-15",
  "description": "Human-readable description of this template",
  "textTemplate": "Summary: ${title}\n${subtitle}\n\n{{#items}}{{name}}  {{value}}\n{{/items}}",
  "uiDefinition": {
    "root": "root",
    "components": {
      "root": {
        "id": "root",
        "componentProperties": {
          "Column": {
            "children": { "explicitList": ["header_card", "items_list"] }
          }
        }
      },
      "header_card": {
        "id": "header_card",
        "componentProperties": {
          "Card": { "child": "header_col" }
        }
      },
      "items_list": {
        "id": "items_list",
        "componentProperties": {
          "Column": {
            "children": { "explicitList": [] }
          }
        }
      }
    },
    "itemTemplate": {
      "itemListId": "items_list",
      "rootId": "row_{i}",
      "dividerId": "div_{i}",
      "components": {
        "row_{i}": {
          "id": "row_{i}",
          "componentProperties": {
            "Row": {
              "children": { "explicitList": ["name_{i}", "value_{i}"] },
              "distribution": "spaceBetween"
            }
          }
        },
        "name_{i}": {
          "id": "name_{i}",
          "componentProperties": {
            "Text": {
              "text": { "literalString": "{name}" },
              "usageHint": "body"
            }
          }
        },
        "value_{i}": {
          "id": "value_{i}",
          "componentProperties": {
            "Text": {
              "text": { "literalString": "{value}" },
              "usageHint": "body"
            }
          }
        }
      }
    }
  }
}
```

**Step 2 — Create `data/my_template.json`:**

```json
{
  "title": "Portfolio Summary",
  "subtitle": "As of March 2026",
  "items": [
    { "name": "AAPL", "value": "$182.50" },
    { "name": "GOOGL", "value": "$175.30" }
  ]
}
```

**Step 3 — Add keywords to `intent_router.py`:**

Add a new condition block in the `classify()` function with appropriate keyword matching logic and return an `IntentMatch` with your template and data IDs.

### Placeholder Conventions

| Syntax | Context | Example | Substituted with |
|--------|---------|---------|------------------|
| `${key}` | Text template & UI components | `${title}` | Scalar value from data JSON |
| `{{#list}}...{{/list}}` | Text template only | `{{#transactions}}{{action}}{{/transactions}}` | Repeated for each item in the array |
| `{i}` | Item template components | `row_{i}`, `text_{i}` | Item index (0, 1, 2, …) |
| `{field}` | Item template `literalString` values | `{action}`, `{amount}` | Field value from each item in the data array |

---

## Template JSON Structure

### Required Fields

```
templateId          string    Unique identifier (matches data file name)
version             string    Semantic version, e.g., "1.0.0"
approvedBy          string    Approval authority (e.g., "Legal Team")
approvedDate        string    ISO date of approval
description         string    Human-readable description
textTemplate        string    Plain text with ${key} and {{#list}} sections
uiDefinition        object    A2UI component tree
  ├─ root           string    ID of the root component
  ├─ components     object    Map of component ID → component definition
  └─ itemTemplate   object    (Optional) Template for list expansion
      ├─ itemListId string    Target Column/List ID to inject expanded rows
      ├─ rootId     string    Pattern with {i}, e.g., "row_{i}"
      ├─ dividerId  string    (Optional) Divider pattern, e.g., "div_{i}"
      └─ components object    Item component map with {i} and {field} placeholders
```

### Component Types Used

- **Column** — Vertical layout with `children.explicitList`
- **Row** — Horizontal layout with `children.explicitList` and `distribution`
- **Card** — Container with single `child`
- **Text** — Text display with `text.literalString` and `usageHint` (`title`, `heading`, `body`, `caption`)
- **List** — Scrollable list with `children.explicitList`
- **Divider** — Visual separator

---

## SSE Protocol

The `/chat/stream` endpoint emits Server-Sent Events in this order:

| # | Event Type | Payload | Description |
|---|-----------|---------|-------------|
| 1 | `text` | `{"text": "..."}` | User-facing summary text |
| 2 | `a2ui_op` | `{"beginRendering": {"surfaceId": "response_<suffix>", "root": "root"}}` | Initialize a new rendering surface |
| 3 | `a2ui_op` | `{"dataModelUpdate": {"surfaceId": "...", "path": "", "contents": [...]}}` | Populate data model with path-bound values |
| 4…N | `a2ui_op` | `{"surfaceUpdate": {"surfaceId": "...", "components": [...]}}` | Component chunks (up to 15 components each) |
| N+1 | `done` | `{}` | Stream complete |

**Timing:**
- 100ms delay after `text` event before first `a2ui_op`
- 150ms delay between subsequent `a2ui_op` events (progressive rendering)

### Transform Pipeline Details

The `a2ui_transform` module converts the rendered template into the SSE operation sequence:

1. **Template expansion** — `itemTemplate` × data items → individual components. `{i}` replaced with indices, `{field}` with item values. Max 200 items.
2. **Path bindings** — Every `literalString` value in components is extracted into a `dataModelUpdate` entry and replaced with a `path` reference (e.g., `{"literalString": "Hello"}` → `{"path": "/component_id"}`).
3. **Sanitization** — Removes dangling child references where a child ID doesn't exist in the component map.
4. **Chunking** — Components split into batches of 15 for incremental `surfaceUpdate` delivery.

---

## Testing

### Health Check

```bash
curl -s http://localhost:8000/health | python -m json.tool
```

### Stream a Chat Response

```bash
curl -N -X POST http://localhost:8000/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message": "Show my last transactions"}'
```

```bash
curl -N -X POST http://localhost:8000/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message": "What are my account balances?"}'
```

```bash
curl -N -X POST http://localhost:8000/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message": "Show brokerage activity"}'
```

### Send a UI Event

```bash
curl -X POST http://localhost:8000/event \
  -H "Content-Type: application/json" \
  -d '{
    "surface_id": "response_abc123",
    "event_type": "userAction",
    "name": "tap",
    "source_component_id": "row_0"
  }'
```

### Test Fallback (No Intent Match)

```bash
curl -N -X POST http://localhost:8000/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message": "What is the weather?"}'
```

---

## Connecting to the Android App

The template agent is **fully compatible** with the Android app — no code changes required.

### Setup

1. **Start the template agent** on your host machine:
   ```bash
   cd agent-templates
   pip install -r requirements.txt
   python template_agent.py    # runs on :8000
   ```

2. **Enable real agent** in `app/build.gradle.kts`:
   ```kotlin
   // In the debug buildType, set:
   buildConfigField("Boolean", "USE_REAL_AGENT", "true")
   ```

3. **Run the app** on an Android emulator. The emulator uses `10.0.2.2:8000` to reach the host machine's `localhost:8000` (configured in `RealChatRepository.kt`).

### Physical Device

For a physical device, update the base URL in `RealChatRepository.kt`:

```kotlin
private val baseUrl: String = "http://<your-lan-ip>:8000"
```

And ensure your device and machine are on the same network.

### How It Works

| Build Config Flag | Default (Debug) | For Template Agent |
|-------------------|------------------|--------------------|
| `USE_REAL_AGENT` | `false` (mock data) | Set to `true` |
| `USE_JSONL_ENDPOINT` | `true` | Keep as `true` |

The app's `ChatViewModel` switches between `MockChatRepository` (offline mock) and `RealChatRepository` (network SSE) based on the `USE_REAL_AGENT` flag. The template agent serves the same SSE protocol as the LLM agent, so the Android app handles both identically.

---

## Comparison: Template Agent vs LLM Agent

| | Template Agent (`agent-templates/`) | LLM Agent (`agent/`) |
|--|--------------------------------------|----------------------|
| **Determinism** | ✅ Same input → same output | ❌ Non-deterministic |
| **Dependencies** | FastAPI + uvicorn only | + GitHub Copilot SDK / Models API |
| **API keys** | None | `GITHUB_TOKEN` or `GITHUB_MODELS_TOKEN` |
| **Latency** | < 10ms render + simulated streaming | Seconds (model inference) |
| **Compliance** | Pre-approved templates only | Generated content requires review |
| **Flexibility** | Fixed intents & templates | Free-form conversation |
| **Port** | 8000 | 8000 |
| **Endpoints** | `/chat/stream`, `/event`, `/health` | `/chat/stream`, `/chat/stream/jsonl`, `/event`, `/health` |
