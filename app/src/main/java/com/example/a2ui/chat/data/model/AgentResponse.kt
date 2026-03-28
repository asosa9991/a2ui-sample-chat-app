package com.example.a2ui.chat.data.model

import com.contextable.a2ui4k.model.Component
import com.contextable.a2ui4k.model.UiDefinition
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class AgentResponseDto(
    val text: String,
    @SerialName("ui_definition")
    val uiDefinition: UiDefinitionDto? = null,
    val error: String? = null
)

@Serializable
data class UiDefinitionDto(
    val surfaceId: String,
    val root: String? = null,
    val components: Map<String, ComponentDto> = emptyMap()
)

@Serializable
data class ComponentDto(
    val id: String,
    val componentProperties: Map<String, JsonObject> = emptyMap()
)

fun UiDefinitionDto.toDomain(): UiDefinition = UiDefinition(
    surfaceId = surfaceId,
    root = root,
    components = components.mapValues { (_, dto) ->
        Component(
            id = dto.id,
            componentProperties = dto.componentProperties
        )
    }
)
