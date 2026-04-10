# Designer-First Template Workflow — Implementation Plan

**Date:** 2026-04-10  
**Based on:** Research report in session-state/research/

---

## Problem Statement

Build a **designer-first authoring loop** where:
1. A UX designer uses the app in LLM mode to craft financial card experiences iteratively
2. The designer saves a polished response as a **named, reusable template** (UI + data schema)
3. When a financial end-user asks a similar question at runtime, the system serves the saved template with the user's real data — zero LLM inference, instant response

The system is **~65% already built**. The template engine, transform pipeline, intent router, and 3 existing templates all exist and work. What's missing is the designer authoring loop, a data schema contract, and a live data adapter.

---

## Architecture Overview

```
DESIGN TIME (designer)                    RUNTIME (financial user)
─────────────────────────                 ────────────────────────────
App in DESIGNER mode                      App in USER mode
  └─ chat with LLM                          └─ "show my transactions"
  └─ iterate on card UI                             │
  └─ tap "Save as Template"               IntentRouter.classify()
       │                                   → template_id="transaction_history"
       ▼                                           │
POST /designer/save-template               DataAdapter.fetch(template_id, user_id)
  └─ transform_to_path_bindings()          → reads dataSchema from template
  └─ extract example data values           → calls financial API
  └─ write templates/{id}.json             → maps API fields → flat dict
  └─ write data/{id}.json (example)                │
  └─ hot-reload TemplateRenderer            TemplateRenderer.render(id, user_data)
  └─ hot-reload IntentRouter                        │
                                           a2ui_transform → SSE → app renders
```

---

## Phase 1 — Backend Foundation (Python) ✅ 65% Done

### Goal
Make templates self-describing: intent triggers and data schema baked into the template file, not hardcoded in Python.

### Todos

**P1-1: Add `intentTriggers` field to template JSON files**
- Add `"intentTriggers": ["transaction", "transactions", ...]` to each existing template
- `intent_router.py` loads triggers from template files at startup instead of hardcoding
- Result: adding a new template automatically registers its intent triggers

Files: `agent/templates/*.json`, `agent/intent_router.py`

**P1-2: Add `dataSchema` field to template JSON files**
- Add explicit `dataSchema` block to each template declaring field names and types
- This makes the implicit `agent/data/*.json` contract explicit and machine-readable
- Format:
```json
"dataSchema": {
  "source": "mock",
  "fields": {
    "title":       { "type": "string" },
    "periodRange": { "type": "string" },
    "countLabel":  { "type": "string" },
    "transactions": {
      "type": "array",
      "itemSchema": {
        "action": { "type": "string" },
        "date":   { "type": "string" },
        "amount": { "type": "string" }
      }
    }
  }
}
```

Files: `agent/templates/*.json`

**P1-3: Create `DataAdapter` abstraction**
- New file `agent/data_adapter.py`
- Interface: `fetch(template_id: str, user_id: str | None) -> dict`
- `MockDataAdapter`: reads from `agent/data/{template_id}.json` (current behavior)
- `ApiDataAdapter`: stub that reads `dataSchema.source` and calls the right API (Phase 4)
- `TemplateRenderer` receives a `DataAdapter` instance at construction

Files: new `agent/data_adapter.py`, updated `agent/template_renderer.py`, `agent/agent.py`

**P1-4: Pass `user_id` through request chain**
- `ChatRequest` already has `session_id` — use it as `user_id` for data fetching
- Pass through intent router → template renderer → data adapter

Files: `agent/agent.py`

---

## Phase 2 — Designer Save API (Python)

### Goal
Allow a designer to POST an LLM response and have the server freeze it as a reusable template.

### Todos

**P2-1: `POST /designer/save-template` endpoint**

Request body:
```json
{
  "name": "Monthly Transactions",
  "templateId": "transaction_history_v2",
  "intentTriggers": ["transaction", "transactions", "trades", "history"],
  "uiDefinition": { "...raw LLM output with literalString values..." },
  "textTemplate": "${title}\n${periodRange}\n${countLabel}",
  "description": "Designer-saved template for monthly transaction history"
}
```

Server logic:
1. Call `transform_to_path_bindings(uiDefinition.components)` → get `(path_bound_components, data_entries)`
2. Build example data dict from `data_entries`: `{entry["key"]: entry["valueString"] for entry in data_entries}`
3. Build template JSON with `intentTriggers`, `dataSchema` (inferred from data entries), `textTemplate`, `uiDefinition` (path-bound)
4. Write `agent/templates/{templateId}.json`
5. Write `agent/data/{templateId}.json` (example data)
6. Call `_template_renderer.reload()` — hot-reload caches
7. Return: `{ templateId, intentTriggers, dataSchema, previewUrl }`

Files: `agent/agent.py` (new route), `agent/template_renderer.py` (add `reload()`)

**P2-2: `GET /designer/templates` — list all templates**

Returns array of `{ templateId, name, description, intentTriggers, version, approvedBy, status }`.

**P2-3: `DELETE /designer/templates/{templateId}` — remove template**

Deletes template + data files, reloads caches.

**P2-4: `GET /designer/templates/{templateId}/preview` — render with example data**

Calls `template_renderer.render(templateId, templateId)` and returns the A2UI ops as JSON (for preview before publishing).

**P2-5: Template status field**
- Add `"status": "draft"` to saved templates
- Only `"approved"` templates are served to financial users via `/chat/stream/template`
- Designer can publish: `POST /designer/templates/{templateId}/publish` → sets status to `approved`

Files: `agent/agent.py`, `agent/template_renderer.py`, `agent/intent_router.py`

---

## Phase 3 — Android "Save as Template" UX

### Goal
Add the designer authoring interaction to the Android app.

### Todos

**P3-1: `AgentMode.DESIGNER` enum value**
- Add `DESIGNER` to the `AgentMode` enum (alongside `REAL`, `TEMPLATE`, `MOCK`)
- When `DESIGNER` mode, the app connects to `/chat/stream` (LLM) for generation
- "Save" button appears in designer mode after a card renders

Files: `app/.../ChatViewModel.kt`, `app/.../RealChatRepository.kt`

**P3-2: "Save as Template" action button in `MessageBubble`**
- Appears as an icon button below the rendered A2UI card (only in DESIGNER mode)
- Tapping opens a bottom sheet dialog

Files: `app/.../components/MessageBubble.kt`

**P3-3: Save Template bottom sheet dialog**
- Fields: Template name (TextField), Intent keywords (chip input)
- "Save" button → calls `DesignerRepository.saveTemplate(uiDefinition, name, triggers)`
- Success toast: "Template saved and live"

Files: new `app/.../components/SaveTemplateDialog.kt`

**P3-4: `DesignerRepository`**
- `saveTemplate(uiDefinition, textTemplate, name, triggers)` → POST to `/designer/save-template`
- Returns `SaveTemplateResult(templateId, previewUrl)`

Files: new `app/.../data/repository/DesignerRepository.kt`

**P3-5: Designer mode toggle**
- Long-press on the send button (or debug settings) to switch between USER and DESIGNER mode
- Shown only in debug builds initially

Files: `app/.../presentation/ChatViewModel.kt`

---

## Phase 4 — Real Data Adapter (Future)

### Goal
Replace static mock JSON files with real financial API calls per user.

### Todos (scoped separately, requires API access)

**P4-1: `ApiDataAdapter` implementation** — reads `dataSchema.source`, dispatches to the right API connector  
**P4-2: Financial API connectors** — FDX, Plaid, or internal APIs for transactions, balances, brokerage  
**P4-3: User context resolver** — maps `session_id → user credentials / API tokens`  
**P4-4: Response mapper** — maps `apiPath` field specs from `dataSchema` to flat dict  
**P4-5: TTL caching** — Redis or in-memory cache (avoid API calls on every render)

---

## Implementation Sequence

```
Phase 1 (Python backend foundation)
  P1-1 intentTriggers ──┐
  P1-2 dataSchema     ──┤ parallel
  P1-3 DataAdapter    ──┤
  P1-4 user_id wiring ──┘
        │
        ▼ (Phase 1 complete)
Phase 2 (Designer Save API) — sequential, depends on P1-3
  P2-1 save-template endpoint
  P2-2 list templates
  P2-3 delete template
  P2-4 preview endpoint
  P2-5 status/publish gate
        │
        ▼ (Phase 2 API complete)
Phase 3 Android UX
  P3-1 AgentMode.DESIGNER
  P3-2 Save button (MessageBubble)
  P3-3 SaveTemplateDialog bottom sheet
  P3-4 DesignerRepository
  P3-5 Mode toggle
```

---

## Files Changed (Full Scope)

| # | File | Change | Phase |
|---|---|---|---|
| 1 | `agent/templates/transaction_history.json` | Add `intentTriggers`, `dataSchema` | P1 |
| 2 | `agent/templates/account_balances.json` | Add `intentTriggers`, `dataSchema` | P1 |
| 3 | `agent/templates/brokerage_activity.json` | Add `intentTriggers`, `dataSchema` | P1 |
| 4 | `agent/intent_router.py` | Load triggers dynamically from template files | P1 |
| 5 | new `agent/data_adapter.py` | `DataAdapter` interface + `MockDataAdapter` | P1 |
| 6 | `agent/template_renderer.py` | Accept `DataAdapter`, add `reload()` | P1 |
| 7 | `agent/agent.py` | Wire `DataAdapter`, add designer routes | P1+P2 |
| 8 | new `app/.../DesignerRepository.kt` | Save template API call | P3 |
| 9 | new `app/.../components/SaveTemplateDialog.kt` | Bottom sheet UI | P3 |
| 10 | `app/.../components/MessageBubble.kt` | Add save button | P3 |
| 11 | `app/.../presentation/ChatViewModel.kt` | Designer mode support | P3 |

---

## Key Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Template storage | Files (JSON on disk) | Already working; git-versionable; migrate to DB if multi-team |
| LLM → Template freeze | Server-side via `transform_to_path_bindings()` | Python already owns this; no logic duplication in apps |
| Intent trigger assignment | Manual keyword entry by designer | Simple; LLM-suggested triggers as Phase 3 enhancement |
| Data schema source of truth | Inferred from LLM output (Phase 1-3), explicit `apiPath` mapping (Phase 4) | Progressive enhancement |
| Legal approval gate | `status` field: `draft → approved`; only approved templates serve users | Lightweight but enforceable |
| Designer mode access | Debug/settings toggle; not in production UI for end-users | Safety: designers are internal users |

---

## DoD Gates Per Phase

Each phase must complete:
1. ✅ Design (Android Designer) — required for Phase 3 UI components
2. ✅ Implementation — Python Expert (Ph1+2), Android Expert (Ph3)
3. ✅ Smoke test — agent starts + /health passes (Python); compileDebugKotlin (Android)
4. ✅ Code review — Code Reviewer on all changed files
5. ✅ Tests — Integration Tester: full suite (Python unit tests + Android compile + API e2e)
6. ✅ Release notes — Documentation Writer appends to release/RELEASE_NOTES.md
7. ✅ Push — git push; confirm origin/main up to date
8. ✅ Retro — mini-retro appended to release notes

---

## What's Already Done (Pre-Conditions ✅)

- Bug 1: `dataModelJson` dropped in sync mode — **fixed** (commit: ChatViewModel.kt)
- Bug 2: textTemplate Mustache loop — **fixed** (commit: transaction_history.json)
- Bug 3: DataModel key mismatch — **fixed** (commit bdb5ecd: path bindings + scalars pipeline)
- All 3 templates produce correct DataModel with original data file key names
- All 58 agent tests pass

---

## Out of Scope

- Real financial API integration (Phase 4 — requires separate API access decisions)
- Multi-user template sharing / designer team collaboration
- Template analytics / usage tracking
- A/B testing between templates
- iOS app — designer workflow not in scope for this release
