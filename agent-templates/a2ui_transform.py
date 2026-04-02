"""
A2UI Transform Pipeline — extracted from agent/agent.py for the template agent.

This module contains the reusable transform functions that convert LLM JSON
responses into A2UI v0.8 protocol operations (template expansion, path bindings,
sanitization, chunking).  It is fully self-contained with ZERO third-party
dependencies — only Python stdlib is used.
"""

import json
import logging
import random
import re
import string

logger = logging.getLogger("a2ui_transform")

# ─── Constants ────────────────────────────────────────────────────────────────

MAX_TEMPLATE_ITEMS = 200


# ─── Helpers ──────────────────────────────────────────────────────────────────

def _random_suffix(n: int = 6) -> str:
    return "".join(random.choices(string.ascii_lowercase + string.digits, k=n))


def _replace_index(s: str, index: int) -> str:
    """Replace {i} placeholder in a string."""
    return s.replace("{i}", str(index))


# ─── Template Expansion ──────────────────────────────────────────────────────

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


# ─── Path Bindings & Sanitization ─────────────────────────────────────────────

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


# ─── Chunking & Final Transform ───────────────────────────────────────────────

def chunk_components(components: dict, chunk_size: int = 15) -> list[list[dict]]:
    """Split components into chunks for progressive surfaceUpdate emissions."""
    comp_list = []
    for comp_id, comp_data in components.items():
        props = comp_data.get("componentProperties", {})
        comp_list.append({"id": comp_id, "component": props})

    return [comp_list[i:i + chunk_size] for i in range(0, len(comp_list), chunk_size)]


def transform_to_operations(parsed_response: dict, surface_suffix: str, chunk_size: int = 15) -> list[dict]:
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
        chunks = chunk_components(transformed_components, chunk_size=chunk_size)
        logger.debug("[transform] chunked into %d batches", len(chunks))
        for chunk in chunks:
            operations.append({
                "type": "a2ui_op",
                "data": {"surfaceUpdate": {"surfaceId": surface_id, "components": chunk}},
            })

    # 5. done
    operations.append({"type": "done", "data": {}})

    return operations
