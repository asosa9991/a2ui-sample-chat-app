"""JSON Schema and semantic validators for A2UI uiDefinition structures."""

A2UI_SCHEMA = {
    "type": "object",
    "required": ["root", "components"],
    "properties": {
        "root": {"type": "string"},
        "surfaceId": {"type": "string"},
        "components": {
            "type": "object",
            "additionalProperties": {
                "type": "object",
                "required": ["id", "componentProperties"],
                "properties": {
                    "id": {"type": "string"},
                    "componentProperties": {
                        "type": "object",
                        "minProperties": 1,
                    },
                },
            },
        },
    },
    "additionalProperties": True,  # Allow template fields like itemTemplate, items, etc.
}
