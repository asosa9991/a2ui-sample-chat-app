"""
Template renderer — loads pre-approved A2UI templates and mock data,
renders them by substituting placeholders with data values.
"""
import copy
import json
import logging
import re
from pathlib import Path
from typing import Optional

logger = logging.getLogger("template_renderer")


class TemplateRenderer:
    """Load and cache pre-approved A2UI templates and mock data."""

    def __init__(self, templates_dir: str = "templates", data_dir: str = "data"):
        self.templates: dict[str, dict] = {}
        self.data: dict[str, dict] = {}
        self._load_templates(templates_dir)
        self._load_data(data_dir)

    # ── Loading ───────────────────────────────────────────────────────────

    def _load_templates(self, templates_dir: str):
        path = Path(templates_dir)
        if not path.exists():
            logger.error("Templates directory not found: %s", path.absolute())
            return
        for f in sorted(path.glob("*.json")):
            try:
                with open(f) as fh:
                    template = json.load(fh)
                tid = template.get("templateId", f.stem)
                self.templates[tid] = template
                logger.info(
                    "Loaded template: %s (v%s) from %s",
                    tid,
                    template.get("version", "?"),
                    f.name,
                )
            except (json.JSONDecodeError, OSError) as e:
                logger.error("Failed to load template %s: %s", f, e)

    def _load_data(self, data_dir: str):
        path = Path(data_dir)
        if not path.exists():
            logger.error("Data directory not found: %s", path.absolute())
            return
        for f in sorted(path.glob("*.json")):
            try:
                with open(f) as fh:
                    data = json.load(fh)
                did = f.stem
                self.data[did] = data
                logger.info("Loaded data: %s from %s", did, f.name)
            except (json.JSONDecodeError, OSError) as e:
                logger.error("Failed to load data %s: %s", f, e)

    # ── Rendering ─────────────────────────────────────────────────────────

    def render(self, template_id: str, data_id: str) -> Optional[dict]:
        """
        Render a template with data.

        Returns ``{"text": "...", "uiDefinition": {...}}`` compatible with
        ``transform_to_operations()``, or *None* if template/data not found.
        """
        template = self.templates.get(template_id)
        data = self.data.get(data_id)

        if not template:
            logger.warning(
                "Template not found: %s (available: %s)",
                template_id,
                list(self.templates.keys()),
            )
            return None
        if not data:
            logger.warning(
                "Data not found: %s (available: %s)",
                data_id,
                list(self.data.keys()),
            )
            return None

        # Deep copy so the cached originals are never mutated.
        rendered = copy.deepcopy(template)

        # 1. Render text template — substitute ${key} with scalar data values.
        text = rendered.get("textTemplate", "")
        text = self._substitute_placeholders(text, data)

        # 2. Render UI definition — substitute ${key} in all string values.
        ui_def = rendered.get("uiDefinition", {})
        ui_def = self._substitute_ui_placeholders(ui_def, data)

        # 3. Inject items array into uiDefinition so that expand_templates()
        #    (called inside transform_to_operations) can find them.
        if "itemTemplate" in ui_def:
            items_key = self._find_items_key(data)
            if items_key and items_key in data:
                ui_def["items"] = data[items_key]
                # Propagate itemListId to the top-level uiDefinition so
                # expand_templates() can attach expanded rows to the list.
                item_list_id = ui_def["itemTemplate"].get("itemListId")
                if item_list_id:
                    ui_def["itemListId"] = item_list_id
                logger.info(
                    "Injected %d items (key=%s) for template %s",
                    len(data[items_key]),
                    items_key,
                    template_id,
                )

        return {"text": text, "uiDefinition": ui_def}

    # ── Placeholder helpers ───────────────────────────────────────────────

    def _substitute_placeholders(self, text: str, data: dict) -> str:
        """
        Replace ``${key}`` scalar placeholders and expand
        ``{{#list}}...{{/list}}`` sections with list data.
        """
        # 1. Expand {{#listField}}body{{/listField}} sections first
        def _expand_section(match: re.Match) -> str:
            field = match.group(1)
            body = match.group(2)
            items = data.get(field)
            if not isinstance(items, list):
                return ""
            lines: list[str] = []
            for item in items:
                line = body
                for k, v in item.items():
                    line = line.replace("{{" + k + "}}", str(v))
                lines.append(line)
            return "".join(lines)

        text = re.sub(
            r"\{\{#(\w+)\}\}(.*?)\{\{/\1\}\}",
            _expand_section,
            text,
            flags=re.DOTALL,
        )

        # 2. Replace ${key} with scalar data values
        def replacer(match):
            key = match.group(1)
            value = data.get(key)
            if value is not None and not isinstance(value, (list, dict)):
                return str(value)
            return match.group(0)  # Leave unreplaced if missing or non-scalar

        return re.sub(r"\$\{(\w+)\}", replacer, text)

    def _substitute_ui_placeholders(self, obj, data: dict):
        """Recursively substitute ``${key}`` in every string value."""
        if isinstance(obj, str):
            return self._substitute_placeholders(obj, data)
        elif isinstance(obj, dict):
            return {
                k: self._substitute_ui_placeholders(v, data)
                for k, v in obj.items()
            }
        elif isinstance(obj, list):
            return [self._substitute_ui_placeholders(item, data) for item in obj]
        return obj

    def _find_items_key(self, data: dict) -> Optional[str]:
        """Return the first list-valued key in *data* (by insertion order)."""
        for key, value in data.items():
            if isinstance(value, list):
                return key
        return None

    # ── Introspection ─────────────────────────────────────────────────────

    def get_loaded_templates(self) -> list[str]:
        """Return IDs of all cached templates."""
        return list(self.templates.keys())

    def get_loaded_data(self) -> list[str]:
        """Return IDs of all cached data sets."""
        return list(self.data.keys())
