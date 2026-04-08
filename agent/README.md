# A2UI Agent Server

Python FastAPI server that uses `github-copilot-sdk` (`CopilotClient`) to call an LLM and return A2UI operations as SSE streams to the Android chat app.

---

## Setup

A virtual environment is strongly recommended.

```bash
cd agent
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt   # a2ui-agent will fail to install — that's OK (see note below)
cp .env.example .env              # add your GITHUB_TOKEN
```

> **Note on `a2ui-agent`:** The `a2ui-agent` package in `requirements.txt` is a **local SDK** — it is not published to PyPI. The install will fail for this package; that's expected and safe. The server detects this at startup and sets `_SDK_AVAILABLE = False`, falling back to a built-in system prompt. Everything works without it.

---

## Auth

**Option A — GitHub Copilot subscription (recommended):**
```
GITHUB_TOKEN=ghu_your_token_here
```

**Option B — GitHub Models API (free tier):**
```
GITHUB_MODELS_TOKEN=github_pat_your_token_here
```
Get a token at https://github.com/settings/tokens.

---

## LLM Backend

- Client: `github-copilot-sdk` (`CopilotClient`) using `GITHUB_TOKEN`
- Model: `claude-sonnet-4.6` (via GitHub Copilot)
- Falls back to the GitHub Models API (`openai` client) when `GITHUB_MODELS_TOKEN` is set instead of `GITHUB_TOKEN`

---

## Running the Server

**Background (production / persistent):**
```bash
cd agent && source venv/bin/activate
nohup uvicorn agent:app --host 0.0.0.0 --port 8000 < /dev/null >> /tmp/a2ui_agent.log 2>&1 &
disown $!
```
Logs go to `/tmp/a2ui_agent.log`. This form avoids the `reload=True` bad-fd crash that occurs when backgrounding `python agent.py`.

**Foreground (development, auto-reload):**
```bash
python agent.py
```

Server starts at `http://localhost:8000`.

---

## Endpoints

### GET /health

Health check.

```bash
curl http://localhost:8000/health
# {"status":"ok","service":"a2ui-agent"}
```

---

### POST /chat/stream

SSE stream with **custom event types**. This is the primary endpoint used by the Android app.

**Request:**
```json
{"message": "Show my trades from last week", "session_id": "optional"}
```

**SSE events emitted (in order):**

| # | Event type | Data shape |
|---|---|---|
| 1 | `text` | `{"text": "summary..."}` |
| 2 | `a2ui_op` | `{"beginRendering": {"surfaceId": "response_xxx", "root": "root"}}` |
| 3 | `a2ui_op` | `{"dataModelUpdate": {"surfaceId": "...", "path": "", "contents": [...]}}` |
| 4 | `a2ui_op` | `{"surfaceUpdate": {"surfaceId": "...", "components": [...]}}` |
| 5 | `done` | `{}` |

```bash
curl -N -X POST http://localhost:8000/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message": "Show my trades from last week"}'
```

---

### POST /chat/stream/jsonl

SSE stream in **spec-compliant JSONL format** — plain `data:` lines, no custom event types.

- Uses the SDK-generated system prompt (when `_SDK_AVAILABLE = True`)
- Message order: `text` → `surfaceUpdate` → `dataModelUpdate` → `beginRendering` → `done`

```bash
curl -N -X POST http://localhost:8000/chat/stream/jsonl \
  -H "Content-Type: application/json" \
  -d '{"message": "Show my trades from last week"}'
```

---

### POST /event

Handles UI interaction events sent back from the Android app.

**Request:**
```json
{
  "surface_id": "response_xxx",
  "event_type": "userAction",
  "name": "button_clicked",
  "context": {}
}
```

| `event_type` | Response |
|---|---|
| `userAction` | SSE stream with updated A2UI operations |
| `dataChange` | Sync JSON `{"status": "received"}` |
| `feedback` | SSE stream with text acknowledgement |

---

## Integration Tests

Requires the server to be running. Tests auto-skip if the server is unreachable.

```bash
cd agent && source venv/bin/activate
pytest test_agent.py -v -m integration --timeout=90
```

- Tests `/chat/stream` and `/chat/stream/jsonl` with the message `"Show my trades from last week"`
- Expects a full A2UI response: `beginRendering` + `dataModelUpdate` + `surfaceUpdate` + `done`

---

## Android Connection

| Device | Base URL |
|---|---|
| Emulator | `http://10.0.2.2:8000` (hardcoded in `RealChatRepository.kt`) |
| Physical device | `http://192.168.1.x:8000` — update `RealChatRepository.kt` with your machine's LAN IP |

Toggle between real agent and mock data in `ChatViewModel.kt`:
```kotlin
private const val USE_REAL_AGENT = true
```

---

## Logcat Tags

| Tag | Source |
|---|---|
| `A2UI.VM` | `ChatViewModel` |
| `A2UI.Repo` | `RealChatRepository` |
| `A2UI.Surface` | `SurfaceStateManager` |
| `FinancialCatalog` | Widget overrides |

---

## Mock Data Injection

Before each LLM call, `agent.py` detects the user's intent and injects pre-built display-model data into the system prompt. The LLM copies pre-formatted values verbatim — it does not invent financial data or perform any formatting or masking.

### Functions

**`detect_intent(message: str) -> str`**
Keyword-based classifier — no LLM call. Scans the user message and returns one of:
`accounts | positions | transactions | activities | none`

**`load_mock_data(intent: str) -> str`**
Reads the matching `*_display.json` file from `mockdata/` and returns a prompt-injection string prefixed with:
```
[CUSTOMER DATA — use values verbatim, do not reformat or recalculate]
```

### Intent → File Mapping

| Intent | Keywords (any match) | Injected file |
|---|---|---|
| `accounts` | balance, account, net worth, summary, worth, cash | `mockdata/accounts_display.json` |
| `positions` | holding, portfolio, position, stock, equity, etf, fund, allocation | `mockdata/positions_display.json` |
| `transactions` | transaction, trade, history, bought, sold, purchase, order | `mockdata/transactions_display.json` |
| `activities` | activity, feed, recent, event, alert, notification | `mockdata/activities_display.json` |
| `none` | (no match) | nothing injected; LLM responds conversationally |

### How It Works

1. `detect_intent` scans the user message for keywords from `_INTENT_KEYWORDS`.
2. `load_mock_data` reads the matching `*_display.json` file and prepends the `[CUSTOMER DATA]` header.
3. The resulting string is appended to the system prompt for that request only — the base prompt is never mutated.
4. The LLM copies pre-formatted values (`"$487,234.56"`, `"••••4821"`) directly into `literalString` fields.

### Mock Data Files

All display files live in `mockdata/` (one level above `agent/`). They represent a single consistent demo customer: **Michael Hartwell (CUST-7842931)**.

| File | Contents |
|---|---|
| `mockdata/accounts_display.json` | 14 accounts grouped by type (BROKERAGE, RETIREMENT, HSA, BANKING) |
| `mockdata/positions_display.json` | 31 positions (stocks, ETFs, mutual funds) grouped by account |
| `mockdata/transactions_display.json` | 42 transactions date-grouped with pending banner |
| `mockdata/activities_display.json` | 58 activity events with open orders banner and upcoming section |

The raw `*.json` files (also in `mockdata/`) are the domain-model source of truth and are **not** used by the agent.

### Why Display Files

The `*_display.json` files are pre-formatted for direct UI consumption:
- Amounts are already strings: `"$487,234.56"`, `"+$3,127.88"`
- Account numbers are pre-masked: `"••••4821"`
- Direction signals are explicit: `"positive" | "negative" | "neutral"`
- Items are pre-grouped into sections/date-groups matching the A2UI layout hierarchy

This means the LLM copies values verbatim — no formatting math, no masking logic.

### Adding a New Intent

1. Add a new entry to `_INTENT_KEYWORDS` in `agent.py` with your intent name and keyword list.
2. Add the matching entry to `_INTENT_FILE_MAP` pointing to a `*_display.json` file in `mockdata/`.
3. Create the display JSON file in `mockdata/` following the existing format (pre-formatted strings, direction signals, date-grouped sections).
