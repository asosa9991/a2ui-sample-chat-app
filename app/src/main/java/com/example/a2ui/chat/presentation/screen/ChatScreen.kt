package com.example.a2ui.chat.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.a2ui.chat.presentation.ChatUiState
import com.example.a2ui.chat.presentation.ChatViewModel
import com.example.a2ui.chat.presentation.components.*

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory),
    onEditMessage: (messageId: String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backendMode by viewModel.backendMode.collectAsStateWithLifecycle()
    val wireFormat by viewModel.wireFormat.collectAsStateWithLifecycle()
    val designerState by viewModel.designerState.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalDesignerMode provides designerState.isDesignerMode) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column {
                    ChatTopBar(
                        selectedMode = backendMode,
                        onModeSelected = { mode -> viewModel.setBackendMode(mode) },
                        selectedWireFormat = wireFormat,
                        onWireFormatSelected = viewModel::setWireFormat,
                        isDesignerMode = designerState.isDesignerMode,
                        onAvatarTripleTap = { viewModel.enterDesignerMode() },
                    )
                    DesignerModeBanner(
                        visible = designerState.isDesignerMode,
                        onExitClick = { viewModel.exitDesignerMode() },
                    )
                }
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
                        MessageList(
                            messages = state.messages,
                            isAiResponding = state.isAiResponding,
                            onEvent = { event -> viewModel.sendUiEvent(event) },
                            onFeedback = { messageId, rating, reason ->
                                viewModel.sendFeedback(messageId, rating, reason)
                            },
                            onEditClick = onEditMessage,
                            onSaveTemplate = { messageId ->
                                viewModel.showSaveTemplateDialog(messageId)
                            },
                        )
                    }
                }
            }
        }

        // Save Template Dialog — shown over the Scaffold when a message is selected
        val messageIdToSave = designerState.showSaveDialogForMessageId
        if (messageIdToSave != null) {
            SaveTemplateDialog(
                onDismiss = { viewModel.dismissSaveTemplateDialog() },
                onSave = { name, keywords ->
                    viewModel.saveTemplate(messageIdToSave, name, keywords)
                },
                saveState = designerState.saveState,
                errorMessage = designerState.saveErrorMessage,
            )
        }
    }
}

