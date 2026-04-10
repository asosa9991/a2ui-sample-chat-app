package com.example.a2ui.chat.domain.model

enum class WireFormat {
    SSE,
    JSONL,
    SYNC;

    val label: String
        get() = when (this) {
            SSE   -> "SSE"
            JSONL -> "JSONL"
            SYNC  -> "Sync"
        }
}
