package com.example.a2ui.chat.data.a2ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.contextable.a2ui4k.catalog.CoreCatalog
import com.contextable.a2ui4k.model.Catalog
import com.contextable.a2ui4k.model.CatalogItem
import com.contextable.a2ui4k.model.DataChangeEvent
import com.contextable.a2ui4k.model.DataContext
import com.contextable.a2ui4k.model.DataReferenceParser
import com.contextable.a2ui4k.model.LiteralBoolean
import com.contextable.a2ui4k.model.LiteralString
import com.contextable.a2ui4k.model.PathBoolean
import com.contextable.a2ui4k.model.PathString
import com.contextable.a2ui4k.model.UserActionEvent
import com.contextable.a2ui4k.render.LocalUiDefinition
import com.contextable.a2ui4k.util.parseBasicMarkdown
import com.example.a2ui.chat.theme.AccentNeutral
import com.example.a2ui.chat.theme.DividerColor
import com.example.a2ui.chat.theme.FormFieldBackground
import com.example.a2ui.chat.theme.FormFieldBorder
import com.example.a2ui.chat.theme.NegativeText
import com.example.a2ui.chat.theme.OnSurface
import com.example.a2ui.chat.theme.OnSurfaceMuted
import com.example.a2ui.chat.theme.PositiveGreen
import com.example.a2ui.chat.theme.PositiveText
import com.example.a2ui.chat.theme.OnSurfaceVariant
import com.example.a2ui.chat.theme.Primary
import androidx.compose.foundation.Canvas
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ── Date formatting ──────────────────────────────────────────────────────────
private val ISO_DATE_REGEX = Regex("""^\d{4}-(\d{2})-(\d{2})$""")
private val MONTH_ABBRS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

/** Converts "2026-03-27" → "Mar 27". Returns original string for non-dates. */
private fun formatDateIfIso(text: String): String {
    val match = ISO_DATE_REGEX.matchEntire(text) ?: return text
    val month = match.groupValues[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return text
    val day = match.groupValues[2].toIntOrNull() ?: return text
    return "${MONTH_ABBRS[month - 1]} $day"
}

// ── CompositionLocals for Row ↔ Text signaling ───────────────────────────────

/**
 * Written by the amount Text composable when it detects a monetary value.
 * Read by the parent Row to color the left accent bar.
 */
internal val LocalAccentColorSink = compositionLocalOf<((Color) -> Unit)?> { null }

/**
 * When true, "body"-hinted Text upgrades to SemiBold — applied inside
 * transaction rows to match Fidelity's visual weight hierarchy.
 */
internal val LocalBodyEmphasis = compositionLocalOf { false }

/**
 * Set by [financialListWidget] for each rendered item — contains the absolute DataContext
 * path prefix for that item (e.g., "/transactions/2"). [financialListItemWidget] prefixes
 * relative field paths with this value to resolve item-scoped data.
 */
internal val LocalListItemPath = compositionLocalOf<String?> { null }

// ── Widget overrides ─────────────────────────────────────────────────────────

/** Overrides the standard Text widget to apply semantic color and weight. */
private val financialTextWidget = CatalogItem(name = "Text") { _, data, _, dataContext, _ ->
    FinancialTextContent(data = data, dataContext = dataContext)
}

/**
 * Overrides Row to:
 * - Add a 3dp colored left accent bar to spaceBetween (transaction) rows
 * - Apply 13dp vertical + 12dp/16dp horizontal padding
 * - Center-align amount vertically against description+date column
 * - Signal accent color from child amount Text via CompositionLocal
 */
private val financialRowWidget = CatalogItem(name = "Row") { _, data, buildChild, dataContext, _ ->
    val childrenEl = data["children"]
    val children: List<String> =
        DataReferenceParser.parseComponentArray(childrenEl)?.componentIds
            ?: (childrenEl as? JsonObject)?.get("explicitList")?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()

    val distributionRef = DataReferenceParser.parseString(data["distribution"])
    val distribution = when (distributionRef) {
        is LiteralString -> distributionRef.value
        is PathString -> dataContext.getString(distributionRef.path)
        else -> null
    }
    val isSpaceBetween = distribution?.lowercase() == "spacebetween"

    if (!isSpaceBetween) {
        val arrangement = when (distribution?.lowercase()) {
            "center" -> Arrangement.Center
            "end" -> Arrangement.End
            "spacearound" -> Arrangement.SpaceAround
            "spaceevenly" -> Arrangement.SpaceEvenly
            else -> Arrangement.Start
        }
        Row(horizontalArrangement = arrangement, verticalAlignment = Alignment.Top) {
            children.forEach { buildChild(it) }
        }
        return@CatalogItem
    }

    // Transaction row: accent bar + structured two-column layout
    // Starts transparent — only shows color if a monetary amount child signals via SideEffect
    val accentColor = remember { mutableStateOf(Color.Transparent) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)  // lets accent bar use fillMaxHeight()
    ) {
        // Left accent bar — pre-attentive semantic color signal
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accentColor.value)
        )

        // Content row — receives accent color sink via CompositionLocal
        CompositionLocalProvider(
            LocalAccentColorSink provides { color -> accentColor.value = color }
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 16.dp, top = 13.dp, bottom = 13.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                children.forEachIndexed { index, childId ->
                    when (index) {
                        0 -> {
                            // Left column: weight(1f, fill=false) — expands to fill available
                            // space but yields to the right-side amount; never fillMaxWidth()
                            CompositionLocalProvider(LocalBodyEmphasis provides true) {
                                Box(modifier = Modifier.weight(1f, fill = false)) {
                                    buildChild(childId)
                                }
                            }
                        }
                        1 -> {
                            Spacer(modifier = Modifier.width(8.dp)) // minimum gap guard
                            buildChild(childId)
                        }
                        else -> buildChild(childId)
                    }
                }
            }
        }
    }
}

/**
 * Overrides Column to add spacing between children.
 * Parses optional `spacing` property from the server:
 * - "form"       → 16dp (between field groups and button row)
 * - "fieldGroup" → 4dp  (between a label Text and its TextField)
 * - omitted      → 2dp  (default: transaction description→metadata, unchanged)
 *
 * No fillMaxWidth() — inner columns inside SpaceBetween rows must wrap
 * content so the sibling amount text has room on the right.
 */
private val financialColumnWidget = CatalogItem(name = "Column") { _, data, buildChild, dataContext, _ ->
    val childrenEl = data["children"]
    val children: List<String> =
        DataReferenceParser.parseComponentArray(childrenEl)?.componentIds
            ?: (childrenEl as? JsonObject)?.get("explicitList")?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()

    val alignmentRef = DataReferenceParser.parseString(data["alignment"])
    val alignment = when (alignmentRef) {
        is LiteralString -> alignmentRef.value
        is PathString -> dataContext.getString(alignmentRef.path)
        else -> null
    }
    val horizontalAlignment = when (alignment?.lowercase()) {
        "center" -> Alignment.CenterHorizontally
        "end" -> Alignment.End
        else -> Alignment.Start
    }

    val spacingRef = DataReferenceParser.parseString(data["spacing"])
    val spacingToken = when (spacingRef) {
        is LiteralString -> spacingRef.value
        is PathString -> dataContext.getString(spacingRef.path)
        else -> null
    }
    val verticalSpacing = when (spacingToken?.lowercase()) {
        "form" -> 16.dp        // Between field groups and submit button
        "fieldgroup" -> 4.dp   // Between label Text and its TextField
        else -> 2.dp           // Default: transaction description→metadata
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        horizontalAlignment = horizontalAlignment
    ) {
        children.forEach { childId -> buildChild(childId) }
    }
}

/**
 * Overrides the standard Card widget with a flat Box (no extra elevation).
 * Elevation and border are provided by MessageBubble's Surface wrapper.
 */
private val financialCardWidget = CatalogItem(name = "Card") { _, data, buildChild, _, _ ->
    val childId = DataReferenceParser.parseComponentRef(data["child"])?.componentId
        ?: (data["child"] as? JsonPrimitive)?.contentOrNull
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        if (childId != null) buildChild(childId)
    }
}

/**
 * Overrides TextField with Fidelity-style form field.
 *
 * Value storage strategy:
 * - If server sends `text: { path: "/form/X" }`, use that path (standard binding)
 * - If no text path (current server format), derive path from component ID:
 *   "field_state" → "/form/state". This matches the button context references.
 *
 * Hint text: reads `placeholder` literal first, then falls back to `label`.
 */
private val financialTextFieldWidget = CatalogItem(name = "TextField") { componentId, data, _, dataContext, onEvent ->
    val labelRef = DataReferenceParser.parseString(data["label"])
    val textRef = DataReferenceParser.parseString(data["text"])
    val placeholderRef = DataReferenceParser.parseString(data["placeholder"])
    val typeRef = DataReferenceParser.parseString(data["textFieldType"])
    val regexpRef = DataReferenceParser.parseString(data["validationRegexp"])

    // Hint text: prefer explicit placeholder, then label
    val hintText = when (placeholderRef) {
        is LiteralString -> placeholderRef.value
        is PathString -> dataContext.getString(placeholderRef.path) ?: ""
        else -> when (labelRef) {
            is LiteralString -> labelRef.value
            is PathString -> dataContext.getString(labelRef.path) ?: ""
            else -> ""
        }
    }

    val textFieldType = when (typeRef) {
        is LiteralString -> typeRef.value
        is PathString -> dataContext.getString(typeRef.path)
        else -> null
    }
    val validationRegexp = when (regexpRef) {
        is LiteralString -> regexpRef.value
        is PathString -> dataContext.getString(regexpRef.path)
        else -> null
    }

    // Storage path: prefer explicit text binding from server.
    // Fallback derives /{componentId}/value — this WILL NOT MATCH if the server nests fields
    // under a parent container (e.g., /fields/first_field/value vs /first_field/value).
    // Fix: server must send text: { path: "..." } in every TextField matching the button context.
    val storagePath: String? = when (textRef) {
        is PathString -> textRef.path
        else -> {
            Log.w("FinancialCatalog", "TextField[$componentId] has no text path binding — falling back to /$componentId/value. Button context paths may not match. Server should send explicit text: {path: ...} in TextField definitions.")
            "/$componentId/value"
        }
    }
    val initialValue = when (textRef) {
        is PathString -> dataContext.getString(textRef.path) ?: ""
        is LiteralString -> textRef.value
        else -> dataContext.getString("/$componentId/value") ?: ""
    }

    val uiDefinition = LocalUiDefinition.current
    val surfaceId = uiDefinition?.surfaceId ?: "default"

    // ── Build checks list ────────────────────────────────────────────────────
    // Prefer server-sent `checks` array; fall back to legacy `validationRegexp`.
    val checksArray: JsonArray? = data["checks"] as? JsonArray
    val effectiveChecks: JsonArray = when {
        checksArray != null -> checksArray
        validationRegexp != null -> JsonArray(
            listOf(
                JsonObject(mapOf(
                    "call" to JsonPrimitive("regex"),
                    "args" to JsonObject(mapOf("pattern" to JsonPrimitive(validationRegexp)))
                ))
            )
        )
        else -> JsonArray(emptyList())
    }

    fun validate(value: String): String? {
        for (checkEl in effectiveChecks) {
            val check = checkEl as? JsonObject ?: continue
            val call = check["call"]?.jsonPrimitive?.content ?: continue
            val args = check["args"] as? JsonObject
            when (call) {
                "required" -> {
                    if (value.isBlank()) return "This field is required"
                }
                "numeric" -> {
                    val num = value.toDoubleOrNull()
                    if (num == null) return "Must be a number"
                    val min = args?.get("min")?.jsonPrimitive?.doubleOrNull
                    val max = args?.get("max")?.jsonPrimitive?.doubleOrNull
                    if (min != null && num < min) return "Must be at least ${min.toLong()}"
                    if (max != null && num > max) return "Must be at most ${max.toLong()}"
                }
                "regex" -> {
                    val pattern = args?.get("pattern")?.jsonPrimitive?.content ?: continue
                    if (value.isNotEmpty() && !Regex(pattern).matches(value)) return "Invalid format"
                }
            }
        }
        return null
    }

    var textValue by remember(initialValue) { mutableStateOf(initialValue) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var touched by remember { mutableStateOf(false) }

    // Seed DataContext with the current value on composition so the button's
    // context resolution finds it even if the user never modifies the field.
    LaunchedEffect(storagePath, initialValue) {
        if (storagePath != null) {
            dataContext.update(storagePath, initialValue)
            Log.d("FinancialCatalog", "TextField[$componentId] seeded $storagePath = \"$initialValue\"")
        }
    }

    val keyboardType: KeyboardType
    val visualTransformation: VisualTransformation
    val singleLine: Boolean
    val heightModifier: Modifier

    when (textFieldType?.lowercase()) {
        "number" -> { keyboardType = KeyboardType.Number; visualTransformation = VisualTransformation.None; singleLine = true; heightModifier = Modifier.heightIn(min = 52.dp) }
        "obscured" -> { keyboardType = KeyboardType.Password; visualTransformation = PasswordVisualTransformation(); singleLine = true; heightModifier = Modifier.heightIn(min = 52.dp) }
        "longtext" -> { keyboardType = KeyboardType.Text; visualTransformation = VisualTransformation.None; singleLine = false; heightModifier = Modifier.height(120.dp) }
        else -> { keyboardType = KeyboardType.Text; visualTransformation = VisualTransformation.None; singleLine = true; heightModifier = Modifier.heightIn(min = 52.dp) }
    }

    val showError = touched && errorMessage != null

    Column {
        OutlinedTextField(
            value = textValue,
            onValueChange = { newValue ->
                textValue = newValue
                touched = true
                errorMessage = validate(newValue)
                // Write to DataContext so button context can read it at submit time
                if (storagePath != null) {
                    dataContext.update(storagePath, newValue)
                    Log.d("FinancialCatalog", "TextField[$componentId] changed $storagePath = \"$newValue\"")
                    onEvent(DataChangeEvent(surfaceId = surfaceId, path = storagePath, value = newValue))
                }
            },
            placeholder = if (hintText.isNotEmpty()) {
                { Text(hintText, style = MaterialTheme.typography.bodyLarge.copy(color = OnSurfaceMuted)) }
            } else null,
            label = null,  // Suppress floating label — static label is a separate Text widget above
            modifier = Modifier.fillMaxWidth().then(heightModifier),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = FormFieldBackground,
                focusedContainerColor   = Color.White,
                errorContainerColor     = FormFieldBackground,
                unfocusedBorderColor    = FormFieldBorder,
                focusedBorderColor      = PositiveGreen,
                errorBorderColor        = NegativeText,
                unfocusedTextColor      = OnSurface,
                focusedTextColor        = OnSurface,
                cursorColor             = PositiveGreen,
                errorCursorColor        = NegativeText,
            ),
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = singleLine,
            isError = showError,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visualTransformation
        )
        if (showError) {
            Text(
                text = errorMessage ?: "",
                style = MaterialTheme.typography.bodySmall.copy(color = NegativeText),
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}

/**
 * Overrides Button with Fidelity-style actions.
 *
 * Supports both server payload formats:
 * - style: "filled"/"outlined" (current server) OR primary: true/false (legacy)
 * - actions: [{name, context}] (current server) OR action: {name, context} (legacy)
 *
 * Context resolution handles the flat format the server sends:
 *   {"key": "street", "path": "/form/street"}
 * where the path string is read directly from DataContext.
 */
private val financialButtonWidget = CatalogItem(name = "Button") { componentId, data, buildChild, dataContext, onEvent ->
    val childRef = DataReferenceParser.parseString(data["child"])
    val childId = when (childRef) {
        is LiteralString -> childRef.value
        is PathString -> dataContext.getString(childRef.path)
        else -> null
    }
    val labelRef = DataReferenceParser.parseString(data["label"])
    val label = when (labelRef) {
        is LiteralString -> labelRef.value
        is PathString -> dataContext.getString(labelRef.path)
        else -> null
    }

    // ── Primary detection: prefer style string, fall back to primary bool ──
    val styleValue = (data["style"] as? JsonPrimitive)?.contentOrNull
    val isPrimary: Boolean = when (styleValue?.lowercase()) {
        "filled" -> true
        "outlined", "text" -> false
        else -> {
            val primaryRef = DataReferenceParser.parseBoolean(data["primary"])
            when (primaryRef) {
                is LiteralBoolean -> primaryRef.value
                is PathBoolean -> dataContext.getBoolean(primaryRef.path) ?: false
                else -> false
            }
        }
    }

    // ── Action: prefer actions[] array, fall back to action single object ──
    val firstAction: JsonObject? = (data["actions"] as? kotlinx.serialization.json.JsonArray)
        ?.firstOrNull() as? JsonObject
        ?: data["action"] as? JsonObject

    val uiDefinition = LocalUiDefinition.current
    val surfaceId = uiDefinition?.surfaceId ?: "default"

    val onClick: () -> Unit = {
        val actionName = firstAction?.get("name")?.jsonPrimitive?.content
            ?: (data["action"] as? JsonPrimitive)?.contentOrNull
            ?: "click"

        // Resolve context at click time so field values are current
        val contextArray = firstAction?.get("context") as? kotlinx.serialization.json.JsonArray
        val legacyContextArray = (data["action"] as? JsonObject)?.get("context") as? kotlinx.serialization.json.JsonArray
        val resolvedContext = resolveActionContext(contextArray ?: legacyContextArray, dataContext)

        Log.d("FinancialCatalog", "Button[$componentId] firing action=$actionName")
        Log.d("FinancialCatalog", "Button[$componentId] context=${resolvedContext?.entries?.joinToString { "${it.key}=${it.value}" } ?: "null"}")

        onEvent(UserActionEvent(
            name = actionName,
            surfaceId = surfaceId,
            sourceComponentId = componentId,
            timestamp = java.time.Instant.now().toString(),
            context = resolvedContext
        ))
    }

    if (isPrimary) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PositiveGreen,
                contentColor = Color.White
            )
        ) {
            val btnStyle = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold, color = Color.White
            )
            when {
                childId != null -> buildChild(childId)
                label != null -> Text(label, style = btnStyle)
                else -> Text("Submit", style = btnStyle)
            }
        }
    } else {
        TextButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = PositiveGreen)
        ) {
            when {
                childId != null -> buildChild(childId)
                label != null -> Text(label)
                else -> Text("Cancel")
            }
        }
    }
}

/**
 * Resolves action context path bindings from DataContext at event time.
 *
 * Supports two formats:
 * 1. Flat (current server): {"key": "street", "path": "/form/street"}
 * 2. Nested (legacy):       {"key": "street", "value": {"path": "/form/street"}}
 */
private fun resolveActionContext(
    contextArray: kotlinx.serialization.json.JsonArray?,
    dataContext: DataContext
): JsonObject? {
    if (contextArray == null || contextArray.isEmpty()) return null
    val resolved = mutableMapOf<String, JsonElement>()
    for (entry in contextArray) {
        val entryObj = entry as? JsonObject ?: continue
        val key = entryObj["key"]?.jsonPrimitive?.content ?: continue

        val resolvedValue: JsonElement? = when {
            // Format 1 — flat: {key, path} where path is a string
            entryObj.containsKey("path") -> {
                val path = entryObj["path"]?.jsonPrimitive?.content ?: continue
                val value = dataContext.getString(path) ?: ""
                Log.d("FinancialCatalog", "  resolving key=$key path=$path → \"$value\"")
                // Always include the key — send empty string if nothing typed yet
                JsonPrimitive(value)
            }
            // Format 2 — nested: {key, value: {path/literalString/...}}
            entryObj.containsKey("value") -> {
                val value = entryObj["value"] as? JsonObject ?: continue
                when {
                    value.containsKey("path") -> {
                        val path = value["path"]?.jsonPrimitive?.content ?: ""
                        JsonPrimitive(
                            dataContext.getString(path)
                                ?: dataContext.getBoolean(path)?.toString()
                                ?: ""
                        )
                    }
                    value.containsKey("literalString") -> value["literalString"]?.jsonPrimitive?.content?.let { JsonPrimitive(it) }
                    value.containsKey("literalBoolean") -> value["literalBoolean"]?.jsonPrimitive?.booleanOrNull?.let { JsonPrimitive(it) }
                    else -> null
                }
            }
            else -> null
        }
        if (resolvedValue != null) resolved[key] = resolvedValue
    }
    return if (resolved.isNotEmpty()) JsonObject(resolved) else null
}

private val financialListWidget = CatalogItem(name = "List") { _, data, buildChild, dataContext, _ ->
    val childrenObj = data["children"] as? JsonObject
    val path = childrenObj?.get("path")?.jsonPrimitive?.content
    val templateComponentId = childrenObj?.get("componentId")?.jsonPrimitive?.content

    if (path != null && templateComponentId != null) {
        // Array-probing pattern: discover how many items exist via DataContext path iteration.
        // getObjectKeys() uses as? JsonObject internally — returns non-null List for object-valued
        // items (e.g. {action, date, amount}), null for missing indices.
        // getArraySize() uses as? JsonArray internally — returns non-null Int for array-valued
        // items, null otherwise.  getString() covers primitive-valued items.
        // All three calls are fully non-throwing; no try-catch needed.
        val items = mutableListOf<Int>()
        var index = 0
        while (index < 50) {
            val itemExists = dataContext.getObjectKeys("$path/$index") != null  // JsonObject items
                || dataContext.getArraySize("$path/$index") != null              // JsonArray items
                || dataContext.getString("$path/$index") != null                 // primitive items
            if (!itemExists) break
            items.add(index)
            index++
        }
        if (items.isEmpty()) {
            Log.d("FinancialCatalog", "List: no items found at path=$path")
        }
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            items.forEachIndexed { listIndex, itemIndex ->
                CompositionLocalProvider(LocalListItemPath provides "$path/$itemIndex") {
                    buildChild(templateComponentId)
                }
                // Divider between items only — not after the last one
                if (listIndex < items.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 15.dp),
                        thickness = 1.dp,
                        color = DividerColor
                    )
                }
            }
        }
    } else {
        // Standard child-list fallback (same as Column)
        val componentIds = DataReferenceParser.parseComponentArray(data["children"])?.componentIds
            ?: emptyList()
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            componentIds.forEach { childId -> buildChild(childId) }
        }
    }
}


// ── Chart widgets ─────────────────────────────────────────────────────────────

/**
 * DonutChart: ring chart for portfolio allocation or asset-class breakdown.
 * Segments are embedded directly in componentProperties["segments"] as a JSON array.
 * Each segment: {label, pct (float), pctDisplay, colorHint}.
 */
private val financialDonutChartWidget = CatalogItem(name = "DonutChart") { _, data, _, dataContext, _ ->
    val titleRef = DataReferenceParser.parseString(data["title"])
    val title = when (titleRef) {
        is LiteralString -> titleRef.value
        is PathString    -> dataContext.getString(titleRef.path) ?: ""
        else             -> ""
    }
    val centerLabelRef = DataReferenceParser.parseString(data["centerLabel"])
    val centerLabel = when (centerLabelRef) {
        is LiteralString -> centerLabelRef.value
        is PathString    -> dataContext.getString(centerLabelRef.path) ?: ""
        else             -> ""
    }
    val centerSublabelRef = DataReferenceParser.parseString(data["centerSublabel"])
    val centerSublabel = when (centerSublabelRef) {
        is LiteralString -> centerSublabelRef.value
        is PathString    -> dataContext.getString(centerSublabelRef.path) ?: ""
        else             -> ""
    }
    val showLegend = (data["showLegend"] as? JsonPrimitive)?.booleanOrNull ?: true

    data class Segment(val label: String, val pct: Float, val pctDisplay: String, val colorHint: String)

    val segments = buildList {
        val arr = data["segments"] as? JsonArray ?: return@buildList
        for (el in arr) {
            val obj = el as? JsonObject ?: continue
            val lbl  = obj["label"]?.jsonPrimitive?.contentOrNull ?: continue
            val pct  = obj["pct"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: 0f
            val pctD = obj["pctDisplay"]?.jsonPrimitive?.contentOrNull ?: "${"%.1f".format(pct)}%"
            val hint = obj["colorHint"]?.jsonPrimitive?.contentOrNull ?: "blue"
            add(Segment(lbl, pct, pctD, hint))
        }
    }

    fun hintToColor(hint: String): Color = when (hint.lowercase()) {
        "blue"   -> Primary
        "teal"   -> Color(0xFF0D9488)
        "green"  -> PositiveText
        "indigo" -> Color(0xFF4F46E5)
        "amber"  -> Color(0xFFD97706)
        "slate"  -> OnSurfaceVariant
        "rose"   -> NegativeText
        "cyan"   -> Color(0xFF0891B2)
        "violet" -> Color(0xFF7C3AED)
        "orange" -> Color(0xFFEA580C)
        "lime"   -> Color(0xFF65A30D)
        else     -> Primary
    }

    val segColors = segments.map { hintToColor(it.colorHint) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (title.isNotBlank()) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = OnSurface)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(180.dp)) {
                val strokeW = 38.dp.toPx()
                val radius  = (size.minDimension - strokeW) / 2f
                val cx      = size.width  / 2f
                val cy      = size.height / 2f
                val topLeft = Offset(cx - radius, cy - radius)
                val arcSize = Size(radius * 2f, radius * 2f)
                var startAngle = -90f
                val gap = 2f
                segments.forEachIndexed { i, seg ->
                    val sweep = ((seg.pct / 100f) * 360f - gap).coerceAtLeast(0f)
                    drawArc(
                        color      = segColors[i],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter  = false,
                        topLeft    = topLeft,
                        size       = arcSize,
                        style      = Stroke(width = strokeW, cap = StrokeCap.Butt)
                    )
                    startAngle += sweep + gap
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (centerLabel.isNotBlank()) {
                    Text(
                        centerLabel,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = OnSurface
                    )
                }
                if (centerSublabel.isNotBlank()) {
                    Text(centerSublabel, style = MaterialTheme.typography.bodySmall, color = OnSurfaceMuted)
                }
            }
        }

        if (showLegend && segments.isNotEmpty()) {
            val rowCount = (segments.size + 1) / 2
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(rowCount) { r ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf(r * 2, r * 2 + 1).forEach { idx ->
                            Box(modifier = Modifier.weight(1f)) {
                                val seg = segments.getOrNull(idx)
                                if (seg != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(segColors[idx], RoundedCornerShape(2.dp))
                                        )
                                        Text(
                                            "${seg.label}  ${seg.pctDisplay}",
                                            style    = MaterialTheme.typography.bodySmall,
                                            color    = OnSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * BarChart: horizontal bar chart for gain/loss by position, balances by account type, etc.
 * Bars are embedded directly in componentProperties["bars"] as a JSON array.
 * Each bar: {label, valueDisplay, value (float), direction ("positive"|"negative"|"neutral")}.
 */
private val financialBarChartWidget = CatalogItem(name = "BarChart") { _, data, _, dataContext, _ ->
    val titleRef = DataReferenceParser.parseString(data["title"])
    val title = when (titleRef) {
        is LiteralString -> titleRef.value
        is PathString    -> dataContext.getString(titleRef.path) ?: ""
        else             -> ""
    }
    val subtitleRef = DataReferenceParser.parseString(data["subtitle"])
    val subtitle = when (subtitleRef) {
        is LiteralString -> subtitleRef.value
        is PathString    -> dataContext.getString(subtitleRef.path) ?: ""
        else             -> ""
    }
    val showValues = (data["showValues"] as? JsonPrimitive)?.booleanOrNull ?: true

    data class Bar(val label: String, val valueDisplay: String, val value: Float, val direction: String)

    val bars = buildList {
        val arr = data["bars"] as? JsonArray ?: return@buildList
        for (el in arr) {
            val obj = el as? JsonObject ?: continue
            val lbl  = obj["label"]?.jsonPrimitive?.contentOrNull ?: continue
            val valD = obj["valueDisplay"]?.jsonPrimitive?.contentOrNull ?: ""
            val valN = obj["value"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: 0f
            val dir  = obj["direction"]?.jsonPrimitive?.contentOrNull ?: "neutral"
            add(Bar(lbl, valD, valN, dir))
        }
    }

    fun dirColor(dir: String): Color = when (dir.lowercase()) {
        "positive" -> PositiveText
        "negative" -> NegativeText
        else       -> Primary
    }

    val maxAbs = bars.maxOfOrNull { kotlin.math.abs(it.value) }?.takeIf { it > 0f } ?: 1f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (title.isNotBlank()) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = OnSurface)
        }
        if (subtitle.isNotBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnSurfaceMuted)
        }
        Spacer(Modifier.height(4.dp))

        bars.forEach { bar ->
            val color    = dirColor(bar.direction)
            val fraction = (kotlin.math.abs(bar.value) / maxAbs).coerceIn(0.04f, 1f)
            Row(
                modifier  = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    bar.label,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = OnSurface,
                    modifier = Modifier.width(52.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(modifier = Modifier
                    .weight(1f)
                    .height(22.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(color.copy(alpha = 0.10f), RoundedCornerShape(4.dp))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(color.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                    )
                }
                if (showValues) {
                    Text(
                        bar.valueDisplay,
                        style     = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color     = color,
                        modifier  = Modifier.width(88.dp),
                        maxLines  = 1,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

/**
 * Renders a financial list row with a leading accent bar and four semantic text slots.
 *
 * Fields are resolved from [data] via path-based or literal bindings. Relative paths
 * are prefixed with [LocalListItemPath] (set by the parent [financialListWidget]).
 *
 * Layout: accent bar | left col (label + subLabel?) | right col (value + subValue?)
 * Accent bar color: green for +$, red for -$, neutral gray for non-monetary, transparent if empty.
 */
private val financialListItemWidget = CatalogItem(name = "ListItem") { _, data, _, dataContext, _ ->
    val itemPath = LocalListItemPath.current

    fun resolveField(key: String): String? {
        val fieldEl = data[key] as? JsonObject ?: return null
        return when {
            fieldEl.containsKey("path") -> {
                val rel = fieldEl["path"]?.jsonPrimitive?.content ?: return null
                val absPath = if (itemPath != null) "$itemPath/$rel" else "/$rel"
                dataContext.getString(absPath)
            }
            fieldEl.containsKey("literalString") -> fieldEl["literalString"]?.jsonPrimitive?.content
            else -> null
        }
    }

    val label    = resolveField("label")    ?: ""
    val subLabel = resolveField("subLabel")
    val value    = resolveField("value")    ?: ""
    val subValue = resolveField("subValue")

    val displayLabel    = formatDateIfIso(label)
    val displaySubLabel = subLabel?.let { formatDateIfIso(it) }
    val displayValue    = formatDateIfIso(value)
    val displaySubValue = subValue?.let { formatDateIfIso(it) }

    val monetaryBarColor = monetaryColor(value)
    val barColor = when {
        monetaryBarColor != null -> monetaryBarColor
        value.isNotBlank()       -> AccentNeutral
        else                     -> Color.Transparent
    }
    val valueColor = monetaryBarColor ?: OnSurface

    // Accessibility: merged content description for TalkBack
    val valueSemantic = displayValue
        .replace(Regex("^\\+\\$"), "positive \\$")
        .replace(Regex("^-\\$"), "negative \\$")
    val contentDesc = buildString {
        append(displayLabel)
        append(", ")
        append(valueSemantic)
        if (!displaySubLabel.isNullOrBlank()) append(", $displaySubLabel")
        if (!displaySubValue.isNullOrBlank()) append(", $displaySubValue")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .height(IntrinsicSize.Min)
            .semantics { contentDescription = contentDesc },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Accent bar — decorative; excluded from accessibility tree
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(barColor)
                .semantics { invisibleToUser() }
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 16.dp, top = 13.dp, bottom = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left column: label + optional subLabel
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .semantics { invisibleToUser() },
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = displayLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!displaySubLabel.isNullOrBlank()) {
                    Text(
                        text = displaySubLabel,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = OnSurfaceVariant
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right column: value + optional subValue (right-aligned)
            Column(
                modifier = Modifier.semantics { invisibleToUser() },
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = valueColor
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!displaySubValue.isNullOrBlank()) {
                    Text(
                        text = displaySubValue,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = OnSurfaceMuted
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private val financialDividerWidget = CatalogItem(name = "Divider") { _, _, _, _, _ ->
    HorizontalDivider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

val FinancialCatalog: Catalog = CoreCatalog + Catalog.of(
    "financial",
    financialDonutChartWidget,
    financialBarChartWidget,
    financialTextWidget,
    financialRowWidget,
    financialColumnWidget,
    financialCardWidget,
    financialTextFieldWidget,
    financialButtonWidget,
    financialListWidget,
    financialListItemWidget,
    financialDividerWidget
)


// ── Text rendering ───────────────────────────────────────────────────────────

@Composable
private fun FinancialTextContent(data: JsonObject, dataContext: DataContext) {
    val textRef = DataReferenceParser.parseString(data["text"])
    val hintRef = DataReferenceParser.parseString(data["usageHint"])

    val text = when (textRef) {
        is LiteralString -> textRef.value
        is PathString -> dataContext.getString(textRef.path) ?: ""
        else -> ""
    }
    val hint = when (hintRef) {
        is LiteralString -> hintRef.value
        is PathString -> dataContext.getString(hintRef.path)
        else -> null
    }

    val displayText = formatDateIfIso(text)
    val monetaryColor = monetaryColor(text)
    val captionColor = captionColor(hint)
    val bodyEmphasis = LocalBodyEmphasis.current
    val baseStyle = textStyleForHint(hint)

    val effectiveStyle = when {
        // Amount text: SemiBold + right-aligned + semantic color
        monetaryColor != null -> baseStyle.copy(
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            color = monetaryColor
        )
        // Description inside a transaction row: SemiBold for visual weight
        hint?.lowercase() == "body" && bodyEmphasis -> baseStyle.copy(
            fontWeight = FontWeight.SemiBold,
            color = captionColor ?: baseStyle.color
        )
        // Caption and all other text
        else -> if (captionColor != null) baseStyle.copy(color = captionColor) else baseStyle
    }

    // Signal accent color to parent Row — SideEffect fires after composition,
    // before draw, so accent bar updates within the same frame.
    val accentSink = LocalAccentColorSink.current
    if (monetaryColor != null && accentSink != null) {
        SideEffect { accentSink(monetaryColor) }
    }

    Text(text = parseBasicMarkdown(displayText), style = effectiveStyle)
}

/** Returns semantic color for monetary amounts; null for all other text. */
private fun monetaryColor(text: String): Color? = when {
    text.startsWith("+") && text.contains("$") -> PositiveText
    text.startsWith("-") && text.contains("$") -> NegativeText
    else -> null
}

/** Returns muted color for caption-hinted text (dates, metadata). */
private fun captionColor(hint: String?): Color? =
    if (hint?.lowercase() == "caption") OnSurfaceMuted else null

@Composable
private fun textStyleForHint(hint: String?): TextStyle = when (hint?.lowercase()) {
    "h1" -> MaterialTheme.typography.headlineLarge
    "h2" -> MaterialTheme.typography.headlineMedium
    "h3" -> MaterialTheme.typography.headlineSmall
    "h4" -> MaterialTheme.typography.titleLarge
    "h5" -> MaterialTheme.typography.titleMedium
    "body" -> MaterialTheme.typography.bodyLarge
    "caption" -> MaterialTheme.typography.bodySmall
    else -> LocalTextStyle.current
}

