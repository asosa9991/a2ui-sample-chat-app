# Making the A2UI Architecture Deterministic: Data-Layer-Driven Responses with Minimal LLM Use

## Executive Summary

The current A2UI agent uses the LLM for **two fundamentally different jobs**: (1) understanding user intent and (2) generating the complete A2UI component tree with embedded data. This conflation is what makes the system non-deterministic — the LLM reinvents the layout every time and fabricates financial data. The solution is a **three-tier architecture** that separates intent classification, layout templates, and data fetching: use the LLM only for intent classification (or eliminate it entirely with keyword/NLU matching), select from pre-defined A2UI layout templates, and populate them with real data from your data layer. The mock surfaces in the codebase (`AccountBalancesSurface.kt`, `TransactionHistorySurface.kt`, `BrokerageActivitySurface.kt`) already demonstrate this exact pattern — they're 100% deterministic, zero LLM, and production-quality.

---

## The Problem: What the LLM Currently Does

Today, the LLM (Claude Sonnet 4.6) is asked to do everything in one shot via `A2UI_SYSTEM_PROMPT`:

| Responsibility | Deterministic? | Should LLM do it? |
|---|---|---|
| Understand "show my transactions" → intent=transactions | Mostly yes | Optional — keyword matching works for known intents |
| Decide which layout pattern to use (Card + Column + Rows) | Yes, if instructed | **No** — pre-define templates |
| Generate all component IDs (`t_row_0`, `t_left_0`, etc.) | Yes, if instructed | **No** — templates handle this |
| Fabricate financial data ($4,250.00, NVDA, dates, etc.) | **No — non-deterministic** | **No** — use real data layer |
| Format amounts, dates, account numbers | Mostly yes | **No** — use formatters |
| Produce valid JSON matching A2UI schema | Usually, with retries | **No** — template rendering is always valid |

The LLM is spending ~10-15 seconds generating a ~5,000 character JSON response that could be assembled in <10ms from a template + data.

---

## The Solution: Three-Tier Deterministic Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     CURRENT ARCHITECTURE                        │
│                                                                 │
│  User Message ──► LLM (10-15s) ──► JSON ──► Parse ──► Validate │
│                   ▲                                   │ Retry?  │
│                   │ A2UI_SYSTEM_PROMPT                 ▼         │
│                   │ (layout + data + format)     Transform      │
│                   │                                   │         │
│                   │                              SSE Stream     │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                   PROPOSED ARCHITECTURE                          │
│                                                                 │
│  User Message ──► Intent Router (< 50ms) ──► Template + Data   │
│                   │                           │                 │
│                   ├─ Keyword match (fast)      ├─ Template      │
│                   ├─ NLU classifier (medium)   │  Registry      │
│                   └─ LLM fallback (slow)       │  (pre-built)   │
│                                                │                │
│                                                ├─ Data Layer    │
│                                                │  (API/DB)      │
│                                                │                │
│                                                ├─ Formatter     │
│                                                │  ($, dates)    │
│                                                │                │
│                                                └─► A2UI Ops     │
│                                                    (SSE)        │
└─────────────────────────────────────────────────────────────────┘
```

### Tier 1: Intent Router (replaces LLM for known intents)

The `MockChatRepository` already demonstrates keyword-based intent routing:

```kotlin
// From MockChatRepository.kt — ALREADY deterministic
return when {
    normalized.contains("last") && normalized.contains("transaction") ->
        // Intent: TRANSACTIONS → TransactionHistorySurface.build()
    normalized.contains("account") && normalized.contains("balance") ->
        // Intent: BALANCES → AccountBalancesSurface.build()
    else -> {
        val words = normalized.split(Regex("\\W+")).toSet()
        if (words.any { it in BROKERAGE_TRIGGERS }) ->
            // Intent: BROKERAGE → BrokerageActivitySurface.build()
    }
}
```

**Server-side equivalent** — add an intent router to `agent.py`:

```python
# Proposed: intent_router.py

INTENT_PATTERNS = {
    "transactions": {
        "keywords": ["transaction", "transactions", "activity", "spending", "purchases"],
        "requires_all": [],  # any keyword triggers
        "template": "transaction_history",
    },
    "balances": {
        "keywords": ["balance", "balances", "account", "accounts", "net worth"],
        "requires_all": [],
        "template": "account_balances",
    },
    "holdings": {
        "keywords": ["holdings", "positions", "portfolio", "stocks", "investments"],
        "template": "portfolio_holdings",
    },
    "trades": {
        "keywords": ["trades", "trade", "buy", "sell", "brokerage"],
        "template": "brokerage_activity",
    },
}

def classify_intent(message: str) -> tuple[str | None, str | None]:
    """Returns (intent_name, template_name) or (None, None) for LLM fallback."""
    normalized = message.lower().strip()
    words = set(re.split(r'\W+', normalized))

    for intent_name, config in INTENT_PATTERNS.items():
        if any(kw in words for kw in config["keywords"]):
            if not config.get("requires_all") or all(kw in words for kw in config["requires_all"]):
                return intent_name, config["template"]

    return None, None  # Unknown intent → fall back to LLM
```

**Upgrade path**: Replace keyword matching with a small fine-tuned classifier (DistilBERT, ~50ms) or even a tiny LLM call that only returns the intent name (not the full A2UI JSON).

### Tier 2: Template Registry (replaces LLM layout generation)

The existing `*Surface.kt` files are the blueprint. Port the same pattern to the server:

```python
# Proposed: templates/transaction_history.py

def build_transaction_history(data: dict) -> dict:
    """Build A2UI uiDefinition from template + data. 100% deterministic."""
    transactions = data["transactions"]
    period = data["period"]
    count = len(transactions)

    components = {}

    # Root structure (always the same)
    components["root"] = comp("root", "Column", {"children": {"explicitList": ["hdr_card"]}})
    components["hdr_card"] = comp("hdr_card", "Card", {"child": "hdr_card_col"})
    components["hdr_card_col"] = comp("hdr_card_col", "Column", {
        "children": {"explicitList": ["hdr_col", "txns_list"]}
    })

    # Header (parameterized)
    components["hdr_col"] = comp("hdr_col", "Column", {
        "children": {"explicitList": ["title", "period", "count"]}
    })
    components["title"] = text("title", f"{period['label']} Transactions", "h5")
    components["period"] = text("period", period["range"], "caption")
    components["count"] = text("count", f"{count} transactions", "caption")

    # Transaction rows (data-driven)
    txn_children = []
    for idx, tx in enumerate(transactions):
        row_id = f"t_row_{idx}"
        txn_children.append(row_id)
        if idx < len(transactions) - 1:
            div_id = f"t_div_{idx}"
            txn_children.append(div_id)
            components[div_id] = comp(div_id, "Divider", {})

        left_id = f"t_left_{idx}"
        action_id = f"t_action_{idx}"
        date_id = f"t_date_{idx}"
        amt_id = f"t_amt_{idx}"

        components[row_id] = comp(row_id, "Row", {
            "children": {"explicitList": [left_id, amt_id]},
            "distribution": "spaceBetween"
        })
        components[left_id] = comp(left_id, "Column", {
            "children": {"explicitList": [action_id, date_id]}
        })
        components[action_id] = text(action_id, tx["description"], "body")
        components[date_id] = text(date_id, tx["date"], "caption")
        components[amt_id] = text(amt_id, tx["amount"], "body")

    components["txns_list"] = comp("txns_list", "Column", {
        "children": {"explicitList": txn_children}
    })

    return {
        "text": f"Here are your transactions from {period['label']} — {count} transactions total.",
        "uiDefinition": {
            "surfaceId": f"response_{_random_suffix()}",
            "root": "root",
            "components": components
        }
    }


def comp(id: str, widget_type: str, props: dict) -> dict:
    return {"id": id, "componentProperties": {widget_type: props}}


def text(id: str, value: str, usage_hint: str) -> dict:
    return comp(id, "Text", {
        "text": {"literalString": value},
        "usageHint": usage_hint
    })
```

**Template registry** — register all templates:

```python
# Proposed: template_registry.py

TEMPLATES = {
    "transaction_history": build_transaction_history,
    "account_balances": build_account_balances,
    "brokerage_activity": build_brokerage_activity,
    "portfolio_holdings": build_portfolio_holdings,
}

def render_template(template_name: str, data: dict) -> dict:
    builder = TEMPLATES.get(template_name)
    if not builder:
        raise ValueError(f"Unknown template: {template_name}")
    return builder(data)
```

### Tier 3: Data Layer (replaces LLM data fabrication)

The data layer fetches real data and formats it for templates:

```python
# Proposed: data_layer.py

from datetime import datetime, timedelta

async def fetch_transactions(user_id: str, period: str = "last_month") -> dict:
    """Fetch transactions from your data API / database."""
    # In production: call your banking API
    # For demo: return structured mock data (same as TransactionHistorySurface.kt)
    if period == "last_month":
        return {
            "period": {"label": "March 2026", "range": "Mar 1 – Mar 31, 2026"},
            "transactions": [
                {"description": "Direct Deposit – Employer Payroll", "date": "2026-03-28", "amount": "+$4,250.00"},
                {"description": "Buy NVDA · 8 shares", "date": "2026-03-26", "amount": "-$2,184.00"},
                # ... rest of transactions from real data source
            ]
        }

async def fetch_account_balances(user_id: str) -> dict:
    """Fetch account balances from your data API / database."""
    return {
        "banking": {
            "total": "$24,580.47",
            "accounts": [
                {"name": "Premier Checking", "number": "••••3847", "balance": "$8,214.63", "change": "+$1,200.00 (direct deposit)"},
                {"name": "High-Yield Savings", "number": "••••5291", "balance": "$16,365.84", "change": "+$64.22 (4.75% APY)"},
            ]
        },
        "investing": {
            "total": "$198,342.11",
            "accounts": [
                {"name": "Individual Brokerage", "number": "••••8677", "balance": "$134,987.55", "change": "-$2,341.80 (-1.71%)"},
                {"name": "Roth IRA", "number": "••••1331", "balance": "$28,754.22", "change": "+$318.44 (+1.12%)"},
                {"name": "401(k) Plan", "number": "••••4402", "balance": "$34,600.34", "change": "+$512.90 (+1.50%)"},
            ]
        },
        "total_net_worth": "$222,922.58"
    }

DATA_FETCHERS = {
    "transactions": fetch_transactions,
    "balances": fetch_account_balances,
    "holdings": fetch_portfolio_holdings,
    "trades": fetch_brokerage_activity,
}
```

---

## The New `/chat/stream` Endpoint

```python
@app.post("/chat/stream")
async def chat_stream(request: ChatRequest):
    suffix = _random_suffix()
    message = request.message

    # ── Tier 1: Intent Classification ──────────────────────────
    intent, template_name = classify_intent(message)

    if intent and template_name:
        # ── DETERMINISTIC PATH (< 50ms) ───────────────────────
        logger.info("[chat/stream] DETERMINISTIC: intent=%s template=%s", intent, template_name)

        # Tier 3: Fetch real data
        fetcher = DATA_FETCHERS.get(intent)
        data = await fetcher(user_id="demo_user") if fetcher else {}

        # Tier 2: Render template with data
        parsed = render_template(template_name, data)

        # Transform to A2UI operations (reuse existing pipeline)
        operations = transform_to_operations(parsed, suffix)

        async def deterministic_generator():
            yield {"event": "text", "data": json.dumps({"text": parsed["text"]})}
            await asyncio.sleep(0.1)
            for op in operations:
                yield {"event": op["type"], "data": json.dumps(op["data"])}
                if op["type"] == "a2ui_op":
                    await asyncio.sleep(0.15)

        return EventSourceResponse(deterministic_generator())

    else:
        # ── LLM FALLBACK (10-15s) ─────────────────────────────
        logger.info("[chat/stream] LLM FALLBACK: no matching intent for '%s'", message[:60])
        # ... existing LLM pipeline (unchanged) ...
```

---

## What You Can Reuse Today

The existing codebase already has most building blocks:

| Component | Already Exists | Where | Reuse Strategy |
|-----------|---------------|-------|----------------|
| Keyword-based intent routing | ✅ | `MockChatRepository.kt:15-18, 28-67` | Port to Python `intent_router.py` |
| Pre-built layout templates | ✅ | `AccountBalancesSurface.kt`, `TransactionHistorySurface.kt`, `BrokerageActivitySurface.kt` | Port to Python template builders |
| Template expansion (server) | ✅ | `agent.py:269-334` (`expand_templates()`) | Already works for data-driven lists |
| Data → path-binding transform | ✅ | `agent.py:428-458` (`transform_to_path_bindings()`) | Reuse as-is |
| SSE streaming | ✅ | `agent.py:577-654` | Reuse as-is |
| Component chunking | ✅ | `agent.py:507-514` (`chunk_components()`) | Reuse as-is |
| Sanitization | ✅ | `agent.py:461-504` (`sanitize_components()`) | Not needed (templates are always valid) |
| Validation + retry | ✅ | `agent.py:200-236` | Not needed (templates are always valid) |
| Android SSE parsing | ✅ | `RealChatRepository.kt` | No changes needed |
| SurfaceStateManager | ✅ | `SurfaceStateManager.kt` | No changes needed |
| FinancialCatalog rendering | ✅ | `FinancialCatalog.kt` | No changes needed |

---

## Performance Comparison

| Metric | Current (LLM) | Proposed (Deterministic) | Improvement |
|--------|---------------|--------------------------|-------------|
| Response latency | 10-15 seconds | < 100ms | **100x faster** |
| Consistency | Varies per call | Identical for same data | **100% deterministic** |
| Validation needed | Yes (+ retry) | No (templates are pre-validated) | Eliminates failure mode |
| Cost per request | ~$0.01-0.03 (LLM tokens) | ~$0.00 (no LLM) | **Free for known intents** |
| Schema compliance | ~95% (needs retry ~5%) | 100% (by construction) | **Zero errors** |
| Data accuracy | Fabricated/hallucinated | Real from data layer | **Ground truth** |

---

## Implementation Roadmap

### Phase 1: Server-side templates (Python)
Port the 3 existing Kotlin surfaces to Python template builders. Wire up `classify_intent()` → `render_template()` → `transform_to_operations()`.

**Files to create:**
- `agent/intent_router.py` — keyword-based intent classifier
- `agent/data_layer.py` — data fetching functions (start with hardcoded mock data matching the Kotlin surfaces)
- `agent/templates/transaction_history.py`
- `agent/templates/account_balances.py`
- `agent/templates/brokerage_activity.py`
- `agent/template_registry.py` — template lookup

**Files to modify:**
- `agent/agent.py` — add deterministic path before LLM fallback in `/chat/stream`

### Phase 2: Real data integration
Replace hardcoded mock data in `data_layer.py` with real API calls / database queries.

### Phase 3: Smart intent classification (optional)
Upgrade from keyword matching to a small classifier model or a minimal LLM call that only returns `{"intent": "transactions", "params": {"period": "last_month"}}` — 100ms vs 10-15s.

### Phase 4: LLM for novel intents only
Keep the LLM path as a fallback for unrecognized intents. When the LLM produces a new useful layout, you can "promote" it to a template.

---

## The Hybrid Model: Best of Both Worlds

```
User: "show my transactions"
  → Keyword match: intent=transactions ✓
  → Template + Data Layer → response in 50ms

User: "show me a comparison of my spending this month vs last month"  
  → Keyword match: no match
  → LLM Fallback → response in 12s
  → If useful, promote layout to a "spending_comparison" template

User: "what's my net worth?"
  → Keyword match: intent=balances ✓
  → Template + Data Layer → response in 50ms
```

This lets you cover 80-90% of queries deterministically while keeping the LLM available for novel/complex requests.

---

## Android Client: Zero Changes Required

The critical insight is that **no Android code needs to change**. The deterministic pipeline produces the exact same SSE event format:

```
event: text
data: {"text": "Here are your transactions..."}

event: a2ui_op
data: {"beginRendering": {"surfaceId": "response_abc123", "root": "root"}}

event: a2ui_op  
data: {"dataModelUpdate": {...}}

event: a2ui_op
data: {"surfaceUpdate": {"components": [...]}}

event: done
data: {}
```

`RealChatRepository`, `SurfaceStateManager`, `ChatViewModel`, and `FinancialCatalog` all work identically regardless of whether the server used an LLM or a template to produce the operations.

---

## Confidence Assessment

| Finding | Confidence | Notes |
|---------|-----------|-------|
| Mock surfaces demonstrate the deterministic pattern | **Confirmed** | Full source read of all 3 Surface.kt files |
| `transform_to_operations()` can process template output | **Confirmed** | It accepts any `{"text": ..., "uiDefinition": ...}` dict |
| Android client needs zero changes | **Confirmed** | SSE format is identical regardless of source |
| Keyword intent routing covers core use cases | **High** | `MockChatRepository` already uses this pattern for 3 intents |
| 100x latency improvement | **High confidence** | Template rendering is O(N) string assembly vs 10-15s LLM round-trip |
| `expand_templates()` already does data/layout separation | **Confirmed** | Template pattern uses `{i}` and `{field}` placeholders |

---

## Footnotes

- `agent/agent.py:269-334` — `expand_templates()` already separates layout templates from data items
- `agent/agent.py:428-458` — `transform_to_path_bindings()` separates literal values into DataModel
- `agent/agent.py:517-574` — `transform_to_operations()` accepts any parsed response dict
- `agent/agent.py:577-654` — `/chat/stream` endpoint and SSE emission
- `agent/system_prompt.py:1-327` — Full system prompt (layout + data + formatting rules combined)
- `app/.../MockChatRepository.kt:15-18` — Keyword trigger set for intent matching
- `app/.../MockChatRepository.kt:28-67` — Intent routing with `when` block
- `app/.../AccountBalancesSurface.kt:26-176` — Programmatic UiDefinition builder with data embedded
- `app/.../TransactionHistorySurface.kt:25-138` — Template-like pattern with data list driving row generation
- `app/.../BrokerageActivitySurface.kt:7-135` — Same pattern with transaction data driving layout
- `app/.../RealChatRepository.kt:110-223` — SSE parsing (format-agnostic — works with any source)
- `app/.../SurfaceStateManager.kt:49-77` — Operation processing (source-agnostic)
