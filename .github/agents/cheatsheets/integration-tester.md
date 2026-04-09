# Integration Tester — Personal Cheatsheet

> Read at the start of every run. Update after every run with new patterns, gotchas, and fixes.

## Service Management
```bash
./agent.sh start llm        # LLM agent on :8000
./agent.sh start template   # template agent on :8000
./agent.sh stop             # stop running agent
./agent.sh status           # check PID + last logs
```

## Health Check (non-LLM — always safe)
```bash
curl -s http://localhost:8000/health | python3 -m json.tool
# Expected: {"status": "ok", "service": "a2ui-agent"}
```

## Template Agent SSE Test (safe — no LLM tokens)
```bash
curl -s -N --max-time 15 \
  -H "Content-Type: application/json" \
  -d '{"message": "show my account balances"}' \
  http://localhost:8000/chat/stream
# Expected events: text → a2ui_op(beginRendering) → a2ui_op(surfaceUpdate) → done
```

## Android Build Check
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:compileDebugKotlin --no-daemon -q && echo "BUILD_OK"
```

## Android UI Tests
```bash
./run_ui_tests.sh   # requires running emulator
```

## Event Endpoint Test
```bash
curl -s -X POST \
  -H "Content-Type: application/json" \
  -d '{"surface_id":"test","event_type":"button_tap","source_component_id":"test","value":"test"}' \
  http://localhost:8000/event
# Note: surface_id is REQUIRED and snake_case
```

## CORS Check
```bash
curl -sI -X OPTIONS \
  -H "Origin: http://localhost:3000" \
  -H "Access-Control-Request-Method: POST" \
  http://localhost:8000/chat/stream | grep -i access-control
```

## LLM Agent Tests (⚠️ token-consuming — escalate to System Debugger)
- NEVER call /chat/stream on LLM agent without explicit user approval
- Escalate to `System Debugger` who has a user-approved smoke test workflow

## Session Log
| Date | Pattern Learned |
|---|---|
| 2026-04-09 | event endpoint requires surface_id (snake_case), not component_id |
