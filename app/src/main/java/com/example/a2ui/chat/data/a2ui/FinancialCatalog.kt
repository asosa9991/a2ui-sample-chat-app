package com.example.a2ui.chat.data.a2ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.example.a2ui.chat.theme.CardBorderSubtle
import com.example.a2ui.chat.theme.NegativeText
import com.example.a2ui.chat.theme.OnSurface
import com.example.a2ui.chat.theme.OnSurfaceMuted
import com.example.a2ui.chat.theme.PositiveText
import com.example.a2ui.chat.theme.Primary
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
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
    val children = DataReferenceParser.parseComponentArray(data["children"])?.componentIds
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
 * Overrides Column to add 2dp spacing between children — creates a clear
 * label→metadata hierarchy between description and date text.
 *
 * No fillMaxWidth() — inner columns inside SpaceBetween rows must wrap
 * content so the sibling amount text has room on the right.
 */
private val financialColumnWidget = CatalogItem(name = "Column") { _, data, buildChild, dataContext, _ ->
    val children = DataReferenceParser.parseComponentArray(data["children"])?.componentIds ?: emptyList()

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

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        if (childId != null) buildChild(childId)
    }
}

/**
 * Overrides TextField with Fidelity-style form field:
 * - Rounded corners (8dp) matching the reference design
 * - No floating label — the server sends a separate Text label above each field;
 *   the TextField label is repurposed as a subtle placeholder hint
 * - Focus border uses Primary blue; unfocused uses subtle CardBorderSubtle
 * - 52dp minimum height for comfortable touch targets
 */
private val financialTextFieldWidget = CatalogItem(name = "TextField") { componentId, data, _, dataContext, onEvent ->
    val labelRef = DataReferenceParser.parseString(data["label"])
    val textRef = DataReferenceParser.parseString(data["text"])
    val typeRef = DataReferenceParser.parseString(data["textFieldType"])
    val regexpRef = DataReferenceParser.parseString(data["validationRegexp"])

    val label = when (labelRef) {
        is LiteralString -> labelRef.value
        is PathString -> dataContext.getString(labelRef.path) ?: ""
        else -> ""
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
    val initialValue = when (textRef) {
        is PathString -> dataContext.getString(textRef.path) ?: ""
        is LiteralString -> textRef.value
        else -> ""
    }

    val uiDefinition = LocalUiDefinition.current
    val surfaceId = uiDefinition?.surfaceId ?: "default"

    var textValue by remember(initialValue) { mutableStateOf(initialValue) }
    var isError by remember { mutableStateOf(false) }

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

    OutlinedTextField(
        value = textValue,
        onValueChange = { newValue ->
            textValue = newValue
            isError = if (validationRegexp != null && newValue.isNotEmpty()) {
                !Regex(validationRegexp).matches(newValue)
            } else false
            if (textRef is PathString) {
                dataContext.update(textRef.path, newValue)
                onEvent(DataChangeEvent(surfaceId = surfaceId, path = textRef.path, value = newValue))
            }
        },
        placeholder = if (label.isNotEmpty()) {
            { Text(label, style = MaterialTheme.typography.bodyLarge.copy(color = OnSurfaceMuted)) }
        } else null,
        label = null,  // Suppress floating label — label is a separate Text widget above the field
        modifier = Modifier.fillMaxWidth().then(heightModifier),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = CardBorderSubtle,
            focusedBorderColor = Primary,
            errorBorderColor = NegativeText,
            unfocusedContainerColor = Color.White,
            focusedContainerColor = Color.White,
            errorContainerColor = Color.White,
        ),
        textStyle = MaterialTheme.typography.bodyLarge,
        singleLine = singleLine,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation
    )
}

/**
 * Overrides Button with Fidelity-style actions:
 * - primary=true  → full-width green filled button (52dp, 8dp corners)
 * - primary=false → text-only button for secondary actions like Cancel
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
    val primaryRef = DataReferenceParser.parseBoolean(data["primary"])
    val isPrimary = when (primaryRef) {
        is LiteralBoolean -> primaryRef.value
        is PathBoolean -> dataContext.getBoolean(primaryRef.path) ?: false
        else -> false
    }

    val actionElement = data["action"]
    val actionData = actionElement as? JsonObject
    val actionNameDirect = (actionElement as? JsonPrimitive)?.contentOrNull

    val uiDefinition = LocalUiDefinition.current
    val surfaceId = uiDefinition?.surfaceId ?: "default"

    // Resolve context array so form field values reach the server on submit
    val contextArray = actionData?.get("context")?.let {
        it as? kotlinx.serialization.json.JsonArray
    }
    val resolvedContext = resolveActionContext(contextArray, dataContext)

    val onClick: () -> Unit = {
        val actionName = actionNameDirect
            ?: actionData?.get("name")?.jsonPrimitive?.content
            ?: "click"
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
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PositiveText,
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
        TextButton(onClick = onClick) {
            when {
                childId != null -> buildChild(childId)
                label != null -> Text(label, color = OnSurface)
                else -> Text("Cancel", color = OnSurface)
            }
        }
    }
}

/** Resolves action.context path bindings from the DataContext at event time. */
private fun resolveActionContext(
    contextArray: kotlinx.serialization.json.JsonArray?,
    dataContext: DataContext
): JsonObject? {
    if (contextArray == null || contextArray.isEmpty()) return null
    val resolved = mutableMapOf<String, JsonElement>()
    for (entry in contextArray) {
        val entryObj = entry as? JsonObject ?: continue
        val key = entryObj["key"]?.jsonPrimitive?.content ?: continue
        val value = entryObj["value"] as? JsonObject ?: continue
        val resolvedValue: JsonElement? = when {
            value.containsKey("path") -> {
                val path = value["path"]?.jsonPrimitive?.content ?: ""
                dataContext.getString(path)?.let { JsonPrimitive(it) }
                    ?: dataContext.getBoolean(path)?.let { JsonPrimitive(it) }
            }
            value.containsKey("literalString") -> value["literalString"]?.jsonPrimitive?.content?.let { JsonPrimitive(it) }
            value.containsKey("literalNumber") -> value["literalNumber"]?.jsonPrimitive?.doubleOrNull?.let { JsonPrimitive(it) }
            value.containsKey("literalBoolean") -> value["literalBoolean"]?.jsonPrimitive?.booleanOrNull?.let { JsonPrimitive(it) }
            else -> null
        }
        if (resolvedValue != null) resolved[key] = resolvedValue
    }
    return if (resolved.isNotEmpty()) JsonObject(resolved) else null
}

val FinancialCatalog: Catalog = CoreCatalog + Catalog.of(
    "financial",
    financialTextWidget,
    financialRowWidget,
    financialColumnWidget,
    financialCardWidget,
    financialTextFieldWidget,
    financialButtonWidget
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

