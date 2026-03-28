# Quick Start: Build Your A2UI AI Agent

**Full report:** `how-do-i-create-the-ai-agent-users-vijayakella-poc.md` (2,100 lines, 66 KB)

---

## TL;DR

You need to build a **server** that:
1. Receives user messages from Android app
2. Calls Claude/GPT-4/Gemini LLM with A2UI prompt
3. Streams JSON operations back to client
4. Client renders via `SurfaceStateManager` → `A2UISurface`

---

## 5-Minute Setup

### 1. Create Python Agent Backend

```bash
pip install fastapi uvicorn anthropic pydantic
```

**agent.py:**
```python
import json
import re
from fastapi import FastAPI
from fastapi.responses import StreamingResponse
import anthropic

app = FastAPI()
client = anthropic.Anthropic()

A2UI_SYSTEM_PROMPT = """
You are a UI generation assistant. Generate A2UI protocol JSON.

When the user asks for a UI:
1. Generate JSON array of A2UI operations
2. Return ONLY JSON, no explanation
3. Always start with beginRendering
4. Use surfaceUpdate for components
5. Use dataModelUpdate for data

Component types: Text, Button, Column, Row, Card, List, TextField, CheckBox, etc.

Example: User "Show hello world"

[
  {"beginRendering": {"surfaceId": "hello", "root": "text"}},
  {"surfaceUpdate": {"surfaceId": "hello", "components": [
    {"id": "text", "component": {"Text": {"text": {"literalString": "Hello!"}}}}
  ]}}
]
"""

@app.post("/chat")
async def chat(message: str):
    async def generate():
        try:
            response = client.messages.create(
                model="claude-3-5-sonnet-20241022",
                max_tokens=2048,
                system=A2UI_SYSTEM_PROMPT,
                messages=[{"role": "user", "content": message}]
            )
            
            output = response.content[0].text
            
            # Extract JSON array
            json_match = re.search(r'\[.*\]', output, re.DOTALL)
            json_str = json_match.group(0) if json_match else output
            operations = json.loads(json_str)
            
            # Stream as JSONL
            for op in operations:
                yield f"data: {json.dumps(op)}\n\n"
        except Exception as e:
            yield f"data: {json.dumps({'error': str(e)})}\n\n"
    
    return StreamingResponse(generate(), media_type="text/event-stream")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
```

**Run:**
```bash
export ANTHROPIC_API_KEY="sk-ant-..."
python agent.py
```

### 2. Update Android App

**Replace `MockChatRepository` usage in `ChatViewModel.kt`:**

```kotlin
// Old:
val repository = MockChatRepository()

// New:
val repository = RealChatRepository("http://10.0.2.2:8000")
```

### 3. Test

```bash
# Terminal 1: Run agent
python agent.py

# Terminal 2: Test endpoint
curl -X POST "http://localhost:8000/chat?message=Show%20my%20balance"

# Should output JSONL:
# data: {"beginRendering": {"surfaceId": "...", "root": "..."}}
# data: {"surfaceUpdate": {"surfaceId": "...", "components": [...]}}
# data: {"dataModelUpdate": {"surfaceId": "...", "contents": [...]}}
```

---

## Key A2UI Operations

### 1. `beginRendering` — Start Surface
```json
{
  "beginRendering": {
    "surfaceId": "unique_id",
    "root": "root_component_id"
  }
}
```

### 2. `surfaceUpdate` — Add Components
```json
{
  "surfaceUpdate": {
    "surfaceId": "surface_id",
    "components": [
      {
        "id": "text_1",
        "component": {
          "Text": {
            "text": {"literalString": "Hello"}
          }
        }
      }
    ]
  }
}
```

### 3. `dataModelUpdate` — Set Data
```json
{
  "dataModelUpdate": {
    "surfaceId": "surface_id",
    "contents": [
      {"key": "name", "valueString": "Alice"},
      {"key": "age", "valueNumber": 30}
    ]
  }
}
```

### 4. `deleteSurface` — Clean Up
```json
{
  "deleteSurface": {
    "surfaceId": "surface_id"
  }
}
```

---

## All 18 A2UI Widgets

| Category | Widgets |
|----------|---------|
| **Content** | Text, Image, Icon, Divider, Video, AudioPlayer |
| **Layout** | Column, Row, List, Card, Tabs, Modal |
| **Input** | Button, TextField, CheckBox, Slider, MultipleChoice, DateTimeInput |

---

## Data Binding (Reactive)

**Literal value:**
```json
{"text": {"literalString": "Fixed text"}}
```

**Data-bound (updates automatically):**
```json
{"text": {"path": "/user/name"}}
```

When you update `/user/name` in `dataModelUpdate`, text automatically re-renders.

---

## Common Use Cases

### Show Data
1. Create Text widgets with `{"path": "/..."}` bindings
2. Send dataModelUpdate with values

### Collect Input
1. Create TextField/CheckBox/MultipleChoice widgets
2. Bind to `/form/fieldName` paths
3. Client sends DataChangeEvent when user modifies field

### Dynamic Lists
1. Use List widget with template: `{"template": {"dataBinding": "/items", "componentId": "item-card"}}`
2. Data becomes array in dataModelUpdate: `[{...item1...}, {...item2...}]`

---

## Prompt Engineering Tips

✅ **Do:**
- Provide exact JSON examples
- List explicit rules ("Component IDs must be unique")
- Use specific widget types (Column for vertical, Row for horizontal)
- Bind reactive data with `{"path": "/..."}`

❌ **Don't:**
- Provide vague instructions
- Expect the LLM to invent widget names
- Mix literal and path bindings randomly
- Create circular component references

---

## Testing Checklist

- [ ] Agent backend runs on `http://localhost:8000/chat`
- [ ] Single query produces valid JSONL output
- [ ] Operations include `beginRendering` first
- [ ] All component IDs are unique within surface
- [ ] Data bindings use either `literalString` or `path`, not both
- [ ] Android app makes HTTP request to agent
- [ ] SSE stream is parsed and fed to `SurfaceStateManager`
- [ ] UI renders correctly on Android screen

---

## Next Steps

1. **Iterate on prompt** — Test with various queries, refine examples
2. **Add history** — Store conversation context
3. **Handle events** — Implement event handler for user interactions
4. **Add tools** — Connect to databases, APIs
5. **Optimize latency** — Consider WebSocket instead of SSE
6. **Production** — Add auth, rate limiting, error handling

---

## Resources

- **Full A2UI Spec:** https://github.com/google/A2UI
- **a2ui-4k Library:** https://github.com/Contextable/a2ui-4k
- **Anthropic Claude API:** https://docs.anthropic.com
- **OpenAI GPT-4 API:** https://platform.openai.com/docs
- **Google Gemini API:** https://ai.google.dev

---

**Status:** Ready to implement! Start with the Python agent, test the endpoint, then integrate with Android app.

