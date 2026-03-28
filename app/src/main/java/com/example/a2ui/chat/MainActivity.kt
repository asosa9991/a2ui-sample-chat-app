package com.example.a2ui.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.a2ui.chat.presentation.screen.ChatScreen
import com.example.a2ui.chat.theme.A2UIChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            A2UIChatTheme {
                ChatScreen()
            }
        }
    }
}
