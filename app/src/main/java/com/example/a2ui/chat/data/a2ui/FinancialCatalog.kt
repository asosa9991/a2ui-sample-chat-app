package com.example.a2ui.chat.data.a2ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.contextable.a2ui4k.catalog.CoreCatalog
import com.contextable.a2ui4k.model.Catalog
import com.contextable.a2ui4k.model.CatalogItem
import com.contextable.a2ui4k.model.DataContext
import com.contextable.a2ui4k.model.DataReferenceParser
import com.contextable.a2ui4k.model.LiteralString
import com.contextable.a2ui4k.model.PathString
import com.contextable.a2ui4k.util.parseBasicMarkdown
import com.example.a2ui.chat.theme.NegativeText
import com.example.a2ui.chat.theme.OnSurfaceMuted
import com.example.a2ui.chat.theme.PositiveText
import kotlinx.serialization.json.JsonObject

// ── Date formatting ─────────────────────────────────────────────────────────
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

/**
 * A financial-aware A2UI catalog that extends [CoreCatalog] with:
 * - Color-coded monetary amounts: green for gains (+$), red for losses (-$)
 * - Flat Card widget so the outer Surface in MessageBubble provides the sole elevation/border
 *
 * Usage: pass [FinancialCatalog] as the `catalog` parameter to [A2UISurface].
 */

/** Overrides the standard Text widget to apply semantic color to monetary values. */
private val financialTextWidget = CatalogItem(name = "Text") { _, data, _, dataContext, _ ->
    FinancialTextContent(data = data, dataContext = dataContext)
}

/**
 * Overrides Row to add vertical breathing room to spaceBetween rows (transaction rows)
 * and center-aligns children vertically so amounts align with the description column.
 * Per designer spec:
 *   - transaction rows: 12dp top/bottom + 4dp horizontal (→ 16dp total from card edge)
 *   - verticalAlignment: CenterVertically for amount-to-description pairing
 */
private val financialRowWidget = CatalogItem(name = "Row") { _, data, buildChild, dataContext, _ ->
    val children = DataReferenceParser.parseComponentArray(data["children"])?.componentIds ?: emptyList()

    val distributionRef = DataReferenceParser.parseString(data["distribution"])
    val distribution = when (distributionRef) {
        is LiteralString -> distributionRef.value
        is PathString -> dataContext.getString(distributionRef.path)
        else -> null
    }

    val isSpaceBetween = distribution?.lowercase() == "spacebetween"

    val arrangement = when (distribution?.lowercase()) {
        "center" -> Arrangement.Center
        "end" -> Arrangement.End
        "spacearound" -> Arrangement.SpaceAround
        "spaceevenly" -> Arrangement.SpaceEvenly
        "spacebetween" -> Arrangement.SpaceBetween
        else -> Arrangement.Start
    }

    val modifier = if (isSpaceBetween)
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp)
    else
        Modifier

    Row(
        modifier = modifier,
        horizontalArrangement = arrangement,
        verticalAlignment = if (isSpaceBetween) Alignment.CenterVertically else Alignment.Top
    ) {
        children.forEach { childId -> buildChild(childId) }
    }
}

/**
 * Overrides Column to add 2dp spacing between children, creating a clear
 * "label → metadata" hierarchy between description and date text.
 * Per designer spec: spacedBy(2dp) matches Material 3 two-line ListItem pattern.
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
 * Elevation and border are provided by MessageBubble's Surface wrapper,
 * preventing a double-shadow effect.
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

val FinancialCatalog: Catalog = CoreCatalog + Catalog.of(
    "financial",
    financialTextWidget,
    financialRowWidget,
    financialColumnWidget,
    financialCardWidget
)

@Composable
private fun FinancialTextContent(data: JsonObject, dataContext: DataContext) {
    val textRef = DataReferenceParser.parseString(data["text"])
    val usageHintRef = DataReferenceParser.parseString(data["usageHint"])

    val text = when (textRef) {
        is LiteralString -> textRef.value
        is PathString -> dataContext.getString(textRef.path) ?: ""
        else -> ""
    }

    val hint = when (usageHintRef) {
        is LiteralString -> usageHintRef.value
        is PathString -> dataContext.getString(usageHintRef.path)
        else -> null
    }

    val displayText = formatDateIfIso(text)
    val baseStyle = textStyleForHint(hint)
    val semanticColor = monetaryColor(text) ?: captionColor(hint)
    val style = if (semanticColor != null) baseStyle.copy(color = semanticColor) else baseStyle

    Text(text = parseBasicMarkdown(displayText), style = style)
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
