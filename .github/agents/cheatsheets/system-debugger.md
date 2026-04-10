# System Debugger — Personal Cheatsheet

> Read at the start of every run. Update after every run with new patterns, gotchas, and fixes.

## Quick Diagnosis Commands
```bash
# Port status
lsof -ti:8000 && echo "PORT IN USE" || echo "port free"

# Agent status
./agent.sh status

# Health check
curl -s http://localhost:8000/health

# Last 50 log lines
./agent.sh logs llm
./agent.sh logs template

# Syntax check
source agent/.venv/bin/activate
python -m py_compile agent/agent.py && echo "SYNTAX_OK"

# Import check
python -c "from copilot.session import PermissionHandler; print('OK')"
```

## Common Error → Root Cause Map
| Error | Root Cause | Fix Owner |
|---|---|---|
| `cannot import name 'PermissionHandler' from 'copilot'` | Not in `__init__`, use `copilot.session` | Python Expert |
| `ModuleNotFoundError: No module named 'X'` | Missing from requirements.txt | Python Expert |
| `AttributeError: 'list' object has no attribute 'get'` | Template JSON is bare array, needs dict | Python Expert |
| `WARN FinancialCatalog` in logcat | TextField missing explicit `text.path` binding | Android Expert |
| `address already in use :8000` | Previous agent still running | `./agent.sh stop` |
| Empty SSE stream | LLM API timeout or token expired | Check `.env` token |

## SSE Event Sequence (expected)
```
event: text
data: {"text": "..."}

event: a2ui_op
data: {"op": "beginRendering", "surfaceId": "...", "rootComponentId": "..."}

event: a2ui_op
data: {"op": "surfaceUpdate", "surfaceId": "...", "components": [...]}

event: done
data: {}
```

## Logcat Tags (Android)
| Tag | Source |
|---|---|
| `A2UI.VM` | ChatViewModel |
| `A2UI.Repo` | RealChatRepository |
| `A2UI.Surface` | SurfaceStateManager |
| `FinancialCatalog` | Widget overrides |

## LLM Smoke Test (user-approved only — max 2 requests)
```bash
curl -s -N --max-time 30 \
  -H "Content-Type: application/json" \
  -d '{"message": "show my account balances"}' \
  http://localhost:8000/chat/stream
```
PASS: contains `event: a2ui_op`
FAIL: contains `ImportError`, `500`, or empty after 30s

## Session Log
| Date | Pattern Learned |
|---|---|
| 2026-04-09 | PermissionHandler import error: 3 sites in agent.py, all need copilot.session |
| 2026-04-09 | stray `=1.6.0` file from unquoted pip install shell redirect |

## Copilot SDK Timeout Patterns
- `TimeoutError: Timeout after 60.0s waiting for session.idle` → LLM never responded; check token validity and Copilot CLI health
- Pattern: pings every 15s in SSE log but ZERO LLM tokens = confirmed silent hang at Copilot service level
- `Task exception was never retrieved` for `CopilotSession.send_and_wait` → `stream_llm_copilot_sdk` leaks tasks on client disconnect
- Copilot headless CLI runs on port 4321 (`copilot --headless --port 4321`); NOT HTTP — it's JSON-RPC, curl to :4321 will fail

## b86dd36 post-mortem
- ImportError fix (b86dd36) is CORRECT; the timeout it revealed is a LATENT environment issue
- Before fix: fail fast with ImportError at ~0s; After fix: fail with TimeoutError at 60s
- Two separate bugs: (1) wrong import path (fixed) (2) LLM backend not responding (unresolved, env issue)
- Orphaned task leak in stream_llm_copilot_sdk: send_task not cancelled in finally block when client disconnects early

## Copilot SDK Timeout Diagnosis Checklist
```bash
# 1. Check if Copilot headless process is alive (port 4321)
lsof -ti:4321 && echo "running" || echo "NOT running — run ./agent.sh start llm"
# 2. Check token presence
grep -E "^(GITHUB|COPILOT)_TOKEN" agent/.env | sed 's/=.*/=<SET>/'
# 3. Look for "Task exception was never retrieved" — indicates task leak
grep "Task exception" logs/agent-llm.log | wc -l
# 4. Check if any LLM tokens arrived between request and timeout
grep -A 5 "chat/stream.*message=" logs/agent-llm.log | grep -v ping | head -20
```

## Copilot CLI v1.0.21 — `--headless` removed, use `--acp`
- Old: `copilot --headless --port 4321`  (SDK was compatible with this)
- New v1.0.21: `copilot --acp` (Agent Client Protocol server — port may differ)
- Python SDK `copilot.client` still hardcodes `localhost:4321` → `ConnectionRefused`
- `agent.sh start llm` does NOT start the Copilot CLI — it must be started separately
- `SDK available: False` at agent startup = `No module named 'a2ui'` (different issue, a2ui SDK not installed)
- DIAGNOSIS SHORTCUT: check `lsof -ti:4321`; if empty after `./agent.sh start llm` → Copilot CLI not running → escalate to Python Expert

## Session Log
| 2026-04-09 | Copilot CLI v1.0.21 dropped --headless; SDK still connects to :4321 → ConnectionRefused; agent.sh never starts CLI |

## Agent Startup Gotcha — Background shell vs nohup
- `python3 agent.py &` in a sync bash tool call may die when the shell exits; port opens then immediately closes
- Use `nohup .venv/bin/python3 agent.py > /tmp/agent.log 2>&1 &` to survive shell session end
- Always verify with `lsof -ti:8000` after 4s sleep

## Template Agent API — Verified Endpoints (agent.py)
- `GET /health` → 200, JSON with `routes.llm`, `routes.template`, `templates[]` array
- `POST /chat/stream/template` → SSE: text → beginRendering → dataModelUpdate → surfaceUpdate → done
- `POST /event` payload: `{"surface_id":..., "event_type":..., "component_id":..., "session_id":...}` → 200 `{"status":"received","surface_id":...}`
- Weather/unknown fallback: text event + done, no 500
- surfaceUpdate may be split across 2 events (chunked) — both valid, Android merges them
