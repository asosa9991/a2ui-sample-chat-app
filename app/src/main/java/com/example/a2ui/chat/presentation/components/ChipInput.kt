package com.example.a2ui.chat.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.a2ui.chat.theme.DesignerAmberBorder
import com.example.a2ui.chat.theme.DesignerAmberContainer
import com.example.a2ui.chat.theme.FormFieldBackground
import com.example.a2ui.chat.theme.FormFieldBorder
import com.example.a2ui.chat.theme.OnDesignerAmberContainer
import com.example.a2ui.chat.theme.OnSurfaceMuted

/**
 * A chip-based keyword input. Typing a space or comma commits the current text as a chip.
 * Pressing Backspace on an empty field removes the last chip.
 *
 * @param chips           Current list of committed keyword chips.
 * @param onChipsChanged  Called whenever the chip list changes (add/remove).
 * @param modifier        Optional layout modifier.
 * @param placeholder     Hint text shown when the text field is empty.
 * @param maxChips        Maximum number of chips allowed.
 */
@Composable
fun ChipInput(
    chips: List<String>,
    onChipsChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Type keyword, press Space…",
    maxChips: Int = 10,
) {
    var inputText by remember { mutableStateOf("") }

    fun commitText() {
        val trimmed = inputText.trim().trimEnd(',')
        if (trimmed.isNotEmpty() && chips.size < maxChips && trimmed !in chips) {
            onChipsChanged(chips + trimmed)
        }
        inputText = ""
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FormFieldBorder, RoundedCornerShape(8.dp))
                .background(FormFieldBackground, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                chips.forEach { chip ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = {
                            Text(
                                text = chip,
                                fontSize = 13.sp,
                                color = OnDesignerAmberContainer,
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { onChipsChanged(chips - chip) },
                                modifier = Modifier.size(16.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove $chip",
                                    modifier = Modifier.size(10.dp),
                                )
                            }
                        },
                        colors = InputChipDefaults.inputChipColors(
                            containerColor = DesignerAmberContainer,
                            labelColor = OnDesignerAmberContainer,
                        ),
                        border = InputChipDefaults.inputChipBorder(
                            enabled = true,
                            selected = false,
                            borderColor = DesignerAmberBorder,
                        ),
                        modifier = Modifier.height(28.dp),
                    )
                }

                if (chips.size < maxChips) {
                    BasicTextField(
                        value = inputText,
                        onValueChange = { newValue ->
                            when {
                                newValue.endsWith(' ') || newValue.endsWith(',') -> commitText()
                                else -> inputText = newValue
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done,
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 120.dp)
                            .align(Alignment.CenterVertically)
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.key == Key.Backspace
                                    && keyEvent.type == KeyEventType.KeyDown
                                    && inputText.isEmpty()
                                    && chips.isNotEmpty()
                                ) {
                                    onChipsChanged(chips.dropLast(1))
                                    true
                                } else {
                                    false
                                }
                            },
                        decorationBox = { inner ->
                            Box {
                                if (inputText.isEmpty()) {
                                    Text(
                                        text = placeholder,
                                        fontSize = 13.sp,
                                        color = OnSurfaceMuted,
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }
            }
        }
    }
}
