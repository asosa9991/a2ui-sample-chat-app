import asyncio
import json
import os
import random
import string
import sys
from contextlib import asynccontextmanager
from typing import AsyncGenerator, Optional

import uvicorn
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from sse_starlette.sse import EventSourceResponse

from system_prompt import A2UI_SYSTEM_PROMPT

load_dotenv()


# ─── Request / Response Models ────────────────────────────────────────────────

class ChatRequest(BaseModel):
    message: str
    session_id: Optional[str] = None


class AgentResponse(BaseModel):
    text: str
    ui_definition: Optional[dict] = None
    error: Optional[str] = None


# ─── LLM Backend Selection ────────────────────────────────────────────────────

def _random_suffix(n: int = 6) -> str:
    return "".join(random.choices(string.ascii_lowercase + string.digits, k=n))


async def call_llm_copilot_sdk(message: str) -> dict:
    """Call LLM via github-copilot-sdk."""
    try:
        from copilot import CopilotClient
        from copilot.session import PermissionHandler

        client = CopilotClient({
         "cli_url": "localhost:4321",
         "model": "Claude Sonnet 4.6 (copilot)",
         "streaming": True,
      })
        await client.start()

        try:
            session = await client.create_session(
                on_permission_request=PermissionHandler.approve_all,
                system_message={"content": A2UI_SYSTEM_PROMPT},
            )
            response = await session.send_and_wait({"prompt": message}, timeout=30000)
            await session.disconnect()
            return {"content": response.data.content if response else "{}"}
        finally:
            await client.stop()

    except Exception as e:
        raise RuntimeError(f"copilot-sdk error: {e}") from e


async def call_llm_github_models(message: str) -> dict:
    """Fallback: call GitHub Models API (OpenAI-compatible, needs GITHUB_MODELS_TOKEN)."""
    from openai import AsyncOpenAI

    token = os.environ.get("GITHUB_MODELS_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if not token:
        raise RuntimeError("No GITHUB_MODELS_TOKEN or GITHUB_TOKEN set")

    client = AsyncOpenAI(
        base_url="https://models.inference.ai.azure.com",
        api_key=token,
    )

    response = await client.chat.completions.create(
        model=os.environ.get("MODEL", "gpt-4o"),
        messages=[
            {"role": "system", "content": A2UI_SYSTEM_PROMPT},
            {"role": "user", "content": message},
        ],
        response_format={"type": "json_object"},
        max_tokens=16384,
        temperature=0.3,
    )

    return {"content": response.choices[0].message.content}


async def stream_llm_github_models(message: str) -> AsyncGenerator[str, None]:
    """Stream LLM tokens via OpenAI streaming API. Yields individual tokens, returns full text."""
    from openai import AsyncOpenAI

    token = os.environ.get("GITHUB_MODELS_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if not token:
        raise RuntimeError("No GITHUB_MODELS_TOKEN or GITHUB_TOKEN set")

    client = AsyncOpenAI(
        base_url="https://models.inference.ai.azure.com",
        api_key=token,
    )

    # Note: streaming is incompatible with response_format=json_object,
    # so we stream raw text and parse JSON from the accumulated result at the end.
    stream = await client.chat.completions.create(
        model=os.environ.get("MODEL", "gpt-4o"),
        messages=[
            {"role": "system", "content": A2UI_SYSTEM_PROMPT},
            {"role": "user", "content": message},
        ],
        max_tokens=16384,
        temperature=0.3,
        stream=True,
    )

    async for chunk in stream:
        if chunk.choices and chunk.choices[0].delta.content:
            yield chunk.choices[0].delta.content


async def call_llm(message: str) -> dict:
    """Try copilot-sdk first, fall back to GitHub Models."""
    try:
        return await call_llm_copilot_sdk(message)
    except Exception as sdk_err:
        print(f"[copilot-sdk] unavailable ({sdk_err}), falling back to GitHub Models API")
        return await call_llm_github_models(message)


def parse_agent_response(raw_content: str, surface_suffix: str) -> AgentResponse:
    """Parse LLM JSON output into AgentResponse with truncation recovery."""
    content = raw_content.strip()

    # Strip markdown code blocks if LLM wrapped them
    if content.startswith("```"):
        content = content.split("```")[1]
        if content.startswith("json"):
            content = content[4:]
        content = content.strip()

    # First try: parse as valid JSON
    try:
        parsed = json.loads(content)
        text = parsed.get("text", "Here is the information you requested.")
        ui_def = parsed.get("uiDefinition") or parsed.get("ui_definition")

        if ui_def and isinstance(ui_def, dict):
            ui_def["surfaceId"] = f"response_{surface_suffix}"

        return AgentResponse(text=text, ui_definition=ui_def)
    except (json.JSONDecodeError, KeyError):
        pass

    # Second try: extract "text" field from truncated JSON
    try:
        import re
        text_match = re.search(r'"text"\s*:\s*"((?:[^"\\]|\\.)*)"', content)
        if text_match:
            extracted_text = text_match.group(1).replace('\\"', '"').replace('\\n', '\n')
            print(f"[parse] Recovered text from truncated JSON: '{extracted_text[:60]}...'")
            return AgentResponse(text=extracted_text, ui_definition=None)
    except Exception:
        pass

    # Last resort: treat entire content as plain text
    print(f"[parse] Could not parse response, treating as plain text")
    # If it looks like JSON, give a friendly message instead of showing raw JSON
    if content.startswith("{"):
        return AgentResponse(
            text="I generated the information but the response was too large to display. Please try asking for fewer items.",
            ui_definition=None,
        )
    return AgentResponse(text=content, ui_definition=None)


# ─── FastAPI App ──────────────────────────────────────────────────────────────

@asynccontextmanager
async def lifespan(app: FastAPI):
    print("A2UI Agent Server starting...")
    yield
    print("A2UI Agent Server shutting down...")


app = FastAPI(title="A2UI Agent Server", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
async def health():
    return {"status": "ok", "service": "a2ui-agent"}


@app.post("/chat", response_model=AgentResponse)
async def chat(request: ChatRequest):
    if not request.message.strip():
        raise HTTPException(status_code=400, detail="Message cannot be empty")

    suffix = _random_suffix()
    print(f"[chat] message='{request.message[:60]}...' suffix={suffix}")

    try:
        llm_result = await call_llm(request.message)
        response = parse_agent_response(llm_result["content"], suffix)
        print(f"[chat] has_ui={response.ui_definition is not None}")
        return response
    except Exception as e:
        print(f"[chat] ERROR: {e}")
        return AgentResponse(
            text="Sorry, I encountered an error. Please try again.",
            ui_definition=None,
            error=str(e),
        )


def extract_data_model(components: dict) -> list[dict]:
    """Extract literal values from components to build a DataModel."""
    contents = []
    for comp_id, comp_data in components.items():
        props = comp_data.get("componentProperties", {})
        for widget_type, config in props.items():
            if widget_type == "Text":
                text_val = config.get("text", {})
                if isinstance(text_val, dict) and "literalString" in text_val:
                    contents.append({
                        "key": comp_id,
                        "valueString": text_val["literalString"],
                    })
    return contents


def transform_to_operations(parsed_response: dict, surface_suffix: str) -> list[dict]:
    """Transform LLM JSON response into A2UI v0.8 protocol operations."""
    text = parsed_response.get("text", "")
    ui_def = parsed_response.get("uiDefinition") or parsed_response.get("ui_definition")
    surface_id = f"response_{surface_suffix}"

    operations = []

    if ui_def:
        root = ui_def.get("root", "root")
        components = ui_def.get("components", {})

        # 1. beginRendering
        operations.append({
            "type": "a2ui_op",
            "data": {"beginRendering": {"surfaceId": surface_id, "root": root}},
        })

        # 2. surfaceUpdate — transform componentProperties → component
        comp_list = []
        for comp_id, comp_data in components.items():
            props = comp_data.get("componentProperties", {})
            comp_list.append({"id": comp_id, "component": props})

        operations.append({
            "type": "a2ui_op",
            "data": {"surfaceUpdate": {"surfaceId": surface_id, "components": comp_list}},
        })

        # 3. dataModelUpdate — extract literal values from components
        data_contents = extract_data_model(components)
        if data_contents:
            operations.append({
                "type": "a2ui_op",
                "data": {"dataModelUpdate": {"surfaceId": surface_id, "path": "", "contents": data_contents}},
            })

    # 4. text event
    if text:
        operations.append({"type": "text", "data": {"text": text}})

    # 5. done
    operations.append({"type": "done", "data": {}})

    return operations


@app.post("/chat/stream")
async def chat_stream(request: ChatRequest):
    if not request.message.strip():
        raise HTTPException(status_code=400, detail="Message cannot be empty")

    suffix = _random_suffix()
    print(f"[chat/stream] message='{request.message[:60]}...' suffix={suffix}")

    async def event_generator():
        full_content = ""
        try:
            async for token in stream_llm_github_models(request.message):
                full_content += token

            # Parse accumulated content and transform to A2UI operations
            response = parse_agent_response(full_content, suffix)

            parsed = {"text": response.text}
            if response.ui_definition:
                parsed["uiDefinition"] = response.ui_definition

            operations = transform_to_operations(parsed, suffix)
            print(f"[chat/stream] emitting {len(operations)} ops, has_ui={response.ui_definition is not None}")
            for op in operations:
                yield {"event": op["type"], "data": json.dumps(op["data"])}

        except Exception as e:
            print(f"[chat/stream] ERROR: {e}")
            yield {"event": "text", "data": json.dumps({"text": f"Sorry, I encountered an error: {str(e)}"})}
            yield {"event": "done", "data": "{}"}

    return EventSourceResponse(event_generator())


class UiEventRequest(BaseModel):
    surface_id: str
    event_type: str  # "userAction" or "dataChange"
    name: Optional[str] = None
    source_component_id: Optional[str] = None
    path: Optional[str] = None
    value: Optional[str] = None
    context: Optional[dict] = None


@app.post("/event")
async def handle_event(request: UiEventRequest):
    print(f"[event] surface={request.surface_id} type={request.event_type} name={request.name} path={request.path}")
    return {"status": "received", "surface_id": request.surface_id}


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8000))
    print(f"Starting A2UI Agent on http://localhost:{port}")
    uvicorn.run("agent:app", host="0.0.0.0", port=port, reload=True)
