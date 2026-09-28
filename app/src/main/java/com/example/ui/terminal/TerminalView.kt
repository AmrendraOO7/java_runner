package com.example.ui.terminal

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TerminalEntry
import com.example.model.TerminalStreamType
import com.example.util.ClipboardHelper

@Composable
fun TerminalView(
    entries: List<TerminalEntry>,
    isRunning: Boolean,
    onClear: () -> Unit,
    onSendInput: (String) -> Unit,
    lastExitCode: Int?,
    lastDurationMs: Long?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var fontSizeSp by remember { mutableIntStateOf(13) }

    // Auto-scroll to bottom on new output
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117)) // GitHub Dark / Terminal Black
            .testTag("terminal_view")
    ) {
        // Terminal Header Bar
        Surface(
            color = Color(0xFF161B22),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Status indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusDotColor = when {
                        isRunning -> Color(0xFFE3B341) // Yellow running
                        lastExitCode == null -> Color(0xFF8B949E) // Gray ready
                        lastExitCode == 0 -> Color(0xFF3FB950) // Green success
                        else -> Color(0xFFF85149) // Red error
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isRunning -> "RUNNING..."
                            lastExitCode == null -> "READY"
                            lastExitCode == 0 -> "EXIT CODE 0"
                            else -> "EXIT CODE $lastExitCode"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color(0xFFC9D1D9)
                    )

                    if (lastDurationMs != null && !isRunning) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(${lastDurationMs}ms)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF8B949E)
                        )
                    }
                }

                // Control Actions
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Font size toggle
                    IconButton(
                        onClick = {
                            fontSizeSp = if (fontSizeSp >= 16) 11 else fontSizeSp + 2
                        },
                        modifier = Modifier.size(32.dp).testTag("terminal_font_size_btn")
                    ) {
                        Text(
                            text = "${fontSizeSp}sp",
                            fontSize = 10.sp,
                            color = Color(0xFF8B949E),
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Copy all output
                    IconButton(
                        onClick = {
                            val allOutput = entries.joinToString("\n") { it.text }
                            ClipboardHelper.copyText(context, allOutput, "Terminal Output", showToast = true)
                        },
                        modifier = Modifier.size(32.dp).testTag("terminal_copy_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Output",
                            tint = Color(0xFF8B949E),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Clear terminal
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(32.dp).testTag("terminal_clear_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear Terminal",
                            tint = Color(0xFF8B949E),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                }
            }
        }

        // Terminal Log Output
        SelectionContainer(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Terminal is idle",
                            color = Color(0xFF484F58),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap Run to compile & execute Java code",
                            color = Color(0xFF30363D),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        TerminalLine(entry = entry, fontSizeSp = fontSizeSp)
                    }
                }
            }
        }

        // Running progress bar
        if (isRunning) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161B22))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = Color(0xFF58A6FF),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Executing program (awaiting output or input)...",
                    color = Color(0xFF58A6FF),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Interactive STDIN Input Bar
        Surface(
            color = Color(0xFF161B22),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ">",
                    color = Color(0xFF388BFD),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(start = 4.dp, end = 6.dp)
                )

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = if (isRunning) "Send input to Scanner (System.in)..." else "STDIN input (type and send)...",
                            fontSize = 12.sp,
                            color = Color(0xFF484F58),
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("terminal_stdin_input"),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color(0xFFE6EDF3),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    ),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF388BFD),
                        unfocusedBorderColor = Color(0xFF30363D),
                        cursorColor = Color(0xFF58A6FF)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotEmpty()) {
                                onSendInput(inputText)
                                inputText = ""
                            }
                        }
                    )
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Paste from clipboard into STDIN
                IconButton(
                    onClick = {
                        val text = ClipboardHelper.getClipboardText(context)
                        if (!text.isNullOrEmpty()) {
                            inputText += text
                            Toast.makeText(context, "Pasted into input", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("terminal_stdin_paste_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Paste to STDIN",
                        tint = Color(0xFF79C0FF),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = {
                        if (inputText.isNotEmpty()) {
                            onSendInput(inputText)
                            inputText = ""
                        }
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("terminal_stdin_send_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send STDIN",
                        tint = if (inputText.isNotBlank()) Color(0xFF58A6FF) else Color(0xFF484F58),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TerminalLine(entry: TerminalEntry, fontSizeSp: Int) {
    val (textColor, prefix) = when (entry.type) {
        TerminalStreamType.STDOUT -> Pair(Color(0xFFE6EDF3), "")
        TerminalStreamType.STDERR -> Pair(Color(0xFFF85149), "")
        TerminalStreamType.STDIN -> Pair(Color(0xFF79C0FF), "> ")
        TerminalStreamType.INFO -> Pair(Color(0xFF8B949E), "")
        TerminalStreamType.SUCCESS -> Pair(Color(0xFF3FB950), "")
        TerminalStreamType.ERROR -> Pair(Color(0xFFF85149), "")
        TerminalStreamType.SYSTEM -> Pair(Color(0xFFD29922), "")
    }

    val horizontalScroll = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .horizontalScroll(horizontalScroll)
    ) {
        Text(
            text = "$prefix${entry.text}",
            color = textColor,
            fontFamily = FontFamily.Monospace,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp + 5).sp
        )
    }
}
