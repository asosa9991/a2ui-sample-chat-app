package com.example.a2ui.chat.presentation

import com.example.a2ui.chat.domain.model.Message
import com.example.a2ui.chat.presentation.components.SaveTemplateState
import kotlinx.collections.immutable.ImmutableList

sealed interface ChatUiState {
    data object Empty : ChatUiState
    data class Active(
        val messages: ImmutableList<Message>,
        val isAiResponding: Boolean
    ) : ChatUiState
}

/**
 * Encapsulates all designer-mode transient state hoisted into the ViewModel.
 *
 * @param isDesignerMode             True while the user has activated designer mode.
 * @param savedMessageIds            Set of message IDs that have been successfully saved as templates.
 * @param showSaveDialogForMessageId Non-null → show the SaveTemplateDialog for this message.
 * @param saveState                  Current state of the async save-template operation.
 * @param saveErrorMessage           Error description shown in the dialog when [saveState] == ERROR.
 */
data class DesignerState(
    val isDesignerMode: Boolean = false,
    val savedMessageIds: Set<String> = emptySet(),
    val showSaveDialogForMessageId: String? = null,
    val saveState: SaveTemplateState = SaveTemplateState.IDLE,
    val saveErrorMessage: String? = null,
)

