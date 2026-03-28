import asyncio
import json
import os
import random
import string
import sys
from contextlib import asynccontextmanager
from typing import Optional

import uvicorn
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

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

        client = CopilotClient()
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
        max_tokens=4096,
        temperature=0.3,
    )

    return {"content": response.choices[0].message.content}


async def call_llm(message: str) -> dict:
    """Try copilot-sdk first, fall back to GitHub Models."""
    try:
        return await call_llm_copilot_sdk(message)
    except Exception as sdk_err:
        print(f"[copilot-sdk] unavailable ({sdk_err}), falling back to GitHub Models API")
        return await call_llm_github_models(message)


def parse_agent_response(raw_content: str, surface_suffix: str) -> AgentResponse:
    """Parse LLM JSON output into AgentResponse."""
    try:
        # Strip markdown code blocks if LLM wrapped them
        content = raw_content.strip()
        if content.startswith("```"):
            content = content.split("```")[1]
            if content.startswith("json"):
                content = content[4:]
            content = content.strip()

        parsed = json.loads(content)
        text = parsed.get("text", "Here is the information you requested.")
        ui_def = parsed.get("uiDefinition") or parsed.get("ui_definition")

        # Ensure surfaceId is unique
        if ui_def and isinstance(ui_def, dict):
            ui_def["surfaceId"] = f"response_{surface_suffix}"

        return AgentResponse(text=text, ui_definition=ui_def)

    except (json.JSONDecodeError, KeyError) as e:
        # LLM returned plain text — wrap it
        print(f"[parse] JSON parse failed ({e}), treating as plain text")
        return AgentResponse(text=raw_content.strip(), ui_definition=None)


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


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8000))
    print(f"Starting A2UI Agent on http://localhost:{port}")
    uvicorn.run("agent:app", host="0.0.0.0", port=port, reload=True)
