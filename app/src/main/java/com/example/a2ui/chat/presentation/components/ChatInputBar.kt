package com.example.a2ui.chat.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.a2ui.chat.theme.InputBarBackground
import com.example.a2ui.chat.theme.SendButtonActive
import com.example.a2ui.chat.theme.SendButtonInactive

@Composable
fun ChatInputBar(
    onSendMessage: (String) -> Unit,
    isResponding: Boolean,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    
    val isSendEnabled = text.isNotBlank() && !isResponding

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .imePadding(),
        shape = RoundedCornerShape(28.dp),
        color = InputBarBackground
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add attachment",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = "Chat with Claude",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            IconButton(
                onClick = {
                    if (isSendEnabled) {
                        onSendMessage(text)
                        text = ""
                    }
                },
                enabled = isSendEnabled
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = if (isSendEnabled) SendButtonActive else SendButtonInactive,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
