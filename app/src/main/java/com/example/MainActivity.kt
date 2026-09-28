package com.example

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.compiler.DiagnosticSeverity
import com.example.ui.diagnostics.DiagnosticsView
import com.example.ui.editor.CodeEditorView
import com.example.ui.files.CreateFileDialog
import com.example.ui.files.FileDrawer
import com.example.ui.terminal.TerminalView
import com.example.ui.testing.TestingView
import com.example.util.ClipboardHelper
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.IdeTab
import com.example.viewmodel.JavaIdeViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: JavaIdeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true) {
                JavaRunnerApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JavaRunnerApp(viewModel: JavaIdeViewModel) {
    val context = LocalContext.current
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val currentFile by viewModel.currentFile.collectAsStateWithLifecycle()
    val files by viewModel.files.collectAsStateWithLifecycle()
    val codeValue by viewModel.codeValue.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val terminalEntries by viewModel.terminalEntries.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val lastExitCode by viewModel.lastExitCode.collectAsStateWithLifecycle()
    val lastDurationMs by viewModel.lastDurationMs.collectAsStateWithLifecycle()
    val testCase by viewModel.testCase.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val defaultProjectFolder by viewModel.defaultProjectFolder.collectAsStateWithLifecycle()
    val defaultProjectFolderName = defaultProjectFolder?.let { uri ->
        runCatching {
            DocumentsContract.getTreeDocumentId(uri)
                .substringAfterLast('/')
                .substringAfterLast(':')
                .ifBlank { "Selected folder" }
        }.getOrDefault("Selected folder")
    }

    var showCreateFileDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    // Android SAF: Save As, starting in the selected default project folder.
    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            retainUriPermission(context, uri, result.data?.flags ?: 0)
            viewModel.saveAsExternal(context, uri)
        }
    }

    // Android SAF: Open a file, starting in the selected default project folder.
    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            retainUriPermission(context, uri, result.data?.flags ?: 0)
            var fileName = "Imported.java"
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            } catch (_: Exception) {}

            viewModel.openExternalFile(context, uri, fileName)
        }
    }

    val projectFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                viewModel.setDefaultProjectFolder(uri)
            } catch (_: SecurityException) {
                Toast.makeText(context, "Could not retain access to that folder", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Handle back button on sub-tabs
    BackHandler(enabled = activeTab != IdeTab.EDITOR) {
        viewModel.setActiveTab(IdeTab.EDITOR)
    }

    val errorCount = remember(diagnostics) {
        diagnostics.count { it.severity == DiagnosticSeverity.ERROR }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("java_runner_scaffold"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFFE5A038),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "☕ Java Runner",
                                color = Color(0xFF1E1E1E),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    // Quick Paste into Editor
                    IconButton(
                        onClick = {
                            val text = ClipboardHelper.getClipboardText(context)
                            if (text.isNullOrEmpty()) {
                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                            } else {
                                val currentCode = codeValue.text
                                val sel = codeValue.selection
                                val newCode = if (currentCode.isEmpty()) {
                                    text
                                } else {
                                    currentCode.replaceRange(sel.min, sel.max, text)
                                }
                                val newCursor = (sel.min + text.length).coerceIn(0, newCode.length)
                                viewModel.onCodeChanged(
                                    codeValue.copy(
                                        text = newCode,
                                        selection = androidx.compose.ui.text.TextRange(newCursor)
                                    )
                                )
                                Toast.makeText(context, "Pasted ${text.length} characters", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("appbar_paste_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste",
                            tint = Color(0xFF79C0FF)
                        )
                    }

                    // Quick Copy Current Code
                    IconButton(
                        onClick = {
                            val currentCode = codeValue.text
                            if (currentCode.isEmpty()) {
                                Toast.makeText(context, "No code to copy", Toast.LENGTH_SHORT).show()
                            } else {
                                ClipboardHelper.copyText(context, currentCode, "Java Code", showToast = true)
                            }
                        },
                        modifier = Modifier.testTag("appbar_copy_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy All Code",
                            tint = Color(0xFFC9D1D9)
                        )
                    }

                    // New File Action in TopAppBar
                    IconButton(
                        onClick = { showCreateFileDialog = true },
                        modifier = Modifier.testTag("appbar_new_file_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NoteAdd,
                            contentDescription = "New File",
                            tint = Color(0xFF58A6FF)
                        )
                    }

                    // Quick Save Button
                    IconButton(
                        onClick = { viewModel.saveCurrentFile(context) },
                        modifier = Modifier.testTag("appbar_save_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save File",
                            tint = Color(0xFFC9D1D9)
                        )
                    }

                    // Run / Stop action in Top Bar
                    if (isRunning) {
                        Button(
                            onClick = { viewModel.stopExecution() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDA3633)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("appbar_stop_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.runCode() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("appbar_run_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Run",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Run", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF161B22)
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161B22))
            ) {
                NavigationBar(
                    containerColor = Color(0xFF161B22),
                    contentColor = Color(0xFF8B949E),
                    windowInsets = WindowInsets(0, 0, 0, 0)
                ) {
                // Editor Tab
                NavigationBarItem(
                    selected = activeTab == IdeTab.EDITOR,
                    onClick = { viewModel.setActiveTab(IdeTab.EDITOR) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "Editor Tab"
                        )
                    },
                    label = { Text("Editor", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF58A6FF),
                        selectedTextColor = Color(0xFF58A6FF),
                        unselectedIconColor = Color(0xFF8B949E),
                        unselectedTextColor = Color(0xFF8B949E),
                        indicatorColor = Color(0xFF1F242C)
                    ),
                    modifier = Modifier.testTag("nav_editor_tab")
                )

                // Terminal Tab
                NavigationBarItem(
                    selected = activeTab == IdeTab.TERMINAL,
                    onClick = { viewModel.setActiveTab(IdeTab.TERMINAL) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (isRunning) {
                                    Badge(containerColor = Color(0xFFE3B341))
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "Terminal Tab"
                            )
                        }
                    },
                    label = { Text("Terminal", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF3FB950),
                        selectedTextColor = Color(0xFF3FB950),
                        unselectedIconColor = Color(0xFF8B949E),
                        unselectedTextColor = Color(0xFF8B949E),
                        indicatorColor = Color(0xFF1B2F23)
                    ),
                    modifier = Modifier.testTag("nav_terminal_tab")
                )

                // Diagnostics Tab
                NavigationBarItem(
                    selected = activeTab == IdeTab.DIAGNOSTICS,
                    onClick = { viewModel.setActiveTab(IdeTab.DIAGNOSTICS) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (errorCount > 0) {
                                    Badge(containerColor = Color(0xFFF85149)) {
                                        Text("$errorCount")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = "Diagnostics Tab"
                            )
                        }
                    },
                    label = { Text("Diagnostics", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFF85149),
                        selectedTextColor = Color(0xFFF85149),
                        unselectedIconColor = Color(0xFF8B949E),
                        unselectedTextColor = Color(0xFF8B949E),
                        indicatorColor = Color(0xFF331F23)
                    ),
                    modifier = Modifier.testTag("nav_diagnostics_tab")
                )

                // Testing Tab
                NavigationBarItem(
                    selected = activeTab == IdeTab.TESTING,
                    onClick = { viewModel.setActiveTab(IdeTab.TESTING) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Quiz,
                            contentDescription = "Testing Tab"
                        )
                    },
                    label = { Text("Testing", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFD29922),
                        selectedTextColor = Color(0xFFD29922),
                        unselectedIconColor = Color(0xFF8B949E),
                        unselectedTextColor = Color(0xFF8B949E),
                        indicatorColor = Color(0xFF2E2619)
                    ),
                    modifier = Modifier.testTag("nav_testing_tab")
                )

                // Files Tab
                NavigationBarItem(
                    selected = activeTab == IdeTab.FILES,
                    onClick = { viewModel.setActiveTab(IdeTab.FILES) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Files Tab"
                        )
                    },
                    label = { Text("Files", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF58A6FF),
                        selectedTextColor = Color(0xFF58A6FF),
                        unselectedIconColor = Color(0xFF8B949E),
                        unselectedTextColor = Color(0xFF8B949E),
                        indicatorColor = Color(0xFF1F242C)
                    ),
                    modifier = Modifier.testTag("nav_files_tab")
                )
                }
                Text(
                    text = "Java Runner By Amrendra Chaurasia",
                    color = Color(0xFF8B949E),
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(top = 2.dp, bottom = 4.dp)
                        .testTag("app_footer")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF1E1E1E))
        ) {
            when (activeTab) {
                IdeTab.EDITOR -> {
                    CodeEditorView(
                        fileName = currentFile.name,
                        codeValue = codeValue,
                        onCodeChange = viewModel::onCodeChanged,
                        diagnostics = diagnostics,
                        onRun = { viewModel.runCode() },
                        onOpenDiagnostics = { viewModel.setActiveTab(IdeTab.DIAGNOSTICS) }
                    )
                }

                IdeTab.TERMINAL -> {
                    TerminalView(
                        entries = terminalEntries,
                        isRunning = isRunning,
                        onClear = { viewModel.clearTerminal() },
                        onSendInput = { viewModel.sendTerminalInput(it) },
                        lastExitCode = lastExitCode,
                        lastDurationMs = lastDurationMs
                    )
                }

                IdeTab.DIAGNOSTICS -> {
                    DiagnosticsView(
                        diagnostics = diagnostics,
                        onSelectDiagnostic = { diag ->
                            viewModel.jumpToDiagnostic(diag)
                        }
                    )
                }

                IdeTab.TESTING -> {
                    TestingView(
                        testCase = testCase,
                        onInputChange = viewModel::updateTestCaseInput,
                        onExpectedChange = viewModel::updateTestCaseExpected,
                        isRunning = isRunning,
                        onRunTest = { viewModel.runTestCase() }
                    )
                }

                IdeTab.FILES -> {
                    FileDrawer(
                        files = files,
                        currentFileId = currentFile.id,
                        onSelectFile = { viewModel.selectFile(it) },
                        defaultProjectFolder = defaultProjectFolderName,
                        onChangeDefaultProjectFolder = {
                            projectFolderPicker.launch(defaultProjectFolder)
                        },
                        onDeleteFile = { viewModel.deleteFile(it) },
                        onSaveCurrent = { viewModel.saveCurrentFile(context) },
                        onSaveAsExternal = {
                            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "text/x-java"
                                putExtra(Intent.EXTRA_TITLE, currentFile.name)
                                defaultProjectFolder?.let {
                                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, it)
                                }
                            }
                            saveAsLauncher.launch(intent)
                        },
                        onOpenExternal = {
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                                defaultProjectFolder?.let {
                                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, it)
                                }
                            }
                            openFileLauncher.launch(intent)
                        },
                        onLoadTemplate = { viewModel.loadTemplate(it) }
                    )
                }
            }
        }
    }

    if (showCreateFileDialog) {
        CreateFileDialog(
            onDismiss = { showCreateFileDialog = false },
            onCreate = { fileName, content ->
                viewModel.createNewFile(fileName, content)
                showCreateFileDialog = false
            }
        )
    }
}

private fun retainUriPermission(context: android.content.Context, uri: Uri, flags: Int) {
    val accessFlags = flags and (
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    )
    if (accessFlags != 0) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, accessFlags)
        } catch (_: SecurityException) {
            // A selected file can still be used for the current session without persistent access.
        }
    }
}
