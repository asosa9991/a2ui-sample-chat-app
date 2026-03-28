package com.example.a2ui.chat.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.a2ui.chat.presentation.ChatUiState
import com.example.a2ui.chat.presentation.ChatViewModel
import com.example.a2ui.chat.presentation.components.*

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ChatTopBar()
        },
        bottomBar = {
            val isResponding = (uiState as? ChatUiState.Active)?.isAiResponding ?: false
            ChatInputBar(
                onSendMessage = viewModel::sendMessage,
                isResponding = isResponding
            )
        }
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val state = uiState) {
                is ChatUiState.Empty -> {
                    EmptyStateGreeting(greetingPeriod = viewModel.greeting)
                }
                is ChatUiState.Active -> {
                    MessageList(messages = state.messages)
                }
            }
        }
    }
}
