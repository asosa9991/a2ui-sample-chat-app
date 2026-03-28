# Copy-Paste Ready Code Examples

All code snippets ready to use. Modify API keys and URLs as needed.

---

## Python FastAPI Agent (Complete Working Example)

**File: `agent.py`**

```python
#!/usr/bin/env python3
"""
A2UI Agent Backend - Stream AI-generated UIs to Android client.

Usage:
    export ANTHROPIC_API_KEY="sk-ant-..."
    python agent.py
    
Test:
    curl -X POST "http://localhost:8000/chat?message=Show+hello+world"
"""

import json
import re
import asyncio
import logging
from typing import List, Dict, Any

from fastapi import FastAPI, HTTPException
from fastapi.responses import StreamingResponse
from fastapi.middleware.cors import CORSMiddleware
import anthropic

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Initialize FastAPI app
app = FastAPI(title="A2UI Agent Backend")

# Add CORS for local development
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Initialize Anthropic client
client = anthropic.Anthropic()

# A2UI System Prompt
A2UI_SYSTEM_PROMPT = """
You are a UI generation assistant. Your job is to create rich, interactive user interfaces using the A2UI (Agent-to-User Interface) protocol.

## Your Task

When the user asks for information or performs an action, you must:

1. **Understand** the user's intent
2. **Generate** A2UI protocol operations as a JSON array
3. **Output format:**
   - Respond ONLY with a JSON array of A2UI message objects
   - Each element must be a valid A2UI v0.8 operation
   - No markdown, no explanation, no preamble - just the JSON array

## A2UI Protocol (v0.8)

You can use these operations:

### beginRendering
Initializes a new surface. Always send this first.

```json
{
  "beginRendering": {
    "surfaceId": "unique_id",
    "root": "root_component_id"
  }
}
```

### surfaceUpdate
Defines UI components in an adjacency list (flat structure).

```json
{
  "surfaceUpdate": {
    "surfaceId": "surface_id",
    "components": [
      {
        "id": "component_id",
        "component": {
          "ComponentType": {
            "property": value
          }
        }
      }
    ]
  }
}
```

### dataModelUpdate
Sets reactive data that components bind to.

```json
{
  "dataModelUpdate": {
    "surfaceId": "surface_id",
    "contents": [
      {"key": "fieldName", "valueString": "value"},
      {"key": "age", "valueNumber": 30}
    ]
  }
}
```

## Component Types

### Content
- Text - Static or bound text
- Image - URL-based images
- Icon - Material Design icons
- Divider - Separator

### Layout
- Column - Vertical stack
- Row - Horizontal stack
- Card - Container with styling
- List - Dynamic list with template

### Input
- Button - Clickable action
- TextField - Text input
- CheckBox - Boolean toggle
- MultipleChoice - Select from options

## Example

User query: "Show my account balance"

Response:

```json
[
  {
    "beginRendering": {
      "surfaceId": "account",
      "root": "root"
    }
  },
  {
    "surfaceUpdate": {
      "surfaceId": "account",
      "components": [
        {
          "id": "root",
          "component": {
            "Column": {
              "children": {"explicitList": ["title", "card"]}
            }
          }
        },
        {
          "id": "title",
          "component": {
            "Text": {
              "text": {"literalString": "Account Balance"},
              "usageHint": "h2"
            }
          }
        },
        {
          "id": "card",
          "component": {
            "Card": {
              "child": "amount"
            }
          }
        },
        {
          "id": "amount",
          "component": {
            "Text": {
              "text": {"path": "/balance"},
              "usageHint": "h1"
            }
          }
        }
      ]
    }
  },
  {
    "dataModelUpdate": {
      "surfaceId": "account",
      "contents": [
        {"key": "balance", "valueString": "$48,291.73"}
      ]
    }
  }
]
```

## Rules

1. **Always start with beginRendering** (exactly once per surface)
2. **Component IDs must be unique** within a surface
3. **Use {"path": "/..."} for reactive data** (will update when data changes)
4. **Use {"literalString": "..."} for static text**
5. **For children, use {"explicitList": ["id1", "id2"]}**
6. **Validate JSON structure** - must be valid JSON
7. **No circular references** - component cannot be its own child
8. **Return ONLY JSON** - no markdown formatting, no explanations

Now, generate A2UI operations for the user's request. Remember to return ONLY the JSON array.
"""


def call_llm(user_message: str) -> str:
    """Call Claude to generate A2UI operations."""
    logger.info(f"Calling LLM with message: {user_message}")
    
    message = client.messages.create(
        model="claude-3-5-sonnet-20241022",
        max_tokens=2048,
        system=A2UI_SYSTEM_PROMPT,
        messages=[
            {"role": "user", "content": user_message}
        ]
    )
    
    return message.content[0].text


def extract_json_from_output(llm_output: str) -> str:
    """Extract JSON array from LLM output."""
    # Try to find JSON array in output
    json_match = re.search(r'\[.*\]', llm_output, re.DOTALL)
    if json_match:
        return json_match.group(0)
    
    # Fallback: assume entire output is JSON
    return llm_output.strip()


def validate_a2ui_operations(operations: Any) -> bool:
    """Basic validation of A2UI operations."""
    if not isinstance(operations, list):
        logger.error("Operations must be a list")
        return False
    
    if len(operations) == 0:
        logger.error("Operations list is empty")
        return False
    
    # Check first operation is beginRendering
    first_op = operations[0]
    if "beginRendering" not in first_op:
        logger.warning("First operation should be beginRendering")
    
    for op in operations:
        if not isinstance(op, dict):
            logger.error(f"Operation is not a dict: {op}")
            return False
        
        # Must have exactly one top-level key
        keys = list(op.keys())
        if len(keys) != 1:
            logger.error(f"Operation must have exactly one key: {keys}")
            return False
        
        op_type = keys[0]
        if op_type not in ["beginRendering", "surfaceUpdate", "dataModelUpdate", "deleteSurface"]:
            logger.error(f"Unknown operation type: {op_type}")
            return False
    
    return True


def fallback_operation(message: str) -> List[Dict[str, Any]]:
    """Fallback: simple text response if A2UI generation fails."""
    return [
        {"beginRendering": {"surfaceId": "error", "root": "text"}},
        {"surfaceUpdate": {"surfaceId": "error", "components": [
            {"id": "text", "component": {"Text": {"text": {"literalString": message}}}}
        ]}}
    ]


@app.post("/chat")
async def chat(message: str):
    """
    Stream A2UI operations as Server-Sent Events (JSONL).
    
    Query params:
        message (str): User query
    
    Returns:
        Streaming response with JSONL (one A2UI operation per line)
    
    Example:
        curl -X POST "http://localhost:8000/chat?message=Show+hello+world"
    """
    
    if not message or message.strip() == "":
        raise HTTPException(status_code=400, detail="Message cannot be empty")
    
    async def generate_stream():
        try:
            # Call LLM
            logger.info(f"Generating A2UI operations for: {message}")
            llm_output = call_llm(message)
            logger.info(f"LLM output length: {len(llm_output)}")
            
            # Extract JSON
            json_str = extract_json_from_output(llm_output)
            
            # Parse JSON
            try:
                operations = json.loads(json_str)
            except json.JSONDecodeError as e:
                logger.error(f"JSON decode error: {e}")
                logger.error(f"JSON string: {json_str[:200]}")
                operations = fallback_operation(f"Error parsing UI: {str(e)}")
            
            # Validate
            if not validate_a2ui_operations(operations):
                logger.warning("Validation failed, using fallback")
                operations = fallback_operation("Failed to generate valid UI")
            
            # Stream each operation as JSONL
            logger.info(f"Streaming {len(operations)} operations")
            for i, op in enumerate(operations):
                # SSE format: "data: <json>\n\n"
                yield f"data: {json.dumps(op)}\n\n"
                await asyncio.sleep(0.01)  # Small delay for perceived streaming
            
            logger.info("Streaming complete")
        
        except Exception as e:
            logger.error(f"Error in generate_stream: {e}", exc_info=True)
            error_op = fallback_operation(f"Server error: {str(e)}")
            for op in error_op:
                yield f"data: {json.dumps(op)}\n\n"
    
    return StreamingResponse(
        generate_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
            "Content-Type": "text/event-stream; charset=utf-8"
        }
    )


@app.post("/event")
async def handle_event(event: Dict[str, Any]):
    """
    Handle UserActionEvent or DataChangeEvent from client.
    
    In a real agent, process the event and decide what UI to show next.
    """
    logger.info(f"Received event: {event}")
    return {"status": "ok"}


@app.get("/health")
async def health():
    """Health check endpoint."""
    return {"status": "ok", "service": "a2ui-agent"}


@app.get("/")
async def root():
    """Root endpoint with API documentation."""
    return {
        "service": "A2UI Agent Backend",
        "version": "1.0",
        "endpoints": {
            "POST /chat": "Generate A2UI operations (query param: message)",
            "POST /event": "Handle user events",
            "GET /health": "Health check",
            "GET /": "This help message"
        },
        "example": "curl -X POST 'http://localhost:8000/chat?message=Show+hello+world'"
    }


if __name__ == "__main__":
    import uvicorn
    
    # Run with: uvicorn agent:app --reload --host 0.0.0.0 --port 8000
    uvicorn.run(
        app,
        host="0.0.0.0",
        port=8000,
        log_level="info"
    )
```

**Setup:**
```bash
pip install fastapi uvicorn anthropic pydantic
export ANTHROPIC_API_KEY="sk-ant-..."
python agent.py
```

**Test:**
```bash
curl -X POST "http://localhost:8000/chat?message=Show+hello+world"
```

---

## Android Kotlin: RealChatRepository

**File: `RealChatRepository.kt`**

```kotlin
package com.example.a2ui.chat.data.repository

import android.util.Log
import com.contextable.a2ui4k.model.UiDefinition
import com.contextable.a2ui4k.state.SurfaceStateManager
import com.example.a2ui.chat.domain.model.Message
import com.example.a2ui.chat.domain.model.Sender
import com.example.a2ui.chat.domain.repository.ChatRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.parseToJsonElement
import java.util.Calendar
import java.util.UUID
import kotlin.random.Random

class RealChatRepository(
    private val agentBaseUrl: String = "http://10.0.2.2:8000"
) : ChatRepository {
    
    private val httpClient = HttpClient()
    private val stateManager = SurfaceStateManager()
    private val tag = "RealChatRepository"
    
    override suspend fun sendMessage(userMessage: String): Message {
        val messageId = UUID.randomUUID().toString()
        
        return try {
            Log.d(tag, "Sending message to agent: $userMessage")
            
            // Call agent backend via HTTP GET with SSE
            val response = httpClient.get("$agentBaseUrl/chat?message=${userMessage.encodeURLComponent()}") {
                contentType(ContentType.Application.Json)
            }
            
            Log.d(tag, "Received response from agent")
            
            // Parse SSE stream (Server-Sent Events as JSONL)
            response.bodyAsChannel().use { channel ->
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: continue
                    
                    if (line.startsWith("data: ")) {
                        val jsonStr = line.substring(6)
                        
                        try {
                            // Parse operation
                            val json = parseToJsonElement(jsonStr)
                            val obj = json.jsonObject
                            
                            Log.d(tag, "Processing operation: ${obj.keys}")
                            
                            // Route to appropriate handler
                            when {
                                obj.containsKey("beginRendering") -> {
                                    Log.d(tag, "Processing beginRendering")
                                    stateManager.processSnapshot(messageId, json)
                                }
                                obj.containsKey("surfaceUpdate") -> {
                                    Log.d(tag, "Processing surfaceUpdate")
                                    stateManager.processDelta(messageId, jsonArray(json))
                                }
                                obj.containsKey("dataModelUpdate") -> {
                                    Log.d(tag, "Processing dataModelUpdate")
                                    stateManager.processDelta(messageId, jsonArray(json))
                                }
                                obj.containsKey("deleteSurface") -> {
                                    Log.d(tag, "Processing deleteSurface")
                                    stateManager.processDelta(messageId, jsonArray(json))
                                }
                                obj.containsKey("error") -> {
                                    Log.e(tag, "Agent error: ${obj["error"]}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(tag, "Error parsing operation: ${e.message}", e)
                        }
                    }
                }
            }
            
            Log.d(tag, "Finished receiving stream")
            
            // Get the generated UI from state manager
            val surfaces = stateManager.getSurfaces()
            val uiDefinition = surfaces.values.firstOrNull()
            
            Log.d(tag, "UI Definition: $uiDefinition")
            
            Message(
                id = messageId,
                content = "UI generated from agent",
                sender = Sender.AI,
                timestamp = System.currentTimeMillis(),
                isLoading = false,
                uiDefinition = uiDefinition
            )
        } catch (e: Exception) {
            Log.e(tag, "Error communicating with agent: ${e.message}", e)
            
            // Fallback: error message
            Message(
                id = UUID.randomUUID().toString(),
                content = "Error: ${e.message}",
                sender = Sender.AI,
                timestamp = System.currentTimeMillis(),
                isLoading = false
            )
        }
    }
    
    override fun getGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "morning"
            hour < 17 -> "afternoon"
            else -> "evening"
        }
    }
}

// Extension function to encode URL components
private fun String.encodeURLComponent(): String {
    return java.net.URLEncoder.encode(this, "UTF-8")
}
```

**Usage in `ChatViewModel.kt`:**

```kotlin
companion object {
    val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            // Use real agent instead of mock
            val repository = if (BuildConfig.DEBUG && false) {  // Set true to use mock
                MockChatRepository()
            } else {
                RealChatRepository("http://10.0.2.2:8000")  // Agent URL
            }
            
            val useCase = SendMessageUseCase(repository)
            return ChatViewModel(useCase, repository) as T
        }
    }
}
```

---

## Minimal FastAPI Agent (35 lines)

Perfect for quick testing:

```python
from fastapi import FastAPI
from fastapi.responses import StreamingResponse
import anthropic
import json
import re

app = FastAPI()
client = anthropic.Anthropic()

PROMPT = """Generate ONLY a JSON array of A2UI operations. No explanation.
Always start with beginRendering. Use surfaceUpdate for components, dataModelUpdate for data.
Available widgets: Text, Button, Column, Row, Card, TextField, CheckBox, etc."""

@app.post("/chat")
async def chat(message: str):
    async def stream():
        resp = client.messages.create(
            model="claude-3-5-sonnet-20241022",
            max_tokens=1024,
            system=PROMPT,
            messages=[{"role": "user", "content": message}]
        )
        output = resp.content[0].text
        json_str = re.search(r'\[.*\]', output, re.DOTALL).group(0)
        for op in json.loads(json_str):
            yield f"data: {json.dumps(op)}\n\n"
    
    return StreamingResponse(stream(), media_type="text/event-stream")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
```

---

## Test the Agent (Python Script)

```python
#!/usr/bin/env python3
"""Test agent output."""

import anthropic
import json
import re

PROMPT = """Generate ONLY JSON array of A2UI v0.8 operations...
[full prompt from above]"""

client = anthropic.Anthropic()

test_queries = [
    "Show hello world",
    "Create a card with my name Alice and email alice@example.com",
    "Show a list of 3 colors: red, green, blue",
    "Create a text input form",
]

for query in test_queries:
    print(f"\n{'='*60}")
    print(f"Query: {query}")
    print(f"{'='*60}")
    
    resp = client.messages.create(
        model="claude-3-5-sonnet-20241022",
        max_tokens=1024,
        system=PROMPT,
        messages=[{"role": "user", "content": query}]
    )
    
    output = resp.content[0].text
    
    try:
        json_match = re.search(r'\[.*\]', output, re.DOTALL)
        if not json_match:
            print("❌ No JSON found")
            continue
        
        ops = json.loads(json_match.group(0))
        print(f"✅ {len(ops)} operations")
        for i, op in enumerate(ops):
            op_type = list(op.keys())[0]
            print(f"   {i+1}. {op_type}")
    except json.JSONDecodeError as e:
        print(f"❌ JSON error: {e}")
```

---

## Run Everything Together

**Terminal 1: Start Agent**
```bash
export ANTHROPIC_API_KEY="sk-ant-..."
python agent.py
```

**Terminal 2: Test Agent**
```bash
curl -X POST "http://localhost:8000/chat?message=Show+hello+world"
```

**Terminal 3: Run Android Emulator**
```bash
# In Android Studio or:
emulator -avd Pixel_6_API_30 &
./gradlew installDebug
```

**Connect in Android App:**
- Update `ChatViewModel.Factory` to use `RealChatRepository("http://10.0.2.2:8000")`
- Send message in app
- UI should render from agent!

---

That's it! Copy, paste, modify API keys, and go.

