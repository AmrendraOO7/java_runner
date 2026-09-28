package com.example.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.ClipboardHelper

enum class FileStarterTemplate(
    val label: String,
    val icon: ImageVector,
    val description: String
) {
    STANDARD_MAIN(
        label = "Main Class",
        icon = Icons.Default.Code,
        description = "Executable class with public static void main"
    ),
    SCANNER_INPUT(
        label = "Scanner Input",
        icon = Icons.Default.Input,
        description = "Interactive program reading from System.in"
    ),
    OOP_CLASS(
        label = "OOP Class",
        icon = Icons.Default.Widgets,
        description = "Object blueprint with constructor & methods"
    ),
    BLANK(
        label = "Blank File",
        icon = Icons.Default.NoteAdd,
        description = "Empty .java file to write your own code"
    );

    fun generateContent(className: String): String {
        return when (this) {
            STANDARD_MAIN -> """
                public class $className {
                    public static void main(String[] args) {
                        System.out.println("Hello from $className!");
                    }
                }
            """.trimIndent()

            SCANNER_INPUT -> """
                import java.util.Scanner;

                public class $className {
                    public static void main(String[] args) {
                        Scanner scanner = new Scanner(System.in);
                        System.out.println("--- $className ---");
                        System.out.println("Please enter your name or a number:");
                        
                        if (scanner.hasNextLine()) {
                            String input = scanner.nextLine();
                            System.out.println("Received input: " + input);
                        } else {
                            System.out.println("No input provided.");
                        }
                    }
                }
            """.trimIndent()

            OOP_CLASS -> """
                public class $className {
                    private String title;
                    private int value;

                    public $className(String title, int value) {
                        this.title = title;
                        this.value = value;
                    }

                    public String getTitle() {
                        return title;
                    }

                    public int getValue() {
                        return value;
                    }

                    public void display() {
                        System.out.println(title + ": " + value);
                    }

                    public static void main(String[] args) {
                        $className item = new $className("Sample Item", 100);
                        item.display();
                    }
                }
            """.trimIndent()

            BLANK -> ""
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateFileDialog(
    onDismiss: () -> Unit,
    onCreate: (fileName: String, initialContent: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var rawName by remember { mutableStateOf("NewProgram") }
    var selectedTemplate by remember { mutableStateOf(FileStarterTemplate.STANDARD_MAIN) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val cleanClassName = rawName.trim().removeSuffix(".java").replace(" ", "")
    val finalFileName = if (cleanClassName.isEmpty()) "Program.java" else "$cleanClassName.java"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NoteAdd,
                    contentDescription = "New File",
                    tint = Color(0xFF58A6FF),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Create New Java File",
                    color = Color(0xFFE6EDF3),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "File / Class Name",
                    color = Color(0xFF8B949E),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = rawName,
                    onValueChange = {
                        rawName = it
                        errorMessage = null
                    },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "e.g. MyAlgorithm",
                            color = Color(0xFF6E7681),
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_file_name_input"),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color(0xFFE6EDF3),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val text = ClipboardHelper.getClipboardText(context)
                                if (!text.isNullOrEmpty()) {
                                    val clean = text.trim().removeSuffix(".java").replace(" ", "")
                                    if (clean.isNotEmpty()) {
                                        rawName = clean
                                    }
                                }
                            },
                            modifier = Modifier.size(28.dp).testTag("create_file_paste_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Paste Name",
                                tint = Color(0xFF58A6FF),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    suffix = {
                        Text(
                            text = ".java",
                            color = Color(0xFF8B949E),
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    isError = errorMessage != null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF58A6FF),
                        unfocusedBorderColor = Color(0xFF30363D)
                    )
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFF85149),
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Starter Template",
                    color = Color(0xFF8B949E),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Template selection chips
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FileStarterTemplate.values().forEach { tmpl ->
                        val isSelected = tmpl == selectedTemplate
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFF0D2D44) else Color(0xFF161B22),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) Color(0xFF58A6FF) else Color(0xFF30363D)
                            ),
                            modifier = Modifier
                                .clickable { selectedTemplate = tmpl }
                                .testTag("template_chip_${tmpl.name}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = tmpl.icon,
                                    contentDescription = tmpl.label,
                                    tint = if (isSelected) Color(0xFF58A6FF) else Color(0xFF8B949E),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tmpl.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFF58A6FF) else Color(0xFFC9D1D9)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = selectedTemplate.description,
                    color = Color(0xFF8B949E),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (cleanClassName.isEmpty()) {
                        errorMessage = "Please enter a valid file name"
                        return@Button
                    }
                    val content = selectedTemplate.generateContent(cleanClassName)
                    onCreate(finalFileName, content)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("confirm_create_file_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Create File", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_create_file_button")
            ) {
                Text("Cancel", color = Color(0xFF8B949E))
            }
        },
        containerColor = Color(0xFF252526),
        modifier = modifier.testTag("create_file_dialog")
    )
}
