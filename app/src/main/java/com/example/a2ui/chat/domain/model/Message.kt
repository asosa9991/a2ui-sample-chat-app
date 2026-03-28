package com.example.a2ui.chat.domain.model

import androidx.compose.runtime.Immutable
import com.contextable.a2ui4k.model.UiDefinition
import kotlinx.serialization.json.JsonObject

@Immutable
data class Message(
    val id: String,
    val content: String,
    val sender: Sender,
    val timestamp: Long,
    val isLoading: Boolean = false,
    val uiDefinition: UiDefinition? = null,
    /** Reactive data to populate the A2UISurface's DataModel. */
    val dataModelJson: JsonObject? = null
)
