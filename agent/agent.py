import asyncio
import json
import logging
import os
import random
import re
import string
import sys
import time
from contextlib import asynccontextmanager
from typing import AsyncGenerator, Optional

import jsonschema
import uvicorn
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from sse_starlette.sse import EventSourceResponse

from a2ui_schema import A2UI_SCHEMA
from system_prompt import A2UI_SYSTEM_PROMPT

load_dotenv()

logging.basicConfig(
    level=logging.DEBUG,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger("a2ui-agent")


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
    """Call LLM via Copilot SDK (non-streaming, accumulates full response)."""
    from copilot import CopilotClient
    from copilot.session import PermissionHandler
    from copilot.generated.session_events import SessionEventType

    client = CopilotClient()
    await client.start()

    try:
        session = await client.create_session(
            on_permission_request=PermissionHandler.approve_all,
            model="claude-sonnet-4.6",
            streaming=True,
            system_message=A2UI_SYSTEM_PROMPT,
        )

        collected: list[str] = []

        def handle_event(event):
            if event.type == SessionEventType.ASSISTANT_MESSAGE_DELTA:
                collected.append(event.data.delta_content)

        session.on(handle_event)
        await session.send_and_wait(message, timeout=60.0)
        return {"content": "".join(collected)}
    finally:
        await client.stop()


async def stream_llm_copilot_sdk(message: str) -> AsyncGenerator[str, None]:
    """Stream LLM tokens via Copilot SDK. Yields individual tokens."""
    from copilot import CopilotClient
    from copilot.session import PermissionHandler
    from copilot.generated.session_events import SessionEventType

    queue: asyncio.Queue[str | None] = asyncio.Queue()

    client = CopilotClient()
    await client.start()

    try:
        session = await client.create_session(
            on_permission_request=PermissionHandler.approve_all,
            model="claude-sonnet-4.6",
            streaming=True,
            system_message=A2UI_SYSTEM_PROMPT,
        )

        def handle_event(event):
            if event.type == SessionEventType.ASSISTANT_MESSAGE_DELTA:
                queue.put_nowait(event.data.delta_content)
            elif event.type == SessionEventType.SESSION_IDLE:
                queue.put_nowait(None)  # Signal completion

        session.on(handle_event)

        # send_and_wait runs in background; we yield tokens as they arrive
        send_task = asyncio.create_task(session.send_and_wait(message, timeout=60.0))

        while True:
            token = await asyncio.wait_for(queue.get(), timeout=120.0)
            if token is None:
                break
            yield token

        await send_task  # Ensure send completes cleanly
    finally:
        await client.stop()


async def call_llm(message: str) -> dict:
    """Call LLM via Copilot SDK."""
    return await call_llm_copilot_sdk(message)


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
            logger.warning("[parse] Recovered text from truncated JSON: '%.60s...'", extracted_text)
            return AgentResponse(text=extracted_text, ui_definition=None)
    except Exception:
        pass

    # Last resort: treat entire content as plain text
    logger.warning("[parse] Could not parse response, treating as plain text")
    # If it looks like JSON, give a friendly message instead of showing raw JSON
    if content.startswith("{"):
        return AgentResponse(
            text="I generated the information but the response was too large to display. Please try asking for fewer items.",
            ui_definition=None,
        )
    return AgentResponse(text=content, ui_definition=None)


# ─── Validation ───────────────────────────────────────────────────────────────

MAX_VALIDATION_RETRIES = 1
MAX_TEMPLATE_ITEMS = 200


def validate_ui_definition(ui_def: dict) -> tuple[bool, str]:
    """Validate uiDefinition against A2UI schema. Returns (is_valid, error_message)."""
    # 1. JSON Schema validation
    try:
        jsonschema.validate(ui_def, A2UI_SCHEMA)
    except jsonschema.ValidationError as e:
        logger.warning("[validation] Schema error: %.200s", e.message)
        return False, f"Schema error: {e.message} at {list(e.absolute_path)}"

    # 2. Semantic validation: root references a real component
    root = ui_def.get("root")
    components = ui_def.get("components", {})

    if root and root not in components:
        logger.warning("[validation] Semantic error: Root '%s' not found in components", root)
        return False, f"Root '{root}' not found in components"

    # 3. Check child references (skip template placeholders containing {i})
    for comp_id, comp_data in components.items():
        props = comp_data.get("componentProperties", {})
        for widget_type, config in props.items():
            if widget_type in ("Column", "Row", "List") and isinstance(config, dict):
                children = config.get("children", {})
                if isinstance(children, dict):
                    for child_id in children.get("explicitList", []):
                        if "{i}" not in child_id and child_id not in components:
                            error_detail = f"Component '{comp_id}' references missing child '{child_id}'"
                            logger.warning("[validation] Semantic error: %s", error_detail)
                            return False, error_detail
            elif widget_type == "Card" and isinstance(config, dict):
                child_id = config.get("child")
                if child_id and "{i}" not in child_id and child_id not in components:
                    error_detail = f"Card '{comp_id}' references missing child '{child_id}'"
                    logger.warning("[validation] Semantic error: %s", error_detail)
                    return False, error_detail

    return True, ""


# ─── Template Expansion ──────────────────────────────────────────────────────


def _replace_index(s: str, index: int) -> str:
    """Replace {i} placeholder in a string."""
    return s.replace("{i}", str(index))


def deep_replace(obj, index: int, item_data: dict):
    """Recursively replace {i} in all strings and {field} in literalString values."""
    if isinstance(obj, str):
        result = obj.replace("{i}", str(index))
        # Single-pass field replacement to prevent double-substitution
        def _field_replacer(match):
            field_name = match.group(1)
            if field_name in item_data:
                return str(item_data[field_name])
            return match.group(0)  # Leave unmatched placeholders as-is
        result = re.sub(r'\{(\w+)\}', _field_replacer, result)
        return result
    elif isinstance(obj, dict):
        return {
            _replace_index(k, index): deep_replace(v, index, item_data)
            for k, v in obj.items()
        }
    elif isinstance(obj, list):
        return [deep_replace(item, index, item_data) for item in obj]
    return obj


def expand_templates(ui_def: dict) -> dict:
    """
    Expand itemTemplate × items into individual components.
    Modifies ui_def in-place and returns it.
    """
    template = ui_def.get("itemTemplate")
    items = ui_def.get("items")
    list_id = ui_def.get("itemListId")

    if not template or not items or not list_id:
        return ui_def  # No template to expand

    if len(items) > MAX_TEMPLATE_ITEMS:
        logger.warning("[template] Capping items from %d to %d", len(items), MAX_TEMPLATE_ITEMS)
        items = items[:MAX_TEMPLATE_ITEMS]

    components = ui_def.get("components", {})
    template_components = template.get("components", {})
    root_id_pattern = template.get("rootId", "")
    divider_id_pattern = template.get("dividerId")

    expanded_list_children: list[str] = []

    for idx, item_data in enumerate(items):
        # Clone each template component with index + field substitution
        for tmpl_id, tmpl_comp in template_components.items():
            new_id = tmpl_id.replace("{i}", str(idx))
            new_comp = deep_replace(tmpl_comp, idx, item_data)
            components[new_id] = new_comp

        # Add this item's root to the list
        item_root = root_id_pattern.replace("{i}", str(idx))
        expanded_list_children.append(item_root)

        # Add divider between items (not after last)
        if divider_id_pattern and idx < len(items) - 1:
            div_id = divider_id_pattern.replace("{i}", str(idx))
            components[div_id] = {
                "id": div_id,
                "componentProperties": {"Divider": {}},
            }
            expanded_list_children.append(div_id)

    # Update the target List/Column children
    if list_id in components:
        props = components[list_id].get("componentProperties", {})
        if "List" in props:
            props["List"]["children"] = {"explicitList": expanded_list_children}
        elif "Column" in props:
            props["Column"]["children"] = {"explicitList": expanded_list_children}
        else:
            logger.warning("[template] '%s' is neither List nor Column — expanded items not attached", list_id)
    else:
        logger.warning("[template] itemListId '%s' not found in components — expanded items not attached", list_id)

    # Clean up template fields — the client never sees them
    ui_def.pop("itemTemplate", None)
    ui_def.pop("items", None)
    ui_def.pop("itemListId", None)

    logger.info(
        "[template] Expanded %d items → %d children, %d total components",
        len(items), len(expanded_list_children), len(components),
    )

    return ui_def


# ─── FastAPI App ──────────────────────────────────────────────────────────────

@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("A2UI Agent Server starting...")
    yield
    logger.info("A2UI Agent Server shutting down...")


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
    logger.info("[chat] message='%.60s' suffix=%s", request.message, suffix)

    try:
        t0 = time.time()
        llm_result = await call_llm(request.message)
        elapsed = time.time() - t0
        logger.info("[chat] LLM completed in %.2fs", elapsed)
        logger.debug("[chat] LLM response preview: %.200s", llm_result["content"][:200])
        response = parse_agent_response(llm_result["content"], suffix)

        # Validate UI if present — no retry on the sync endpoint, just strip
        if response.ui_definition:
            is_valid, error = validate_ui_definition(response.ui_definition)
            if not is_valid:
                logger.warning("[validation] FAILED (chat, no retry): %s", error)
                response = AgentResponse(text=response.text, ui_definition=None)

        logger.info("[chat] has_ui=%s", response.ui_definition is not None)
        return response
    except Exception as e:
        logger.error("[chat] %s", e, exc_info=True)
        return AgentResponse(
            text="Sorry, I encountered an error. Please try again.",
            ui_definition=None,
            error=str(e),
        )


def transform_to_path_bindings(components: dict) -> tuple[dict, list[dict]]:
    """
    Walk all components and transform literal Text values to DataModel path bindings.
    Returns (transformed_components_dict, data_model_entries).
    """
    data_entries = []
    transformed = {}

    for comp_id, comp_data in components.items():
        props = comp_data.get("componentProperties", {})
        new_props = {}

        for widget_type, config in props.items():
            if widget_type == "Text" and isinstance(config, dict):
                text_val = config.get("text", {})
                if isinstance(text_val, dict) and "literalString" in text_val:
                    new_config = dict(config)
                    new_config["text"] = {"path": f"/{comp_id}"}
                    new_props[widget_type] = new_config
                    data_entries.append({
                        "key": comp_id,
                        "valueString": text_val["literalString"],
                    })
                else:
                    new_props[widget_type] = config
            else:
                new_props[widget_type] = config

        transformed[comp_id] = {**comp_data, "componentProperties": new_props}

    return transformed, data_entries


def sanitize_components(components: dict) -> dict:
    """Remove dangling child references — children IDs that don't exist in the component map."""
    valid_ids = set(components.keys())
    sanitized = {}
    removed_count = 0

    for comp_id, comp_data in components.items():
        props = comp_data.get("componentProperties", {})
        new_props = {}
        skip_component = False

        for widget_type, config in props.items():
            if widget_type in ("Column", "Row", "List") and isinstance(config, dict):
                children = config.get("children", {})
                if isinstance(children, dict) and "explicitList" in children:
                    original_list = children["explicitList"]
                    filtered = [cid for cid in original_list if cid in valid_ids]
                    if len(filtered) < len(original_list):
                        removed_count += len(original_list) - len(filtered)
                        new_config = dict(config)
                        new_config["children"] = {"explicitList": filtered}
                        new_props[widget_type] = new_config
                    else:
                        new_props[widget_type] = config
                else:
                    new_props[widget_type] = config
            elif widget_type == "Card" and isinstance(config, dict):
                child_id = config.get("child")
                if child_id and child_id not in valid_ids:
                    skip_component = True
                    removed_count += 1
                    break
                else:
                    new_props[widget_type] = config
            else:
                new_props[widget_type] = config

        if not skip_component:
            sanitized[comp_id] = {**comp_data, "componentProperties": new_props}

    if removed_count > 0:
        logger.info("[sanitize] Removed %d dangling component reference(s)", removed_count)

    return sanitized


def chunk_components(components: dict, chunk_size: int = 15) -> list[list[dict]]:
    """Split components into chunks for progressive surfaceUpdate emissions."""
    comp_list = []
    for comp_id, comp_data in components.items():
        props = comp_data.get("componentProperties", {})
        comp_list.append({"id": comp_id, "component": props})

    return [comp_list[i:i + chunk_size] for i in range(0, len(comp_list), chunk_size)]


def transform_to_operations(parsed_response: dict, surface_suffix: str) -> list[dict]:
    """Transform LLM JSON response into A2UI v0.8 protocol operations with path bindings."""
    text = parsed_response.get("text", "")
    ui_def = parsed_response.get("uiDefinition") or parsed_response.get("ui_definition")
    surface_id = f"response_{surface_suffix}"

    operations = []

    # 1. Text event first — user sees summary immediately
    if text:
        operations.append({"type": "text", "data": {"text": text}})

    if ui_def:
        # Expand templates FIRST (before path bindings and sanitization)
        ui_def = expand_templates(ui_def)

        root = ui_def.get("root", "root")
        components = ui_def.get("components", {})
        logger.debug("[transform] input has %d components, root=%s", len(components), root)

        # Transform literal values → path bindings + extract DataModel
        transformed_components, data_entries = transform_to_path_bindings(components)
        logger.debug("[transform] after path-binding: %d data entries", len(data_entries))

        # Sanitize: remove dangling child references (truncated LLM output)
        transformed_components = sanitize_components(transformed_components)
        logger.debug("[transform] after sanitize: %d components", len(transformed_components))

        # 2. beginRendering
        operations.append({
            "type": "a2ui_op",
            "data": {"beginRendering": {"surfaceId": surface_id, "root": root}},
        })

        # 3. dataModelUpdate BEFORE surfaceUpdate — paths resolve immediately
        if data_entries:
            operations.append({
                "type": "a2ui_op",
                "data": {"dataModelUpdate": {
                    "surfaceId": surface_id,
                    "path": "",
                    "contents": data_entries,
                }},
            })

        # 4. Chunked surfaceUpdates
        chunks = chunk_components(transformed_components)
        logger.debug("[transform] chunked into %d batches", len(chunks))
        for chunk in chunks:
            operations.append({
                "type": "a2ui_op",
                "data": {"surfaceUpdate": {"surfaceId": surface_id, "components": chunk}},
            })

    # 5. done
    operations.append({"type": "done", "data": {}})

    return operations


@app.post("/chat/stream")
async def chat_stream(request: ChatRequest):
    if not request.message.strip():
        raise HTTPException(status_code=400, detail="Message cannot be empty")

    suffix = _random_suffix()
    logger.info("[chat/stream] message='%.60s' suffix=%s", request.message, suffix)

    async def event_generator():
        full_content = ""
        try:
            t0 = time.time()

            async for token in stream_llm_copilot_sdk(request.message):
                full_content += token
            elapsed = time.time() - t0
            logger.info("[chat/stream] LLM completed in %.2fs, content_len=%d", elapsed, len(full_content))

            logger.debug("[chat/stream] LLM response preview: %.200s", full_content[:200])

            # Parse accumulated content and transform to A2UI operations
            response = parse_agent_response(full_content, suffix)

            # Validate UI if present — retry once on failure
            if response.ui_definition:
                is_valid, error = validate_ui_definition(response.ui_definition)
                if not is_valid:
                    logger.warning("[validation] FAILED: %s, retrying...", error)
                    retry_message = (
                        f"{request.message}\n\n"
                        f"[SYSTEM: Your previous response had a UI validation error: {error}. "
                        f"Please fix the issue and try again.]"
                    )
                    retry_content = ""
                    t0_retry = time.time()
                    async for token in stream_llm_copilot_sdk(retry_message):
                        retry_content += token
                    retry_elapsed = time.time() - t0_retry
                    logger.info("[retry] LLM completed in %.2fs, content_len=%d", retry_elapsed, len(retry_content))
                    logger.debug("[retry] LLM response preview: %.200s", retry_content[:200])
                    response = parse_agent_response(retry_content, suffix)

                    if response.ui_definition:
                        is_valid2, error2 = validate_ui_definition(response.ui_definition)
                        if not is_valid2:
                            logger.warning("[validation] Retry FAILED: %s, falling back to text-only", error2)
                            response = AgentResponse(text=response.text, ui_definition=None)
                        else:
                            logger.info("[validation] Retry succeeded")
                    else:
                        logger.warning("[validation] Retry returned no UI")
                else:
                    logger.info("[validation] OK")

            parsed = {"text": response.text}
            if response.ui_definition:
                parsed["uiDefinition"] = response.ui_definition

            operations = transform_to_operations(parsed, suffix)
            logger.info("[chat/stream] emitting %d ops, has_ui=%s", len(operations), response.ui_definition is not None)

            # Emit final complete text before A2UI operations
            yield {"event": "text", "data": json.dumps({"text": response.text})}
            await asyncio.sleep(0.1)  # Small gap before UI operations start

            for op in operations:
                yield {"event": op["type"], "data": json.dumps(op["data"])}
                if op["type"] == "a2ui_op":
                    await asyncio.sleep(0.15)  # 150ms between ALL A2UI ops for visible progressive rendering

        except Exception as e:
            logger.error("[chat/stream] %s", e, exc_info=True)
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
    """Handle UI events — for userAction, stream back new A2UI operations."""
    logger.info("[event] surface=%s type=%s name=%s path=%s", request.surface_id, request.event_type, request.name, request.path)

    if request.event_type == "userAction" and request.name:
        # Construct follow-up prompt from action context
        context_desc = ""
        if request.context:
            context_desc = ", ".join(f"{k}={v}" for k, v in request.context.items())

        follow_up = f"The user clicked the '{request.name}' button"
        if context_desc:
            follow_up += f" with context: {context_desc}"
        follow_up += ". Respond with updated information."

        suffix = _random_suffix()

        async def event_op_generator():
            full_content = ""
            try:
                t0 = time.time()

                async for token in stream_llm_copilot_sdk(follow_up):
                    full_content += token
                elapsed = time.time() - t0
                logger.info("[event] LLM completed in %.2fs, content_len=%d", elapsed, len(full_content))

                logger.debug("[event] LLM response preview: %.200s", full_content[:200])

                response = parse_agent_response(full_content, suffix)

                # Validate UI if present — retry once on failure
                if response.ui_definition:
                    is_valid, error = validate_ui_definition(response.ui_definition)
                    if not is_valid:
                        logger.warning("[validation] FAILED (event): %s, retrying...", error)
                        retry_message = (
                            f"{follow_up}\n\n"
                            f"[SYSTEM: Your previous response had a UI validation error: {error}. "
                            f"Please fix the issue and try again.]"
                        )
                        retry_content = ""
                        t0_retry = time.time()
                        async for token in stream_llm_copilot_sdk(retry_message):
                            retry_content += token
                        retry_elapsed = time.time() - t0_retry
                        logger.info("[retry] LLM completed in %.2fs, content_len=%d", retry_elapsed, len(retry_content))
                        logger.debug("[retry] LLM response preview: %.200s", retry_content[:200])
                        response = parse_agent_response(retry_content, suffix)

                        if response.ui_definition:
                            is_valid2, error2 = validate_ui_definition(response.ui_definition)
                            if not is_valid2:
                                logger.warning("[validation] Retry FAILED (event): %s, falling back to text-only", error2)
                                response = AgentResponse(text=response.text, ui_definition=None)
                            else:
                                logger.info("[validation] Retry succeeded (event)")
                        else:
                            logger.warning("[validation] Retry returned no UI (event)")
                    else:
                        logger.info("[validation] OK (event)")

                parsed = {"text": response.text}
                if response.ui_definition:
                    parsed["uiDefinition"] = response.ui_definition

                operations = transform_to_operations(parsed, suffix)

                # Emit final complete text before A2UI operations
                yield {"event": "text", "data": json.dumps({"text": response.text})}
                await asyncio.sleep(0.1)  # Small gap before UI operations start

                for op in operations:
                    yield {"event": op["type"], "data": json.dumps(op["data"])}
                    if op["type"] == "a2ui_op":
                        await asyncio.sleep(0.15)  # 150ms between ALL A2UI ops for visible progressive rendering
            except Exception as e:
                logger.error("[event] %s", e, exc_info=True)
                yield {"event": "text", "data": json.dumps({"text": f"Error processing action: {str(e)}"})}
                yield {"event": "done", "data": "{}"}

        return EventSourceResponse(event_op_generator())

    # dataChange events — acknowledge
    return {"status": "received", "surface_id": request.surface_id}


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8000))
    logger.info("=" * 60)
    logger.info("A2UI Agent Server v1.0")
    logger.info("Port: %d", port)
    logger.info("LLM: Copilot SDK (claude-sonnet-4.6)")
    logger.info("=" * 60)
    uvicorn.run("agent:app", host="0.0.0.0", port=port, reload=True)
