"""
Deterministic Template-Based A2UI Agent
Serves pre-approved A2UI templates via SSE — no LLM, no API keys.
Drop-in replacement for agent/agent.py on port 8000.
"""
import asyncio
import json
import logging
import os
import random
import time
from typing import Optional

import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from sse_starlette.sse import EventSourceResponse

from a2ui_transform import transform_to_operations, _random_suffix
from intent_router import classify
from template_renderer import TemplateRenderer

# ── Logging ───────────────────────────────────────────────
logging.basicConfig(
    level=logging.DEBUG,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger("template_agent")

# ── Pydantic models (match agent/agent.py exactly) ───────
class ChatRequest(BaseModel):
    message: str
    session_id: Optional[str] = None

class UiEventRequest(BaseModel):
    surface_id: str
    event_type: str  # "userAction", "dataChange", "feedback"
    name: Optional[str] = None
    source_component_id: Optional[str] = None
    path: Optional[str] = None
    value: Optional[str] = None
    context: Optional[dict] = None

# ── App setup ─────────────────────────────────────────────
app = FastAPI(title="A2UI Template Agent", version="1.0.0",
              description="Deterministic A2UI agent using pre-approved templates")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── Load templates and data at startup ────────────────────
_BASE_DIR = os.path.dirname(os.path.abspath(__file__))
renderer = TemplateRenderer(
    templates_dir=os.path.join(_BASE_DIR, "templates"),
    data_dir=os.path.join(_BASE_DIR, "data"),
)

# ── Fallback text responses ──────────────────────────────
FALLBACK_RESPONSES = [
    "I can help you with account balances, transaction history, and brokerage activity. Try asking about one of those!",
    "I'm a template-based agent. Try asking about your last transactions, account balances, or brokerage activity.",
    "I don't have a template for that query. Try: 'Show my last transactions' or 'What are my account balances?'",
]

# ── Endpoints ─────────────────────────────────────────────

@app.get("/health")
async def health():
    """Health check endpoint."""
    return {
        "status": "ok",
        "service": "a2ui-template-agent",
        "mode": "deterministic",
        "templates": renderer.get_loaded_templates(),
        "data": renderer.get_loaded_data(),
    }

@app.post("/chat/stream")
async def chat_stream(request: ChatRequest):
    """SSE streaming endpoint — compatible with RealChatRepository.kt."""
    if not request.message.strip():
        raise HTTPException(status_code=400, detail="Message cannot be empty")

    suffix = _random_suffix()
    t0 = time.time()
    logger.info("[chat/stream] message='%.60s' suffix=%s", request.message, suffix)

    async def event_generator():
        try:
            # 1. Classify intent
            intent = classify(request.message)

            if intent is None:
                # No template match — plain text response
                fallback = random.choice(FALLBACK_RESPONSES)
                logger.info("[chat/stream] no intent match → fallback text")
                yield {"event": "text", "data": json.dumps({"text": fallback})}
                yield {"event": "done", "data": "{}"}
                return

            logger.info("[chat/stream] intent=%s confidence=%s", intent.template_id, intent.confidence)

            # 2. Render template with data
            rendered = renderer.render(intent.template_id, intent.data_id)
            if rendered is None:
                logger.error("[chat/stream] template render failed for %s", intent.template_id)
                yield {"event": "text", "data": json.dumps({"text": "Sorry, template rendering failed."})}
                yield {"event": "done", "data": "{}"}
                return

            # 3. Transform to A2UI operations
            operations = transform_to_operations(rendered, suffix)
            elapsed = time.time() - t0
            logger.info("[chat/stream] rendered in %.3fs, %d ops, template=%s",
                       elapsed, len(operations), intent.template_id)

            # 4. Stream SSE events
            # Emit text first (matches agent.py line 640-641 behaviour)
            yield {"event": "text", "data": json.dumps({"text": rendered["text"]})}
            await asyncio.sleep(0.1)  # Small gap before UI operations

            # Emit A2UI operations (skip the text op from transform_to_operations
            # to avoid double-emit — the Android client accumulates text)
            for op in operations:
                if op["type"] == "text":
                    continue  # Already emitted above
                yield {"event": op["type"], "data": json.dumps(op["data"])}
                if op["type"] == "a2ui_op":
                    await asyncio.sleep(0.15)  # 150ms between A2UI ops for progressive rendering

        except Exception as e:
            logger.error("[chat/stream] error: %s", e, exc_info=True)
            yield {"event": "text", "data": json.dumps({"text": f"Sorry, I encountered an error: {str(e)}"})}
            yield {"event": "done", "data": "{}"}

    return EventSourceResponse(event_generator())

@app.post("/event")
async def handle_event(request: UiEventRequest):
    """Acknowledge UI events. No LLM processing."""
    logger.info("[event] type=%s surface=%s name=%s component=%s",
                request.event_type, request.surface_id, request.name, request.source_component_id)

    if request.event_type == "feedback":
        logger.info("[feedback] rating=%s reason=%s", request.name, request.value)

    return {"status": "received", "surface_id": request.surface_id}

# ── Main ──────────────────────────────────────────────────
if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8000))
    print("=" * 60)
    print("  A2UI Template Agent (Deterministic)")
    print(f"  Templates: {renderer.get_loaded_templates()}")
    print(f"  Data:      {renderer.get_loaded_data()}")
    print(f"  Port:      {port}")
    print(f"  Mode:      No LLM — pre-approved templates only")
    print("=" * 60)
    uvicorn.run(app, host="0.0.0.0", port=port)
