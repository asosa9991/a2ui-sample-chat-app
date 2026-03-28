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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.contextable.a2ui4k.catalog.CoreCatalog
import com.contextable.a2ui4k.model.Catalog
import com.contextable.a2ui4k.model.CatalogItem
import com.contextable.a2ui4k.model.DataContext
import com.contextable.a2ui4k.model.DataReferenceParser
import com.contextable.a2ui4k.model.LiteralString
import com.contextable.a2ui4k.model.PathString
import com.contextable.a2ui4k.util.parseBasicMarkdown
import com.example.a2ui.chat.theme.AccentNeutral
import com.example.a2ui.chat.theme.NegativeText
import com.example.a2ui.chat.theme.OnSurfaceMuted
import com.example.a2ui.chat.theme.PositiveText
import kotlinx.serialization.json.JsonObject

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
    val accentColor = remember { mutableStateOf(AccentNeutral) }

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

val FinancialCatalog: Catalog = CoreCatalog + Catalog.of(
    "financial",
    financialTextWidget,
    financialRowWidget,
    financialColumnWidget,
    financialCardWidget
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

