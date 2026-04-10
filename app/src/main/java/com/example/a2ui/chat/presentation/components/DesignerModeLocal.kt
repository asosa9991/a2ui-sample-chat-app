package com.example.a2ui.chat.presentation.components

import androidx.compose.runtime.compositionLocalOf

/** Ambient boolean — true while designer mode is active. Set in ChatScreen, consumed in MessageBubble. */
val LocalDesignerMode = compositionLocalOf { false }
