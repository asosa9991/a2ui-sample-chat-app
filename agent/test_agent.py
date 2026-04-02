"""
Integration tests for the FastAPI A2UI agent at http://localhost:8000.

Run with:
    pytest test_agent.py -v -m integration --timeout=90
"""

import json
import pytest
import httpx

BASE_URL = "http://localhost:8000"
TEST_MESSAGE = "Show my trades from last week"
TIMEOUT = 60  # seconds — LLM can take up to 15s


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _check_server() -> bool:
    """Return True if the server is reachable via /health."""
    try:
        r = httpx.get(f"{BASE_URL}/health", timeout=5)
        return r.status_code == 200
    except Exception:
        return False


def parse_sse_events(lines: list[str]) -> list[dict]:
    """Parse raw SSE lines into a list of {event, data} dicts."""
    events = []
    current: dict = {}
    for line in lines:
        if line.startswith("event:"):
            current["event"] = line[6:].strip()
        elif line.startswith("data:"):
            raw = line[5:].strip()
            if raw:
                current["data"] = json.loads(raw)
        elif line == "" and current:
            events.append(current)
            current = {}
    if current:
        events.append(current)
    return events


def _stream_sse_lines(path: str) -> list[str]:
    """POST to `path` and collect all raw SSE text lines."""
    payload = {"message": TEST_MESSAGE}
    lines: list[str] = []
    with httpx.stream(
        "POST",
        f"{BASE_URL}{path}",
        json=payload,
        timeout=TIMEOUT,
    ) as response:
        response.raise_for_status()
        for line in response.iter_lines():
            lines.append(line)
    return lines


def _stream_jsonl_messages(path: str) -> list[dict]:
    """POST to `path` and return parsed JSON objects from `data:` lines."""
    lines = _stream_sse_lines(path)
    messages: list[dict] = []
    for line in lines:
        if line.startswith("data:"):
            raw = line[5:].strip()
            if raw:
                messages.append(json.loads(raw))
    return messages


# ---------------------------------------------------------------------------
# Module-level server health check fixture
# ---------------------------------------------------------------------------

@pytest.fixture(scope="module", autouse=True)
def require_server():
    """Skip the entire module if the agent server is not running."""
    if not _check_server():
        pytest.skip(
            f"Agent server not reachable at {BASE_URL}/health — start it before running integration tests."
        )


# ---------------------------------------------------------------------------
# Shared fixtures (module-scoped to avoid redundant LLM calls)
# ---------------------------------------------------------------------------

@pytest.fixture(scope="module")
def sse_events() -> list[dict]:
    """Fetch and parse SSE events from /chat/stream once per module."""
    lines = _stream_sse_lines("/chat/stream")
    return parse_sse_events(lines)


@pytest.fixture(scope="module")
def jsonl_messages() -> list[dict]:
    """Fetch and parse JSONL messages from /chat/stream/jsonl once per module."""
    return _stream_jsonl_messages("/chat/stream/jsonl")


# ---------------------------------------------------------------------------
# TestChatStream — POST /chat/stream
# ---------------------------------------------------------------------------

@pytest.mark.integration
class TestChatStream:

    def test_chat_stream_returns_sse_events(self, sse_events):
        """text, a2ui_op, and done event types must all be present."""
        event_types = {e.get("event") for e in sse_events}
        assert "text" in event_types, f"Expected 'text' event, got: {event_types}"
        assert "a2ui_op" in event_types, f"Expected 'a2ui_op' event, got: {event_types}"
        assert "done" in event_types, f"Expected 'done' event, got: {event_types}"

    def test_chat_stream_has_begin_rendering(self, sse_events):
        """An a2ui_op event must carry a beginRendering op with surfaceId and root."""
        begin_ops = [
            e["data"]
            for e in sse_events
            if e.get("event") == "a2ui_op" and "beginRendering" in e.get("data", {})
        ]
        assert begin_ops, "No a2ui_op with 'beginRendering' found in SSE stream"
        br = begin_ops[0]["beginRendering"]
        assert "surfaceId" in br, f"'beginRendering' missing 'surfaceId': {br}"
        assert "root" in br, f"'beginRendering' missing 'root': {br}"
        assert br["surfaceId"], "surfaceId must be non-empty"
        assert br["root"], "root must be non-empty"

    def test_chat_stream_has_data_model_update(self, sse_events):
        """An a2ui_op event must carry a dataModelUpdate op with a contents list."""
        dmu_ops = [
            e["data"]
            for e in sse_events
            if e.get("event") == "a2ui_op" and "dataModelUpdate" in e.get("data", {})
        ]
        assert dmu_ops, "No a2ui_op with 'dataModelUpdate' found in SSE stream"
        dmu = dmu_ops[0]["dataModelUpdate"]
        assert "contents" in dmu, f"'dataModelUpdate' missing 'contents': {dmu}"
        assert isinstance(dmu["contents"], list), "'contents' must be a list"

    def test_chat_stream_has_surface_update(self, sse_events):
        """An a2ui_op event must carry a surfaceUpdate op with a non-empty components list."""
        su_ops = [
            e["data"]
            for e in sse_events
            if e.get("event") == "a2ui_op" and "surfaceUpdate" in e.get("data", {})
        ]
        assert su_ops, "No a2ui_op with 'surfaceUpdate' found in SSE stream"
        su = su_ops[0]["surfaceUpdate"]
        assert "components" in su, f"'surfaceUpdate' missing 'components': {su}"
        assert isinstance(su["components"], list), "'components' must be a list"
        assert su["components"], "'components' must be non-empty"

    def test_chat_stream_surface_ids_match(self, sse_events):
        """All a2ui_op events must share the same surfaceId."""
        a2ui_events = [e for e in sse_events if e.get("event") == "a2ui_op"]
        assert a2ui_events, "No a2ui_op events found"

        surface_ids: set[str] = set()
        for e in a2ui_events:
            data = e.get("data", {})
            for op_key in ("beginRendering", "dataModelUpdate", "surfaceUpdate"):
                if op_key in data:
                    sid = data[op_key].get("surfaceId")
                    if sid:
                        surface_ids.add(sid)

        assert len(surface_ids) == 1, (
            f"Expected all a2ui_op events to share one surfaceId, found: {surface_ids}"
        )

    def test_chat_stream_done_is_last(self, sse_events):
        """The 'done' event must be the final event in the stream."""
        assert sse_events, "No SSE events received"
        last = sse_events[-1]
        assert last.get("event") == "done", (
            f"Expected last event to be 'done', got: {last.get('event')!r}"
        )


# ---------------------------------------------------------------------------
# TestJsonlStream — POST /chat/stream/jsonl
# ---------------------------------------------------------------------------

@pytest.mark.integration
class TestJsonlStream:

    def test_jsonl_stream_returns_data_lines(self, jsonl_messages):
        """text, surfaceUpdate, dataModelUpdate, beginRendering, and done must all be present."""
        keys_present = set()
        for msg in jsonl_messages:
            keys_present.update(msg.keys())

        for expected in ("text", "surfaceUpdate", "dataModelUpdate", "beginRendering", "done"):
            assert expected in keys_present, (
                f"Expected key '{expected}' in JSONL messages; found keys: {keys_present}"
            )

    def test_jsonl_stream_has_begin_rendering(self, jsonl_messages):
        """At least one message must have a 'beginRendering' key."""
        br_msgs = [m for m in jsonl_messages if "beginRendering" in m]
        assert br_msgs, "No message with 'beginRendering' found in JSONL stream"
        br = br_msgs[0]["beginRendering"]
        assert "surfaceId" in br, f"'beginRendering' missing 'surfaceId': {br}"
        assert br["surfaceId"], "surfaceId must be non-empty"

    def test_jsonl_stream_has_surface_update(self, jsonl_messages):
        """At least one message must have a 'surfaceUpdate' with non-empty components."""
        su_msgs = [m for m in jsonl_messages if "surfaceUpdate" in m]
        assert su_msgs, "No message with 'surfaceUpdate' found in JSONL stream"
        su = su_msgs[0]["surfaceUpdate"]
        assert "components" in su, f"'surfaceUpdate' missing 'components': {su}"
        assert isinstance(su["components"], list), "'components' must be a list"
        assert su["components"], "'components' must be non-empty"

    def test_jsonl_stream_done_is_last(self, jsonl_messages):
        """The last JSONL message must be {"done": {}}."""
        assert jsonl_messages, "No JSONL messages received"
        last = jsonl_messages[-1]
        assert "done" in last, (
            f"Expected last JSONL message to contain 'done', got: {last}"
        )

    def test_jsonl_stream_begin_rendering_last_before_done(self, jsonl_messages):
        """
        beginRendering must appear after both surfaceUpdate and dataModelUpdate,
        i.e. it is the second-to-last meaningful message (done is last).
        """
        assert jsonl_messages, "No JSONL messages received"

        def _index_of_key(key: str) -> int:
            for i, msg in enumerate(jsonl_messages):
                if key in msg:
                    return i
            return -1

        idx_su = _index_of_key("surfaceUpdate")
        idx_dmu = _index_of_key("dataModelUpdate")
        idx_br = _index_of_key("beginRendering")

        assert idx_su != -1, "'surfaceUpdate' not found in JSONL messages"
        assert idx_dmu != -1, "'dataModelUpdate' not found in JSONL messages"
        assert idx_br != -1, "'beginRendering' not found in JSONL messages"

        assert idx_br > idx_su, (
            f"'beginRendering' (idx={idx_br}) should come after 'surfaceUpdate' (idx={idx_su})"
        )
        assert idx_br > idx_dmu, (
            f"'beginRendering' (idx={idx_br}) should come after 'dataModelUpdate' (idx={idx_dmu})"
        )
