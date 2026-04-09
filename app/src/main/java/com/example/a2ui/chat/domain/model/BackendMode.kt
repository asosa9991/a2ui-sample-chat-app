package com.example.a2ui.chat.domain.model

enum class BackendMode {
    LLM,
    TEMPLATE;

    val label: String
        get() = when (this) {
            LLM      -> "LLM"
            TEMPLATE -> "Template"
        }
}
