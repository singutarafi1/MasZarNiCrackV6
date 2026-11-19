package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TerminalLine
import com.example.ui.ServiceUiState
import com.example.ui.theme.LocalAppPalette
import com.example.ui.theme.LocalFontConfig

@Composable
fun FullscreenCliTerminalScreen(
    uiState: ServiceUiState,
    onTerminalInputChange: (String) -> Unit,
    onSubmitCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val fontConfig = LocalFontConfig.current

    val terminalBg = palette.terminalBg
    val dockBg = palette.headerBg
    val dividerColor = palette.divider
    val promptColor = palette.accentPrimary
    val inputColor = palette.textPrimary
    val hintColor = palette.textMuted
    val keyBg = palette.cardAltBg
    val keyBorder = palette.cardBorder
    val keyTextColor = palette.textPrimary

    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val interactionSource = remember { MutableInteractionSource() }
    var copiedFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(copiedFeedback) {
        if (copiedFeedback) {
            delay(1500)
            copiedFeedback = false
        }
    }

    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = uiState.terminalInput,
                selection = TextRange(uiState.terminalInput.length)
            )
        )
    }
    var historyCursor by remember { mutableIntStateOf(-1) }

    LaunchedEffect(uiState.terminalInput) {
        if (uiState.terminalInput != textFieldValue.text) {
            textFieldValue = TextFieldValue(
                text = uiState.terminalInput,
                selection = TextRange(uiState.terminalInput.length)
            )
        }
    }

    LaunchedEffect(uiState.terminalLines.size) {
        if (uiState.terminalLines.isNotEmpty()) {
            listState.animateScrollToItem(uiState.terminalLines.lastIndex)
        }
    }

    fun submitCurrent() {
        if (uiState.isBusy) return
        historyCursor = -1
        onSubmitCommand(textFieldValue.text)
    }

    fun insertToken(token: String) {
        val current = textFieldValue.text
        val nextText = when {
            current.isBlank() -> "$token "
            current.endsWith(" ") -> "$current$token "
            else -> "$current $token "
        }
        historyCursor = -1
        textFieldValue = TextFieldValue(
            text = nextText,
            selection = TextRange(nextText.length)
        )
        onTerminalInputChange(nextText)
        try {
            focusRequester.requestFocus()
            keyboardController?.show()
        } catch (_: Exception) {
        }
    }

    fun stepHistoryUp() {
        val history = uiState.terminalHistory
        if (history.isEmpty()) return
        val nextCursor = if (historyCursor == -1) {
            history.lastIndex
        } else {
            (historyCursor - 1).coerceAtLeast(0)
        }
        historyCursor = nextCursor
        val cmd = history[nextCursor]
        textFieldValue = TextFieldValue(text = cmd, selection = TextRange(cmd.length))
        onTerminalInputChange(cmd)
    }

    fun stepHistoryDown() {
        val history = uiState.terminalHistory
        if (history.isEmpty() || historyCursor == -1) return
        val nextCursor = historyCursor + 1
        if (nextCursor > history.lastIndex) {
            historyCursor = -1
            textFieldValue = TextFieldValue(text = "", selection = TextRange(0))
            onTerminalInputChange("")
        } else {
            historyCursor = nextCursor
            val cmd = history[nextCursor]
            textFieldValue = TextFieldValue(text = cmd, selection = TextRange(cmd.length))
            onTerminalInputChange(cmd)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(terminalBg)
            .testTag("fullscreen_termux_terminal")
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    try {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    } catch (_: Exception) {
                    }
                }
        ) {
            if (uiState.terminalLines.isEmpty()) {
                Text(
                    text = "~ $ ready (type 'help', 'adb devices', or 'fastboot devices')",
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontConfig.terminalOutSp,
                    color = hintColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                )
            } else {
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.terminalLines, key = { it.id }) { line ->
                            TermuxOutputLine(line = line)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = dividerColor)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(dockBg)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TermuxExtraKey("adb", keyBg, keyBorder, keyTextColor) { insertToken("adb") }
                TermuxExtraKey("fastboot", keyBg, keyBorder, keyTextColor) { insertToken("fastboot") }
                TermuxExtraKey("shell", keyBg, keyBorder, keyTextColor) { insertToken("shell") }
                TermuxExtraKey("devices", keyBg, keyBorder, keyTextColor) { insertToken("devices") }
                TermuxExtraKey("reboot", keyBg, keyBorder, keyTextColor) { insertToken("reboot") }
                TermuxExtraKey("↑", keyBg, keyBorder, promptColor) { stepHistoryUp() }
                TermuxExtraKey("↓", keyBg, keyBorder, promptColor) { stepHistoryDown() }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (copiedFeedback) Color(0xFF10B981).copy(alpha = 0.18f) else keyBg,
                    border = BorderStroke(1.dp, if (copiedFeedback) Color(0xFF10B981) else keyBorder),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 32.dp)
                        .testTag("termux_copy_button")
                        .clickable {
                            val txt = uiState.terminalLines.joinToString("\n") { it.text }
                            clipboard.setText(AnnotatedString(txt))
                            copiedFeedback = true
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (copiedFeedback) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy Terminal",
                            tint = if (copiedFeedback) Color(0xFF10B981) else promptColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (copiedFeedback) "COPIED" else "COPY",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (copiedFeedback) Color(0xFF10B981) else keyTextColor
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = keyBg,
                    border = BorderStroke(1.dp, keyBorder),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 32.dp)
                        .testTag("termux_clear_button")
                        .clickable {
                            historyCursor = -1
                            onSubmitCommand("clear")
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear Terminal",
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "CLR",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.accentSecondary
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = dividerColor)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(dockBg)
                .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "~ $ ",
                fontFamily = FontFamily.Monospace,
                fontSize = fontConfig.terminalCmdSp,
                fontWeight = FontWeight.Bold,
                color = promptColor
            )
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 40.dp)
            ) {
                if (uiState.isBusy) {
                    Text(
                        text = "running...",
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontConfig.terminalCmdSp,
                        color = hintColor
                    )
                } else {
                    if (textFieldValue.text.isEmpty()) {
                        Text(
                            text = "adb / fastboot command...",
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontConfig.terminalOutSp,
                            color = hintColor
                        )
                    }
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { next ->
                            if (next.text.contains('\n')) {
                                val clean = next.text.replace("\n", "")
                                historyCursor = -1
                                textFieldValue = TextFieldValue("", TextRange(0))
                                onSubmitCommand(clean)
                            } else {
                                textFieldValue = next
                                onTerminalInputChange(next.text)
                            }
                        },
                        singleLine = true,
                        cursorBrush = SolidColor(promptColor),
                        textStyle = TextStyle(
                            color = inputColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontConfig.terminalCmdSp,
                            lineHeight = 20.sp
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = { submitCurrent() },
                            onDone = { submitCurrent() }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.Enter, Key.NumPadEnter -> {
                                            submitCurrent()
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            stepHistoryUp()
                                            true
                                        }
                                        Key.DirectionDown -> {
                                            stepHistoryDown()
                                            true
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                            .testTag("termux_cli_input")
                    )
                }
            }
            Spacer(modifier = Modifier.width(2.dp))
            IconButton(
                onClick = { submitCurrent() },
                enabled = !uiState.isBusy,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("termux_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Run Command",
                    tint = if (uiState.isBusy) hintColor else promptColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun TermuxExtraKey(
    label: String,
    bgColor: Color,
    borderColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .defaultMinSize(minHeight = 32.dp)
            .clickable { onClick() }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

@Composable
private fun TermuxOutputLine(
    line: TerminalLine
) {
    val palette = LocalAppPalette.current
    val fontConfig = LocalFontConfig.current

    val promptColor = palette.accentPrimary
    val cmdColor = palette.textPrimary
    val outputColor = if (palette.isDark) Color(0xFFE5E7EB) else palette.textSecondary
    val errorColor = if (palette.isDark) Color(0xFFF87171) else Color(0xFFDC2626)

    if (line.isCommand) {
        val promptPrefix = "~ $ "
        val cmdBody = line.text.removePrefix(promptPrefix)
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = promptColor, fontWeight = FontWeight.Bold)) {
                    append(promptPrefix)
                }
                withStyle(SpanStyle(color = cmdColor)) {
                    append(cmdBody)
                }
            },
            fontFamily = FontFamily.Monospace,
            fontSize = fontConfig.terminalCmdSp,
            lineHeight = 19.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp)
        )
    } else {
        Text(
            text = line.text,
            fontFamily = FontFamily.Monospace,
            fontSize = fontConfig.terminalOutSp,
            lineHeight = 18.sp,
            color = if (line.isError) errorColor else outputColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp)
        )
    }
}
