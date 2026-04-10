package com.example.a2ui.chat.theme

import androidx.compose.ui.graphics.Color

// ── Background & Surface ────────────────────────────────────────────────────
val LightBackground     = Color(0xFFF4F6FA)
val LightSurface        = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFECF0F7)
val InputBarBackground  = Color(0xFFEDEFF5)

// ── Message bubbles ─────────────────────────────────────────────────────────
val UserBubble          = Color(0xFFD6E4FF)
val AiBubble            = Color(0xFFF8FAFD)  // Near-white; plain AI text messages

// ── Core text ───────────────────────────────────────────────────────────────
val OnBackground        = Color(0xFF0F172A)
val OnSurface           = Color(0xFF1E2740)
val OnSurfaceVariant    = Color(0xFF64748B)
val OnSurfaceMuted      = Color(0xFF94A3B8)  // Timestamps, disabled, secondary metadata

// ── Primary / Brand ─────────────────────────────────────────────────────────
val Primary             = Color(0xFF2563EB)
val PrimaryContainer    = Color(0xFFD6E4FF)
val OnPrimary           = Color(0xFFFFFFFF)
val OnPrimaryContainer  = Color(0xFF1E40AF)
val SecondaryContainer  = Color(0xFFEEF2FB)
val LinkText            = Color(0xFF1D4ED8)
val SendButtonActive    = Color(0xFF2563EB)
val SendButtonInactive  = Color(0xFFBAC4D8)

// ── Semantic: Positive (gains, sells) ───────────────────────────────────────
val PositiveGreen       = Color(0xFF0D7C4F)  // Primary positive text color
val PositiveText        = Color(0xFF0D7C4F)
val PositiveTextMuted   = Color(0xFF16A06A)
val PositiveContainer   = Color(0xFFDCFAED)
val PositiveGreenContainer = Color(0xFFDCFAED)
val OnPositiveContainer = Color(0xFF0A5C3A)

// ── Semantic: Negative (losses, buys) ───────────────────────────────────────
val NegativeRed         = Color(0xFFB91C1C)  // Primary negative text color
val NegativeText        = Color(0xFFB91C1C)
val NegativeTextMuted   = Color(0xFFDC2626)
val NegativeContainer   = Color(0xFFFFECEC)
val NegativeRedContainer = Color(0xFFFFECEC)
val OnNegativeContainer = Color(0xFF991B1B)

// ── Structural chrome ────────────────────────────────────────────────────────
val CardBorderSubtle    = Color(0xFFE2E8F2)
val SurfaceCardBorder   = Color(0xFFE2E8F2)  // Alias for MessageBubble compat
val DividerColor        = Color(0xFFEEF1F7)
val TopBarDivider       = Color(0xFFE2E8F2)
val AccentNeutral       = Color(0xFFC4C9D4)  // Left-bar accent for neutral/unknown transactions

// ── Form fields ──────────────────────────────────────────────────────────────
val FormFieldBackground = Color(0xFFF9FAFB)  // Unfocused container — off-white
val FormFieldBorder     = Color(0xFFD0D5DD)  // Unfocused border (more visible than CardBorderSubtle)

// ── Wire Format ──────────────────────────────────────────────────────────────
val WireFormatPrimary   = Color(0xFF7C3AED)  // Violet-600 — active segment fill
val OnWireFormatPrimary = Color(0xFFFFFFFF)  // White — active segment label

// ── Toggle component ─────────────────────────────────────────────────────────
val ToggleLabelUnselected = Color(0xFF475569)  // Slate-600; 6.43:1 on LightSurfaceVariant ✓ WCAG AA
