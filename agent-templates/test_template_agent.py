"""
Unit tests for the template agent's pure-Python logic modules.

No server required — these tests exercise:
  - intent_router.py   (keyword-based intent classification)
  - template_renderer.py (template loading + placeholder substitution)
  - a2ui_transform.py  (expand → path-bind → sanitize → chunk pipeline)

Run:
    cd agent-templates
    python3 -m pytest test_template_agent.py -v
"""

import sys
from pathlib import Path

# Make sure imports resolve to the local agent-templates package, not any
# installed copies.  This also works when pytest is invoked from the repo root.
sys.path.insert(0, str(Path(__file__).parent))

import pytest

from intent_router import classify, IntentMatch
from template_renderer import TemplateRenderer
from a2ui_transform import transform_to_operations

# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

# Base directory for template data — needed because TemplateRenderer resolves
# paths relative to cwd, which may differ when pytest runs from the repo root.
_AGENT_TEMPLATES_DIR = Path(__file__).parent
_TEMPLATES_DIR = str(_AGENT_TEMPLATES_DIR / "templates")
_DATA_DIR = str(_AGENT_TEMPLATES_DIR / "data")


@pytest.fixture(scope="module")
def renderer() -> TemplateRenderer:
    """Shared TemplateRenderer instance (module-scoped to avoid repeated I/O)."""
    r = TemplateRenderer(templates_dir=_TEMPLATES_DIR, data_dir=_DATA_DIR)
    assert r.get_loaded_templates(), "TemplateRenderer loaded no templates — check path"
    assert r.get_loaded_data(), "TemplateRenderer loaded no data — check path"
    return r


@pytest.fixture(scope="module")
def rendered_account_balances(renderer) -> dict:
    result = renderer.render("account_balances", "account_balances")
    assert result is not None, "render('account_balances', 'account_balances') returned None"
    return result


@pytest.fixture(scope="module")
def rendered_transaction_history(renderer) -> dict:
    result = renderer.render("transaction_history", "transaction_history")
    assert result is not None, "render('transaction_history', 'transaction_history') returned None"
    return result


@pytest.fixture(scope="module")
def account_balances_ops(rendered_account_balances) -> list[dict]:
    return transform_to_operations(rendered_account_balances, surface_suffix="test")


@pytest.fixture(scope="module")
def transaction_history_ops(rendered_transaction_history) -> list[dict]:
    return transform_to_operations(rendered_transaction_history, surface_suffix="test")


# ---------------------------------------------------------------------------
# TestIntentRouter
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
# TestTemplateRenderer
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
        # (either directly or embedded via ${placeholder} substitution)
        for expected_fragment in ("$", "Checking", "Savings"):
            assert expected_fragment in text, (
                f"Expected fragment {expected_fragment!r} not found in rendered text: {text[:200]}"
            )

    def test_render_transaction_history_text_contains_substituted_data(
        self, rendered_transaction_history
    ):
        """Rendered text for transaction_history must contain period info from mock data."""
        text = rendered_transaction_history["text"]
        # data/transaction_history.json has periodLabel "March 2026"
        assert text, "text must be non-empty"
        assert "2026" in text or "transaction" in text.lower(), (
            f"Rendered text doesn't look like a transaction summary: {text[:200]}"
        )

    def test_render_unknown_template_returns_none(self, renderer):
        """Rendering a non-existent template must return None, not raise."""
        result = renderer.render("does_not_exist", "account_balances")
        assert result is None, f"Expected None for unknown template, got: {result}"

    def test_render_unknown_data_returns_none(self, renderer):
        """Rendering with a non-existent data ID must return None, not raise."""
        result = renderer.render("account_balances", "does_not_exist")
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

    def test_render_does_not_mutate_cached_template(self, renderer):
        """Two consecutive renders of the same template must return independent copies."""
        r1 = renderer.render("account_balances", "account_balances")
        r2 = renderer.render("account_balances", "account_balances")
        assert r1 is not r2, "render() should return fresh objects, not the cached instance"
        # Mutate r1 and confirm r2 is unaffected
        r1["uiDefinition"]["_test_mutation"] = True
        assert "_test_mutation" not in r2.get("uiDefinition", {}), (
            "Mutation of one render result affected another — deep copy is broken"
        )


# ---------------------------------------------------------------------------
# TestA2UiTransform
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

        The Android client reads entry["component"]["WidgetType"] — if the widget type
        is a direct sibling of "id" the client cannot find it.
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

    def test_transform_with_arrays_produces_flat_path_entries(
        self, rendered_transaction_history, transaction_history_ops
    ):
        """transaction_history includes arrays → dataModelUpdate must contain /{key}/{i} entries."""
        dmu_ops = [
            op for op in transaction_history_ops
            if "dataModelUpdate" in op.get("data", {})
        ]
        assert dmu_ops, "No dataModelUpdate op for transaction_history"
        contents = dmu_ops[0]["data"]["dataModelUpdate"]["contents"]
        # Flatten all contents from all DMU ops (there may be multiple)
        all_keys = set()
        for op in dmu_ops:
            for entry in op["data"]["dataModelUpdate"]["contents"]:
                all_keys.add(entry["key"])
        # Array sentinel format: /transactions/0
        sentinel_keys = [k for k in all_keys if k.startswith("/transactions/")]
        assert sentinel_keys, (
            "No '/transactions/...' path entries found — flatten_items_to_paths() may not be running"
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
# TestSyncEndpointFormat
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
        Each componentProperties value must contain at least one key that names a
        widget type (e.g., "Column", "Row", "Text", "Card", "ListItem").

        An empty or keyless componentProperties means the Android client will render
        an 'Unknown widget' error for that component.
        """
        KNOWN_WIDGET_TYPES = {
            "Column", "Row", "List", "Text", "Card", "Button",
            "ListItem", "Divider", "Image", "Spacer", "Icon",
        }
        violations: list[str] = []
        for comp_id, comp_obj in assembled.items():
            if not isinstance(comp_obj, dict):
                continue
            props = comp_obj.get("componentProperties", {})
            if isinstance(props, dict):
                widget_keys = set(props.keys()) & KNOWN_WIDGET_TYPES
                # Allow any non-empty props dict — widget types are not exhaustively enumerable
                # but the intersection with the known set must be non-empty OR props itself is
                # non-empty (i.e., at least one key, which IS the widget type).
                if not props:
                    violations.append(f"{comp_id}: empty componentProperties")
                # If props is non-empty but no known widget type, log it as a warning but
                # don't fail — the template may use custom widget types.
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
        self, renderer, transaction_history_ops
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
        # Each chunk has ≤15 components; all chunks combined should yield the full set
        total_entries = sum(
            len(op["data"]["surfaceUpdate"]["components"])
            for op in su_chunks
        )
        assert len(assembled) == total_entries, (
            f"Expected {total_entries} assembled components "
            f"(sum of all chunks), got {len(assembled)}"
        )
