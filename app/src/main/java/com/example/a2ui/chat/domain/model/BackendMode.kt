package com.example.a2ui.chat.domain.model

enum class BackendMode {
    LLM,
    TEMPLATE,
    DESIGNER;  // Internal: activated by 3-tap gesture; not shown in BackendModeToggle

    val label: String
        get() = when (this) {
            LLM      -> "LLM"
            TEMPLATE -> "Template"
            DESIGNER -> "Designer"  // Not rendered in toggle segments
        }
}
