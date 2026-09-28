package com.example.ui.editor

import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.compiler.DiagnosticSeverity
import com.example.compiler.JavaDiagnostic
import com.example.ui.highlighter.JavaSyntaxHighlighter
import com.example.util.ClipboardHelper
import kotlinx.coroutines.delay

@Composable
fun CodeEditorView(
    fileName: String,
    codeValue: TextFieldValue,
    onCodeChange: (TextFieldValue) -> Unit,
    diagnostics: List<JavaDiagnostic>,
    onRun: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val errorLines = remember(diagnostics) {
        diagnostics.filter { it.severity == DiagnosticSeverity.ERROR }.map { it.line }.toSet()
    }
    val warningLines = remember(diagnostics) {
        diagnostics.filter { it.severity == DiagnosticSeverity.WARNING }.map { it.line }.toSet()
    }

    val linesCount = remember(codeValue.text) {
        codeValue.text.count { it == '\n' } + 1
    }

    val context = LocalContext.current
    val vScrollState = rememberScrollState()
    val hScrollState = rememberScrollState()

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var isEditorFocused by remember { mutableStateOf(false) }

    // Pulsing/blinking cursor animation for visual feedback & cursor position indicator
    val infiniteTransition = rememberInfiniteTransition(label = "cursor_blink")
    val cursorBlinkAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 530),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    // Automatically request focus and place blinking cursor in the editor on load/file change
    LaunchedEffect(fileName) {
        delay(150)
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    val cursorPosition = remember(codeValue.text, codeValue.selection) {
        val cursorOffset = codeValue.selection.start.coerceIn(0, codeValue.text.length)
        val textBefore = codeValue.text.take(cursorOffset)
        val line = textBefore.count { it == '\n' } + 1
        val lastNewline = textBefore.lastIndexOf('\n')
        val col = if (lastNewline == -1) cursorOffset + 1 else cursorOffset - lastNewline
        line to col
    }

    val onMoveCursorLeft: () -> Unit = {
        val start = codeValue.selection.start
        if (start > 0) {
            onCodeChange(codeValue.copy(selection = TextRange(start - 1)))
        }
    }

    val onMoveCursorRight: () -> Unit = {
        val start = codeValue.selection.start
        if (start < codeValue.text.length) {
            onCodeChange(codeValue.copy(selection = TextRange(start + 1)))
        }
    }

    val density = LocalDensity.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val toggleKeyboard: () -> Unit = {
        if (isKeyboardVisible) keyboardController?.hide() else keyboardController?.show()
    }
    val handlePaste: () -> Unit = {
        val clipboardText = ClipboardHelper.getClipboardText(context)
        if (clipboardText.isNullOrEmpty()) {
            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
        } else {
            val currentText = codeValue.text
            val selection = codeValue.selection
            val newText = if (currentText.isEmpty()) {
                clipboardText
            } else {
                currentText.replaceRange(selection.min, selection.max, clipboardText)
            }
            val newCursor = selection.min + clipboardText.length
            onCodeChange(
                codeValue.copy(
                    text = newText,
                    selection = TextRange(newCursor.coerceIn(0, newText.length))
                )
            )
            Toast.makeText(context, "Pasted ${clipboardText.length} characters", Toast.LENGTH_SHORT).show()
        }
    }

    val handleCopy: () -> Unit = {
        val currentText = codeValue.text
        val selection = codeValue.selection
        val textToCopy = if (selection.min != selection.max) {
            currentText.substring(selection.min, selection.max)
        } else {
            currentText
        }

        if (textToCopy.isEmpty()) {
            Toast.makeText(context, "No code to copy", Toast.LENGTH_SHORT).show()
        } else {
            ClipboardHelper.copyText(context, textToCopy, "Java Code", showToast = true)
        }
    }

    val handleCut: () -> Unit = {
        val currentText = codeValue.text
        val selection = codeValue.selection
        if (selection.min != selection.max) {
            val textToCut = currentText.substring(selection.min, selection.max)
            ClipboardHelper.copyText(context, textToCut, "Java Code", showToast = false)
            val newText = currentText.removeRange(selection.min, selection.max)
            onCodeChange(
                codeValue.copy(
                    text = newText,
                    selection = TextRange(selection.min)
                )
            )
            Toast.makeText(context, "Cut selection to clipboard", Toast.LENGTH_SHORT).show()
        } else if (currentText.isNotEmpty()) {
            ClipboardHelper.copyText(context, currentText, "Java Code", showToast = false)
            onCodeChange(
                codeValue.copy(
                    text = "",
                    selection = TextRange(0)
                )
            )
            Toast.makeText(context, "Cut all code to clipboard", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "No code to cut", Toast.LENGTH_SHORT).show()
        }
    }

    val handleSelectAll: () -> Unit = {
        if (codeValue.text.isNotEmpty()) {
            onCodeChange(
                codeValue.copy(
                    selection = TextRange(0, codeValue.text.length)
                )
            )
            Toast.makeText(context, "All code selected", Toast.LENGTH_SHORT).show()
        }
    }

    val handleClear: () -> Unit = {
        if (codeValue.text.isNotEmpty()) {
            onCodeChange(
                codeValue.copy(
                    text = "",
                    selection = TextRange(0)
                )
            )
            Toast.makeText(context, "Editor cleared", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E)) // VS Code Editor Dark
            .testTag("code_editor_view")
    ) {
        // Editor Top Bar
        Surface(
            color = Color(0xFF252526),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Java File",
                        tint = Color(0xFFE5A038),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = fileName,
                        color = Color(0xFFCCCCCC),
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (diagnostics.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        val errCount = diagnostics.count { it.severity == DiagnosticSeverity.ERROR }
                        Surface(
                            color = if (errCount > 0) Color(0xFF5A1D28) else Color(0xFF433419),
                            shape = RoundedCornerShape(10.dp),
                            onClick = onOpenDiagnostics,
                            modifier = Modifier.testTag("editor_diagnostics_pill")
                        ) {
                            Text(
                                text = if (errCount > 0) "$errCount ✖" else "${diagnostics.size} ⚠",
                                color = if (errCount > 0) Color(0xFFFF7B72) else Color(0xFFD29922),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Blinking Cursor Indicator & Position pill
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Color(0xFF161B22),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isEditorFocused) Color(0xFF58A6FF) else Color(0xFF30363D)
                        ),
                        onClick = {
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        },
                        modifier = Modifier.testTag("editor_cursor_status_pill")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isEditorFocused) Color(0xFF58A6FF).copy(alpha = cursorBlinkAlpha)
                                        else Color(0xFF8B949E)
                                    )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Ln ${cursorPosition.first}, Col ${cursorPosition.second}",
                                color = if (isEditorFocused) Color(0xFF58A6FF) else Color(0xFF8B949E),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                }

            }
        }

        // Quick Clipboard and Edit Toolbar
        Surface(
            color = Color(0xFF1B2028),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Keyboard Toggle Button (Hide / Show)
                Surface(
                    color = if (isKeyboardVisible) Color(0xFF382326) else Color(0xFF1F4E79),
                    shape = RoundedCornerShape(6.dp),
                    onClick = toggleKeyboard,
                    modifier = Modifier.testTag("editor_keyboard_toggle_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (isKeyboardVisible) Icons.Default.KeyboardHide else Icons.Default.Keyboard,
                            contentDescription = if (isKeyboardVisible) "Hide Keyboard" else "Show Keyboard",
                            tint = if (isKeyboardVisible) Color(0xFFFFA198) else Color(0xFF79C0FF),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isKeyboardVisible) "Hide Keyboard" else "Show Keyboard",
                            color = if (isKeyboardVisible) Color(0xFFFFA198) else Color(0xFF79C0FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Paste Button (Prominent)
                Surface(
                    color = Color(0xFF1F4E79),
                    shape = RoundedCornerShape(6.dp),
                    onClick = handlePaste,
                    modifier = Modifier.testTag("editor_paste_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste",
                            tint = Color(0xFF79C0FF),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Paste",
                            color = Color(0xFF79C0FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Copy Button (Selection or All)
                Surface(
                    color = Color(0xFF2D333B),
                    shape = RoundedCornerShape(6.dp),
                    onClick = handleCopy,
                    modifier = Modifier.testTag("editor_copy_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = Color(0xFFC9D1D9),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (codeValue.selection.min != codeValue.selection.max) "Copy Sel" else "Copy All",
                            color = Color(0xFFC9D1D9),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Cut Button
                Surface(
                    color = Color(0xFF2D333B),
                    shape = RoundedCornerShape(6.dp),
                    onClick = handleCut,
                    modifier = Modifier.testTag("editor_cut_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCut,
                            contentDescription = "Cut",
                            tint = Color(0xFFC9D1D9),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Cut",
                            color = Color(0xFFC9D1D9),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Select All Button
                Surface(
                    color = Color(0xFF2D333B),
                    shape = RoundedCornerShape(6.dp),
                    onClick = handleSelectAll,
                    modifier = Modifier.testTag("editor_select_all_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SelectAll,
                            contentDescription = "Select All",
                            tint = Color(0xFFC9D1D9),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Select All",
                            color = Color(0xFFC9D1D9),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Clear Button
                Surface(
                    color = Color(0xFF382326),
                    shape = RoundedCornerShape(6.dp),
                    onClick = handleClear,
                    modifier = Modifier.testTag("editor_clear_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear",
                            tint = Color(0xFFFF7B72),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Clear",
                            color = Color(0xFFFF7B72),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Main Editor Surface (Line numbers + Monospaced text input)
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(vScrollState)
        ) {
            // Line numbers column
            Column(
                modifier = Modifier
                    .width(44.dp)
                    .background(Color(0xFF252526))
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.End
            ) {
                for (i in 1..linesCount) {
                    val hasError = errorLines.contains(i)
                    val hasWarning = warningLines.contains(i)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                    ) {
                        if (hasError) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF85149))
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                        } else if (hasWarning) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFD29922))
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                        }

                        Text(
                            text = i.toString(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = if (hasError) Color(0xFFF85149) else Color(0xFF6E7681)
                        )
                    }
                }
            }

            // Code input field with horizontal scroll
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(hScrollState)
                    .padding(horizontal = 8.dp, vertical = 12.dp)
                    .runOnTripleTap(onRun)
            ) {
                BasicTextField(
                    value = codeValue,
                    onValueChange = onCodeChange,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = Color(0xFFD4D4D4)
                    ),
                    cursorBrush = SolidColor(Color(0xFF58A6FF)),
                    visualTransformation = JavaSyntaxHighlighter.visualTransformation,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Default
                    ),
                    keyboardActions = KeyboardActions.Default,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minWidth = 1200.dp, minHeight = 800.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { isEditorFocused = it.isFocused }
                        .testTag("code_text_field")
                )
            }
        }

        // Accessory Symbol Toolbar for rapid coding on mobile
        AccessorySymbolBar(
            onInsert = { symbol ->
                val currentText = codeValue.text
                val selection = codeValue.selection
                val newText = currentText.replaceRange(selection.min, selection.max, symbol)
                val newCursor = selection.min + symbol.length
                onCodeChange(
                    codeValue.copy(
                        text = newText,
                        selection = TextRange(newCursor)
                    )
                )
            },
            onPaste = handlePaste,
            onCopy = handleCopy,
            onSelectAll = handleSelectAll,
            onMoveCursorLeft = onMoveCursorLeft,
            onMoveCursorRight = onMoveCursorRight,
            isKeyboardVisible = isKeyboardVisible,
            onToggleKeyboard = toggleKeyboard
        )
    }
}

private fun Modifier.runOnTripleTap(onRun: () -> Unit): Modifier = pointerInput(onRun) {
    var tapCount = 0
    var previousTapTime = 0L
    var previousTapPosition: Offset? = null

    awaitEachGesture {
        val down = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial
        )
        var moved = false
        var releasedAt: Long? = null
        var releasePosition = down.position

        while (releasedAt == null) {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                moved = true
            }
            if (!change.pressed) {
                releasedAt = change.uptimeMillis
                releasePosition = change.position
            }
        }

        val releaseTime = releasedAt
        if (releaseTime == null || moved || releaseTime - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis) {
            tapCount = 0
            previousTapPosition = null
            return@awaitEachGesture
        }

        val sameSpot = previousTapPosition?.let {
            (releasePosition - it).getDistance() <= viewConfiguration.touchSlop * 2
        } == true
        tapCount = if (
            sameSpot && releaseTime - previousTapTime <= viewConfiguration.doubleTapTimeoutMillis
        ) {
            tapCount + 1
        } else {
            1
        }
        previousTapTime = releaseTime
        previousTapPosition = releasePosition

        if (tapCount == 3) {
            tapCount = 0
            previousTapPosition = null
            onRun()
        }
    }
}

@Composable
fun AccessorySymbolBar(
    onInsert: (String) -> Unit,
    onPaste: () -> Unit,
    onCopy: () -> Unit,
    onSelectAll: () -> Unit,
    onMoveCursorLeft: () -> Unit,
    onMoveCursorRight: () -> Unit,
    isKeyboardVisible: Boolean,
    onToggleKeyboard: () -> Unit
) {
    val symbols = listOf(
        "TAB" to "    ",
        "{" to "{}",
        "}" to "}",
        "(" to "()",
        ")" to ")",
        ";" to ";",
        "\"" to "\"\"",
        "=" to " = ",
        "==" to " == ",
        "+" to " + ",
        "-" to " - ",
        "*" to " * ",
        "/" to " / ",
        "[" to "[]",
        "]" to "]",
        "." to ".",
        "sysout" to "System.out.println();",
        "Scanner" to "Scanner scanner = new Scanner(System.in);"
    )

    val scrollState = rememberScrollState()

    Surface(
        color = Color(0xFF252526),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Quick Hide / Show Keyboard button on thumb-level accessory bar
            Surface(
                color = if (isKeyboardVisible) Color(0xFF382326) else Color(0xFF1F4E79),
                shape = RoundedCornerShape(4.dp),
                onClick = onToggleKeyboard,
                modifier = Modifier.testTag("symbol_bar_keyboard_toggle")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isKeyboardVisible) Icons.Default.KeyboardHide else Icons.Default.Keyboard,
                        contentDescription = if (isKeyboardVisible) "Hide Keyboard" else "Show Keyboard",
                        tint = if (isKeyboardVisible) Color(0xFFFFA198) else Color(0xFF79C0FF),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (isKeyboardVisible) "HIDE KB" else "SHOW KB",
                        color = if (isKeyboardVisible) Color(0xFFFFA198) else Color(0xFF79C0FF),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            // Quick Paste button on accessory bar
            Surface(
                color = Color(0xFF1F4E79),
                shape = RoundedCornerShape(4.dp),
                onClick = onPaste,
                modifier = Modifier.testTag("symbol_bar_paste")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Paste",
                        tint = Color(0xFF79C0FF),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "PASTE",
                        color = Color(0xFF79C0FF),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Quick Copy button on accessory bar
            Surface(
                color = Color(0xFF333333),
                shape = RoundedCornerShape(4.dp),
                onClick = onCopy,
                modifier = Modifier.testTag("symbol_bar_copy")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = Color(0xFFC9D1D9),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "COPY",
                        color = Color(0xFFC9D1D9),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Quick Select All button on accessory bar
            Surface(
                color = Color(0xFF333333),
                shape = RoundedCornerShape(4.dp),
                onClick = onSelectAll,
                modifier = Modifier.testTag("symbol_bar_select_all")
            ) {
                Text(
                    text = "ALL",
                    color = Color(0xFFC9D1D9),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }

            // Move cursor left button
            Surface(
                color = Color(0xFF2D333B),
                shape = RoundedCornerShape(4.dp),
                onClick = onMoveCursorLeft,
                modifier = Modifier.testTag("symbol_bar_cursor_left")
            ) {
                Text(
                    text = "◀",
                    color = Color(0xFF58A6FF),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                )
            }

            // Move cursor right button
            Surface(
                color = Color(0xFF2D333B),
                shape = RoundedCornerShape(4.dp),
                onClick = onMoveCursorRight,
                modifier = Modifier.testTag("symbol_bar_cursor_right")
            ) {
                Text(
                    text = "▶",
                    color = Color(0xFF58A6FF),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                )
            }

            for ((label, insertVal) in symbols) {
                Surface(
                    color = Color(0xFF333333),
                    shape = RoundedCornerShape(4.dp),
                    onClick = { onInsert(insertVal) },
                    modifier = Modifier.testTag("symbol_bar_$label")
                ) {
                    Text(
                        text = label,
                        color = Color(0xFFE6EDF3),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
