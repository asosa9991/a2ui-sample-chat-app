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
