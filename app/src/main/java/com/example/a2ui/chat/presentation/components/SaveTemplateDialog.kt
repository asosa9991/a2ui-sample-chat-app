package com.example.a2ui.chat.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.a2ui.chat.theme.DesignerAmber
import com.example.a2ui.chat.theme.OnDesignerAmber
import com.example.a2ui.chat.theme.OnSurfaceVariant

/** State machine for the save-template async flow. */
enum class SaveTemplateState { IDLE, SAVING, SAVED, ERROR }

/**
 * Bottom sheet dialog for naming and saving an AI-generated card as a re-usable template.
 *
 * @param onDismiss      Called when the sheet is dismissed (blocked while [saveState] == SAVING).
 * @param onSave         Called with the entered name and keyword list when the user taps "Save".
 * @param saveState      Drives the button's loading/success/error UI state.
 * @param errorMessage   Error message shown when [saveState] == ERROR.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveTemplateDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, keywords: List<String>) -> Unit,
    saveState: SaveTemplateState = SaveTemplateState.IDLE,
    errorMessage: String? = null,
) {
    var templateName by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf(listOf<String>()) }

    val isSaving = saveState == SaveTemplateState.SAVING

    ModalBottomSheet(
        onDismissRequest = { if (!isSaving) onDismiss() },
        sheetState = rememberModalBottomSheetState(
            confirmValueChange = { !isSaving }
        ),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .imePadding()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Header ──────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.BookmarkAdd,
                    contentDescription = null,
                    tint = DesignerAmber,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Save as Template",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // ── Template Name ────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Template name",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnSurfaceVariant,
                )
                OutlinedTextField(
                    value = templateName,
                    onValueChange = { templateName = it },
                    placeholder = { Text("e.g. Monthly Transactions") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
            }

            // ── Intent Keywords ───────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Intent keywords",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnSurfaceVariant,
                )
                ChipInput(
                    chips = keywords,
                    onChipsChanged = { keywords = it },
                    placeholder = "Type keyword, press Space…",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── Error Message ─────────────────────────────────────────────
            if (saveState == SaveTemplateState.ERROR && errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                )
            }

            // ── Save Button ────────────────────────────────────────────────
            Button(
                onClick = {
                    if (templateName.isNotBlank()) {
                        onSave(templateName.trim(), keywords)
                    }
                },
                enabled = templateName.isNotBlank() && !isSaving && saveState != SaveTemplateState.SAVED,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DesignerAmber,
                    contentColor = OnDesignerAmber,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                AnimatedContent(
                    targetState = saveState,
                    label = "saveButtonContent",
                ) { state ->
                    when (state) {
                        SaveTemplateState.SAVING -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = OnDesignerAmber,
                                    strokeWidth = 2.dp,
                                )
                                Text("Saving…")
                            }
                        }
                        SaveTemplateState.SAVED -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text("Saved!")
                            }
                        }
                        else -> Text("Save Template")
                    }
                }
            }
        }
    }
}
