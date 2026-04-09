# Python Expert — Personal Cheatsheet

> Read at the start of every run. Update after every run with new patterns, gotchas, and fixes.

## Agent Management
```bash
./agent.sh start llm        # start LLM agent
./agent.sh start template   # start template agent
./agent.sh stop             # stop whichever is running
./agent.sh status           # show PID, port, last log lines
./agent.sh logs llm         # tail LLM agent log
./agent.sh setup llm        # create venv + pip install
```

## Venv Activation
```bash
source agent/.venv/bin/activate       # LLM agent
source agent-templates/.venv/bin/activate  # template agent
```

## LLM Agent — Key Files
- `agent/agent.py` — FastAPI server, SSE streaming, intent detection
- `agent/system_prompt.py` — A2UI widget schemas, instructions to LLM
- `agent/requirements.txt` — dependencies
- `agent/.env` — GITHUB_TOKEN or GITHUB_MODELS_TOKEN

## Template Agent — Key Files
- `agent-templates/template_agent.py` — FastAPI server
- `agent-templates/intent_router.py` — keyword-based intent classification
- `agent-templates/template_renderer.py` — loads templates, placeholder substitution
- `agent-templates/a2ui_transform.py` — expand, path bindings, sanitize, chunk pipeline
- `agent-templates/templates/*.json` — must be dict with `templateId` key (NOT bare array)
- `agent-templates/data/*.json` — mock data files

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
./agent.sh start llm && sleep 5
curl -s http://localhost:8000/health
ls -la <changed_file>  # confirm file exists
```

## Known Gotchas
- `a2ui-agent` is a local package — NOT on PyPI, comment it out in requirements.txt
- Stray `=1.6.0` file created by unquoted shell: `pip install pkg>=1.6.0` → redirect accident
- Template agent `.get("templateId")` requires dict format, not array

## Session Log
| Date | Pattern Learned |
|---|---|
| 2026-04-09 | PermissionHandler must import from copilot.session, not copilot |
| 2026-04-09 | Template JSON must be dict with templateId, not bare array |
