# Implementation Plan: Deterministic Template-Based A2UI Agent

## Problem Statement

The current Python agent (`agent/agent.py`) uses an LLM (via GitHub Copilot SDK) to generate A2UI protocol responses. This introduces non-determinism, latency, API-key dependencies, and legal review complexity. We need a **new, separate agent** that serves the **same SSE protocol** using **pre-approved JSON templates + mock data**, making responses 100% deterministic, instantaneous, offline-capable, and legal-reviewable.

The new agent lives in `agent-templates/` and is a drop-in replacement — the Android client requires **zero changes** because both agents serve identical SSE event formats on port 8000.

## Success Criteria

- [ ] All 3 template types (transaction_history, account_balances, brokerage_activity) render identically to the existing Kotlin mock surfaces
- [ ] SSE event stream matches the format consumed by `RealChatRepository.kt` (`text` → `a2ui_op` → `done`)
- [ ] `SurfaceStateManager.kt` successfully processes all operations (beginRendering, dataModelUpdate, surfaceUpdate)
- [ ] No LLM, no API keys, no external network calls — fully offline
- [ ] Template JSON files are human-readable and legal-reviewable
- [ ] Response latency < 50ms (no LLM round-trip)
- [ ] Unrecognized messages return plain text (graceful fallback)
- [ ] Existing `run_ui_tests.sh` passes against the template agent

## Scope Boundaries

### In Scope
- New `agent-templates/` directory with all source files
- Extract reusable transform pipeline from `agent/agent.py` into `a2ui_transform.py`
- 3 template JSON files ported from Kotlin mock surfaces
- 3 mock data JSON files with matching data
- Keyword-based intent router (port of `MockChatRepository` logic)
- Template renderer with `${placeholder}` substitution and `itemTemplate` expansion
- FastAPI server with `/chat/stream`, `/event`, `/health` endpoints
- README with setup, usage, and template authoring guide

### Out of Scope
- Any changes to the Android client code
- JSONL endpoint (`/chat/stream/jsonl`) — can be added later; `/chat/stream` is sufficient
- LLM fallback for unmatched intents (plain text only)
- Template hot-reloading (restart required for template changes)
- Authentication or rate limiting
- Production deployment configuration (Docker, etc.)
- A2UI schema validation (the existing agent's `validate_ui_definition` uses the a2ui-agent SDK which we're not including)

## Assumptions

1. The Android client uses the `/chat/stream` SSE endpoint (controlled by `USE_JSONL_ENDPOINT` BuildConfig flag — we assume the default SSE mode)
2. The transform pipeline functions (`expand_templates`, `transform_to_path_bindings`, `sanitize_components`, `chunk_components`, `transform_to_operations`) have no external dependencies beyond Python stdlib
3. Template JSON files will be authored once and updated infrequently (no hot-reload needed)
4. The 3 existing Kotlin mock surfaces define the complete set of initial templates
5. Port 8000 is available and not shared with the existing LLM agent simultaneously

## Open Questions

1. **Should we also implement `/chat/stream/jsonl`?** — The Android client supports both modes via BuildConfig. The plan covers `/chat/stream` only. Adding JSONL is a ~30-min extension.
2. **Should the `/event` endpoint return SSE streams for button taps?** — The current agent does. The template agent can return acknowledgment-only initially, with SSE responses as a follow-up task.
3. **Template versioning strategy** — The plan includes version fields in templates but does not implement version negotiation. Is version pinning sufficient?

---

## Todos

### Task 1: Extract Transform Pipeline → `a2ui_transform.py`

**Goal:** Extract the 7 pure functions from `agent/agent.py` into a standalone module with zero third-party dependencies.

**Functions to extract (with source line references):**

| Function | Lines in agent.py | Dependencies |
|----------|-------------------|-------------|
| `_random_suffix(n=6)` | 68-69 | `random`, `string` |
| `_replace_index(s, index)` | 242-244 | (none) |
| `deep_replace(obj, index, item_data)` | 247-266 | `re` |
| `expand_templates(ui_def)` | 269-334 | `logging` |
| `transform_to_path_bindings(components)` | 428-458 | (none) |
| `sanitize_components(components)` | 461-504 | `logging` |
| `chunk_components(components, chunk_size=15)` | 507-514 | (none) |
| `transform_to_operations(parsed, suffix, chunk_size=15)` | 517-574 | `logging` (calls all above) |

**Constant to extract:**
- `MAX_TEMPLATE_ITEMS = 200` (line 197)

**File:** `agent-templates/a2ui_transform.py`

**Implementation details:**
```python
# agent-templates/a2ui_transform.py
"""
A2UI transform pipeline — extracted from agent/agent.py.
Converts parsed template+data responses into SSE-ready operations.
Pure functions, stdlib only.
"""
import json
import logging
import random
import re
import string

logger = logging.getLogger("a2ui_transform")
MAX_TEMPLATE_ITEMS = 200

def _random_suffix(n: int = 6) -> str: ...
def _replace_index(s: str, index: int) -> str: ...
def deep_replace(obj, index: int, item_data: dict): ...
def expand_templates(ui_def: dict) -> dict: ...
def transform_to_path_bindings(components: dict) -> tuple[dict, list[dict]]: ...
def sanitize_components(components: dict) -> dict: ...
def chunk_components(components: dict, chunk_size: int = 15) -> list[list[dict]]: ...
def transform_to_operations(parsed_response: dict, surface_suffix: str, chunk_size: int = 15) -> list[dict]: ...
```

**Verification:**
- Unit test: Feed a known component dict through `transform_to_operations()` and assert the output matches expected operations
- Smoke test: Compare output of extracted module vs. inline functions in agent.py for identical input

**Effort:** ~1 hour

---

### Task 2: Create Template JSON Files (3 templates)

**Goal:** Port each Kotlin mock surface into a JSON template file with `${placeholder}` substitutions for data-driven values and `itemTemplate` for repeating rows.

#### 2a. `templates/transaction_history.json`

**Source:** `TransactionHistorySurface.kt` (surface ID: `response_run1`)

**Template structure:**
- Static components: `root`, `hdr_card`, `hdr_card_col`, `hdr_col`, `title`, `period`, `count`, `txns_list`
- Placeholders in Text `literalString` fields: `${title}`, `${periodRange}`, `${countLabel}`
- `itemTemplate` for transaction rows: `t_row_{i}`, `t_left_{i}`, `t_action_{i}`, `t_date_{i}`, `t_amt_{i}`, `t_div_{i}`
- Item fields: `{action}`, `{date}`, `{amount}`

**Key component definitions (JSON format):**
```json
{
  "templateId": "transaction_history",
  "version": "1.0.0",
  "approvedBy": "Legal Team",
  "approvedDate": "2026-03-15",
  "description": "Transaction history list with header and repeating transaction rows",
  "textTemplate": "Here are your transactions from ${periodLabel} — ${count} transactions total.",
  "uiDefinition": {
    "root": "root",
    "components": {
      "root": {
        "id": "root",
        "componentProperties": {
          "Column": {
            "children": { "explicitList": ["hdr_card"] }
          }
        }
      },
      "hdr_card": {
        "id": "hdr_card",
        "componentProperties": {
          "Card": { "child": "hdr_card_col" }
        }
      },
      "hdr_card_col": {
        "id": "hdr_card_col",
        "componentProperties": {
          "Column": {
            "children": { "explicitList": ["hdr_col", "txns_list"] }
          }
        }
      },
      "hdr_col": {
        "id": "hdr_col",
        "componentProperties": {
          "Column": {
            "children": { "explicitList": ["title", "period", "count"] }
          }
        }
      },
      "title": {
        "id": "title",
        "componentProperties": {
          "Text": {
            "text": { "literalString": "${title}" },
            "usageHint": "h5"
          }
        }
      },
      "period": {
        "id": "period",
        "componentProperties": {
          "Text": {
            "text": { "literalString": "${periodRange}" },
            "usageHint": "caption"
          }
        }
      },
      "count": {
        "id": "count",
        "componentProperties": {
          "Text": {
            "text": { "literalString": "${countLabel}" },
            "usageHint": "caption"
          }
        }
      },
      "txns_list": {
        "id": "txns_list",
        "componentProperties": {
          "Column": {
            "children": { "explicitList": [] }
          }
        }
      }
    },
    "itemTemplate": {
      "itemListId": "txns_list",
      "rootId": "t_row_{i}",
      "dividerId": "t_div_{i}",
      "components": {
        "t_row_{i}": {
          "id": "t_row_{i}",
          "componentProperties": {
            "Row": {
              "children": { "explicitList": ["t_left_{i}", "t_amt_{i}"] },
              "distribution": "spaceBetween"
            }
          }
        },
        "t_left_{i}": {
          "id": "t_left_{i}",
          "componentProperties": {
            "Column": {
              "children": { "explicitList": ["t_action_{i}", "t_date_{i}"] }
            }
          }
        },
        "t_action_{i}": {
          "id": "t_action_{i}",
          "componentProperties": {
            "Text": {
              "text": { "literalString": "{action}" },
              "usageHint": "body"
            }
          }
        },
        "t_date_{i}": {
          "id": "t_date_{i}",
          "componentProperties": {
            "Text": {
              "text": { "literalString": "{date}" },
              "usageHint": "caption"
            }
          }
        },
        "t_amt_{i}": {
          "id": "t_amt_{i}",
          "componentProperties": {
            "Text": {
              "text": { "literalString": "{amount}" },
              "usageHint": "body"
            }
          }
        }
      }
    }
  }
}
```

**Critical:** The `itemTemplate.items` field is NOT in the template — it comes from the data file at render time. The renderer must inject `data["transactions"]` as `ui_def["items"]` before calling `expand_templates()`.

#### 2b. `templates/account_balances.json`

**Source:** `AccountBalancesSurface.kt` (surface ID: `response_run2`)

**Template structure — fully static (no itemTemplate):**
All components are defined with `${placeholder}` values:
- `bank_lbl` → `${bankLabel}`, `bank_tot` → `${bankTotal}`
- `chk_name` → `${chkName}`, `chk_num` → `${chkNum}`, `chk_bal` → `${chkBal}`, `chk_chg` → `${chkChg}`
- `sav_name` → `${savName}`, `sav_num` → `${savNum}`, `sav_bal` → `${savBal}`, `sav_chg` → `${savChg}`
- `inv_lbl` → `${invLabel}`, `inv_tot` → `${invTotal}`
- `brok_name` → `${brokName}`, `brok_num` → `${brokNum}`, `brok_bal` → `${brokBal}`, `brok_chg` → `${brokChg}`
- `roth_name` → `${rothName}`, `roth_num` → `${rothNum}`, `roth_bal` → `${rothBal}`, `roth_chg` → `${rothChg}`
- `k401_name` → `${k401Name}`, `k401_num` → `${k401Num}`, `k401_bal` → `${k401Bal}`, `k401_chg` → `${k401Chg}`
- `total_lbl` → `${totalLabel}`, `total_val` → `${totalValue}`

**Component hierarchy (all static, all 30+ components defined inline):**
```
root (Column) → [bank_card, invest_card, total_card]
  bank_card (Card) → bank_col (Column) → [bank_hdr, bdiv1, chk_row, sav_row]
    bank_hdr (Row, spaceBetween) → [bank_lbl, bank_tot]
    chk_row (Row, spaceBetween) → [chk_left (Column → [chk_name, chk_num]), chk_right (Column → [chk_bal, chk_chg])]
    sav_row (Row, spaceBetween) → [sav_left (Column → [sav_name, sav_num]), sav_right (Column → [sav_bal, sav_chg])]
  invest_card (Card) → invest_col (Column) → [inv_hdr, idiv1, brok_row, roth_row, k401_row]
    inv_hdr (Row, spaceBetween) → [inv_lbl, inv_tot]
    brok_row, roth_row, k401_row — same pattern as chk_row/sav_row
  total_card (Card) → total_row (Row, spaceBetween) → [total_lbl, total_val]
```

**Note:** This template has ~35 components with ~20 placeholder values. No `itemTemplate` needed — the account list is fixed-length and legal-approved.

#### 2c. `templates/brokerage_activity.json`

**Source:** `BrokerageActivitySurface.kt` (surface ID: `brokerage_activity`)

**Template structure:**
- Static components: `root`, `acct_card`, `card_col`, `header_row`, `acct_name`, `acct_type`, `balance_amount`, `balance_change`, `divider_main`, `tx_section_header`, `txns_list`
- Placeholders: `${acctName}`, `${acctType}`, `${balanceAmount}`, `${balanceChange}`, `${sectionHeader}`
- `itemTemplate` for transaction rows: `{id}_row`, `{id}_col`, `{id}_desc`, `{id}_sub`, `{id}_date`, `{id}_amount`, `{id}_div`
- Item fields: `{id}`, `{description}`, `{subtitle}`, `{date}`, `{amount}`

**Important difference from transaction_history:** BrokerageActivitySurface uses named IDs (`tx1`, `tx2`, etc.) rather than numeric indices. The `itemTemplate` must use `{i}` for index-based expansion, so we remap to `tx_{i}_row` pattern in the template and use numeric indices.

**Alternative approach (simpler):** Since there are only 5 trades, make this template fully static like account_balances — define all 5 transaction rows inline with `${tx1Desc}`, `${tx1Sub}`, etc. placeholders. This avoids the id-vs-index mismatch.

**Recommendation:** Use `itemTemplate` with `{i}` indexing for transaction_history (14 items, clearly a list). Use fully static templates for account_balances (fixed structure) and brokerage_activity (only 5 items, simpler as static). This keeps templates simpler and more readable for legal review.

**Decision: Use itemTemplate for brokerage_activity too** — to demonstrate the pattern works for both templates and maintain consistency. Use index-based IDs: `tx_row_{i}`, `tx_col_{i}`, etc.

**Effort:** ~3 hours (most time spent on accurate component-by-component porting)

---

### Task 3: Create Mock Data JSON Files (3 data files)

**Goal:** Extract exact mock data from Kotlin surfaces into standalone JSON files.

#### 3a. `data/transaction_history.json`

```json
{
  "title": "March 2026 Transactions",
  "periodLabel": "March 2026",
  "periodRange": "Mar 1 – Mar 31, 2026",
  "count": "14",
  "countLabel": "14 transactions",
  "transactions": [
    {"action": "Direct Deposit – Employer Payroll", "date": "2026-03-28", "amount": "+$4,250.00"},
    {"action": "Buy NVDA · 8 shares", "date": "2026-03-26", "amount": "-$2,184.00"},
    {"action": "Rent Payment – Oakwood Apartments", "date": "2026-03-25", "amount": "-$1,850.00"},
    {"action": "Whole Foods Market", "date": "2026-03-22", "amount": "-$134.57"},
    {"action": "Sell AAPL · 5 shares", "date": "2026-03-20", "amount": "+$1,062.50"},
    {"action": "Netflix Subscription", "date": "2026-03-18", "amount": "-$22.99"},
    {"action": "Transfer to Savings", "date": "2026-03-17", "amount": "-$500.00"},
    {"action": "Dividend – MSFT", "date": "2026-03-15", "amount": "+$61.20"},
    {"action": "Shell Gas Station", "date": "2026-03-14", "amount": "-$78.40"},
    {"action": "Buy VOO · 3 shares", "date": "2026-03-11", "amount": "-$1,371.00"},
    {"action": "Amazon Purchase", "date": "2026-03-09", "amount": "-$56.83"},
    {"action": "Spotify Premium", "date": "2026-03-07", "amount": "-$11.99"},
    {"action": "Direct Deposit – Employer Payroll", "date": "2026-03-14", "amount": "+$4,250.00"},
    {"action": "ATM Withdrawal – Chase ••••4421", "date": "2026-03-03", "amount": "-$200.00"}
  ]
}
```

#### 3b. `data/account_balances.json`

```json
{
  "bankLabel": "BANKING",
  "bankTotal": "$24,580.47",
  "chkName": "Premier Checking",
  "chkNum": "••••3847",
  "chkBal": "$8,214.63",
  "chkChg": "+$1,200.00 (direct deposit)",
  "savName": "High-Yield Savings",
  "savNum": "••••5291",
  "savBal": "$16,365.84",
  "savChg": "+$64.22 (4.75% APY)",
  "invLabel": "INVESTING",
  "invTotal": "$198,342.11",
  "brokName": "Individual Brokerage",
  "brokNum": "••••8677",
  "brokBal": "$134,987.55",
  "brokChg": "-$2,341.80 (-1.71%)",
  "rothName": "Roth IRA",
  "rothNum": "••••1331",
  "rothBal": "$28,754.22",
  "rothChg": "+$318.44 (+1.12%)",
  "k401Name": "401(k) Plan",
  "k401Num": "••••4402",
  "k401Bal": "$34,600.34",
  "k401Chg": "+$512.90 (+1.50%)",
  "totalLabel": "TOTAL NET WORTH",
  "totalValue": "$222,922.58",
  "summaryText": "all your accounts with current balances as of today"
}
```

#### 3c. `data/brokerage_activity.json`

```json
{
  "acctName": "Fidelity Brokerage ••••1234",
  "acctType": "INDIVIDUAL",
  "balanceAmount": "$48,291.73",
  "balanceChange": "+$1,203.45  (+2.56%) today",
  "sectionHeader": "Recent Transactions",
  "transactions": [
    {"description": "Buy AAPL", "subtitle": "10 shares @ $187.50", "date": "Mar 25, 2026", "amount": "-$1,875.00"},
    {"description": "Sell TSLA", "subtitle": "5 shares @ $245.30", "date": "Mar 24, 2026", "amount": "+$1,226.50"},
    {"description": "Dividend · VTI", "subtitle": "Q1 2026 dividend", "date": "Mar 22, 2026", "amount": "+$45.20"},
    {"description": "Buy MSFT", "subtitle": "3 shares @ $412.00", "date": "Mar 20, 2026", "amount": "-$1,236.00"},
    {"description": "ACH Deposit", "subtitle": "Transfer from Chase ••4521", "date": "Mar 18, 2026", "amount": "+$5,000.00"}
  ]
}
```

**Verification:** Compare each data value against the Kotlin surface source. Every string must match character-for-character (including special characters like `–`, `·`, `••••`).

**Effort:** ~1 hour

---

### Task 4: Create Intent Router (`intent_router.py`)

**Goal:** Port the keyword-based intent classification from `MockChatRepository.kt` to Python.

**File:** `agent-templates/intent_router.py`

**Source logic (from MockChatRepository.kt lines 27-67):**
```
"last" + "transaction" → transaction_history
"account" + "balance" → account_balances
any word in BROKERAGE_TRIGGERS → brokerage_activity
else → None (plain text fallback)
```

**Implementation:**

```python
"""
Keyword-based intent classification for template routing.
Mirrors MockChatRepository.kt intent matching logic.
"""
import logging
import re
from dataclasses import dataclass
from typing import Optional

logger = logging.getLogger("intent_router")

@dataclass
class IntentMatch:
    """Result of intent classification."""
    template_id: str
    data_id: str
    confidence: str  # "exact" | "keyword"

BROKERAGE_TRIGGERS = {
    "account", "transaction", "transactions", "activity", "portfolio",
    "balance", "brokerage", "trades", "holdings", "stocks"
}

def classify(message: str) -> Optional[IntentMatch]:
    """
    Classify user message into a template intent.
    Returns None if no intent matches (plain text fallback).
    
    Priority order (matches MockChatRepository.kt):
    1. "last" + "transaction" → transaction_history
    2. "account" + "balance" → account_balances
    3. Any word in BROKERAGE_TRIGGERS → brokerage_activity
    4. None → plain text response
    """
    normalized = message.lower().strip()
    words = set(re.split(r'\W+', normalized))
    
    # Priority 1: Transaction history (requires both keywords)
    if "last" in normalized and "transaction" in normalized:
        return IntentMatch(
            template_id="transaction_history",
            data_id="transaction_history",
            confidence="exact"
        )
    
    # Priority 2: Account balances (requires both keywords)
    if "account" in normalized and "balance" in normalized:
        return IntentMatch(
            template_id="account_balances",
            data_id="account_balances",
            confidence="exact"
        )
    
    # Priority 3: Generic brokerage (any trigger word)
    if words & BROKERAGE_TRIGGERS:
        return IntentMatch(
            template_id="brokerage_activity",
            data_id="brokerage_activity",
            confidence="keyword"
        )
    
    # No match
    return None
```

**Key design decisions:**
- Uses `normalized = message.lower()` with `in` for substring matching (matches Kotlin's `contains()`)
- Uses word splitting with `re.split(r'\W+')` for set intersection (matches Kotlin's `split(Regex("\\W+")).toSet()`)
- Priority order exactly mirrors MockChatRepository.kt's `when` block
- Returns a dataclass instead of a tuple for clarity
- `confidence` field distinguishes exact multi-keyword matches from single-keyword matches

**Verification:** Test with these inputs:
- `"Show my last transactions"` → transaction_history
- `"What are my account balances?"` → account_balances
- `"Show brokerage activity"` → brokerage_activity
- `"Tell me about my portfolio"` → brokerage_activity (keyword: portfolio)
- `"Hello world"` → None
- `"What's the weather?"` → None

**Effort:** ~30 minutes

---

### Task 5: Create Template Renderer (`template_renderer.py`)

**Goal:** Load templates and data at startup, render templates by substituting `${placeholder}` values and injecting `items` arrays.

**File:** `agent-templates/template_renderer.py`

**Implementation:**

```python
"""
Template renderer — loads pre-approved A2UI templates and mock data,
renders them by substituting placeholders with data values.
"""
import copy
import json
import logging
import os
from pathlib import Path
from typing import Optional

logger = logging.getLogger("template_renderer")

class TemplateRenderer:
    def __init__(self, templates_dir: str = "templates", data_dir: str = "data"):
        self.templates: dict[str, dict] = {}
        self.data: dict[str, dict] = {}
        self._load_templates(templates_dir)
        self._load_data(data_dir)

    def _load_templates(self, templates_dir: str):
        """Load all .json files from templates directory."""
        path = Path(templates_dir)
        if not path.exists():
            logger.error("Templates directory not found: %s", path.absolute())
            return
        for f in sorted(path.glob("*.json")):
            try:
                with open(f) as fh:
                    template = json.load(fh)
                tid = template.get("templateId", f.stem)
                self.templates[tid] = template
                logger.info("Loaded template: %s (v%s) from %s",
                           tid, template.get("version", "?"), f.name)
            except (json.JSONDecodeError, OSError) as e:
                logger.error("Failed to load template %s: %s", f, e)

    def _load_data(self, data_dir: str):
        """Load all .json files from data directory."""
        path = Path(data_dir)
        if not path.exists():
            logger.error("Data directory not found: %s", path.absolute())
            return
        for f in sorted(path.glob("*.json")):
            try:
                with open(f) as fh:
                    data = json.load(fh)
                did = f.stem
                self.data[did] = data
                logger.info("Loaded data: %s from %s", did, f.name)
            except (json.JSONDecodeError, OSError) as e:
                logger.error("Failed to load data %s: %s", f, e)

    def render(self, template_id: str, data_id: str) -> Optional[dict]:
        """
        Render a template with data.
        
        Returns {"text": "...", "uiDefinition": {...}} compatible with
        transform_to_operations(), or None if template/data not found.
        """
        template = self.templates.get(template_id)
        data = self.data.get(data_id)
        
        if not template:
            logger.warning("Template not found: %s", template_id)
            return None
        if not data:
            logger.warning("Data not found: %s", data_id)
            return None
        
        # Deep copy template to avoid mutating the cached version
        rendered = copy.deepcopy(template)
        
        # 1. Render text template
        text = rendered.get("textTemplate", "")
        text = self._substitute_placeholders(text, data)
        
        # 2. Render UI definition
        ui_def = rendered.get("uiDefinition", {})
        
        # Substitute ${placeholder} values in all literalString fields
        ui_def = self._substitute_ui_placeholders(ui_def, data)
        
        # Inject items array into itemTemplate if present
        if "itemTemplate" in ui_def:
            items_key = self._find_items_key(template_id, data)
            if items_key and items_key in data:
                ui_def["items"] = data[items_key]
                ui_def["itemListId"] = ui_def["itemTemplate"].get("itemListId")
                logger.info("Injected %d items for template %s",
                           len(data[items_key]), template_id)
        
        return {"text": text, "uiDefinition": ui_def}

    def _substitute_placeholders(self, text: str, data: dict) -> str:
        """Replace ${key} placeholders with data values."""
        import re
        def replacer(match):
            key = match.group(1)
            return str(data.get(key, match.group(0)))
        return re.sub(r'\$\{(\w+)\}', replacer, text)

    def _substitute_ui_placeholders(self, obj, data: dict):
        """Recursively substitute ${placeholder} in all string values."""
        if isinstance(obj, str):
            return self._substitute_placeholders(obj, data)
        elif isinstance(obj, dict):
            return {k: self._substitute_ui_placeholders(v, data) for k, v in obj.items()}
        elif isinstance(obj, list):
            return [self._substitute_ui_placeholders(item, data) for item in obj]
        return obj

    def _find_items_key(self, template_id: str, data: dict) -> Optional[str]:
        """Find the array field in data that should be used as items."""
        # Convention: the items key is the first list-valued field in data
        for key, value in data.items():
            if isinstance(value, list):
                return key
        return None

    def get_loaded_templates(self) -> list[str]:
        """Return list of loaded template IDs."""
        return list(self.templates.keys())

    def get_loaded_data(self) -> list[str]:
        """Return list of loaded data IDs."""
        return list(self.data.keys())
```

**Key design decisions:**
1. **Deep copy on render** — Templates are cached at startup; `copy.deepcopy()` ensures rendering doesn't mutate the cache
2. **Two-phase substitution** — `${placeholder}` for data substitution in the renderer (before transform pipeline)
3. **Items injection** — The renderer finds the list-valued field in data (e.g., `transactions`) and injects it as `ui_def["items"]` + `ui_def["itemListId"]` before the transform pipeline expands them
4. **Convention-based items key** — First list-valued field in data is used as items. This is simple and works for all 3 templates.

**Data flow:**
```
template.json (with ${placeholders} and itemTemplate)
  + data.json (with values and items array)
  → renderer.render() 
  → ${placeholders} substituted in all strings
  → items array injected into uiDefinition
  → returned as {"text": "...", "uiDefinition": {...}}
  → fed into transform_to_operations() which calls expand_templates()
```

**Verification:**
- Unit test: render("transaction_history", "transaction_history") → verify text contains "March 2026", verify uiDefinition has items injected
- Unit test: render("account_balances", "account_balances") → verify all placeholder values substituted (no `${...}` remains)
- Unit test: render with unknown template_id → returns None

**Effort:** ~1.5 hours

---

### Task 6: Create FastAPI Template Agent Server (`template_agent.py`)

**Goal:** FastAPI server that serves the same SSE protocol as `agent/agent.py` but uses templates instead of LLM.

**File:** `agent-templates/template_agent.py`

**Implementation outline:**

```python
"""
Deterministic Template-Based A2UI Agent
Serves pre-approved A2UI templates via SSE — no LLM, no API keys.
Drop-in replacement for agent/agent.py on port 8000.
"""
import asyncio
import json
import logging
import os
import time

import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from sse_starlette.sse import EventSourceResponse
from typing import Optional

from a2ui_transform import transform_to_operations, _random_suffix
from intent_router import classify
from template_renderer import TemplateRenderer

# ── Logging ───────────────────────────────────────────────
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(levelname)s %(message)s")
logger = logging.getLogger("template_agent")

# ── Pydantic models (match agent/agent.py) ────────────────
class ChatRequest(BaseModel):
    message: str
    session_id: Optional[str] = None

class UiEventRequest(BaseModel):
    surface_id: str
    event_type: str
    name: Optional[str] = None
    source_component_id: Optional[str] = None
    path: Optional[str] = None
    value: Optional[str] = None
    context: Optional[dict] = None

# ── App setup ─────────────────────────────────────────────
app = FastAPI(title="A2UI Template Agent", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── Load templates and data at startup ────────────────────
renderer = TemplateRenderer(
    templates_dir=os.path.join(os.path.dirname(__file__), "templates"),
    data_dir=os.path.join(os.path.dirname(__file__), "data"),
)

# ── Fallback text responses ──────────────────────────────
FALLBACK_RESPONSES = [
    "I can help you with account balances, transaction history, and brokerage activity. Try asking about one of those!",
    "I'm a template-based agent. Try asking about your last transactions, account balances, or brokerage activity.",
    "I don't have a template for that query. Try: 'Show my last transactions' or 'What are my account balances?'",
]

# ── Endpoints ─────────────────────────────────────────────

@app.get("/health")
async def health():
    return {
        "status": "ok",
        "service": "a2ui-template-agent",
        "templates": renderer.get_loaded_templates(),
        "data": renderer.get_loaded_data(),
    }

@app.post("/chat/stream")
async def chat_stream(request: ChatRequest):
    if not request.message.strip():
        raise HTTPException(status_code=400, detail="Message cannot be empty")

    suffix = _random_suffix()
    t0 = time.time()
    logger.info("[chat/stream] message='%.60s' suffix=%s", request.message, suffix)

    async def event_generator():
        # 1. Classify intent
        intent = classify(request.message)
        
        if intent is None:
            # No template match — plain text response
            import random
            fallback = random.choice(FALLBACK_RESPONSES)
            logger.info("[chat/stream] no intent match, returning fallback text")
            yield {"event": "text", "data": json.dumps({"text": fallback})}
            yield {"event": "done", "data": "{}"}
            return

        logger.info("[chat/stream] intent=%s confidence=%s", intent.template_id, intent.confidence)

        # 2. Render template with data
        rendered = renderer.render(intent.template_id, intent.data_id)
        if rendered is None:
            yield {"event": "text", "data": json.dumps({"text": "Template rendering failed."})}
            yield {"event": "done", "data": "{}"}
            return

        # 3. Transform to A2UI operations
        operations = transform_to_operations(rendered, suffix)
        elapsed = time.time() - t0
        logger.info("[chat/stream] rendered in %.3fs, %d ops", elapsed, len(operations))

        # 4. Stream operations as SSE events
        # Emit text first (same as agent.py line 640)
        yield {"event": "text", "data": json.dumps({"text": rendered["text"]})}
        await asyncio.sleep(0.1)  # Match agent.py: small gap before UI operations

        for op in operations:
            yield {"event": op["type"], "data": json.dumps(op["data"])}
            if op["type"] == "a2ui_op":
                await asyncio.sleep(0.15)  # Match agent.py: 150ms between A2UI ops

    return EventSourceResponse(event_generator())

@app.post("/event")
async def handle_event(request: UiEventRequest):
    """Acknowledge UI events. No LLM processing — just log and respond."""
    logger.info("[event] type=%s surface=%s component=%s",
                request.event_type, request.surface_id, request.source_component_id)
    return {"status": "received", "surface_id": request.surface_id}

# ── Main ──────────────────────────────────────────────────
if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8000))
    logger.info("=" * 60)
    logger.info("  A2UI Template Agent")
    logger.info("  Templates: %s", renderer.get_loaded_templates())
    logger.info("  Data:      %s", renderer.get_loaded_data())
    logger.info("  Port:      %d", port)
    logger.info("=" * 60)
    uvicorn.run(app, host="0.0.0.0", port=port)
```

**Key design decisions:**

1. **SSE event format matches agent.py exactly:**
   - `yield {"event": "text", "data": json.dumps({"text": ...})}` — text event first
   - `yield {"event": "a2ui_op", "data": json.dumps({...})}` — A2UI operations
   - `yield {"event": "done", "data": "{}"}` — stream termination
   - Same sleep delays: 0.1s after text, 0.15s between a2ui_ops

2. **Note on text event duplication:** The current `agent.py` emits a `text` event on line 640, then `transform_to_operations()` also includes a `text` operation in its output (line 527). This means the client receives TWO text events. The template agent should replicate this exact behavior. Looking at `RealChatRepository.kt`, the `text` event handler accumulates text, so double-emission is harmless but should match for consistency. **Decision:** Emit the text event explicitly before the operations loop (matching agent.py), and the operations list from `transform_to_operations()` will also include a text operation — this matches the current behavior exactly.

3. **CORS matches agent.py:** `allow_origins=["*"]`, `allow_methods=["*"]`, `allow_headers=["*"]`

4. **ChatRequest model matches agent.py:** `message: str`, `session_id: Optional[str] = None`

5. **`/event` endpoint simplified:** Returns JSON acknowledgment instead of SSE stream. The full `/event` SSE response can be added as a follow-up task.

6. **Port configurable via `PORT` env var**, defaults to 8000

**Verification:**
- `curl -X POST http://localhost:8000/chat/stream -H "Content-Type: application/json" -d '{"message": "show my last transactions"}'` → SSE stream with text + a2ui_op events
- `curl http://localhost:8000/health` → JSON with loaded templates list
- `curl -X POST http://localhost:8000/chat/stream -d '{"message": "hello"}'` → text-only fallback

**Effort:** ~1.5 hours

---

### Task 7: Create `requirements.txt`

**File:** `agent-templates/requirements.txt`

```
fastapi==0.115.0
uvicorn==0.30.0
sse-starlette>=1.6.0
pydantic>=2.0.0
```

**No LLM libraries.** No `github-copilot-sdk`, `openai`, `azure-ai-inference`, or `a2ui-agent`.

**Effort:** ~5 minutes

---

### Task 8: Create README.md

**File:** `agent-templates/README.md`

**Contents outline:**

```markdown
# A2UI Template Agent

Deterministic, offline A2UI agent serving pre-approved templates with mock data.
Drop-in replacement for the LLM-based agent — same SSE protocol, same port.

## Quick Start
cd agent-templates
pip install -r requirements.txt
python template_agent.py

## How It Works
1. User sends message via Android app
2. Intent router classifies message into template (keyword matching)
3. Template renderer substitutes data into template JSON
4. Transform pipeline converts to A2UI protocol operations
5. SSE stream delivers operations to Android client

## Supported Queries
| Query | Template | Example |
|-------|----------|---------|
| Transaction history | transaction_history | "Show my last transactions" |
| Account balances | account_balances | "What are my account balances?" |
| Brokerage activity | brokerage_activity | "Show brokerage activity" |

## Adding a New Template
1. Create `templates/my_template.json` with component definitions
2. Create `data/my_template.json` with mock data values
3. Add intent keywords in `intent_router.py`
4. Restart the agent

## Template JSON Format
[specification of templateId, version, metadata, textTemplate, uiDefinition, itemTemplate]

## Data JSON Format
[specification of flat key-value pairs + optional arrays for itemTemplate items]

## Configuration
- PORT env var (default: 8000)
- Templates directory: ./templates/
- Data directory: ./data/

## Testing with Android App
1. Start template agent: `python template_agent.py`
2. Build Android app with `USE_REAL_AGENT=true` (default)
3. Run on emulator (connects to 10.0.2.2:8000)
4. Send test messages
```

**Effort:** ~30 minutes

---

### Task 9: End-to-End Testing

**Goal:** Verify the template agent works with the Android app.

**Test plan:**

#### 9a. Unit Tests (Python)

Create `agent-templates/test_template_agent.py`:

| Test | Description | Expected |
|------|-------------|----------|
| `test_intent_transaction_history` | classify("Show my last transactions") | template_id="transaction_history" |
| `test_intent_account_balances` | classify("What are my account balances?") | template_id="account_balances" |
| `test_intent_brokerage` | classify("Show brokerage activity") | template_id="brokerage_activity" |
| `test_intent_portfolio` | classify("How's my portfolio?") | template_id="brokerage_activity" |
| `test_intent_no_match` | classify("What's the weather?") | None |
| `test_render_transaction_history` | renderer.render("transaction_history", "transaction_history") | text contains "March 2026", ui has components |
| `test_render_no_leftover_placeholders` | render account_balances, check no "${" in output | No unresolved placeholders |
| `test_transform_produces_operations` | transform_to_operations(rendered) | List with text, a2ui_op(beginRendering), a2ui_op(dataModelUpdate), a2ui_op(surfaceUpdate)*, done |
| `test_expand_templates_transaction_rows` | expand_templates on transaction_history uiDef | 14 t_row_N components created |

#### 9b. Integration Tests (curl)

```bash
# Start agent
cd agent-templates && python template_agent.py &

# Test health
curl -s http://localhost:8000/health | python -m json.tool

# Test transaction history
curl -N -X POST http://localhost:8000/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message": "Show my last transactions"}'

# Test account balances
curl -N -X POST http://localhost:8000/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message": "What are my account balances?"}'

# Test brokerage activity
curl -N -X POST http://localhost:8000/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message": "Show brokerage activity"}'

# Test fallback
curl -N -X POST http://localhost:8000/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello there"}'

# Test event endpoint
curl -X POST http://localhost:8000/event \
  -H "Content-Type: application/json" \
  -d '{"surface_id": "test", "event_type": "userAction"}'
```

#### 9c. Android App Test

1. Start template agent on port 8000
2. Build Android app with `USE_REAL_AGENT=true` (ensure `USE_JSONL_ENDPOINT=false` for SSE mode)
3. Run on emulator
4. Send "Show my last transactions" → verify 14-transaction card renders
5. Send "What are my account balances?" → verify banking + investing + total cards render
6. Send "Show brokerage activity" → verify brokerage card with 5 trades renders
7. Send "Hello" → verify plain text response (no card)

#### 9d. Run Existing UI Tests

```bash
# The existing run_ui_tests.sh builds with USE_REAL_AGENT=false (mock mode)
# For template agent testing, we need the agent running and USE_REAL_AGENT=true
# This may require a separate test configuration or manual testing
```

**Effort:** ~2 hours

---

## Notes & Considerations

### Technical Decisions

1. **`${placeholder}` vs `{placeholder}` syntax:**
   - `${key}` for data substitution in the renderer (before transform pipeline)
   - `{i}` and `{field}` for itemTemplate expansion (in `expand_templates()`, already implemented)
   - The two syntaxes are deliberately different to avoid conflicts

2. **Static vs. itemTemplate per surface:**
   - `transaction_history` — uses `itemTemplate` (14 repeating rows, clear list pattern)
   - `account_balances` — fully static (fixed structure, 5 accounts in 2 sections)
   - `brokerage_activity` — uses `itemTemplate` (5 repeating transaction rows)
   - Rule of thumb: Use `itemTemplate` when the data is a variable-length list; use static when the structure is fixed

3. **Double text event:** `agent.py` emits a `text` SSE event on line 640, then `transform_to_operations()` produces another `text` operation. The template agent should replicate this exact behavior. The Android client's text handler (`RealChatRepository`) accumulates text, so the duplicate is harmless.

4. **Surface ID generation:** The original agent uses `response_{random_suffix}`. The template agent does the same via `_random_suffix()`. The Kotlin mock surfaces use hardcoded IDs (`response_run1`, `response_run2`, `brokerage_activity`) but those are only for client-side mock mode.

5. **No schema validation:** The LLM agent uses `validate_ui_definition()` from the `a2ui-agent` SDK. Since templates are pre-approved and deterministic, runtime validation is unnecessary. Validate templates once during authoring.

### Risks

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Template JSON doesn't match Kotlin surface exactly | Medium | High | Side-by-side comparison of rendered output; screenshot diffing |
| `expand_templates()` behavior differs when called from template vs. LLM output | Low | High | Unit test with known input/output pairs from agent.py |
| Android client expects fields not produced by template agent | Low | Medium | Verify SurfaceStateManager processes all operations correctly |
| `${placeholder}` in non-literalString fields causes issues | Low | Low | Only substitute in string values; leave non-strings unchanged |
| Port 8000 conflict with running LLM agent | Medium | Low | Document: stop LLM agent before starting template agent; or use `PORT` env var |

### Dependency Graph

```
Task 1 (a2ui_transform.py) ──┐
                              ├── Task 6 (template_agent.py) ── Task 9 (E2E test)
Task 2 (template JSONs) ─────┤
                              │
Task 3 (data JSONs) ──────────┤
                              │
Task 4 (intent_router.py) ────┤
                              │
Task 5 (template_renderer.py)─┘

Task 7 (requirements.txt) ── independent, do anytime
Task 8 (README.md) ── do after Task 6
```

### Parallelization Opportunities

- **Tasks 1, 2, 3, 4 can run in parallel** — they have no dependencies on each other
- **Task 5** depends on Task 2 and 3 (needs to know template/data format)
- **Task 6** depends on Tasks 1, 4, 5 (imports all three modules)
- **Task 7** is independent
- **Task 8** depends on Task 6 (documents the server)
- **Task 9** depends on all other tasks

### Effort Summary

| Task | Effort | Dependencies |
|------|--------|-------------|
| 1. Extract transform pipeline | ~1 hour | None |
| 2. Create template JSONs | ~3 hours | None |
| 3. Create data JSONs | ~1 hour | None |
| 4. Create intent router | ~30 min | None |
| 5. Create template renderer | ~1.5 hours | Tasks 2, 3 |
| 6. Create FastAPI server | ~1.5 hours | Tasks 1, 4, 5 |
| 7. Create requirements.txt | ~5 min | None |
| 8. Create README | ~30 min | Task 6 |
| 9. End-to-end testing | ~2 hours | All |
| **Total** | **~11 hours** | |

### Final Directory Structure

```
agent-templates/
├── template_agent.py          # FastAPI server — main entry point
├── intent_router.py           # Keyword-based intent classification
├── template_renderer.py       # Load templates + data, substitute placeholders
├── a2ui_transform.py          # Transform pipeline (extracted from agent.py)
├── requirements.txt           # fastapi, uvicorn, sse-starlette, pydantic
├── README.md                  # Setup, usage, template authoring guide
├── test_template_agent.py     # Unit + integration tests
├── templates/                 # Pre-approved A2UI template JSON files
│   ├── transaction_history.json
│   ├── account_balances.json
│   └── brokerage_activity.json
└── data/                      # Mock data JSON files
    ├── transaction_history.json
    ├── account_balances.json
    └── brokerage_activity.json
```
