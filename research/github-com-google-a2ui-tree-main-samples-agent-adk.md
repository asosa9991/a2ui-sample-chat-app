# Research Report: Google A2UI — ADK Agent Samples

> **Source**: [google/A2UI](https://github.com/google/A2UI) · ⭐ 13,697 · 🍴 1,040 · Apache 2.0  
> **Status**: Active — v0.8 Public Preview (v0.9 examples also present)  
> **Primary Languages**: TypeScript (core), Python (agent SDK)  
> **Research Focus**: `samples/agent/adk/` — Agent Development Kit samples  
> **Date**: 2025-07

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [What Is A2UI?](#2-what-is-a2ui)
3. [Repository Structure](#3-repository-structure)
4. [Core Dependency: `a2ui-agent` Python SDK](#4-core-dependency-a2ui-agent-python-sdk)
5. [ADK Workspace Layout](#5-adk-workspace-layout)
6. [Sample Deep Dives](#6-sample-deep-dives)
   - 6.1 [Restaurant Finder](#61-restaurant-finder)
   - 6.2 [Orchestrator](#62-orchestrator)
   - 6.3 [Contact Lookup](#63-contact-lookup)
   - 6.4 [Contact Multiple Surfaces](#64-contact-multiple-surfaces)
   - 6.5 [RizzCharts](#65-rizzcharts)
   - 6.6 [MCP App Proxy](#66-mcp-app-proxy)
7. [Cross-Cutting Architectural Patterns](#7-cross-cutting-architectural-patterns)
8. [v0.8 → v0.9 Protocol Evolution](#8-v08--v09-protocol-evolution)
9. [Key Insights for Our Project](#9-key-insights-for-our-project)
10. [Confidence Assessment](#10-confidence-assessment)
11. [Footnotes](#11-footnotes)

---

## 1. Executive Summary

Google's **A2UI** (Agent-to-UI) is an open standard and set of libraries that allow AI agents to "speak UI" — emitting **declarative JSON operations** that client renderers turn into native platform widgets. The `samples/agent/adk/` directory contains **six Python-based agent samples** built on the **Google Agent Development Kit (ADK)** and the **A2A (Agent-to-Agent) protocol**. Together they demonstrate the canonical way to build agents that generate rich, interactive user interfaces.

### Core Architectural Flow

```
┌──────────────┐      A2A Protocol       ┌───────────────────┐
│  Client App  │  ◄──────────────────►   │   ADK Agent       │
│  (Angular /  │                          │                   │
│   React /    │   ┌──────────────────┐   │  LLM generates    │
│   Flutter /  │   │  A2UI Operations │   │  A2UI JSON ops    │
│   Lit)       │   │  (JSON stream)   │   │                   │
│              │   └──────────────────┘   │  Schema validated  │
│  Renderer    │                          │  Retry on failure  │
│  renders     │   ┌──────────────────┐   │  Text fallback    │
│  natively    │   │  UI Events       │   │                   │
│              │   │  (user actions)  │   │                   │
└──────────────┘   └──────────────────┘   └───────────────────┘
```

**Key takeaways:**

- The LLM generates UI JSON directly (not code) — validated against a JSON schema with retry + text fallback
- All samples use a **dual-runner** pattern: UI runner (with A2UI extension) + text-only runner (fallback)
- Path bindings (`{"path": "/field"}`) are the canonical approach for dynamic data — every example uses them
- v0.9 dramatically simplifies the protocol (flat components, native JSON data, template-driven lists)
- A2A protocol is the transport layer — A2UI operations ride as `DataPart` messages

---

## 2. What Is A2UI?

A2UI is an **open standard** that defines a JSON protocol for AI agents to describe user interfaces declaratively. Instead of generating HTML, Markdown, or platform-specific code, agents emit **A2UI operations** — structured JSON messages that describe:

| Operation | Purpose |
|-----------|---------|
| `createSurface` (v0.9) / `beginRendering` (v0.8) | Initialize a rendering surface |
| `updateComponents` (v0.9) / `surfaceUpdate` (v0.8) | Define or update UI component tree |
| `updateDataModel` (v0.9) / `dataModelUpdate` (v0.8) | Push data values for path-bound components |
| `deleteSurface` | Dismiss/close a surface (e.g., modal) |

Client renderers (Angular, React, Flutter, Lit) interpret these operations and render **native platform widgets**. The agent never knows what platform is rendering — it just describes the UI structure and data.

---

## 3. Repository Structure

```
google/A2UI/
│
├── agent_sdks/
│   └── python/                    # a2ui-agent Python SDK (core library)
│       ├── pyproject.toml
│       └── src/a2ui/
│           ├── adk/               # ADK integration (A2uiExtension, etc.)
│           ├── schema_manager.py  # JSON Schema validation
│           └── assets/            # A2UI specification schemas
│
├── docs/                          # mkdocs documentation site
│
├── renderers/
│   ├── angular/                   # Angular renderer
│   ├── flutter/                   # Flutter renderer
│   ├── lit/                       # Lit/Web Component renderer
│   └── react/                     # React renderer
│
├── samples/
│   ├── agent/
│   │   ├── adk/                   # ★ ADK-based agent samples (THIS REPORT)
│   │   │   ├── contact_lookup/
│   │   │   ├── contact_multiple_surfaces/
│   │   │   ├── mcp_app_proxy/
│   │   │   ├── orchestrator/
│   │   │   ├── restaurant_finder/
│   │   │   ├── rizzcharts/
│   │   │   └── migrate_v08_to_v09.py
│   │   └── mcp/                   # MCP-based agent samples
│   ├── client/
│   │   ├── angular/               # Angular client apps
│   │   ├── lit/                   # Lit/Web Component client apps
│   │   ├── react/                 # React client app
│   │   └── shared/                # Shared client utilities
│   └── personalized_learning/     # End-to-end learning app sample
│
├── specification/                 # A2UI specification documents
└── tools/                         # Build utilities
```

---

## 4. Core Dependency: `a2ui-agent` Python SDK

Located at `agent_sdks/python/`, the `a2ui-agent` package is the foundational library that all ADK samples depend on. It is installed as an **editable dependency** (`{ path = "../../../agent_sdks/python", editable = true }`).

### Dependencies

```toml
# agent_sdks/python/pyproject.toml
dependencies = [
  "a2a-sdk>=0.3.0",       # Agent-to-Agent protocol SDK
  "google-adk>=1.28.0",   # Google Agent Development Kit
  "google-genai>=1.27.0", # Google GenAI SDK (Gemini models)
  "jsonschema>=4.0.0"     # JSON Schema validation
]
```

### Key Modules

| Module | Responsibility |
|--------|---------------|
| `a2ui.adk.extension.A2uiExtension` | ADK extension that injects A2UI schema + examples into LLM prompts |
| `a2ui.schema_manager.A2uiSchemaManager` | Loads catalog schemas, validates LLM output against JSON schema |
| `a2ui.schema_manager.BasicCatalog` | Built-in A2UI component catalog (Text, Button, Image, List, etc.) |
| `a2ui.assets/` | Bundled A2UI specification JSON schemas (v0.8, v0.9) |

---

## 5. ADK Workspace Layout

The `samples/agent/adk/` directory is configured as a **UV workspace** — a monorepo of related Python packages managed by the `uv` package manager.

```toml
# samples/agent/adk/pyproject.toml
[tool.uv.workspace]
members = [
  "contact_lookup",
  "contact_multiple_surfaces",
  "orchestrator",
  "restaurant_finder",
  "rizzcharts",
  "mcp_app_proxy"
]

[tool.uv.sources]
a2ui-agent = { path = "../../../agent_sdks/python", editable = true }
```

### Common Dependencies (All Samples)

Every sample's `pyproject.toml` includes:

| Package | Version | Purpose |
|---------|---------|---------|
| `google-adk` | ≥1.28.0 | Google Agent Development Kit |
| `google-genai` | ≥1.27.0 | GenAI SDK (Gemini model access) |
| `a2a-sdk` | ≥0.3.0 | Agent-to-Agent protocol |
| `a2ui-agent` | editable | A2UI SDK (from repo) |
| `litellm` | latest | Multi-LLM abstraction layer |
| `jsonschema` | ≥4.0.0 | JSON Schema validation |
| `python-dotenv` | latest | `.env` file loading |

---

## 6. Sample Deep Dives

### 6.1 Restaurant Finder

| | |
|---|---|
| **Directory** | `restaurant_finder/` |
| **Port** | 10002 |
| **Purpose** | Restaurant discovery + booking workflow |
| **Lines of Code** | ~371 (agent.py) |

#### File Structure

```
restaurant_finder/
├── __main__.py              # Uvicorn entry point, CORS, static files
├── agent.py                 # Dual-runner agent (text + UI v0.8/v0.9)
├── agent_executor.py        # A2A AgentExecutor, handles UI events
├── prompt_builder.py        # System prompts with layout rules
├── tools.py                 # get_restaurants(cuisine, location, count=5)
├── restaurant_data.json     # Mock data (8 Chinese restaurants)
└── examples/
    ├── 0.8/
    │   ├── two_column_list.json
    │   ├── booking_form.json
    │   └── booking_confirmation.json
    └── 0.9/
        ├── single_column_list.json
        ├── booking_form.json
        └── booking_confirmation.json
```

#### Dual-Runner Architecture

This is the most instructive sample. It creates **two independent ADK Runners**:

```
┌─────────────────────────────────────────────┐
│              Restaurant Finder              │
│                                             │
│  ┌─────────────────┐  ┌──────────────────┐  │
│  │   text_runner    │  │   ui_runner      │  │
│  │                  │  │                  │  │
│  │  LlmAgent       │  │  LlmAgent        │  │
│  │  (no extensions) │  │  + A2uiExtension │  │
│  │                  │  │  + Schema valid. │  │
│  │  Pure text       │  │  + JSON examples │  │
│  │  responses       │  │                  │  │
│  └─────────────────┘  └──────────────────┘  │
│                                             │
│  Strategy: Try ui_runner first.             │
│  If schema validation fails after retry,    │
│  fall back to text_runner.                  │
└─────────────────────────────────────────────┘
```

#### Prompt Engineering — Layout Rules

The system prompt in `prompt_builder.py` includes specific UI layout instructions:

| Scenario | Layout Rule |
|----------|-------------|
| ≤5 restaurant results | Single-column vertical list |
| >5 restaurant results | Two-column grid layout |
| Booking request | Form with TextField, DateTimeInput, Button |
| Booking confirmation | Confirmation card with summary |

#### Schema Validation + Retry Flow

```
LLM generates JSON
        │
        ▼
  ┌──────────────┐     ┌──────────────────┐
  │ JSON Schema  │────►│ Valid?           │
  │ Validation   │     │                  │
  └──────────────┘     └──────────────────┘
                              │
                    ┌─────────┼─────────┐
                    ▼                   ▼
               [YES: emit]        [NO: retry]
                                       │
                                       ▼
                              ┌──────────────┐
                              │ Append error │
                              │ to prompt    │
                              │ Retry ONCE   │
                              └──────────────┘
                                       │
                              ┌────────┼────────┐
                              ▼                 ▼
                         [Valid: emit]   [Invalid: text fallback]
```

#### Event Handling

The `AgentExecutor` intercepts UI events and maps them to follow-up queries:

| Event Name | Action |
|------------|--------|
| `book_restaurant` | Extract restaurant context → generate booking form UI |
| `submit_booking` | Extract form data → generate confirmation card UI |

#### v0.8 Operation Example (`two_column_list.json`)

```json
[
  {
    "beginRendering": {
      "surfaceId": "default",
      "root": "root-column"
    }
  },
  {
    "surfaceUpdate": {
      "surfaceId": "default",
      "components": [
        {
          "id": "root-column",
          "component": { "Column": { "children": { "explicitList": ["..."] } } }
        }
      ]
    }
  },
  {
    "dataModelUpdate": {
      "surfaceId": "default",
      "path": "/",
      "contents": [
        { "key": "title", "valueString": "Chinese Restaurants" }
      ]
    }
  }
]
```

#### v0.9 Operation Example (`single_column_list.json`)

```json
[
  {
    "createSurface": {
      "surfaceId": "default",
      "version": "v0.9",
      "root": "root-column"
    }
  },
  {
    "updateComponents": {
      "surfaceId": "default",
      "components": [
        {
          "id": "item-list",
          "component": "List",
          "direction": "vertical",
          "children": {
            "componentId": "item-card-template",
            "path": "/items"
          }
        },
        {
          "id": "item-card-template",
          "component": "Card",
          "children": ["item-name", "item-cuisine"]
        },
        {
          "id": "item-name",
          "component": "Text",
          "text": { "path": "/name" }
        }
      ]
    }
  },
  {
    "updateDataModel": {
      "surfaceId": "default",
      "path": "/",
      "value": {
        "items": [
          { "name": "Golden Dragon", "cuisine": "Chinese" },
          { "name": "Jade Palace", "cuisine": "Chinese" }
        ]
      }
    }
  }
]
```

> **Template-Driven Lists** — The v0.9 `List` widget with `children: { componentId, path }` eliminates per-item component generation. The LLM generates the template ONCE, and the renderer iterates over the data array. For 100 items, you need ~10 template components + a data array, instead of 100× duplicated components.

---

### 6.2 Orchestrator

| | |
|---|---|
| **Directory** | `orchestrator/` |
| **Port** | 10002 |
| **Purpose** | Multi-agent routing hub |
| **Lines of Code** | ~269 (agent.py) |

#### File Structure

```
orchestrator/
├── __main__.py                    # CLI: --subagent_urls option
├── agent.py                       # Dynamic agent builder
├── agent_executor.py              # Extended A2A executor
├── part_converters.py             # A2A ↔ GenAI part conversion
└── subagent_route_manager.py      # SurfaceId → Subagent routing
```

#### Architecture

```
                    ┌──────────────────────────┐
                    │      Orchestrator        │
                    │                          │
    User query ───► │  LLM decides which       │
                    │  subagent to route to     │
                    │                          │
                    └──────┬───────┬───────────┘
                           │       │
              ┌────────────┘       └────────────┐
              ▼                                 ▼
    ┌──────────────────┐              ┌──────────────────┐
    │ Restaurant Finder│              │ Contact Lookup   │
    │ (RemoteA2aAgent) │              │ (RemoteA2aAgent) │
    │ Port 10002       │              │ Port 10003       │
    └──────────────────┘              └──────────────────┘
```

#### Dynamic Subagent Discovery

```python
async def build_agent(cls, subagent_urls):
    # 1. Fetch A2A agent cards from each subagent URL
    cards = [await A2ACardResolver(url).get_agent_card() for url in subagent_urls]

    # 2. Aggregate A2UI extensions, catalogs, skills from all subagents
    # 3. Create RemoteA2aAgent for each
    # 4. Build orchestrator LLM: "Route to exactly one subagent"
```

#### Surface-Based Routing

When a UI event arrives, the orchestrator must route it to the correct subagent:

```python
# On surface creation — record which subagent owns it
SubagentRouteManager.set_route_to_subagent_name(surfaceId, subagent_name)

# On event dispatch — look up the owner
subagent = SubagentRouteManager.get_route_to_subagent_name(surfaceId)
```

Uses **session state** for persistence across turns.

#### A2UI Metadata Interceptor

A custom `A2AClientFactoryWithA2UIMetadata` class injects A2UI extension headers and client capabilities into all outbound A2A requests — ensuring subagents know the client supports A2UI rendering.

---

### 6.3 Contact Lookup

| | |
|---|---|
| **Directory** | `contact_lookup/` |
| **Port** | 10003 |
| **Purpose** | Employee directory with profile cards |

Follows the same dual-runner architecture as Restaurant Finder. Demonstrates:

- **Contact card rendering** — Single result → detailed profile card
- **Contact list rendering** — Multiple results → scrollable list
- **Action handling** — `view_profile`, `send_email`, `send_message`, `follow_contact`

---

### 6.4 Contact Multiple Surfaces

| | |
|---|---|
| **Directory** | `contact_multiple_surfaces/` |
| **Port** | 10004 |
| **Lines of Code** | ~525 (agent.py) |
| **Purpose** | Multi-surface demo with floor plans |

Extends Contact Lookup with three significant capabilities:

#### Multiple Independent Surfaces

A single agent response can create **multiple rendering surfaces** simultaneously — e.g., a contact card on the main surface AND an org chart on a secondary surface.

#### MCP Integration (Floor Plans)

```python
# floor_plan_server.py — MCP SSE server
@mcp.resource("ui://floor-plan-server/map")
async def get_floor_plan():
    return Resource(html_content)  # Full HTML floor plan
```

The floor plan is served via an **MCP (Model Context Protocol) SSE server** and rendered in the client using the `WebFrame` / `McpApp` widget.

#### Inline Catalogs

```python
schema_manager = A2uiSchemaManager(
    catalog=BasicCatalog,
    version="0.9",
    accepts_inline_catalogs=True,  # ← Allows custom widget definitions
)
```

Inline catalogs let the agent define **custom widgets** on the fly — the catalog definition is sent alongside the UI operations.

#### Modal Dismiss

Uses `deleteSurface` operation to programmatically close overlay/modal surfaces.

---

### 6.5 RizzCharts

| | |
|---|---|
| **Directory** | `rizzcharts/` |
| **Purpose** | Custom chart/map visualization |

Demonstrates:

- **Custom A2UI catalog** with chart widgets (BarChart, LineChart, Map, etc.)
- **v0.8 and v0.9 catalog schemas** side-by-side
- **Non-standard widget integration** via catalog extension mechanism

---

### 6.6 MCP App Proxy

| | |
|---|---|
| **Directory** | `mcp_app_proxy/` |
| **Purpose** | Bridge MCP apps to A2UI surfaces |

Demonstrates:

- **MCP-to-A2UI bridging** — Exposes MCP tools as A2UI-capable agents
- **Dual catalog support** — Serves both v0.8 and v0.9 catalogs simultaneously

---

## 7. Cross-Cutting Architectural Patterns

### Pattern 1: A2UI Extension Integration

Every sample integrates A2UI via the ADK extension system:

```python
from a2ui.adk.extension import A2uiExtension
from a2ui.schema_manager import A2uiSchemaManager, BasicCatalog

# 1. Create schema manager (selects catalog + version)
schema_manager = A2uiSchemaManager(
    catalog=BasicCatalog,
    version="0.9",
    accepts_inline_catalogs=False,
)

# 2. Create extension (injects schema + examples into LLM prompt)
extension = A2uiExtension(
    schema_manager=schema_manager,
    examples=examples,  # Pre-built JSON example files
)

# 3. Create runner with extension
ui_runner = Runner(
    agent=LlmAgent(
        model=LiteLlm(model="gemini-2.5-flash"),
        instruction="You are a restaurant finder...",
    ),
    extensions=[extension],
)
```

### Pattern 2: Schema Validation Is Core

```
┌────────────────┐     ┌──────────────────┐     ┌────────────────┐
│ A2uiSchemaManager │──►│ JSON Schema      │──►│ Validate LLM   │
│                    │  │ (from catalog)   │   │ output          │
│ - Loads catalog    │  └──────────────────┘   │                 │
│ - Selects version  │                          │ - Parse JSON    │
│ - Provides schema  │                          │ - Validate      │
│ - Supplies examples│                          │ - Retry if bad  │
└────────────────────┘                          │ - Text fallback │
                                                └────────────────┘
```

Validation is not optional — it's a first-class part of the generation pipeline. Every LLM response goes through:

1. **Parse** — Extract JSON from LLM output
2. **Validate** — Check against the catalog's JSON schema via `jsonschema`
3. **Retry** — If invalid, append error message and regenerate (once)
4. **Fallback** — If still invalid, use text-only runner

### Pattern 3: A2A Protocol Transport

All samples are **A2A (Agent-to-Agent) servers**:

```python
from a2a.server.starlette import A2AStarletteApplication

a2a_app = A2AStarletteApplication(
    agent_card=agent_card,
    http_handler=http_handler,
)
```

A2UI operations are transported as **`DataPart`** messages within A2A protocol messages. This provides:

- **Session management** — Built-in conversation state
- **Agent discovery** — Via A2A agent cards
- **Capabilities negotiation** — Client declares supported A2UI version
- **Authentication** — A2A's built-in auth mechanisms

### Pattern 4: LLM Generates UI JSON Directly

The LLM receives a system prompt containing:

1. **Role description** — "You are a restaurant finder agent..."
2. **UI layout rules** — "Use single-column for ≤5 items, two-column for >5..."
3. **A2UI JSON schema** — Injected automatically by `A2uiExtension`
4. **Example JSON outputs** — Real working examples from the `examples/` directory

The LLM then generates **valid A2UI operation JSON** as its response. No code generation, no template engine — the LLM IS the template engine.

### Pattern 5: Event → Action → New UI

User interactions follow a structured pipeline:

```
┌─────────┐     ┌─────────────┐     ┌───────────────┐     ┌──────────────┐
│ Client   │     │ A2A Message │     │ AgentExecutor │     │ LLM Agent    │
│          │     │             │     │               │     │              │
│ Button   │────►│ UserAction  │────►│ Map event to  │────►│ Generate new │
│ click    │     │ Event       │     │ query type    │     │ A2UI ops     │
│          │     │ name+context│     │               │     │              │
└─────────┘     └─────────────┘     └───────────────┘     └──────────────┘

Example:
  Event: { name: "book_restaurant", context: { name: "Golden Dragon" } }
  ──► Query: "USER WANTS TO BOOK Golden Dragon"
  ──► LLM generates: booking form A2UI operations
```

### Pattern 6: Dual-Runner Fallback

```python
# Pseudocode from restaurant_finder/agent.py
try:
    result = await ui_runner.run(query)
    # LLM generated A2UI JSON — validate it
    if schema_manager.validate(result):
        return result  # ✅ Rich UI response
    else:
        # Retry once with error feedback
        result = await ui_runner.run(query + f"\nError: {validation_error}")
        if schema_manager.validate(result):
            return result  # ✅ Rich UI response (2nd attempt)
except:
    pass

# Fallback to text-only
return await text_runner.run(query)  # 📝 Plain text response
```

---

## 8. v0.8 → v0.9 Protocol Evolution

The repository includes a migration script (`migrate_v08_to_v09.py`) and side-by-side examples showing the evolution.

### Operation Name Changes

| v0.8 | v0.9 |
|------|------|
| `beginRendering` | `createSurface` (+ explicit `version: "v0.9"` field) |
| `surfaceUpdate` | `updateComponents` |
| `dataModelUpdate` | `updateDataModel` |

### Component Definition Changes

**v0.8 — Nested wrapper format:**
```json
{
  "id": "title",
  "component": {
    "Text": {
      "text": { "literalString": "Hello World" },
      "usageHint": "headline"
    }
  }
}
```

**v0.9 — Flat format:**
```json
{
  "id": "title",
  "component": "Text",
  "text": "Hello World",
  "variant": "headline"
}
```

### Data Model Changes

**v0.8 — DataEntry format:**
```json
{
  "dataModelUpdate": {
    "surfaceId": "default",
    "path": "/",
    "contents": [
      { "key": "name", "valueString": "Golden Dragon" },
      { "key": "rating", "valueDouble": 4.5 }
    ]
  }
}
```

**v0.9 — Native JSON format:**
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

### Children Changes

**v0.8:** `"children": { "explicitList": ["child-1", "child-2"] }`  
**v0.9:** `"children": ["child-1", "child-2"]`

### Template-Driven Lists (v0.9 New)

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

The `List` widget iterates over `/items` in the DataModel and renders `item-card-template` for each item with a scoped `DataContext`. The LLM generates the template **once**, not per-item.

### Full Comparison Table

| Aspect | v0.8 | v0.9 |
|--------|------|------|
| Surface initialization | `beginRendering` | `createSurface` + `version` field |
| Component operations | `surfaceUpdate` | `updateComponents` |
| Component format | `component: { "Text": { props } }` (nested) | `component: "Text", ...props` (flat) |
| Data operations | `dataModelUpdate` | `updateDataModel` |
| Data format | `contents: [{key, valueString}]` (DataEntry) | `value: { key: val }` (native JSON) |
| Text values | `{"literalString": "..."}` or `{"path": "/..."}` | Direct string or `{"path": "/..."}` |
| Children | `{"explicitList": [...]}` | Direct array `[...]` |
| Usage hints | `usageHint` | `variant` |
| Theming | `styles: { primaryColor }` | `theme: { primaryColor, font }` |
| Version | Implicit | Explicit `"version": "v0.9"` |
| Template lists | Not available | `children: { componentId, path }` |

### Migration Script

`migrate_v08_to_v09.py` automates the transformation:

- `beginRendering` → `createSurface`
- `surfaceUpdate` → `updateComponents`
- `dataModelUpdate` → `updateDataModel`
- Nested `component: { "Type": { props } }` → Flat `component: "Type", ...props`
- `explicitList` → Direct arrays
- `literalString` → Direct strings
- `usageHint` → `variant`
- `contents` (DataEntry) → `value` (native JSON)

---

## 9. Key Insights for Our Project

### 9.1 Path Bindings Are the Canonical Approach

Every single example in the Google repo uses **path bindings** (`{"path": "/field"}`) for dynamic data, NOT `literalString`. The component tree is a static template; the DataModel holds all dynamic values.

```json
// Component (static structure)
{ "id": "name", "component": "Text", "text": { "path": "/name" } }

// DataModel (dynamic values)
{ "path": "/", "value": { "name": "Golden Dragon" } }
```

> **Implication**: Our recent server-side transformation from `literalString` to path bindings is exactly the right direction — it matches Google's reference implementation.

### 9.2 Template-Driven Lists Eliminate Redundancy

v0.9's `List` with `children: { componentId, path }` means:
- 100 items = ~10 template components + 1 data array
- NOT 100 × duplicated component definitions

> **Implication**: Massive reduction in LLM token usage and JSON payload size for list-heavy UIs.

### 9.3 Schema Validation Is Core, Not Optional

Google's approach: **generate → validate → retry → text fallback**. This catches malformed UI before it reaches the client.

> **Implication**: Adding JSON schema validation to our pipeline would catch invalid UI structures early, reducing client-side rendering errors.

### 9.4 The Dual-Runner Pattern Is Smart

Text-only fallback is **built into the architecture**, not bolted on. When UI generation fails, the agent seamlessly degrades to pure text.

> **Implication**: We should architect our agent with an explicit fallback path, not hope the UI generation always succeeds.

### 9.5 A2A Transport Provides More Than HTTP

Google uses the **A2A (Agent-to-Agent) protocol** as the transport layer. A2UI operations ride as `DataPart` messages. This provides session management, agent discovery, capabilities negotiation, and authentication — all out of the box.

> **Implication**: Our current raw HTTP/SSE transport works but doesn't get these benefits. Worth evaluating A2A for future iterations.

### 9.6 v0.9 Simplifies Everything

The v0.9 format eliminates:
- `componentProperties` wrapper
- `explicitList` wrapper
- `literalString` wrapper
- `DataEntry` format

Everything becomes native JSON.

> **Implication**: Reduces LLM's JSON generation complexity and token usage significantly. Should be our target protocol version.

### 9.7 Event Handling Architecture

Google's approach is more structured: events go through `AgentExecutor` which maps action names to specific query types (e.g., `book_restaurant` → booking form query).

> **Implication**: Our `/event` endpoint that sends raw event context to the LLM is simpler but less deterministic. A structured event-to-query mapper would give more predictable results.

---

## 10. Confidence Assessment

| Aspect | Confidence | Source |
|--------|-----------|--------|
| Repository structure and file contents | ✅ High | Direct clone and file reading |
| Sample architectures and patterns | ✅ High | Full source code analysis |
| v0.8 vs v0.9 protocol differences | ✅ High | Side-by-side JSON examples from repo |
| Dual-runner and validation patterns | ✅ High | Direct code reading of agent.py files |
| Event handling flow | ✅ High | agent_executor.py analysis |
| `a2ui-agent` SDK internals | ⚠️ Medium | pyproject.toml read; `src/` files not fully explored |
| A2A transport integration details | ⚠️ Medium | Inferred from imports and agent_executor patterns |
| Production deployment patterns | ❌ Low | Samples are development examples, not production configs |

---

## 11. Footnotes

[^1]: Repository: [google/A2UI](https://github.com/google/A2UI) — 13,697 stars, Apache 2.0  
[^2]: `samples/agent/adk/pyproject.toml` — UV workspace configuration  
[^3]: `agent_sdks/python/pyproject.toml` — a2ui-agent SDK, dependencies on google-adk, a2a-sdk  
[^4]: `samples/agent/adk/restaurant_finder/agent.py` — 371 lines, dual-runner pattern  
[^5]: `samples/agent/adk/restaurant_finder/examples/0.9/single_column_list.json` — Template-driven List  
[^6]: `samples/agent/adk/restaurant_finder/examples/0.8/two_column_list.json` — v0.8 path bindings  
[^7]: `samples/agent/adk/restaurant_finder/examples/0.9/booking_form.json` — TextField, DateTimeInput, Button  
[^8]: `samples/agent/adk/orchestrator/agent.py` — 269 lines, dynamic subagent discovery  
[^9]: `samples/agent/adk/orchestrator/subagent_route_manager.py` — SurfaceId routing  
[^10]: `samples/agent/adk/contact_multiple_surfaces/agent.py` — 525 lines, multi-surface + MCP  
[^11]: `samples/agent/adk/contact_multiple_surfaces/floor_plan_server.py` — MCP SSE server  
[^12]: `samples/agent/adk/migrate_v08_to_v09.py` — Protocol migration script  
