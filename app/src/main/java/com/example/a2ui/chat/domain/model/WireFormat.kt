package com.example.a2ui.chat.domain.model

enum class WireFormat {
    SSE,
    JSONL;

    val label: String
        get() = when (this) {
            SSE   -> "SSE"
            JSONL -> "JSONL"
        }
}
