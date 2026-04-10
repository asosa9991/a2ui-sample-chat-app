package com.example.a2ui.chat.data.repository

import android.util.Log
import com.contextable.a2ui4k.model.UiDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class SaveTemplateResult(
    val templateId: String,
    val previewUrl: String?,
    val message: String
)

class DesignerRepository {

    companion object {
        private const val TAG = "DesignerRepo"
        private const val BASE_URL = "http://10.0.2.2:8000"
    }

    suspend fun saveTemplate(
        name: String,
        intentKeywords: List<String>,
        uiDefinition: UiDefinition,
        textTemplate: String,
        description: String = ""
    ): Result<SaveTemplateResult> = withContext(Dispatchers.IO) {
        try {
            val templateId = name.lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trimStart('_')
                .trimEnd('_') + "_" + UUID.randomUUID().toString().take(8)

            val body = buildJsonObject {
                put("name", name)
                put("templateId", templateId)
                put("description", description)
                put("textTemplate", textTemplate)
                putJsonObject("intentTriggers") {
                    putJsonArray("exact") {}
                    putJsonArray("keywords") {
                        intentKeywords.forEach { add(it) }
                    }
                }
                put("uiDefinition", uiDefinition.toJsonObject())
            }

            val url = URL("$BASE_URL/designer/save-template")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }

            val responseCode = conn.responseCode
            val responseStream = if (responseCode < 400) conn.inputStream
                                 else (conn.errorStream ?: conn.inputStream)
            val responseBody = BufferedReader(InputStreamReader(responseStream, Charsets.UTF_8)).use { it.readText() }

            if (responseCode in 200..299) {
                val json = Json.parseToJsonElement(responseBody).jsonObject
                Result.success(
                    SaveTemplateResult(
                        templateId = json["templateId"]?.jsonPrimitive?.content ?: templateId,
                        previewUrl = json["previewUrl"]?.jsonPrimitive?.contentOrNull,
                        message = json["message"]?.jsonPrimitive?.content ?: "Template saved"
                    )
                )
            } else {
                Log.e(TAG, "Save template failed $responseCode: $responseBody")
                Result.failure(Exception("Server error $responseCode"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Save template exception", e)
            Result.failure(e)
        }
    }
}

/**
 * Converts a [UiDefinition] into a [JsonObject] suitable for the designer save-template
 * API payload. Component properties are serialized from the raw [JsonObject] maps held in
 * each [com.contextable.a2ui4k.model.Component].
 */
private fun UiDefinition.toJsonObject(): JsonObject = buildJsonObject {
    put("surfaceId", surfaceId)
    root?.let { put("root", it) }
    catalogId?.let { put("catalogId", it) }
    putJsonArray("components") {
        for ((id, component) in components) {
            addJsonObject {
                put("id", id)
                // componentProperties is Map<String, JsonObject> — wrap as a JsonObject
                put("component", JsonObject(component.componentProperties.mapValues { (_, v) -> v as JsonElement }))
            }
        }
    }
}
