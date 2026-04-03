package com.example.a2ui.chat.presentation.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.contextable.a2ui4k.model.Component
import com.contextable.a2ui4k.model.UiDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Result of parsing editor JSON text. */
sealed interface ParseResult {
    data class Success(val definition: UiDefinition, val dataJson: JsonObject) : ParseResult
    data class Error(val message: String) : ParseResult
    data object Empty : ParseResult
}

/**
 * Parses the wire-format component array + data object into a [UiDefinition].
 *
 * Component array format:
 * ```json
 * [{"id": "root", "component": {"Column": {...}}}, ...]
 * ```
 */
internal object EditorJsonParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(componentsText: String, dataText: String): ParseResult {
        if (componentsText.isBlank()) return ParseResult.Empty

        val components: Map<String, Component>
        try {
            val array = json.parseToJsonElement(componentsText).jsonArray
            components = parseComponents(array)
        } catch (e: kotlinx.serialization.SerializationException) {
            return ParseResult.Error("Components JSON syntax error: ${e.message?.take(100)}")
        } catch (e: Exception) {
            return ParseResult.Error("Components JSON: ${e.message?.take(100)}")
        }

        val dataJson: JsonObject
        try {
            dataJson = if (dataText.isBlank() || dataText.trim() == "{}") {
                JsonObject(emptyMap())
            } else {
                json.parseToJsonElement(dataText).jsonObject
            }
        } catch (e: kotlinx.serialization.SerializationException) {
            return ParseResult.Error("Data JSON syntax error: ${e.message?.take(100)}")
        } catch (e: Exception) {
            return ParseResult.Error("Data JSON: ${e.message?.take(100)}")
        }

        if (components.isEmpty()) {
            return ParseResult.Error("No components found in the JSON array")
        }

        if (!components.containsKey("root")) {
            return ParseResult.Error("Missing component with id='root'")
        }

        return ParseResult.Success(
            definition = UiDefinition(
                surfaceId = "editor-preview",
                root = "root",
                components = components
            ),
            dataJson = dataJson
        )
    }

    private fun parseComponents(array: JsonArray): Map<String, Component> {
        val result = LinkedHashMap<String, Component>()
        for (element in array) {
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: continue
            val componentProps = obj["component"]?.jsonObject ?: continue

            val properties = mutableMapOf<String, JsonObject>()
            for ((widgetType, config) in componentProps) {
                if (config is JsonObject) {
                    properties[widgetType] = config
                }
            }
            result[id] = Component(id = id, componentProperties = properties)
        }
        return result
    }
}

/**
 * Observable state holder for the editor screen.
 *
 * Holds the raw JSON strings for components and data, plus the current [ParseResult]
 * which updates automatically on every edit.
 */
@Stable
class EditorState(
    initialComponents: String,
    initialData: String
) {
    var componentsText by mutableStateOf(initialComponents)
        private set

    var dataText by mutableStateOf(initialData)
        private set

    var parseResult: ParseResult by mutableStateOf(ParseResult.Empty)
        private set

    /** Shortcut for the error message when [parseResult] is [ParseResult.Error]. */
    val errorMessage: String?
        get() = (parseResult as? ParseResult.Error)?.message

    init {
        reparse()
    }

    fun onComponentsChanged(text: String) {
        componentsText = text
        reparse()
    }

    fun onDataChanged(text: String) {
        dataText = text
        reparse()
    }

    private fun reparse() {
        parseResult = EditorJsonParser.parse(componentsText, dataText)
    }
}

@Composable
fun rememberEditorState(
    initialComponents: String,
    initialData: String
): EditorState = remember(initialComponents, initialData) {
    EditorState(initialComponents, initialData)
}
