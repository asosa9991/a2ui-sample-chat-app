"""
Tests for the A2UI agent.

Integration tests (marked `integration`) hit a live server at http://localhost:8000.
Unit tests (TestIntentRouter, TestTemplateRenderer, TestA2UiTransform,
TestSyncEndpointFormat) exercise pure-Python logic — no server required.

Run all unit tests:
    cd agent && python -m pytest test_agent.py -v -m "not integration"

Run integration tests (server must be running):
    cd agent && python -m pytest test_agent.py -v -m integration --timeout=90
"""

import json
from pathlib import Path

import pytest
import httpx

from intent_router import classify, IntentMatch
from template_renderer import TemplateRenderer
from a2ui_transform import transform_to_operations, encode_array_entry, encode_array_entry

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
    """Fetch and parse SSE events from /chat/stream/template once per module.

    Uses the deterministic template endpoint instead of the LLM endpoint so
    tests pass without a running GitHub Copilot CLI server or API token.
    The template endpoint emits the same A2UI SSE protocol as the LLM endpoint.
    """
    lines = _stream_sse_lines("/chat/stream/template")
    return parse_sse_events(lines)


@pytest.fixture(scope="module")
def jsonl_messages() -> list[dict]:
    """Fetch and parse JSONL messages from /chat/stream/template/jsonl once per module.

    Uses the deterministic template endpoint instead of the LLM endpoint so
    tests pass without a running GitHub Copilot CLI server or API token.
    The template endpoint emits the same A2UI JSONL protocol as the LLM endpoint.
    """
    return _stream_jsonl_messages("/chat/stream/template/jsonl")


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


# ---------------------------------------------------------------------------
# TestSyncTemplate — POST /chat/template  (sync endpoint)
# ---------------------------------------------------------------------------

@pytest.fixture(scope="module")
def sync_template_response() -> dict:
    """
    POST to /chat/template (sync) once per module and return the parsed JSON body.

    This endpoint collects all streaming ops and returns a complete AgentResponse
    with ui_definition embedded. The ui_definition.components map must have every
    component wrapped in {"componentProperties": {...}} after the Bug #1 fix.
    """
    payload = {"message": "show my account balances"}
    resp = httpx.post(f"{BASE_URL}/chat/template", json=payload, timeout=TIMEOUT)
    resp.raise_for_status()
    return resp.json()


@pytest.mark.integration
class TestSyncTemplate:
    """
    Integration tests for the /chat/template sync endpoint.

    Bug #1 fix: each component in ui_definition.components must be wrapped as
      {"componentProperties": {"WidgetType": {...}}}
    so that Kotlin's ComponentDto.componentProperties can deserialize them
    (previously the raw {"WidgetType": {...}} was stored directly, yielding
    an empty componentProperties map and a null widgetType).
    """

    def test_sync_response_has_ui_definition(self, sync_template_response):
        """Response must contain a top-level 'ui_definition' key."""
        assert "ui_definition" in sync_template_response, (
            f"'ui_definition' missing from sync response keys: {list(sync_template_response.keys())}"
        )
        assert sync_template_response["ui_definition"] is not None, (
            "'ui_definition' must not be null"
        )

    def test_sync_ui_definition_has_root(self, sync_template_response):
        """ui_definition must have a non-empty 'root' key naming the root component."""
        ui_def = sync_template_response["ui_definition"]
        assert "root" in ui_def, f"'root' missing from ui_definition: {list(ui_def.keys())}"
        assert ui_def["root"], "'root' must be a non-empty string"

    def test_sync_ui_definition_has_components(self, sync_template_response):
        """ui_definition.components must be a non-empty dict."""
        ui_def = sync_template_response["ui_definition"]
        assert "components" in ui_def, (
            f"'components' missing from ui_definition: {list(ui_def.keys())}"
        )
        assert isinstance(ui_def["components"], dict), "'components' must be a dict"
        assert ui_def["components"], "'components' must not be empty"

    def test_sync_root_exists_as_component_key(self, sync_template_response):
        """
        The value of ui_definition.root must be a key in ui_definition.components.
        If root is not present in components the A2UI surface cannot render anything.
        """
        ui_def = sync_template_response["ui_definition"]
        root_id = ui_def.get("root")
        components = ui_def.get("components", {})
        assert root_id in components, (
            f"root '{root_id}' is not a key in ui_definition.components "
            f"(keys: {list(components.keys())[:10]})"
        )

    def test_sync_all_components_have_componentProperties_key(self, sync_template_response):
        """
        Bug #1 regression test: every component object must have a 'componentProperties' key.

        Without the fix, agent.py stored entry["component"] directly (e.g. {"Column": {...}}).
        ComponentDto.componentProperties then deserialized as an empty map — widgetType = null.

        After the fix, agent.py stores {"componentProperties": entry["component"]}, so each
        component object has exactly one top-level key: "componentProperties".
        """
        ui_def = sync_template_response["ui_definition"]
        components: dict = ui_def.get("components", {})

        violations: list[str] = []
        for comp_id, comp_obj in components.items():
            if not isinstance(comp_obj, dict):
                violations.append(f"{comp_id}: not a dict (got {type(comp_obj).__name__})")
                continue
            if "componentProperties" not in comp_obj:
                violations.append(
                    f"{comp_id}: missing 'componentProperties' key "
                    f"(top-level keys: {list(comp_obj.keys())})"
                )

        assert not violations, (
            f"Bug #1 regression — {len(violations)} component(s) missing 'componentProperties':\n"
            + "\n".join(f"  • {v}" for v in violations)
        )

    def test_sync_componentProperties_contains_widget_type(self, sync_template_response):
        """
        Each componentProperties map must have at least one key (the widget type name).
        An empty componentProperties means the widget type cannot be determined and
        the surface will show 'Unknown widget' error for that component.
        """
        ui_def = sync_template_response["ui_definition"]
        components: dict = ui_def.get("components", {})

        empty_props: list[str] = []
        for comp_id, comp_obj in components.items():
            if not isinstance(comp_obj, dict):
                continue
            props = comp_obj.get("componentProperties", {})
            if not props:
                empty_props.append(comp_id)

        assert not empty_props, (
            f"{len(empty_props)} component(s) have empty componentProperties "
            f"(widget type cannot be resolved): {empty_props}"
        )


# ---------------------------------------------------------------------------
# TestTemplateStream — POST /chat/stream/template  (JSONL endpoint)
# ---------------------------------------------------------------------------

@pytest.fixture(scope="module")
def template_stream_messages() -> list[dict]:
    """POST to /chat/stream/template (JSONL) and return parsed messages."""
    return _stream_jsonl_messages("/chat/stream/template")


@pytest.mark.integration
class TestTemplateStream:
    """
    Integration tests for the /chat/stream/template JSONL endpoint.
    Tests that the template agent streams valid A2UI protocol messages.
    """

    def test_template_stream_returns_messages(self, template_stream_messages):
        """Must return at least one message."""
        assert template_stream_messages, "No messages received from /chat/stream/template"

    def test_template_stream_has_text(self, template_stream_messages):
        """At least one message must have a 'text' key."""
        text_msgs = [m for m in template_stream_messages if "text" in m]
        assert text_msgs, "No 'text' message in template JSONL stream"
        assert text_msgs[0]["text"], "'text' must be a non-empty string"

    def test_template_stream_has_surface_update(self, template_stream_messages):
        """At least one message must have 'surfaceUpdate' with non-empty components."""
        su_msgs = [m for m in template_stream_messages if "surfaceUpdate" in m]
        assert su_msgs, "No 'surfaceUpdate' in template JSONL stream"
        su = su_msgs[0]["surfaceUpdate"]
        assert su.get("components"), "'surfaceUpdate.components' must be non-empty"

    def test_template_stream_all_components_have_id_and_component(self, template_stream_messages):
        """Every component entry in surfaceUpdate must have both 'id' and 'component' keys."""
        violations = []
        for msg in template_stream_messages:
            if "surfaceUpdate" in msg:
                for entry in msg["surfaceUpdate"].get("components", []):
                    if "id" not in entry:
                        violations.append(f"Missing 'id': {entry}")
                    if "component" not in entry:
                        violations.append(f"Missing 'component': {entry}")
        assert not violations, "Component format violations:\n" + "\n".join(violations)

    def test_template_stream_done_is_last(self, template_stream_messages):
        """Last message must be {\"done\": {}}."""
        assert template_stream_messages
        assert "done" in template_stream_messages[-1], (
            f"Expected 'done' as last message, got: {template_stream_messages[-1]}"
        )


# ---------------------------------------------------------------------------
# Unit test fixtures (no server required)
# ---------------------------------------------------------------------------

# Base directory for template data.  TemplateRenderer resolves paths relative
# to cwd — using absolute paths here ensures tests work from any working dir.
_AGENT_DIR = Path(__file__).parent
_TEMPLATES_DIR = str(_AGENT_DIR / "templates")
_DATA_DIR = str(_AGENT_DIR / "data")


@pytest.fixture(scope="module")
def unit_renderer() -> TemplateRenderer:
    """Shared TemplateRenderer for unit tests (module-scoped to avoid repeated I/O)."""
    r = TemplateRenderer(templates_dir=_TEMPLATES_DIR, data_dir=_DATA_DIR)
    assert r.get_loaded_templates(), "TemplateRenderer loaded no templates — check path"
    assert r.get_loaded_data(), "TemplateRenderer loaded no data — check path"
    return r


@pytest.fixture(scope="module")
def rendered_account_balances(unit_renderer) -> dict:
    result = unit_renderer.render("account_balances", "account_balances")
    assert result is not None, "render('account_balances', 'account_balances') returned None"
    return result


@pytest.fixture(scope="module")
def rendered_transaction_history(unit_renderer) -> dict:
    result = unit_renderer.render("transaction_history", "transaction_history")
    assert result is not None, "render('transaction_history', 'transaction_history') returned None"
    return result


@pytest.fixture(scope="module")
def account_balances_ops(rendered_account_balances) -> list[dict]:
    return transform_to_operations(rendered_account_balances, surface_suffix="test")


@pytest.fixture(scope="module")
def transaction_history_ops(rendered_transaction_history) -> list[dict]:
    return transform_to_operations(rendered_transaction_history, surface_suffix="test")


# ---------------------------------------------------------------------------
# TestIntentRouter — pure unit tests
# ---------------------------------------------------------------------------

class TestIntentRouter:
    """Unit tests for intent_router.classify()."""

    def test_show_balances_routes_to_account_balances(self):
        """'show my balances' → account_balances template."""
        result = classify("show my balances")
        assert result is not None, "classify() returned None for 'show my balances'"
        assert isinstance(result, IntentMatch)
        assert result.template_id == "account_balances", (
            f"Expected template_id='account_balances', got {result.template_id!r}"
        )

    def test_show_transactions_routes_to_transaction_history(self):
        """'show my transactions' → transaction_history template."""
        result = classify("show my transactions")
        assert result is not None
        assert result.template_id == "transaction_history", (
            f"Expected template_id='transaction_history', got {result.template_id!r}"
        )

    def test_show_trades_routes_to_brokerage_activity(self):
        """'show my trades' → brokerage_activity template."""
        result = classify("show my trades")
        assert result is not None
        assert result.template_id == "brokerage_activity", (
            f"Expected template_id='brokerage_activity', got {result.template_id!r}"
        )

    def test_unknown_message_returns_none(self):
        """A plain greeting should not match any intent."""
        result = classify("hello world")
        assert result is None, (
            f"Expected None for 'hello world', got {result}"
        )

    def test_case_insensitive_routing(self):
        """Upper-case input should route identically to lower-case."""
        lower = classify("show my balances")
        upper = classify("SHOW MY BALANCES")
        assert upper is not None, "classify() returned None for upper-case input"
        assert lower is not None
        assert upper.template_id == lower.template_id, (
            f"Case sensitivity mismatch: '{upper.template_id}' != '{lower.template_id}'"
        )

    def test_exact_match_account_balance_takes_priority(self):
        """'account balance' triggers the high-confidence exact match rule."""
        result = classify("show my account balance")
        assert result is not None
        assert result.template_id == "account_balances"
        assert result.confidence == "exact"

    def test_exact_match_last_transaction_takes_priority(self):
        """'last transaction' triggers the high-confidence exact match rule."""
        result = classify("what was my last transaction?")
        assert result is not None
        assert result.template_id == "transaction_history"
        assert result.confidence == "exact"

    def test_classify_returns_data_id_matching_template_id(self):
        """data_id should equal template_id for all current intents."""
        for msg in ("show my balances", "show my transactions", "show my trades"):
            result = classify(msg)
            assert result is not None
            assert result.data_id == result.template_id, (
                f"data_id '{result.data_id}' != template_id '{result.template_id}' "
                f"for message: {msg!r}"
            )


# ---------------------------------------------------------------------------
# TestTemplateRenderer — pure unit tests
# ---------------------------------------------------------------------------

class TestTemplateRenderer:
    """Unit tests for TemplateRenderer.render() — reads from disk, no server."""

    def test_render_account_balances_returns_ui_definition(self, rendered_account_balances):
        """render('account_balances', ...) must have a non-None 'uiDefinition' key."""
        assert "uiDefinition" in rendered_account_balances, (
            f"Result missing 'uiDefinition'. Keys: {list(rendered_account_balances.keys())}"
        )
        assert rendered_account_balances["uiDefinition"] is not None, (
            "'uiDefinition' must not be None"
        )

    def test_render_returns_text_field(self, rendered_account_balances):
        """Rendered result must have a non-empty 'text' field."""
        assert "text" in rendered_account_balances, (
            f"Rendered result missing 'text'. Keys: {list(rendered_account_balances.keys())}"
        )
        assert rendered_account_balances["text"], "'text' must be a non-empty string"
        assert isinstance(rendered_account_balances["text"], str)

    def test_render_transaction_history_has_arrays(self, rendered_transaction_history):
        """transaction_history render must embed 'transactions' array from mock data."""
        assert "arrays" in rendered_transaction_history, (
            f"Result missing 'arrays' key. Keys: {list(rendered_transaction_history.keys())}"
        )
        arrays = rendered_transaction_history["arrays"]
        assert "transactions" in arrays, (
            f"Expected 'transactions' key in arrays; got: {list(arrays.keys())}"
        )
        tx_list = arrays["transactions"]
        assert isinstance(tx_list, list), "'transactions' must be a list"
        assert len(tx_list) > 0, "'transactions' list must not be empty"

    def test_render_account_balances_has_expected_scalar_data(self, rendered_account_balances):
        """
        account_balances data is all scalars (no lists), so 'arrays' is absent.
        Verify the rendered text contains placeholder-substituted values from the
        mock data file — confirming placeholder substitution worked correctly.
        """
        text = rendered_account_balances["text"]
        # Scalar fields from data/account_balances.json that must appear in text
        for expected_fragment in ("$", "Checking", "Savings"):
            assert expected_fragment in text, (
                f"Expected fragment {expected_fragment!r} not found in rendered text: {text[:200]}"
            )

    def test_render_transaction_history_text_contains_substituted_data(
        self, rendered_transaction_history
    ):
        """Rendered text for transaction_history must contain period info from mock data."""
        text = rendered_transaction_history["text"]
        assert text, "text must be non-empty"
        assert "2026" in text or "transaction" in text.lower(), (
            f"Rendered text doesn't look like a transaction summary: {text[:200]}"
        )

    def test_render_unknown_template_returns_none(self, unit_renderer):
        """Rendering a non-existent template must return None, not raise."""
        result = unit_renderer.render("does_not_exist", "account_balances")
        assert result is None, f"Expected None for unknown template, got: {result}"

    def test_render_unknown_data_returns_none(self, unit_renderer):
        """Rendering with a non-existent data ID must return None, not raise."""
        result = unit_renderer.render("account_balances", "does_not_exist")
        assert result is None, f"Expected None for unknown data ID, got: {result}"

    def test_render_ui_definition_has_root(self, rendered_account_balances):
        """uiDefinition must include a 'root' key pointing to the root component."""
        ui_def = rendered_account_balances["uiDefinition"]
        assert "root" in ui_def, f"'root' missing from uiDefinition. Keys: {list(ui_def.keys())}"
        assert ui_def["root"], "'root' must be a non-empty string"

    def test_render_ui_definition_has_components(self, rendered_account_balances):
        """uiDefinition.components must be a non-empty dict."""
        ui_def = rendered_account_balances["uiDefinition"]
        assert "components" in ui_def, (
            f"'components' missing from uiDefinition. Keys: {list(ui_def.keys())}"
        )
        components = ui_def["components"]
        assert isinstance(components, dict), "'components' must be a dict"
        assert components, "'components' dict must not be empty"

    def test_render_does_not_mutate_cached_template(self, unit_renderer):
        """Two consecutive renders of the same template must return independent copies."""
        r1 = unit_renderer.render("account_balances", "account_balances")
        r2 = unit_renderer.render("account_balances", "account_balances")
        assert r1 is not r2, "render() should return fresh objects, not the cached instance"
        # Mutate r1 and confirm r2 is unaffected
        r1["uiDefinition"]["_test_mutation"] = True
        assert "_test_mutation" not in r2.get("uiDefinition", {}), (
            "Mutation of one render result affected another — deep copy is broken"
        )


# ---------------------------------------------------------------------------
# TestA2UiTransform — pure unit tests
# ---------------------------------------------------------------------------

class TestA2UiTransform:
    """Unit tests for transform_to_operations() — pure Python, no I/O."""

    def test_transform_produces_text_op(self, account_balances_ops):
        """Output must include at least one op with type == 'text'."""
        text_ops = [op for op in account_balances_ops if op.get("type") == "text"]
        assert text_ops, "No op with type='text' in transform output"
        assert "text" in text_ops[0]["data"], (
            f"text op data must contain 'text' key, got: {text_ops[0]['data']}"
        )
        assert text_ops[0]["data"]["text"], "text op must carry a non-empty string"

    def test_transform_produces_begin_rendering_op(self, account_balances_ops):
        """Output must include an a2ui_op carrying 'beginRendering'."""
        br_ops = [
            op for op in account_balances_ops
            if "beginRendering" in op.get("data", {})
        ]
        assert br_ops, "No op with 'beginRendering' in transform output"
        br = br_ops[0]["data"]["beginRendering"]
        assert "surfaceId" in br, f"'beginRendering' missing 'surfaceId': {br}"
        assert "root" in br, f"'beginRendering' missing 'root': {br}"
        assert br["surfaceId"].startswith("response_"), (
            f"surfaceId should start with 'response_', got: {br['surfaceId']!r}"
        )

    def test_transform_produces_surface_update_op(self, account_balances_ops):
        """Output must include at least one a2ui_op carrying 'surfaceUpdate'."""
        su_ops = [
            op for op in account_balances_ops
            if "surfaceUpdate" in op.get("data", {})
        ]
        assert su_ops, "No op with 'surfaceUpdate' in transform output"
        su = su_ops[0]["data"]["surfaceUpdate"]
        assert "components" in su, f"'surfaceUpdate' missing 'components': {su}"
        assert isinstance(su["components"], list), "'components' must be a list"
        assert su["components"], "'components' must be non-empty"

    def test_transform_produces_data_model_update_op(self, account_balances_ops):
        """Output must include an a2ui_op carrying 'dataModelUpdate'."""
        dmu_ops = [
            op for op in account_balances_ops
            if "dataModelUpdate" in op.get("data", {})
        ]
        assert dmu_ops, "No op with 'dataModelUpdate' in transform output"
        dmu = dmu_ops[0]["data"]["dataModelUpdate"]
        assert "contents" in dmu, f"'dataModelUpdate' missing 'contents': {dmu}"
        assert isinstance(dmu["contents"], list), "'contents' must be a list"

    def test_all_surface_update_components_have_id_and_component_keys(
        self, account_balances_ops
    ):
        """Every entry in every surfaceUpdate.components list must have 'id' and 'component'."""
        violations: list[str] = []
        for op in account_balances_ops:
            if "surfaceUpdate" in op.get("data", {}):
                for entry in op["data"]["surfaceUpdate"]["components"]:
                    if "id" not in entry:
                        violations.append(f"Missing 'id': {entry}")
                    if "component" not in entry:
                        violations.append(f"Missing 'component': {entry}")
        assert not violations, (
            f"surfaceUpdate component format violations:\n"
            + "\n".join(f"  • {v}" for v in violations)
        )

    def test_no_component_has_bare_widget_type_as_top_key(self, account_balances_ops):
        """
        CRITICAL regression test: no surfaceUpdate component entry must carry a bare
        widget type (e.g., "Column", "Row") as its ONLY top-level key alongside 'id'.

        WRONG (pre-fix):  {"id": "root", "Column": {"children": ...}}
        RIGHT (expected): {"id": "root", "component": {"Column": {"children": ...}}}
        """
        KNOWN_WIDGET_TYPES = {
            "Column", "Row", "List", "Text", "Card", "Button",
            "ListItem", "Divider", "Image", "Spacer", "Icon",
        }
        violations: list[str] = []
        for op in account_balances_ops:
            if "surfaceUpdate" in op.get("data", {}):
                for entry in op["data"]["surfaceUpdate"]["components"]:
                    top_keys = set(entry.keys())
                    bare_widgets = top_keys & KNOWN_WIDGET_TYPES
                    if bare_widgets:
                        violations.append(
                            f"Component '{entry.get('id', '?')}' has bare widget key(s) "
                            f"{bare_widgets} at top level. "
                            f"All widget types must be inside 'component'."
                        )
        assert not violations, (
            f"Bare-widget-type violations ({len(violations)} found):\n"
            + "\n".join(f"  • {v}" for v in violations)
        )

    def test_transform_done_op_is_last(self, account_balances_ops):
        """The 'done' op must be the final operation emitted by the pipeline."""
        assert account_balances_ops, "transform produced no operations"
        last = account_balances_ops[-1]
        assert last.get("type") == "done", (
            f"Expected last op type='done', got: {last.get('type')!r}"
        )

    def test_transform_text_op_is_first(self, account_balances_ops):
        """The text op must be emitted first (user sees summary immediately)."""
        assert account_balances_ops
        first = account_balances_ops[0]
        assert first.get("type") == "text", (
            f"Expected first op type='text', got: {first.get('type')!r}"
        )

    def test_transform_with_arrays_produces_valuearray_entry(
        self, rendered_transaction_history, transaction_history_ops
    ):
        """transaction_history includes arrays → dataModelUpdate must contain a single
        'transactions' entry with a valueArray key (not dozens of flat /{key}/{i} entries)."""
        dmu_ops = [
            op for op in transaction_history_ops
            if "dataModelUpdate" in op.get("data", {})
        ]
        assert dmu_ops, "No dataModelUpdate op for transaction_history"

        all_entries: list[dict] = []
        for op in dmu_ops:
            all_entries.extend(op["data"]["dataModelUpdate"]["contents"])

        # Must have exactly ONE entry for "transactions" (not dozens of flat path entries)
        tx_entries = [e for e in all_entries if e.get("key") == "transactions"]
        assert len(tx_entries) == 1, (
            f"Expected exactly 1 'transactions' entry, found {len(tx_entries)}. "
            f"Keys present: {[e.get('key') for e in all_entries]}"
        )

        tx_entry = tx_entries[0]
        assert "valueArray" in tx_entry, (
            f"Expected 'valueArray' key in transactions entry, got: {list(tx_entry.keys())}"
        )
        assert isinstance(tx_entry["valueArray"], list), "'valueArray' must be a list"
        assert len(tx_entry["valueArray"]) == 14, (
            f"Expected 14 transaction items, got {len(tx_entry['valueArray'])}"
        )

        # Verify the first item has the expected fields
        first_item = tx_entry["valueArray"][0]
        for field in ("action", "date", "amount"):
            assert field in first_item, (
                f"Expected field '{field}' in first transaction item, got: {list(first_item.keys())}"
            )

        # Confirm old flat sentinel format is gone
        flat_keys = [e.get("key", "") for e in all_entries if e.get("key", "").startswith("/transactions/")]
        assert not flat_keys, (
            f"Found old flat path entries (should not exist): {flat_keys[:5]}"
        )

    def test_transform_surface_ids_are_consistent(self, account_balances_ops):
        """All a2ui_op events must share the same surfaceId."""
        surface_ids: set[str] = set()
        for op in account_balances_ops:
            data = op.get("data", {})
            for key in ("beginRendering", "dataModelUpdate", "surfaceUpdate"):
                if key in data:
                    sid = data[key].get("surfaceId")
                    if sid:
                        surface_ids.add(sid)
        assert len(surface_ids) == 1, (
            f"All a2ui ops should share one surfaceId, found: {surface_ids}"
        )


# ---------------------------------------------------------------------------
# Helper for TestSyncEndpointFormat
# ---------------------------------------------------------------------------

def assemble_components_from_ops(ops: list[dict]) -> dict:
    """
    Mirrors the sync endpoint's component-assembly logic in agent.py.

    Iterates all ops, picks entries from surfaceUpdate.components, and builds
    the final components map as:
        {component_id: {"componentProperties": {WidgetType: {...}}}}

    This wrapping step is what Bug #1 was missing — without it the Android client
    receives raw {"WidgetType": {...}} and ComponentDto.componentProperties is empty.
    """
    all_components: dict = {}
    for op in ops:
        if "surfaceUpdate" in op.get("data", {}):
            for entry in op["data"]["surfaceUpdate"]["components"]:
                all_components[entry["id"]] = {
                    "componentProperties": entry["component"]
                }
    return all_components


# ---------------------------------------------------------------------------
# TestArrayEncoding — encode_array_entry unit tests
# ---------------------------------------------------------------------------

class TestArrayEncoding:
    """Unit tests for encode_array_entry()."""

    def test_encode_array_entry_format(self):
        result = encode_array_entry("transactions", [{"action": "A", "date": "B", "amount": "C"}])
        assert result["key"] == "transactions"
        assert not result["key"].startswith("/"), "key must not have leading slash"
        assert "valueArray" in result
        assert len(result["valueArray"]) == 1
        assert result["valueArray"][0] == {"action": "A", "date": "B", "amount": "C"}

    def test_encode_array_entry_preserves_all_fields(self):
        items = [
            {"action": "Direct Deposit", "date": "2026-03-28", "amount": "+$4,250.00"},
            {"action": "Buy NVDA", "date": "2026-03-26", "amount": "-$2,184.00"},
        ]
        result = encode_array_entry("transactions", items)
        assert len(result["valueArray"]) == 2
        assert result["valueArray"][0]["action"] == "Direct Deposit"
        assert result["valueArray"][1]["amount"] == "-$2,184.00"

    def test_encode_array_entry_empty_list(self):
        result = encode_array_entry("items", [])
        assert result["key"] == "items"
        assert result["valueArray"] == []

# ---------------------------------------------------------------------------
# TestSyncEndpointFormat — pure unit tests
# ---------------------------------------------------------------------------

class TestSyncEndpointFormat:
    """
    Unit tests for the sync endpoint's component-assembly logic.

    These tests validate the Bug #1 fix: components delivered to the Android
    client must be wrapped in {"componentProperties": {...}} so that
    ComponentDto.componentProperties can deserialize them.
    """

    @pytest.fixture(scope="class")
    def assembled(self, account_balances_ops) -> dict:
        """Assembled components dict from real transform output."""
        result = assemble_components_from_ops(account_balances_ops)
        assert result, "assemble_components_from_ops returned an empty dict"
        return result

    def test_assembled_components_have_componentProperties_key(self, assembled):
        """Every assembled component must have 'componentProperties' as a top-level key."""
        violations: list[str] = []
        for comp_id, comp_obj in assembled.items():
            if not isinstance(comp_obj, dict):
                violations.append(f"{comp_id}: not a dict (got {type(comp_obj).__name__})")
                continue
            if "componentProperties" not in comp_obj:
                violations.append(
                    f"{comp_id}: missing 'componentProperties' "
                    f"(top-level keys: {list(comp_obj.keys())})"
                )
        assert not violations, (
            f"Bug #1 regression — components missing 'componentProperties' wrap:\n"
            + "\n".join(f"  • {v}" for v in violations)
        )

    def test_componentProperties_value_is_not_empty(self, assembled):
        """The 'componentProperties' dict for every component must be non-empty."""
        empty_props: list[str] = []
        for comp_id, comp_obj in assembled.items():
            if not isinstance(comp_obj, dict):
                continue
            props = comp_obj.get("componentProperties", {})
            if not props:
                empty_props.append(comp_id)
        assert not empty_props, (
            f"{len(empty_props)} component(s) have empty componentProperties: {empty_props}"
        )

    def test_componentProperties_contains_widget_type(self, assembled):
        """
        Each componentProperties value must contain at least one key (the widget type name).
        An empty componentProperties means the Android client will render an 'Unknown widget' error.
        """
        violations: list[str] = []
        for comp_id, comp_obj in assembled.items():
            if not isinstance(comp_obj, dict):
                continue
            props = comp_obj.get("componentProperties", {})
            if not props:
                violations.append(f"{comp_id}: empty componentProperties")
        assert not violations, (
            f"Components with empty componentProperties:\n"
            + "\n".join(f"  • {v}" for v in violations)
        )

    def test_assembled_root_component_exists(self, rendered_account_balances, assembled):
        """
        The root component ID (from uiDefinition.root) must appear as a key in
        the assembled components dict.  If it's absent, the surface cannot render.
        """
        root_id = rendered_account_balances["uiDefinition"].get("root")
        assert root_id, "uiDefinition.root must be non-empty"
        assert root_id in assembled, (
            f"Root component '{root_id}' not found in assembled components. "
            f"Available keys: {list(assembled.keys())[:10]}"
        )

    def test_assemble_handles_multiple_surface_update_chunks(
        self, unit_renderer, transaction_history_ops
    ):
        """
        transaction_history produces multiple surfaceUpdate chunks.
        assemble_components_from_ops must merge all of them into one dict.
        """
        su_chunks = [
            op for op in transaction_history_ops
            if "surfaceUpdate" in op.get("data", {})
        ]
        assert len(su_chunks) >= 1, "Expected at least one surfaceUpdate chunk"

        assembled = assemble_components_from_ops(transaction_history_ops)
        total_entries = sum(
            len(op["data"]["surfaceUpdate"]["components"])
            for op in su_chunks
        )
        assert len(assembled) == total_entries, (
            f"Expected {total_entries} assembled components "
            f"(sum of all chunks), got {len(assembled)}"
        )



# ---------------------------------------------------------------------------
# New tests for code-review blocking issues
# ---------------------------------------------------------------------------

import shutil
import tempfile
from unittest.mock import patch
from intent_router import _load_rules, _ExactRule, _KeywordRule
from agent import transform_to_path_bindings, _normalize_components, app
from fastapi.testclient import TestClient

_REAL_TEMPLATES_DIR = str(Path(__file__).parent / "templates")


class TestIntentRouterRobustness:
    """
    BLOCK 1 — intent router must NOT crash on malformed template files.
    Any single bad file should log a warning and be skipped; all other
    valid templates must still produce rules.
    """

    def _copy_real_templates_to(self, dest: str) -> None:
        """Copy the 3 built-in templates into *dest*."""
        for f in sorted(Path(_REAL_TEMPLATES_DIR).glob("*.json")):
            shutil.copy(f, dest)

    def test_malformed_top_level_list(self) -> None:
        """
        A template file whose JSON is a top-level list (not a dict) must be
        skipped with a warning; the real templates must still produce rules.
        Confirms classify("transactions") still routes to transaction_history.
        """
        with tempfile.TemporaryDirectory() as tmpdir:
            self._copy_real_templates_to(tmpdir)
            # Inject bad file — top-level JSON list
            bad = Path(tmpdir) / "bad_list.json"
            bad.write_text(json.dumps([1, 2, 3]))

            exact, keyword = _load_rules(tmpdir)

        # Real templates should still yield rules
        assert exact or keyword, "No rules loaded — real templates were not processed"
        template_ids = {r.template_id for r in exact} | {r.template_id for r in keyword}
        assert "transaction_history" in template_ids, (
            "transaction_history rules missing after loading alongside bad file"
        )
        # Module-level rules are unaffected — classify still works
        result = classify("show my transactions")
        assert result is not None
        assert result.template_id == "transaction_history"

    def test_exact_trigger_contains_non_string(self) -> None:
        """
        A template whose intentTriggers.exact contains a list of non-strings
        (e.g. [1, 2]) must be skipped gracefully — no crash, no rules added
        for that entry, and real template rules survive.
        """
        with tempfile.TemporaryDirectory() as tmpdir:
            self._copy_real_templates_to(tmpdir)
            # Inject bad template — exact trigger with integer keywords
            bad_template = {
                "templateId": "bad_exact",
                "intentTriggers": {
                    "exact": [[1, 2]],   # integers, not strings
                    "keywords": [],
                },
            }
            (Path(tmpdir) / "bad_exact.json").write_text(json.dumps(bad_template))

            exact, keyword = _load_rules(tmpdir)

        # bad_exact must NOT appear as an exact rule
        bad_rule_ids = [r.template_id for r in exact if r.template_id == "bad_exact"]
        assert not bad_rule_ids, (
            f"bad_exact exact rule should have been skipped; got: {bad_rule_ids}"
        )
        # Real template rules must still be present
        all_ids = {r.template_id for r in exact} | {r.template_id for r in keyword}
        assert "account_balances" in all_ids or "transaction_history" in all_ids, (
            "Real template rules missing after loading alongside bad_exact"
        )

    def test_missing_intent_triggers_field(self) -> None:
        """
        A template with no intentTriggers field must be silently skipped
        (no rules added for it) without crashing; real templates must still load.
        """
        with tempfile.TemporaryDirectory() as tmpdir:
            self._copy_real_templates_to(tmpdir)
            # Inject template without intentTriggers field
            no_triggers = {
                "templateId": "no_triggers",
                "name": "No Triggers Template",
                # intentTriggers deliberately absent
            }
            (Path(tmpdir) / "no_triggers.json").write_text(json.dumps(no_triggers))

            exact, keyword = _load_rules(tmpdir)

        # no_triggers contributes nothing (it has no triggers — neither crash nor phantom rules)
        no_triggers_exact = [r for r in exact if r.template_id == "no_triggers"]
        no_triggers_kw = [r for r in keyword if r.template_id == "no_triggers"]
        assert not no_triggers_exact and not no_triggers_kw, (
            "no_triggers template should produce zero rules"
        )
        # Real template rules must still be present
        all_ids = {r.template_id for r in exact} | {r.template_id for r in keyword}
        assert any(tid in all_ids for tid in ("account_balances", "transaction_history", "brokerage_activity")), (
            f"Real template rules missing; loaded ids: {all_ids}"
        )


class TestDesignerSaveTemplate:
    """
    BLOCK 2 — /designer/save-template must refuse to overwrite approved templates
    and allow overwriting drafts.
    """

    _DRAFT_ID = "test_draft_overwrite_fixture"

    @pytest.fixture(autouse=True)
    def _cleanup_draft(self):
        """Remove any leftover test draft before and after each test."""
        base = Path(__file__).parent
        tfile = base / "templates" / f"{self._DRAFT_ID}.json"
        dfile = base / "data" / f"{self._DRAFT_ID}.json"
        for f in (tfile, dfile):
            f.unlink(missing_ok=True)
        yield
        for f in (tfile, dfile):
            f.unlink(missing_ok=True)

    @property
    def _client(self) -> TestClient:
        return TestClient(app, raise_server_exceptions=False)

    def _minimal_request(self, template_id: str) -> dict:
        return {
            "templateId": template_id,
            "name": "Test Template",
            "intentTriggers": {"exact": [], "keywords": [template_id]},
            "uiDefinition": {
                "root": "root_comp",
                "components": {
                    "root_comp": {
                        "componentProperties": {
                            "Text": {"text": {"literalString": "Hello"}},
                        }
                    }
                },
            },
            "textTemplate": "Hello",
            "description": "A test template",
            "version": "1.0.0",
        }

    def test_save_template_conflict_with_approved(self) -> None:
        """
        Attempting to save with an ID that matches an approved built-in template
        (account_balances has no 'status' field → treated as approved) must
        return HTTP 409 Conflict.
        """
        client = self._client
        resp = client.post(
            "/designer/save-template",
            json=self._minimal_request("account_balances"),
        )
        assert resp.status_code == 409, (
            f"Expected 409 for approved template overwrite, got {resp.status_code}. "
            f"Body: {resp.text}"
        )
        assert "already exists and is approved" in resp.json().get("detail", ""), (
            f"409 detail message missing expected text. Got: {resp.text}"
        )

    def test_save_template_allows_overwrite_draft(self) -> None:
        """
        Saving a new draft template and then saving again with the same ID must
        succeed (HTTP 200) on the second call — overwriting a draft is allowed.
        """
        client = self._client
        payload = self._minimal_request(self._DRAFT_ID)

        # First save — creates draft
        r1 = client.post("/designer/save-template", json=payload)
        assert r1.status_code == 200, (
            f"First save failed with {r1.status_code}: {r1.text}"
        )
        assert r1.json().get("status") == "draft"

        # Second save — overwrites existing draft
        r2 = client.post("/designer/save-template", json=payload)
        assert r2.status_code == 200, (
            f"Second save (overwrite draft) failed with {r2.status_code}: {r2.text}. "
            "Expected 200 — overwriting a draft must be allowed."
        )


class TestTransformPathBindingsGeneric:
    """
    BLOCK 3 — transform_to_path_bindings() must handle any widget type, not just Text.
    """

    def test_transform_path_bindings_handles_button_labels(self) -> None:
        """
        A Button component with label: {literalString: "Submit"} must be transformed
        to label: {path: "/<comp_id>_label"} and a matching DataModel entry must be
        added.
        """
        comp_id = "submit_btn"
        components = {
            comp_id: {
                "componentProperties": {
                    "Button": {
                        "label": {"literalString": "Submit"},
                    }
                }
            }
        }

        transformed, data_entries = transform_to_path_bindings(components)

        # Transformed component must have label as a path binding
        button_cfg = transformed[comp_id]["componentProperties"]["Button"]
        expected_path = f"/{comp_id}_label"
        assert button_cfg.get("label") == {"path": expected_path}, (
            f"Expected label to be transformed to {{path: '{expected_path}'}}, "
            f"got: {button_cfg.get('label')}"
        )

        # DataModel entries must contain the label entry
        entry_keys = [e["key"] for e in data_entries]
        expected_key = f"{comp_id}_label"
        assert expected_key in entry_keys, (
            f"Expected DataModel entry with key '{expected_key}', got: {entry_keys}"
        )
        label_entry = next(e for e in data_entries if e["key"] == expected_key)
        assert label_entry["valueString"] == "Submit", (
            f"Expected valueString='Submit', got: {label_entry['valueString']}"
        )

    def test_transform_text_widget_preserves_backward_compat_key(self) -> None:
        """
        Existing behavior: Text widget's text.literalString must use path /{comp_id}
        (no suffix) — not /{comp_id}_text.  This ensures existing templates are not broken.
        """
        comp_id = "balance_label"
        components = {
            comp_id: {
                "componentProperties": {
                    "Text": {
                        "text": {"literalString": "$5,000.00"},
                    }
                }
            }
        }

        transformed, data_entries = transform_to_path_bindings(components)

        text_cfg = transformed[comp_id]["componentProperties"]["Text"]
        assert text_cfg.get("text") == {"path": f"/{comp_id}"}, (
            f"Text.text path should be '/{comp_id}' (no suffix), got: {text_cfg.get('text')}"
        )
        assert any(e["key"] == comp_id and e["valueString"] == "$5,000.00" for e in data_entries), (
            f"Expected DataModel entry key='{comp_id}', valueString='$5,000.00'. Entries: {data_entries}"
        )

    def test_transform_non_literal_config_passes_through_unchanged(self) -> None:
        """
        Config values that are NOT literalString dicts (paths, plain strings, numbers)
        must pass through unchanged.
        """
        comp_id = "path_comp"
        components = {
            comp_id: {
                "componentProperties": {
                    "Text": {
                        "text": {"path": "/some/path"},
                        "style": "bold",
                    }
                }
            }
        }

        transformed, data_entries = transform_to_path_bindings(components)

        text_cfg = transformed[comp_id]["componentProperties"]["Text"]
        assert text_cfg["text"] == {"path": "/some/path"}, "Path binding should pass through unchanged"
        assert text_cfg["style"] == "bold", "Non-dict value should pass through unchanged"
        assert data_entries == [], f"No data entries expected, got: {data_entries}"


# ---------------------------------------------------------------------------
# BLOCK 5 — _normalize_components + designer/save-template Android wire format
# ---------------------------------------------------------------------------

class TestNormalizeComponents:
    """Unit tests for the _normalize_components helper."""

    def test_list_format_converts_to_dict(self) -> None:
        raw = [
            {"id": "c1", "component": {"Text": {"text": {"literalString": "Hi"}}}},
            {"id": "c2", "component": {"Button": {"label": {"literalString": "Tap"}}}},
        ]
        result = _normalize_components(raw)
        assert "c1" in result
        assert "c2" in result
        assert result["c1"]["componentProperties"]["Text"]["text"]["literalString"] == "Hi"
        assert result["c2"]["componentProperties"]["Button"]["label"]["literalString"] == "Tap"

    def test_dict_passthrough(self) -> None:
        raw = {"c1": {"componentProperties": {"Text": {}}}}
        result = _normalize_components(raw)
        assert result is raw  # exact same dict returned

    def test_empty_list_returns_empty_dict(self) -> None:
        assert _normalize_components([]) == {}

    def test_none_returns_empty_dict(self) -> None:
        assert _normalize_components(None) == {}

    def test_items_without_id_are_skipped(self) -> None:
        raw = [
            {"component": {"Text": {}}},  # missing id
            {"id": "c1", "component": {"Text": {}}},
        ]
        result = _normalize_components(raw)
        assert list(result.keys()) == ["c1"]

    def test_missing_component_key_defaults_to_empty_dict(self) -> None:
        raw = [{"id": "c1"}]  # no "component" key
        result = _normalize_components(raw)
        assert result["c1"]["componentProperties"] == {}


class TestDesignerSaveTemplateListFormat:
    """
    Regression tests: Android client sends components as a JSON array —
    must not produce a 500 AttributeError.
    """

    _LIST_ID = "list_format_test_abc12345"

    @pytest.fixture(autouse=True)
    def _cleanup(self):
        base = Path(__file__).parent
        tfile = base / "templates" / f"{self._LIST_ID}.json"
        dfile = base / "data" / f"{self._LIST_ID}.json"
        for f in (tfile, dfile):
            f.unlink(missing_ok=True)
        yield
        for f in (tfile, dfile):
            f.unlink(missing_ok=True)

    @property
    def _client(self) -> TestClient:
        return TestClient(app, raise_server_exceptions=False)

    def test_save_template_with_list_components(self) -> None:
        """Android client sends components as a list — must not 500."""
        resp = self._client.post("/designer/save-template", json={
            "name": "List Format Test",
            "templateId": self._LIST_ID,
            "description": "Tests Android wire format",
            "textTemplate": "Test",
            "intentTriggers": {"exact": [], "keywords": ["test"]},
            "uiDefinition": {
                "surfaceId": "test_surface",
                "root": "comp1",
                "components": [
                    {
                        "id": "comp1",
                        "component": {
                            "Text": {"text": {"literalString": "Hello World"}}
                        }
                    }
                ]
            }
        })
        assert resp.status_code in (200, 201), (
            f"Expected 2xx, got {resp.status_code}: {resp.text}"
        )
        data = resp.json()
        assert data["templateId"] == self._LIST_ID
        assert data["status"] == "draft"

    def test_save_template_with_list_components_multi(self) -> None:
        """Multiple components in list format — all must be persisted in the saved template file."""
        resp = self._client.post("/designer/save-template", json={
            "name": "Multi-Component List Format",
            "templateId": self._LIST_ID,
            "description": "Tests multi-component Android wire format",
            "textTemplate": "Multi test",
            "intentTriggers": {"exact": [], "keywords": ["multitest"]},
            "uiDefinition": {
                "surfaceId": "test_surface",
                "root": "comp1",
                "components": [
                    {
                        "id": "comp1",
                        "component": {
                            "Text": {"text": {"literalString": "Hello World"}}
                        }
                    },
                    {
                        "id": "btn1",
                        "component": {
                            "Button": {"label": {"literalString": "Tap Me"}}
                        }
                    }
                ]
            }
        })
        assert resp.status_code in (200, 201), (
            f"Expected 2xx, got {resp.status_code}: {resp.text}"
        )
        data = resp.json()
        assert data["templateId"] == self._LIST_ID

        # The response body is a SaveTemplateResponse (no uiDefinition).
        # Verify that both components were correctly persisted in the template file on disk.
        base = Path(__file__).parent
        tfile = base / "templates" / f"{self._LIST_ID}.json"
        assert tfile.exists(), f"Template file not written: {tfile}"
        saved = json.loads(tfile.read_text())
        saved_components = saved.get("uiDefinition", {}).get("components", {})
        assert "comp1" in saved_components, (
            f"comp1 missing from saved components: {list(saved_components.keys())}"
        )
        assert "btn1" in saved_components, (
            f"btn1 missing from saved components: {list(saved_components.keys())}"
        )


# ---------------------------------------------------------------------------
# TestDataModelExtraction — unit tests for path-based dataModel support
# ---------------------------------------------------------------------------

from agent import parse_agent_response, transform_to_operations as llm_transform_to_operations

_FULL_RESPONSE_RAW = {
    "text": "Here are your recent trades.",
    "uiDefinition": {
        "surfaceId": "response_test01",
        "root": "root",
        "components": {
            "root": {
                "id": "root",
                "componentProperties": {
                    "Column": {"children": {"explicitList": ["txn_list"]}}
                }
            },
            "txn_list": {
                "id": "txn_list",
                "componentProperties": {
                    "List": {
                        "children": {
                            "path": "/transactions",
                            "componentId": "txn_template"
                        }
                    }
                }
            },
            "txn_template": {
                "id": "txn_template",
                "componentProperties": {
                    "ListItem": {
                        "label":    {"path": "action"},
                        "value":    {"path": "amount"},
                        "subValue": {"path": "date"}
                    }
                }
            }
        }
    },
    "dataModel": {
        "title": "Recent Trades",
        "count": "3 trades",
        "transactions": [
            {"action": "Buy AAPL 10 shares", "date": "2024-03-15", "amount": "-$1,875.00"},
            {"action": "Sell TSLA 5 shares",  "date": "2024-03-10", "amount": "+$1,226.50"},
        ]
    }
}

_FULL_RESPONSE = json.dumps(_FULL_RESPONSE_RAW)

_LEGACY_RESPONSE = json.dumps({
    "text": "Here is your balance.",
    "uiDefinition": {
        "surfaceId": "response_legcy",
        "root": "root",
        "components": {
            "root": {
                "id": "root",
                "componentProperties": {
                    "Text": {
                        "text": {"literalString": "$25,000.00"},
                        "usageHint": "h3"
                    }
                }
            }
        }
    }
    # NOTE: no "dataModel" key — this is the legacy literalString format
})


class TestDataModelExtraction:
    """
    Unit tests covering the new path-based LLM output format:
      {text, uiDefinition, dataModel}

    Tests verify:
    1. parse_agent_response() extracts `dataModel` field when present
    2. parse_agent_response() returns data_model=None when absent (backward compat)
    3. transform_to_operations() uses LLM-provided data_model for dataModelUpdate contents
    4. transform_to_operations() falls back to transform_to_path_bindings() when data_model=None
    5. Array values in data_model are encoded as valueArray (not valueString)
    """

    # ─── Test 1: parse_agent_response extracts scalar dataModel fields ────────────

    def test_parse_extracts_data_model_scalar_fields(self):
        """parse_agent_response() must populate AgentResponse.data_model from the top-level
        'dataModel' field in the JSON response."""
        response = parse_agent_response(_FULL_RESPONSE, "dm1")

        assert response.data_model is not None, (
            "data_model must not be None when dataModel is present in the LLM JSON response"
        )
        assert isinstance(response.data_model, dict), "data_model must be a dict"
        assert "title" in response.data_model, "scalar key 'title' must be present in data_model"
        assert response.data_model["title"] == "Recent Trades", (
            f"Expected 'Recent Trades', got {response.data_model['title']!r}"
        )
        assert response.data_model["count"] == "3 trades"

    # ─── Test 1b: parse_agent_response extracts array fields ─────────────────────

    def test_parse_extracts_data_model_array_fields(self):
        """parse_agent_response() must extract list values from dataModel."""
        response = parse_agent_response(_FULL_RESPONSE, "dm1b")

        assert response.data_model is not None
        txns = response.data_model.get("transactions")
        assert isinstance(txns, list), f"transactions must be a list, got {type(txns).__name__}"
        assert len(txns) == 2, f"Expected 2 transactions, got {len(txns)}"
        assert txns[0]["action"] == "Buy AAPL 10 shares"
        assert txns[1]["amount"] == "+$1,226.50"

    # ─── Test 2: data_model=None when absent (backward compat) ───────────────────

    def test_parse_data_model_none_when_absent(self):
        """For legacy responses without a 'dataModel' key, AgentResponse.data_model must be None."""
        response = parse_agent_response(_LEGACY_RESPONSE, "dm2")

        assert response.data_model is None, (
            "data_model must be None when 'dataModel' key is absent from the LLM JSON response "
            "(backward compatibility requirement)"
        )

    def test_parse_conversational_both_none(self):
        """Conversational response: uiDefinition=null and dataModel=null both become None."""
        raw = json.dumps({
            "text": "I can help with balances and transactions.",
            "uiDefinition": None,
            "dataModel": None
        })
        response = parse_agent_response(raw, "dm2b")
        assert response.ui_definition is None
        assert response.data_model is None

    # ─── Test 3: transform_to_operations uses LLM data_model for dataModelUpdate ──

    def test_transform_uses_llm_data_model_for_dmu_contents(self):
        """When a 'dataModel' dict is present in the parsed response, dataModelUpdate.contents
        must be built from it — not from literalString extraction."""
        parsed = json.loads(_FULL_RESPONSE)
        parsed["uiDefinition"]["surfaceId"] = "response_dm3"

        ops = llm_transform_to_operations(parsed, "dm3")

        dmu_ops = [op for op in ops if "dataModelUpdate" in op.get("data", {})]
        assert dmu_ops, "Expected at least one dataModelUpdate operation"
        contents = dmu_ops[0]["data"]["dataModelUpdate"]["contents"]

        title_entries = [e for e in contents if e.get("key") == "title"]
        assert title_entries, "dataModelUpdate must have an entry for 'title'"
        assert title_entries[0].get("valueString") == "Recent Trades", (
            f"Expected valueString='Recent Trades', got {title_entries[0]}"
        )

        count_entries = [e for e in contents if e.get("key") == "count"]
        assert count_entries, "dataModelUpdate must have an entry for 'count'"
        assert count_entries[0].get("valueString") == "3 trades"

    # ─── Test 4: fallback to literalString extraction when no data_model ──────────

    def test_transform_falls_back_to_path_bindings_when_no_data_model(self):
        """Without a 'dataModel' key, transform_to_operations() must fall back to
        transform_to_path_bindings() to extract literalString values from components."""
        parsed = json.loads(_LEGACY_RESPONSE)
        # Confirm no dataModel key
        assert "dataModel" not in parsed and "data_model" not in parsed

        ops = llm_transform_to_operations(parsed, "dm4")

        dmu_ops = [op for op in ops if "dataModelUpdate" in op.get("data", {})]
        assert dmu_ops, (
            "Expected a dataModelUpdate op produced by literalString extraction fallback"
        )
        contents = dmu_ops[0]["data"]["dataModelUpdate"]["contents"]

        # The literalString "$25,000.00" from the root Text component must become a DataModel entry
        root_entries = [e for e in contents if e.get("key") == "root"]
        assert root_entries, (
            "Expected a DataModel entry keyed 'root' from the literalString on the root Text. "
            f"All keys: {[e.get('key') for e in contents]}"
        )
        assert root_entries[0].get("valueString") == "$25,000.00", (
            f"Expected '$25,000.00', got {root_entries[0]}"
        )

    # ─── Test 5: arrays → valueArray (NOT valueString) ────────────────────────────

    def test_transform_arrays_encoded_as_valuearray_not_valuestring(self):
        """Array values in data_model must be emitted as 'valueArray', not 'valueString'.
        Android's financialListWidget probes items at /key/0, /key/1, etc. via
        dataContext.getObjectKeys() — this only works when the data is stored as a
        JsonObject (via valueArray), not as a JSON-serialized string (valueString)."""
        parsed = json.loads(_FULL_RESPONSE)
        parsed["uiDefinition"]["surfaceId"] = "response_dm5"

        ops = llm_transform_to_operations(parsed, "dm5")

        dmu_ops = [op for op in ops if "dataModelUpdate" in op.get("data", {})]
        assert dmu_ops, "Expected a dataModelUpdate operation"
        contents = dmu_ops[0]["data"]["dataModelUpdate"]["contents"]

        txn_entries = [e for e in contents if e.get("key") == "transactions"]
        assert txn_entries, (
            f"Expected a 'transactions' entry in dataModelUpdate contents. "
            f"Keys present: {[e.get('key') for e in contents]}"
        )
        entry = txn_entries[0]

        assert "valueArray" in entry, (
            "Array values must use 'valueArray' so Android path-probing works "
            f"(dataContext.getObjectKeys('/transactions/0') etc). Got keys: {list(entry.keys())}"
        )
        assert "valueString" not in entry, (
            "Array values must NOT use 'valueString' (JSON-stringified) — "
            "that breaks Android path-probing for the List children.path pattern"
        )
        assert len(entry["valueArray"]) == 2, (
            f"Expected 2 transactions in valueArray, got {len(entry['valueArray'])}"
        )
        assert entry["valueArray"][0]["action"] == "Buy AAPL 10 shares"
        assert entry["valueArray"][1]["amount"] == "+$1,226.50"
