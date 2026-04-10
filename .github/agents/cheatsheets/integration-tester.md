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

## Unit Test Check
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:testDebugUnitTest --no-daemon -q   # must be 0 failures
# View results:
find app/build/test-results/testDebugUnitTest -name "*.xml" | xargs grep -l 'failures="[^0]"'
# Empty output = all passing
```

## Template Agent Unit Tests (no server needed)
```bash
cd agent-templates
python3 -m pytest test_template_agent.py -v
# Expected: all TestIntentRouter, TestTemplateRenderer, TestA2UiTransform, TestSyncEndpointFormat pass
```

## Android UI Tests
```bash
./run_ui_tests.sh   # requires running emulator
# NOTE: `bash run_ui_tests.sh 2>&1 | tail -30` masks non-zero exit code (pipeline returns tail's exit)
```

## Template Intent Gotcha
- Phrase `what are my account balances` currently routes to fallback "A2UI Setup Flow Diagram" (no `chkName`) instead of `account_balances` template.

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

## Route/Port Gotchas
- `agent-templates/template_agent.py` exposes `POST /chat/stream` (NOT `/chat/stream/template`)
- Merged-agent-only checks (`/chat/stream/template`, `/health` route map with `llm`+`template`) cannot be validated from template agent alone
- `lsof -ti:8000` can return Android emulator PID due established connection to port 8000 (not LISTEN); verify with `curl --max-time 3 http://localhost:8000/health` before killing

## Session Log
| Date | Pattern Learned |
|---|---|
| 2026-04-09 | event endpoint requires surface_id (snake_case), not component_id |
| 2026-04-09 | In this shell, `pip`/`python` commands are missing; use `pip3`/`python3` when startup fails with exit 127 |
| 2026-04-09 | Account-balances utterance `what are my account balances` returned fallback diagram; also avoid piped UI test command when asserting exit code |
| 2026-04-09 | `run_ui_tests.sh` can exit at device detection when no emulator is attached; `... | tail -60` still returns 0 and can falsely look successful |
| 2026-04-09 | Template agent lacks `/chat/stream/template`; also `lsof -ti:8000` may show emulator PID even when agent is down |
| 2026-04-09 | Merged `agent.py` `/chat/stream/template` passed intents; account-balances can emit multiple `surfaceUpdate` chunks before `done` |
| 2026-04-10 | WireFormat routing spot-check: ViewModel maps 4 endpoint strings correctly and `USE_JSONL_ENDPOINT` appears only in companion object |
| 2026-04-10 | v0.8.4 full integration run: assemble + unit tests pass (19/0), 6 routing endpoints present, no coverage XML/report.xml emitted |
| 2026-04-10 | v0.8.5 crash-fix validation: 30/30 unit tests green (5 suites); verify counts via `app/build/test-results/testDebugUnitTest/*.xml` |
| 2026-04-10 | v0.8.6–v0.8.7 retro: 3 production crashes found post-release; root cause: no tests for monetaryColor(), regex replacement with `$`, explicitList children format, and sync componentProperties contract. Added template agent unit tests, MonetaryColorTest, SurfaceStateManagerTest, ListItemRenderTest. |

## Regression Checklist (run on every release)

- [ ] `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL
- [ ] `./gradlew :app:testDebugUnitTest` → 0 failures (check XML files)
- [ ] `cd agent-templates && python3 -m pytest test_template_agent.py -v` → all pass
- [ ] `cd agent && python3 -m py_compile agent.py` → SYNTAX_OK
- [ ] `curl http://localhost:8000/health` → `{"status":"ok"}`
- [ ] Template SSE smoke: `curl -s -N -X POST -H "Content-Type: application/json" -d '{"message":"show my transactions"}' http://localhost:8000/chat/stream/template` → see `surfaceUpdate` + `done`
- [ ] No `Regex("...", "replacement with bare $")` pattern in FinancialCatalog.kt (grep check)
