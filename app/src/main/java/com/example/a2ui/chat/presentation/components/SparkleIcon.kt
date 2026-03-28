package com.example.a2ui.chat.presentation.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.a2ui.chat.theme.Primary

@Composable
fun SparkleIcon() {
    Icon(
        imageVector = Icons.Default.AutoAwesome,
        contentDescription = "Sparkle",
        tint = Primary,
        modifier = Modifier.size(48.dp)
    )
}
