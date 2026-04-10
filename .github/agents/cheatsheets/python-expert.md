# Python Expert — Personal Cheatsheet

> Read at the start of every run. Update after every run with new patterns, gotchas, and fixes.

## Agent Management
```bash
./agent.sh start            # start agent/agent.py (all routes)
./agent.sh stop             # stop whichever is running
./agent.sh status           # show PID, port, last log lines
./agent.sh logs             # tail agent log
./agent.sh setup            # create venv + pip install
```

## Venv Activation
```bash
source agent/.venv/bin/activate       # unified agent
```

## Unified Agent — Key Files
- `agent/agent.py` — FastAPI server, SSE streaming, all routes (LLM + template)
- `agent/system_prompt.py` — A2UI widget schemas, instructions to LLM
- `agent/intent_router.py` — keyword-based intent classification
- `agent/template_renderer.py` — loads templates, placeholder substitution
- `agent/a2ui_transform.py` — expand, path bindings, sanitize, chunk pipeline
- `agent/templates/*.json` — must be dict with `templateId` key (NOT bare array)
- `agent/data/*.json` — mock data files
- `agent/test_agent.py` — unit + integration tests
- `agent/requirements.txt` — dependencies
- `agent/.env` — GITHUB_TOKEN or GITHUB_MODELS_TOKEN

## Critical Import Fix (learned 2026-04-09)
```python
# WRONG — PermissionHandler not in copilot.__init__
from copilot import PermissionHandler
# CORRECT
from copilot.session import PermissionHandler
```

## Template JSON Format
```json
{"templateId": "account_balances", "uiDefinition": {...}}
```
NOT a bare array `[...]` — `template_renderer.py` calls `.get("templateId")` which requires dict.

## SSE Event Order (A2UI Protocol)
1. `event: text` — human-readable summary
2. `event: a2ui_op` + `data: {"op":"beginRendering",...}`
3. `event: a2ui_op` + `data: {"op":"dataModelUpdate",...}` (optional)
4. `event: a2ui_op` + `data: {"op":"surfaceUpdate",...}` (may chunk)
5. `event: done`

## UiEventRequest Schema
```python
class UiEventRequest(BaseModel):
    surface_id: str          # REQUIRED — snake_case
    event_type: str          # REQUIRED
    name: Optional[str] = None
    source_component_id: Optional[str] = None
    path: Optional[str] = None
    value: Optional[str] = None
    context: Optional[dict] = None
```

## Working Requirements (as of 2026-04-09)
```
fastapi==0.135.3
uvicorn==0.44.0
python-dotenv==1.2.2
sse-starlette==3.3.4
jsonschema>=4.0.0
github-copilot-sdk
openai>=1.0.0
```

## Verification Before "Done"
```bash
python -m py_compile agent/agent.py && echo "SYNTAX_OK"
./agent.sh start && sleep 5
curl -s http://localhost:8000/health
ls -la <changed_file>  # confirm file exists
```

## Known Gotchas
- `a2ui-agent` is a local package — NOT on PyPI, comment it out in requirements.txt
- Stray `=1.6.0` file created by unquoted shell: `pip install pkg>=1.6.0` → redirect accident
- Template agent `.get("templateId")` requires dict format, not array
- `system_message.mode="replace"` strips SDK guardrails — Copilot service may silently hang (no error, no response, 60s timeout). Always use `"append"` mode.
- `model="claude-sonnet-4.6"` may be unavailable via Copilot CLI headless path — omit the `model=` param to use Copilot's default.
- `send_task` in async generators: declare `send_task = None` BEFORE the try block so `finally` can safely guard `if send_task is not None and not send_task.done()`.
- **Sync endpoint component wrap is MANDATORY**: `all_components[id] = entry["component"]` is WRONG. Must be `all_components[id] = {"componentProperties": entry["component"]}`. Without the wrapper, Kotlin's `ComponentDto.componentProperties` deserializes as an empty map and widgetType is null → "Invalid component" error.
- **Agent has unit tests**: `agent/test_agent.py`. Run after any change to `intent_router.py`, `template_renderer.py`, or `a2ui_transform.py`.
- **All surfaceUpdate components must have `id` + `component` keys**: `chunk_components()` emits `{"id": comp_id, "component": props}`. If you modify this, ensure BOTH keys are present. `test_all_surface_update_components_have_id_and_component_keys` catches regressions.

## Copilot SDK — Correct Session Pattern
```python
send_task: asyncio.Task | None = None
client = CopilotClient()
await client.start()
try:
    session = await client.create_session(
        on_permission_request=PermissionHandler.approve_all,
        streaming=True,
        system_message={"mode": "append", "content": system_prompt},  # NOT "replace"
        # DO NOT pin model= unless you know it's available
    )
    def handle_event(event):
        if event.type == SessionEventType.ASSISTANT_MESSAGE_DELTA:
            queue.put_nowait(event.data.delta_content)
        elif event.type == SessionEventType.SESSION_IDLE:
            queue.put_nowait(None)
        elif event.type == SessionEventType.SESSION_ERROR:
            logger.error("SESSION_ERROR: %s", getattr(event.data, "message", str(event.data)))
            queue.put_nowait(None)  # unblock consumer
    session.on(handle_event)
    send_task = asyncio.create_task(session.send_and_wait(message, timeout=60.0))
    # ... yield tokens ...
    await send_task
finally:
    if send_task is not None and not send_task.done():
        send_task.cancel()
        try:
            await send_task
        except (asyncio.CancelledError, TimeoutError):
            pass
    await client.stop()
```

## ListItem / Client-Side Expansion Pattern (Option B)
```python
# flatten_items_to_paths() in a2ui_transform.py
# Emits sentinel + field entries for each item:
# {"key": "/transactions/0", "valueString": "0"}          ← sentinel
# {"key": "/transactions/0/description", "valueString": "Buy AAPL"}
```
- `transform_to_operations()` accepts `arrays: dict | None = None` kwarg
- Falls back to `parsed_response.get("arrays")` if not explicitly passed
- Renderer embeds arrays in returned dict → `template_agent.py` needs no change
- `sanitize_components()` only strips `explicitList` children — `{path, componentId}` passes through unchanged
- `transform_to_path_bindings()` only touches `Text.literalString` — `ListItem` `{path: ...}` refs pass through unchanged
- `expand_templates()` is a no-op when `itemTemplate` absent — safe for new-style templates

## Non-Streaming Template Endpoint Pattern
```python
@app.post("/chat/template", response_model=AgentResponse)
async def chat_template(request: ChatRequest):
    intent = template_classify(request.message)
    if intent is None:
        return AgentResponse(text=random.choice(_TEMPLATE_FALLBACK_RESPONSES), ui_definition=None)
    rendered = _template_renderer.render(intent.template_id, intent.data_id)
    ops = template_transform_to_operations(rendered, suffix)
    text = next((op["data"]["text"] for op in ops if op["type"] == "text"), rendered["text"])
    # Build ui_definition: {surfaceId, root, components: {id: props}, dataModel: [...]}
    begin_op = next((op["data"]["beginRendering"] for op in ops if "beginRendering" in op.get("data", {})), None)
    if begin_op:
        all_components = {}
        for op in ops:
            if "surfaceUpdate" in op.get("data", {}):
                for entry in op["data"]["surfaceUpdate"]["components"]:
                    all_components[entry["id"]] = entry["component"]
        data_model_op = next((op["data"]["dataModelUpdate"] for op in ops if "dataModelUpdate" in op.get("data", {})), None)
        ui_definition = {"surfaceId": begin_op["surfaceId"], "root": begin_op["root"], "components": all_components}
        if data_model_op: ui_definition["dataModel"] = data_model_op["contents"]
    return AgentResponse(text=text, ui_definition=ui_definition)
```
- `components` keys are component IDs; values are the `componentProperties` dict (NOT wrapped in `{componentProperties: ...}`)
- `dataModel` is the flat list of `{key, valueString}` path-binding entries

## JSONL Template Endpoint Pattern
```python
# op["data"] for text ops is already {"text": "string"} — do NOT re-wrap
for op in text_ops:
    yield {"data": json.dumps(op["data"])}   # emits {"text": "..."}
# a2ui ops: op["data"] is {"surfaceUpdate": {...}} / {"dataModelUpdate": {...}} / {"beginRendering": {...}}
for op in surface_ops + dmu_ops + br_ops:
    yield {"data": json.dumps(op["data"])}
yield {"data": json.dumps({"done": {}})}
```
- Task description said `{"text": op["data"]}` but op["data"] is ALREADY `{"text": "..."}` — that creates double-wrap
- Fallback text is a plain Python string, so `json.dumps({"text": fallback_str})` is correct there
- JSONL order: text → surfaceUpdate(s) → dataModelUpdate → beginRendering → done

## Unit Testing Pattern (agent/)
```bash
# Run with the agent venv (pytest not installed system-wide on macOS)
cd agent
.venv/bin/pip install pytest -q
.venv/bin/python -m pytest test_agent.py -v
```
- TemplateRenderer paths must be absolute or relative-to-__file__ when pytest runs from repo root:
  ```python
  _DIR = Path(__file__).parent
  r = TemplateRenderer(templates_dir=str(_DIR / "templates"), data_dir=str(_DIR / "data"))
  ```
- account_balances data is ALL SCALARS (no arrays) — arrays key absent from rendered result
- transaction_history data has `"transactions"` list → arrays key present → flat path entries
- chunk_components() format: `{"id": comp_id, "component": {WidgetType: {...}}}` (no componentProperties wrap)
- assemble_components_from_ops() adds the componentProperties wrap: `{id: {"componentProperties": entry["component"]}}`

## agent.sh Quirks
- `lsof -ti:8000` returns QEMU/emulator PIDs that have CLOSED connections → false positive "already running" error
- Workaround: `lsof -i :8000 -sTCP:LISTEN` to check only listening processes, or kill stale PIDs manually then start with `nohup python agent.py &`
- The unified server (`agent/agent.py`) serves ALL routes: `/chat/stream` (LLM) + `/chat/stream/template` (SSE template) + `/chat/template` (sync) + `/chat/stream/template/jsonl` (JSONL)
- `./agent.sh start` is the single command — no `llm`/`template` argument needed
- If `./agent.sh start` fails with "already running (unknown(56339))", the real server was killed and QEMU holds a stale CLOSED connection — use `nohup` fallback

## Session Log
| Date | Pattern Learned |
|---|---|
| 2026-04-09 | PermissionHandler must import from copilot.session, not copilot |
| 2026-04-09 | Template JSON must be dict with templateId, not bare array |
| 2026-04-09 | Copilot SDK: use mode="append" not "replace"; omit model= pin; always guard send_task.cancel() in finally |
| 2026-04-10 | ListItem/Option B: embed arrays in render() result dict; transform_to_operations() reads parsed_response["arrays"] as fallback — no agent.py changes needed |
| 2026-04-11 | Template engine merged into agent/: intent_router.py, template_renderer.py, a2ui_transform.py + templates/ + data/ live in agent/; Path(__file__).parent resolves correctly |
| 2026-04-11 | Flat-data templates: use ListItem with {"literalString": "${key}"} — renderer's _substitute_ui_placeholders() recurses all strings, so literalString in ANY component type gets substituted |
| 2026-04-11 | account_balances template was corrupted to "A2UI Setup Flow Diagram" — correct version uses ListItem rows for chk/sav/brok/roth/k401 accounts with BANKING/INVESTING section headers |
| 2026-04-11 | POST /chat/template (sync): build ui_definition from ops — surfaceUpdate entries have {id, component} pairs; merge all chunks; shape is {surfaceId, root, components, dataModel} |
| 2026-04-11 | dataModelUpdate merge: use extend() loop over ALL ops (not next()); same pattern as surfaceUpdate; yields 30 entries for brokerage_activity |
| 2026-04-11 | JSONL text op: op["data"] is already {"text": "..."} dict — never re-wrap as {"text": op["data"]} or you get double-nesting |
| 2026-04-12 | Unit tests for agent/: use .venv/bin/python -m pytest; use Path(__file__).parent for template/data dirs; 33 tests across IntentRouter/TemplateRenderer/A2UiTransform/SyncEndpointFormat |
| 2026-04-10 | v0.8.7 retro: sync componentProperties wrap was missing in non-streaming endpoint; added test_agent.py with TestSyncEndpointFormat to catch this class of bug without needing an Android device |
| 2026-04-13 | "Invalid component: root" = server running OLD code, fix already on disk but not loaded — restart with nohup; QEMU CLOSED connections cause false-positive "already running" in agent.sh |
| 2026-04-14 | Consolidated template engine into agent/: migrated 33 unit tests into agent/test_agent.py; removed TEMPLATE_PID/TEMPLATE_LOG from agent.sh; run unit tests with `python -m pytest test_agent.py -m "not integration" -v` |
| 2026-04-15 | TestChatStream/TestJsonlStream fixtures must call template endpoints (/chat/stream/template, /chat/stream/template/jsonl) not LLM endpoints — LLM requires CLI server at localhost:4321; template endpoints are deterministic and token-free |
| 2026-04-15 | SSE template stream done event: emit data: {"done": {}} not data: {} so _stream_jsonl_messages() helper (which only reads data: lines) can detect done by key; add pytest.ini with markers = integration to silence PytestUnknownMarkWarning |
