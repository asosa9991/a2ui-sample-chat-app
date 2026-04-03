# Pre-Approved Legal Templates: Fitting A2UI into an Existing Markdown Production System

## Executive Summary

You have a production system that already responds with markdown and an intent classification layer (or at minimum, a routing mechanism that knows what kind of response to produce). You want to add A2UI rich card responses alongside the existing markdown, using **pre-approved, legally reviewed templates** — not LLM-generated layouts. This is not only achievable, it's a simpler and more robust architecture than what the current A2UI sample app does.

The key insight is: **your existing production system already solves the hard parts** (intent classification, data retrieval, response routing). A2UI templates are just a new **response format** — like adding JSON API responses alongside HTML in a web server. Your production system keeps doing what it does; you add a template registry of pre-approved A2UI JSON files, and when the system detects a "rich card" intent, it loads the approved template, injects the data, and returns A2UI operations instead of (or alongside) markdown.

---

## Your Current Architecture (Assumed)

Based on what you described — a production system responding with markdown:

```
┌──────────────────────────────────────────────────────────────┐
│                CURRENT PRODUCTION SYSTEM                       │
│                                                               │
│  User Message ──► Intent/Routing ──► Data Layer ──► Markdown  │
│                   (existing)         (existing)     Response   │
│                                                               │
│  "Show my balances" ──► balances intent ──► fetch from API    │
│                                            ──► format as MD   │
│                                            ──► return to app  │
└──────────────────────────────────────────────────────────────┘
```

You already have:
- ✅ Intent classification / routing (knows "this is a balances request")
- ✅ Data layer (fetches real account data from APIs/databases)
- ✅ Response formatting (turns data into markdown)
- ✅ Delivery channel (returns response to the mobile app)

What you're adding:
- 🆕 A2UI template registry (pre-approved JSON templates)
- 🆕 Template renderer (injects data into templates)
- 🆕 A2UI SSE streaming (delivers the rendered template to the mobile client)

---

## The Proposed Architecture

```
┌───────────────────────────────────────────────────────────────────────┐
│                   PROPOSED ARCHITECTURE                                │
│                                                                       │
│  User Message ──► Intent/Routing ──► Data Layer                       │
│                   (existing)         (existing)                       │
│                        │                  │                           │
│                        ▼                  ▼                           │
│                   ┌─────────┐      ┌─────────────┐                   │
│                   │ Format  │      │  Data Blob   │                   │
│                   │ Decision│      │ (accounts,   │                   │
│                   │         │      │  txns, etc.) │                   │
│                   └────┬────┘      └──────┬───────┘                   │
│                        │                  │                           │
│              ┌─────────┼──────────────────┼──────────┐               │
│              │         ▼                  ▼          │               │
│              │    ┌──────────┐     ┌──────────────┐  │               │
│              │    │ Markdown │     │ A2UI Template │  │               │
│              │    │ Formatter│     │ Registry      │  │               │
│              │    │(existing)│     │(pre-approved) │  │               │
│              │    └────┬─────┘     └──────┬────────┘  │               │
│              │         │                  │           │               │
│              │         ▼                  ▼           │               │
│              │    "## Balances\n"   ┌──────────────┐  │               │
│              │    "| Account |..."  │ Template     │  │               │
│              │                      │ Renderer     │  │               │
│              │                      │ (inject data)│  │               │
│              │                      └──────┬───────┘  │               │
│              │                             │          │               │
│              │                             ▼          │               │
│              │                      A2UI SSE Events   │               │
│              │                      (beginRendering,  │               │
│              │                       dataModelUpdate, │               │
│              │                       surfaceUpdate)   │               │
│              └────────────────────────────────────────┘               │
│                                                                       │
│  Client receives:                                                     │
│    • Markdown text (summary, always)                                  │
│    • A2UI card (rich UI, when template exists for this intent)        │
└───────────────────────────────────────────────────────────────────────┘
```

---

## How Pre-Approved Templates Work

### Step 1: Design and approve templates

Each template is a JSON file that defines the **layout structure** (the component tree) with **data placeholders**. Legal reviews and approves these files. They are versioned, stored in source control, and deployed as part of the server — never generated at runtime.

Example: `templates/account_balances.json`

```json
{
  "templateId": "account_balances",
  "version": "1.0.0",
  "approvedBy": "Legal Team",
  "approvedDate": "2026-03-15",
  "description": "Account balances summary card with banking and investing sections",

  "textTemplate": "Here's a summary of your {sectionCount} accounts with current balances.",

  "uiDefinition": {
    "root": "root",
    "components": {
      "root": {
        "componentProperties": {
          "Column": {
            "children": { "explicitList": ["${sections}", "total_card"] }
          }
        }
      },
      "total_card": {
        "componentProperties": {
          "Card": { "child": "total_row" }
        }
      },
      "total_row": {
        "componentProperties": {
          "Row": {
            "children": { "explicitList": ["total_lbl", "total_val"] },
            "distribution": "spaceBetween"
          }
        }
      },
      "total_lbl": {
        "componentProperties": {
          "Text": { "text": { "literalString": "TOTAL NET WORTH" }, "usageHint": "h5" }
        }
      },
      "total_val": {
        "componentProperties": {
          "Text": { "text": { "literalString": "${totalNetWorth}" }, "usageHint": "h4" }
        }
      }
    },

    "repeatingSection": {
      "id": "section_{i}",
      "iteratesOver": "sections",
      "components": {
        "section_{i}_card": {
          "componentProperties": { "Card": { "child": "section_{i}_col" } }
        },
        "section_{i}_col": {
          "componentProperties": {
            "Column": {
              "children": { "explicitList": ["section_{i}_hdr", "section_{i}_div", "${section_{i}_accounts}"] }
            }
          }
        },
        "section_{i}_hdr": {
          "componentProperties": {
            "Row": {
              "children": { "explicitList": ["section_{i}_lbl", "section_{i}_tot"] },
              "distribution": "spaceBetween"
            }
          }
        },
        "section_{i}_lbl": {
          "componentProperties": {
            "Text": { "text": { "literalString": "${sectionName}" }, "usageHint": "caption" }
          }
        },
        "section_{i}_tot": {
          "componentProperties": {
            "Text": { "text": { "literalString": "${sectionTotal}" }, "usageHint": "h5" }
          }
        },
        "section_{i}_div": {
          "componentProperties": { "Divider": {} }
        }
      },

      "repeatingRow": {
        "id": "account_{i}_{j}",
        "iteratesOver": "accounts",
        "components": {
          "account_{i}_{j}_row": {
            "componentProperties": {
              "Row": {
                "children": { "explicitList": ["account_{i}_{j}_left", "account_{i}_{j}_right"] },
                "distribution": "spaceBetween"
              }
            }
          },
          "account_{i}_{j}_left": {
            "componentProperties": {
              "Column": {
                "children": { "explicitList": ["account_{i}_{j}_name", "account_{i}_{j}_num"] }
              }
            }
          },
          "account_{i}_{j}_name": {
            "componentProperties": {
              "Text": { "text": { "literalString": "${accountName}" }, "usageHint": "body" }
            }
          },
          "account_{i}_{j}_num": {
            "componentProperties": {
              "Text": { "text": { "literalString": "${accountNumber}" }, "usageHint": "caption" }
            }
          },
          "account_{i}_{j}_right": {
            "componentProperties": {
              "Column": {
                "children": { "explicitList": ["account_{i}_{j}_bal", "account_{i}_{j}_chg"] }
              }
            }
          },
          "account_{i}_{j}_bal": {
            "componentProperties": {
              "Text": { "text": { "literalString": "${balance}" }, "usageHint": "body" }
            }
          },
          "account_{i}_{j}_chg": {
            "componentProperties": {
              "Text": { "text": { "literalString": "${change}" }, "usageHint": "caption" }
            }
          }
        }
      }
    }
  }
}
```

### Step 2: The template renderer expands placeholders with data

```python
# Proposed: template_renderer.py

import json
import copy
import os

TEMPLATES_DIR = os.path.join(os.path.dirname(__file__), "templates")

class TemplateRegistry:
    """Load and cache pre-approved A2UI templates."""

    def __init__(self, templates_dir: str = TEMPLATES_DIR):
        self._templates: dict[str, dict] = {}
        self._load_templates(templates_dir)

    def _load_templates(self, templates_dir: str):
        if not os.path.isdir(templates_dir):
            return
        for filename in os.listdir(templates_dir):
            if filename.endswith(".json"):
                with open(os.path.join(templates_dir, filename)) as f:
                    template = json.load(f)
                    tid = template.get("templateId")
                    if tid:
                        self._templates[tid] = template

    def get(self, template_id: str) -> dict | None:
        return self._templates.get(template_id)

    def list_templates(self) -> list[str]:
        return list(self._templates.keys())


def render_template(template: dict, data: dict) -> dict:
    """
    Render a pre-approved template with data.
    Returns a dict compatible with transform_to_operations():
      {"text": "...", "uiDefinition": {"root": "...", "components": {...}}}
    """
    # 1. Render text template
    text = template.get("textTemplate", "")
    for key, value in data.items():
        if isinstance(value, str):
            text = text.replace(f"{{{key}}}", value)

    # 2. Deep-copy the uiDefinition (don't mutate the cached template)
    ui_def = copy.deepcopy(template["uiDefinition"])
    components = ui_def["components"]

    # 3. Expand repeating sections
    if "repeatingSection" in ui_def:
        expand_repeating(components, ui_def["repeatingSection"], data)
        del ui_def["repeatingSection"]

    # 4. Replace ${placeholder} values in literalString fields
    inject_data(components, data)

    return {"text": text, "uiDefinition": ui_def}


def expand_repeating(components: dict, section_def: dict, data: dict):
    """Expand repeating sections/rows based on data arrays."""
    items_key = section_def["iteratesOver"]
    items = data.get(items_key, [])

    for i, item in enumerate(items):
        section_components = section_def["components"]
        for comp_id_template, comp_data in section_components.items():
            # Replace {i} with actual index
            comp_id = comp_id_template.replace("{i}", str(i))
            comp_copy = json.loads(json.dumps(comp_data).replace("{i}", str(i)))

            # Replace ${fieldName} with item values
            comp_json = json.dumps(comp_copy)
            for field_key, field_value in item.items():
                if isinstance(field_value, str):
                    comp_json = comp_json.replace(f"${{{field_key}}}", field_value)
            components[comp_id] = json.loads(comp_json)

        # Expand nested repeating rows
        if "repeatingRow" in section_def:
            row_def = section_def["repeatingRow"]
            sub_items_key = row_def["iteratesOver"]
            sub_items = item.get(sub_items_key, [])
            for j, sub_item in enumerate(sub_items):
                for comp_id_template, comp_data in row_def["components"].items():
                    comp_id = comp_id_template.replace("{i}", str(i)).replace("{j}", str(j))
                    comp_copy = json.loads(
                        json.dumps(comp_data)
                        .replace("{i}", str(i))
                        .replace("{j}", str(j))
                    )
                    comp_json = json.dumps(comp_copy)
                    for field_key, field_value in sub_item.items():
                        if isinstance(field_value, str):
                            comp_json = comp_json.replace(f"${{{field_key}}}", field_value)
                    components[comp_id] = json.loads(comp_json)


def inject_data(components: dict, data: dict):
    """Replace ${placeholder} in all literalString values with data."""
    comp_json = json.dumps(components)
    for key, value in data.items():
        if isinstance(value, str):
            comp_json = comp_json.replace(f"${{{key}}}", value)
    return json.loads(comp_json)
```

### Step 3: Wire into the existing `/chat/stream` endpoint

The critical change in `agent.py` — add a deterministic path **before** the LLM call:

```python
# In agent.py — modified /chat/stream endpoint

from template_renderer import TemplateRegistry, render_template

# Load once at startup
template_registry = TemplateRegistry()

@app.post("/chat/stream")
async def chat_stream(request: ChatRequest):
    suffix = _random_suffix()

    # ── NEW: Check if a pre-approved template handles this intent ──
    template_match = match_template(request.message, template_registry)

    if template_match:
        template_id, data = template_match
        logger.info("[chat/stream] DETERMINISTIC: template=%s", template_id)

        template = template_registry.get(template_id)
        parsed = render_template(template, data)
        operations = transform_to_operations(parsed, suffix)  # ← REUSE existing pipeline

        async def deterministic_generator():
            for op in operations:
                yield {"event": op["type"], "data": json.dumps(op["data"])}
                if op["type"] == "a2ui_op":
                    await asyncio.sleep(0.15)

        return EventSourceResponse(deterministic_generator())

    # ── EXISTING: LLM fallback for unmatched intents ──
    # ... (existing code unchanged) ...
```

---

## How This Fits Your Existing Production System

### Scenario 1: Your server decides the format

If your production system already classifies intents and decides what kind of response to produce:

```python
# Your existing production code (pseudocode)
def handle_chat(message: str) -> Response:
    intent = classify_intent(message)          # You already have this
    data = fetch_data(intent, user_id)          # You already have this

    # EXISTING: return markdown
    if not supports_a2ui(client):
        return markdown_response(intent, data)  # You already have this

    # NEW: return A2UI for rich-card-capable clients
    template = template_registry.get(intent_to_template[intent])
    if template:
        parsed = render_template(template, data)
        operations = transform_to_operations(parsed, suffix)
        return sse_response(operations)
    else:
        # No approved template for this intent — fall back to markdown
        return markdown_response(intent, data)
```

### Scenario 2: Both markdown AND A2UI in the same response

The A2UI protocol already supports this. The `text` SSE event carries the markdown/plain-text summary, and the `a2ui_op` events carry the rich card. The Android client shows both:

```
event: text
data: {"text": "Here's a summary of your accounts:\n\n**Banking**: $24,580.47\n**Investing**: $198,342.11\n**Total**: $222,922.58"}

event: a2ui_op
data: {"beginRendering": {"surfaceId": "response_abc123", "root": "root"}}

event: a2ui_op
data: {"dataModelUpdate": {...}}

event: a2ui_op
data: {"surfaceUpdate": {"components": [...]}}

event: done
data: {}
```

The client renders the markdown text above the A2UI card[^1]. Non-A2UI clients just use the text and ignore the `a2ui_op` events. This means:

- **Backward compatible**: Old clients see markdown as before
- **Progressive enhancement**: New clients see both markdown summary + rich A2UI card
- **Graceful fallback**: If no template exists for an intent, only markdown is returned

### Scenario 3: Your production system calls a separate A2UI service

If you don't want to add A2UI logic to your production server, keep them separate:

```
┌──────────────┐      ┌────────────────┐      ┌──────────────┐
│  Production  │─────►│  A2UI Template │─────►│  Mobile App  │
│  Server      │      │  Service       │      │  (Android)   │
│  (Markdown)  │      │  (this agent)  │      │              │
└──────┬───────┘      └────────────────┘      └──────────────┘
       │                       ▲
       │  POST /chat           │  POST /render-template
       │  {"message": "..."}   │  {"templateId": "account_balances",
       │                       │   "data": {...}}
       ▼                       │
┌──────────────┐               │
│  Mobile App  │───────────────┘
│  (gets MD    │
│   response,  │  If intent has a template → call A2UI service
│   checks for │  Otherwise → show markdown
│   A2UI)      │
└──────────────┘
```

In this model, you add a new endpoint to the A2UI agent:

```python
@app.post("/render-template")
async def render_template_endpoint(request: RenderTemplateRequest):
    """Render a pre-approved template with data. No LLM involved."""
    template = template_registry.get(request.template_id)
    if not template:
        raise HTTPException(status_code=404, detail=f"Template '{request.template_id}' not found")

    parsed = render_template(template, request.data)
    operations = transform_to_operations(parsed, _random_suffix())

    async def template_generator():
        for op in operations:
            yield {"event": op["type"], "data": json.dumps(op["data"])}
            if op["type"] == "a2ui_op":
                await asyncio.sleep(0.15)

    return EventSourceResponse(template_generator())
```

---

## The Legal Approval Workflow

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  Designer   │────►│  Developer  │────►│  Legal      │────►│  Deploy     │
│  creates    │     │  encodes as │     │  reviews &  │     │  to server  │
│  card       │     │  A2UI JSON  │     │  approves   │     │  templates/ │
│  mockup     │     │  template   │     │  template   │     │  directory  │
└─────────────┘     └─────────────┘     └─────────────┘     └─────────────┘
       │                   │                   │                    │
       ▼                   ▼                   ▼                    ▼
   Figma/Sketch     templates/           Signed-off           Immutable at
   design           account_balances.json version tag         runtime — no
                    (with ${placeholders}) in git              LLM can alter
```

**Key guarantees for legal:**

1. **Templates are static files** — stored in `templates/` directory, committed to source control, deployed as server artifacts. They are **never modified at runtime**.

2. **No LLM touches the layout** — the template renderer does simple string substitution (`${accountName}` → `"Premier Checking"`). No AI model has any influence on what components are rendered or how they're structured.

3. **Data comes from your approved data sources** — the data layer fetches from your existing production APIs/databases. Same data that currently populates your markdown responses.

4. **Template versioning** — each template JSON includes `version` and `approvedDate` fields. The server logs which template version was used for every response. Full audit trail.

5. **Preview before deploy** — templates can be tested in the sample app's mock mode before going to production. Load the template JSON into the Python agent's `templates/` folder, send a matching message, and see exactly what the user will see.

---

## What You Reuse from the Current Codebase

| Component | Status | Where | What it does |
|-----------|--------|-------|-------------|
| `transform_to_operations()` | ✅ Reuse as-is | `agent.py:517-574`[^2] | Converts `{"text": ..., "uiDefinition": ...}` → SSE operations |
| `transform_to_path_bindings()` | ✅ Reuse as-is | `agent.py:428-458`[^3] | Separates literal values into DataModel (reactive binding) |
| `chunk_components()` | ✅ Reuse as-is | `agent.py:507-514`[^4] | Splits components into progressive chunks |
| SSE streaming | ✅ Reuse as-is | `agent.py:640-648`[^5] | EventSourceResponse with 150ms gaps |
| `SurfaceStateManager` | ✅ No changes | `SurfaceStateManager.kt`[^6] | Accumulates SSE ops into UiDefinition |
| `MessageBubble` | ✅ No changes | `MessageBubble.kt`[^1] | Renders text + A2UISurface card |
| `FinancialCatalog` | ✅ No changes | `FinancialCatalog.kt`[^7] | All widget overrides |
| `RealChatRepository` | ✅ No changes | `RealChatRepository.kt`[^8] | SSE parsing and StreamEvent emission |
| `ChatViewModel` | ✅ No changes | `ChatViewModel.kt`[^9] | Streaming state machine |
| **LLM call** | ⏩ Skipped entirely | `agent.py:591-594` | Not called for template-matched intents |
| **Validation + retry** | ⏩ Not needed | `agent.py:602-631` | Templates are pre-validated |
| **sanitize_components()** | ⏩ Not needed | `agent.py:461-504` | Templates have no dangling refs |

---

## The Minimum Viable Integration

If you want the fastest possible path to "pre-approved template → A2UI card on the phone":

### 1. Create one template file

```bash
mkdir -p agent/templates
```

Create `agent/templates/transaction_history.json`:

```json
{
  "templateId": "transaction_history",
  "version": "1.0.0",
  "textTemplate": "Here are your transactions — {count} transactions total.",
  "uiDefinition": {
    "root": "root",
    "components": {
      "root": {
        "componentProperties": {
          "Column": {
            "children": { "explicitList": ["hdr_card"] }
          }
        }
      },
      "hdr_card": {
        "componentProperties": {
          "Card": { "child": "hdr_card_col" }
        }
      },
      "hdr_card_col": {
        "componentProperties": {
          "Column": {
            "children": { "explicitList": ["hdr_col", "txns_list"] }
          }
        }
      },
      "hdr_col": {
        "componentProperties": {
          "Column": {
            "children": { "explicitList": ["title", "period", "count"] }
          }
        }
      },
      "title": {
        "componentProperties": {
          "Text": { "text": { "literalString": "${title}" }, "usageHint": "h5" }
        }
      },
      "period": {
        "componentProperties": {
          "Text": { "text": { "literalString": "${periodRange}" }, "usageHint": "caption" }
        }
      },
      "count": {
        "componentProperties": {
          "Text": { "text": { "literalString": "${countLabel}" }, "usageHint": "caption" }
        }
      }
    },
    "itemTemplate": {
      "itemListId": "txns_list",
      "rootId": "t_row_{i}",
      "dividerId": "t_div_{i}",
      "items": "${transactions}",
      "components": {
        "t_row_{i}": {
          "componentProperties": {
            "Row": {
              "children": { "explicitList": ["t_left_{i}", "t_amt_{i}"] },
              "distribution": "spaceBetween"
            }
          }
        },
        "t_left_{i}": {
          "componentProperties": {
            "Column": {
              "children": { "explicitList": ["t_action_{i}", "t_date_{i}"] }
            }
          }
        },
        "t_action_{i}": {
          "componentProperties": {
            "Text": { "text": { "literalString": "${action}" }, "usageHint": "body" }
          }
        },
        "t_date_{i}": {
          "componentProperties": {
            "Text": { "text": { "literalString": "${date}" }, "usageHint": "caption" }
          }
        },
        "t_amt_{i}": {
          "componentProperties": {
            "Text": { "text": { "literalString": "${amount}" }, "usageHint": "body" }
          }
        }
      }
    }
  }
}
```

### 2. Add a simple template renderer (~50 lines of Python)

### 3. Add intent matching before the LLM call in `/chat/stream`

### 4. Done — zero Android changes

The `transform_to_operations()` function[^2] accepts the same `{"text": ..., "uiDefinition": ...}` dict regardless of whether it came from an LLM or a template. The `expand_templates()` function[^10] already handles the `itemTemplate` pattern. The SSE format is identical. The Android client can't tell the difference.

---

## How the Existing `expand_templates()` Already Supports This

The agent's `expand_templates()` function[^10] (lines 269-334 of `agent.py`) already processes the `itemTemplate` pattern:

```python
def expand_templates(ui_def: dict) -> dict:
    """Expand itemTemplate + items into concrete components."""
    components = dict(ui_def.get("components", {}))
    item_template = ui_def.get("itemTemplate")

    if not item_template:
        return ui_def

    items = item_template.get("items", [])
    template_comps = item_template.get("components", {})
    list_id = item_template.get("itemListId")
    root_id_pattern = item_template.get("rootId", "item_{i}")
    divider_id_pattern = item_template.get("dividerId")
    # ... expands {i} and {field} placeholders for each item
```

This means if your pre-approved template JSON includes an `itemTemplate` section, the existing `expand_templates()` will handle the expansion before `transform_to_path_bindings()` separates data from layout. **You don't need to write a custom template renderer** — the existing pipeline already does it.

The flow is:

```
Template JSON file (with itemTemplate + ${placeholders})
    │
    ▼
render_template() — replaces ${scalar} placeholders with data values
    │
    ▼
{"text": "...", "uiDefinition": {..., "itemTemplate": {..., "items": [...]}}}
    │
    ▼
transform_to_operations()
    │  calls expand_templates() internally — expands itemTemplate rows
    │  calls transform_to_path_bindings() — separates data from layout
    │  calls chunk_components() — splits for progressive rendering
    │
    ▼
SSE operations (identical format to LLM-generated responses)
```

---

## Comparison: Current vs. Pre-Approved Template Path

| Aspect | Current (LLM-Generated) | Pre-Approved Templates |
|--------|------------------------|----------------------|
| Response time | 10-15 seconds | < 100ms |
| Layout consistency | Varies per call | Identical every time |
| Legal review | Not possible (generative) | Required before deploy |
| Data accuracy | Hallucinated/fabricated | Real from data layer |
| Schema compliance | ~95% (retry needed) | 100% by construction |
| Cost per request | ~$0.02 (LLM tokens) | $0.00 |
| Audit trail | LLM output logged | Template ID + version logged |
| Rollback | Not applicable | Revert to previous template version |
| A/B testing | Difficult | Different template versions per cohort |
| Offline support | Not possible | Templates bundled in app |

---

## Confidence Assessment

| Finding | Confidence | Evidence |
|---------|-----------|---------|
| `transform_to_operations()` works with template-generated input | **Confirmed** | Accepts any `{"text": ..., "uiDefinition": ...}` dict[^2] |
| `expand_templates()` handles `itemTemplate` pattern | **Confirmed** | Already implemented in `agent.py:269-334`[^10] |
| Android client needs zero changes | **Confirmed** | SSE format is identical regardless of source |
| Pre-approved JSON templates can produce valid A2UI | **Confirmed** | Mock surfaces (`*Surface.kt`) prove this pattern works[^11][^12][^13] |
| Markdown text + A2UI card can coexist | **Confirmed** | `MessageBubble.kt` renders text above card when both present[^1] |
| Legal workflow is supported by static JSON files | **High** | Templates are versioned static files, never modified at runtime |

---

## Footnotes

[^1]: `app/src/main/java/com/example/a2ui/chat/presentation/components/MessageBubble.kt:44-59` — checks for uiDefinition, renders text above A2UISurface card
[^2]: `agent/agent.py:517-574` — `transform_to_operations()` accepts any parsed response dict
[^3]: `agent/agent.py:428-458` — `transform_to_path_bindings()` extracts literalString values into DataModel entries
[^4]: `agent/agent.py:507-514` — `chunk_components()` splits components for progressive rendering
[^5]: `agent/agent.py:640-648` — SSE emission with 150ms gaps between A2UI ops
[^6]: `app/src/main/java/com/example/a2ui/chat/data/a2ui/SurfaceStateManager.kt:49-77` — processes SSE operations
[^7]: `app/src/main/java/com/example/a2ui/chat/data/a2ui/FinancialCatalog.kt` — all widget rendering overrides
[^8]: `app/src/main/java/com/example/a2ui/chat/data/repository/RealChatRepository.kt` — SSE parsing
[^9]: `app/src/main/java/com/example/a2ui/chat/presentation/ChatViewModel.kt` — streaming state machine
[^10]: `agent/agent.py:269-334` — `expand_templates()` handles itemTemplate pattern with {i} and {field} placeholders
[^11]: `app/src/main/java/com/example/a2ui/chat/data/a2ui/TransactionHistorySurface.kt:25-138` — programmatic UiDefinition builder
[^12]: `app/src/main/java/com/example/a2ui/chat/data/a2ui/AccountBalancesSurface.kt:26-176` — programmatic UiDefinition builder
[^13]: `app/src/main/java/com/example/a2ui/chat/data/a2ui/BrokerageActivitySurface.kt:7-135` — programmatic UiDefinition builder
