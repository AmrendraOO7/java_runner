package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.compiler.DiagnosticSeverity
import com.example.compiler.JavaDiagnostic
import com.example.compiler.JavaExecutionEngine
import com.example.compiler.JavaSyntaxAnalyzer
import com.example.model.ProjectFile
import com.example.model.TerminalEntry
import com.example.model.TerminalStreamType
import com.example.ui.testing.TestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class IdeTab(val title: String) {
    EDITOR("Editor"),
    TERMINAL("Terminal"),
    DIAGNOSTICS("Diagnostics"),
    TESTING("Testing"),
    FILES("Files")
}

class JavaIdeViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = application.getSharedPreferences("java_runner_settings", Context.MODE_PRIVATE)
    private val executionEngine = JavaExecutionEngine()
    private val syntaxAnalyzer = JavaSyntaxAnalyzer()

    private val _activeTab = MutableStateFlow(IdeTab.EDITOR)
    val activeTab: StateFlow<IdeTab> = _activeTab.asStateFlow()

    private val _files = MutableStateFlow<List<ProjectFile>>(emptyList())
    val files: StateFlow<List<ProjectFile>> = _files.asStateFlow()

    private val _defaultProjectFolder = MutableStateFlow(
        preferences.getString("default_project_folder_uri", null)?.let(Uri::parse)
    )
    val defaultProjectFolder: StateFlow<Uri?> = _defaultProjectFolder.asStateFlow()

    private val _currentFile = MutableStateFlow(ProjectFile.defaultMain())
    val currentFile: StateFlow<ProjectFile> = _currentFile.asStateFlow()

    private val _codeValue = MutableStateFlow(TextFieldValue(_currentFile.value.content))
    val codeValue: StateFlow<TextFieldValue> = _codeValue.asStateFlow()

    private val _diagnostics = MutableStateFlow<List<JavaDiagnostic>>(emptyList())
    val diagnostics: StateFlow<List<JavaDiagnostic>> = _diagnostics.asStateFlow()

    private val _terminalEntries = MutableStateFlow<List<TerminalEntry>>(emptyList())
    val terminalEntries: StateFlow<List<TerminalEntry>> = _terminalEntries.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _lastExitCode = MutableStateFlow<Int?>(null)
    val lastExitCode: StateFlow<Int?> = _lastExitCode.asStateFlow()

    private val _lastDurationMs = MutableStateFlow<Long?>(null)
    val lastDurationMs: StateFlow<Long?> = _lastDurationMs.asStateFlow()

    private val _testCase = MutableStateFlow(
        TestCase(
            name = "Test Case 1",
            input = "15\n+\n25\n",
            expectedOutput = ""
        )
    )
    val testCase: StateFlow<TestCase> = _testCase.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var executionJob: Job? = null

    private var syntaxAnalysisJob: Job? = null

    init {
        val projectFolder = _defaultProjectFolder.value
        if (projectFolder == null) {
            loadPersistedFiles()
        } else {
            loadProjectFolder(projectFolder)
        }
        runRealtimeSyntaxAnalysis(_codeValue.value.text, immediate = true)
    }

    fun setActiveTab(tab: IdeTab) {
        _activeTab.value = tab
    }

    fun onCodeChanged(newValue: TextFieldValue) {
        _codeValue.value = newValue
        _currentFile.value = _currentFile.value.copy(
            content = newValue.text,
            isModified = true
        )
        runRealtimeSyntaxAnalysis(newValue.text, immediate = false)
    }

    private fun runRealtimeSyntaxAnalysis(code: String, immediate: Boolean = false) {
        syntaxAnalysisJob?.cancel()
        syntaxAnalysisJob = viewModelScope.launch(Dispatchers.Default) {
            if (!immediate) {
                delay(300)
            }
            val result = syntaxAnalyzer.analyze(code, _currentFile.value.name)
            _diagnostics.value = result.diagnostics
        }
    }

    fun selectFile(file: ProjectFile) {
        saveCurrentFileInternal()
        _currentFile.value = file
        _codeValue.value = TextFieldValue(file.content, selection = TextRange(0))
        runRealtimeSyntaxAnalysis(file.content)
        _activeTab.value = IdeTab.EDITOR
    }

    fun createNewFile(fileName: String, customContent: String? = null) {
        val className = fileName.removeSuffix(".java")
        val content = customContent ?: """
            public class $className {
                public static void main(String[] args) {
                    System.out.println("Running $className");
                }
            }
        """.trimIndent()

        val projectFolder = _defaultProjectFolder.value
        if (projectFolder != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val newFile = createDocumentInFolder(
                        projectFolder,
                        ProjectFile(name = fileName, content = content)
                    )
                    withContext(Dispatchers.Main) {
                        if (_defaultProjectFolder.value != projectFolder) return@withContext
                        _files.value = _files.value + newFile
                        selectFile(newFile)
                        _statusMessage.value = "Created $fileName in project folder"
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "Could not create $fileName: ${e.message}"
                    }
                }
            }
            return
        }

        val newFile = ProjectFile(
            name = fileName,
            content = content
        )
        val updated = _files.value + newFile
        _files.value = updated
        selectFile(newFile)
        persistFilesToDisk()
        _statusMessage.value = "Created $fileName"
    }

    fun deleteFile(fileId: String) {
        if (_files.value.size <= 1) return
        val fileToDelete = _files.value.find { it.id == fileId } ?: return
        if (_defaultProjectFolder.value != null && fileToDelete.uriString != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val deleted = DocumentsContract.deleteDocument(
                        getApplication<Application>().contentResolver,
                        Uri.parse(fileToDelete.uriString)
                    )
                    if (!deleted) throw IllegalStateException("Document provider did not delete ${fileToDelete.name}")

                    withContext(Dispatchers.Main) {
                        val updated = _files.value.filterNot { it.id == fileId }
                        _files.value = updated
                        if (_currentFile.value.id == fileId) {
                            selectFile(updated.first())
                        }
                        _statusMessage.value = "Deleted ${fileToDelete.name}"
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "Could not delete ${fileToDelete.name}: ${e.message}"
                    }
                }
            }
            return
        }

        val updated = _files.value.filter { it.id != fileId }
        _files.value = updated
        if (_currentFile.value.id == fileId) {
            selectFile(updated.first())
        }
        persistFilesToDisk()
    }

    fun loadTemplate(template: ProjectFile) {
        if (_defaultProjectFolder.value != null) {
            val existing = _files.value.find { it.name == template.name }
            if (existing != null) {
                selectFile(existing)
            } else {
                createNewFile(template.name, template.content)
            }
            return
        }

        saveCurrentFileInternal()
        val existing = _files.value.find { it.name == template.name }
        if (existing != null) {
            selectFile(existing.copy(content = template.content))
        } else {
            val newFile = template.copy(id = java.util.UUID.randomUUID().toString())
            _files.value = _files.value + newFile
            selectFile(newFile)
            persistFilesToDisk()
        }
    }

    fun saveCurrentFile(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val cur = _currentFile.value
            // If linked to external URI, write to SAF
            if (cur.uriString != null) {
                try {
                    val uri = Uri.parse(cur.uriString)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(_codeValue.value.text.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                    _statusMessage.value = "Saved to external folder: ${cur.name}"
                } catch (e: Exception) {
                    _statusMessage.value = "Failed to save externally: ${e.message}"
                }
            } else {
                _statusMessage.value = "Saved ${cur.name} successfully"
            }
            saveCurrentFileInternal()
            persistFilesToDisk()
        }
    }

    fun saveAsExternal(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(_codeValue.value.text.toByteArray(Charsets.UTF_8))
                    out.flush()
                }

                // Extract name from URI if possible
                val displayName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: _currentFile.value.name
                val updatedFile = _currentFile.value.copy(
                    uriString = uri.toString(),
                    name = if (displayName.endsWith(".java")) displayName else "$displayName.java",
                    content = _codeValue.value.text,
                    isModified = false
                )
                _currentFile.value = updatedFile

                // Update in files list
                val updatedList = _files.value.map { if (it.id == updatedFile.id) updatedFile else it }
                _files.value = updatedList
                persistFilesToDisk()

                _statusMessage.value = "File saved as: ${updatedFile.name}"
            } catch (e: Exception) {
                _statusMessage.value = "Save As failed: ${e.message}"
            }
        }
    }

    fun openExternalFile(context: Context, uri: Uri, fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: ""

                val newFile = ProjectFile(
                    name = fileName,
                    content = content,
                    uriString = uri.toString()
                )

                _files.value = _files.value + newFile
                withContext(Dispatchers.Main) {
                    selectFile(newFile)
                }
                persistFilesToDisk()
                _statusMessage.value = "Opened $fileName successfully"
            } catch (e: Exception) {
                _statusMessage.value = "Failed to open file: ${e.message}"
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun setDefaultProjectFolder(uri: Uri?) {
        if (_defaultProjectFolder.value == uri) {
            if (uri != null) loadProjectFolder(uri)
            return
        }

        saveCurrentFileInternal()
        persistFilesToDisk()
        preferences.edit().apply {
            if (uri == null) {
                remove("default_project_folder_uri")
            } else {
                putString("default_project_folder_uri", uri.toString())
            }
        }.apply()
        _defaultProjectFolder.value = uri
        if (uri == null) {
            loadPersistedFiles()
            _statusMessage.value = "Default project folder cleared"
        } else {
            loadProjectFolder(uri)
        }
    }

    fun runCode(initialStdin: String = "") {
        if (_isRunning.value) return

        clearTerminal()

        // Switch to terminal tab
        _activeTab.value = IdeTab.TERMINAL
        _isRunning.value = true

        val codeToRun = _codeValue.value.text

        // Save current code
        saveCurrentFileInternal()

        executionJob = viewModelScope.launch {
            executionEngine.execute(
                code = codeToRun,
                initialStdin = initialStdin,
                onOutput = { entry ->
                    _terminalEntries.value = _terminalEntries.value + entry
                },
                onErrorDiagnostic = { diag ->
                    // Add compiler diagnostic to list if not already present
                    val existing = _diagnostics.value
                    if (existing.none { it.line == diag.line && it.message == diag.message }) {
                        _diagnostics.value = existing + diag
                    }
                },
                onFinished = { exitCode, duration ->
                    _isRunning.value = false
                    _lastExitCode.value = exitCode
                    _lastDurationMs.value = duration
                }
            )
        }
    }

    fun stopExecution() {
        executionEngine.stopExecution()
        executionJob?.cancel()
        _isRunning.value = false
    }

    fun sendTerminalInput(text: String) {
        _terminalEntries.value = _terminalEntries.value + TerminalEntry(
            type = TerminalStreamType.STDIN,
            text = text
        )
        executionEngine.sendInput(text)
    }

    fun clearTerminal() {
        _terminalEntries.value = emptyList()
        _lastExitCode.value = null
        _lastDurationMs.value = null
    }

    fun updateTestCaseInput(input: String) {
        _testCase.value = _testCase.value.copy(input = input)
    }

    fun updateTestCaseExpected(expected: String) {
        _testCase.value = _testCase.value.copy(expectedOutput = expected)
    }

    fun runTestCase() {
        val currentTc = _testCase.value
        val capturedOutput = StringBuilder()

        _isRunning.value = true
        _activeTab.value = IdeTab.TESTING

        viewModelScope.launch {
            executionEngine.execute(
                code = _codeValue.value.text,
                initialStdin = currentTc.input,
                onOutput = { entry ->
                    if (entry.type == TerminalStreamType.STDOUT || entry.type == TerminalStreamType.STDERR) {
                        capturedOutput.append(entry.text).append("\n")
                    }
                    _terminalEntries.value = _terminalEntries.value + entry
                },
                onErrorDiagnostic = { diag ->
                    _diagnostics.value = _diagnostics.value + diag
                },
                onFinished = { exitCode, duration ->
                    _isRunning.value = false
                    val actual = capturedOutput.toString().trim()
                    val expected = currentTc.expectedOutput.trim()
                    val passed = if (expected.isNotEmpty()) {
                        actual.contains(expected) || actual == expected
                    } else {
                        exitCode == 0
                    }

                    _testCase.value = currentTc.copy(
                        actualOutput = actual,
                        passed = passed,
                        executionTimeMs = duration
                    )
                }
            )
        }
    }

    fun jumpToDiagnostic(diag: JavaDiagnostic) {
        _activeTab.value = IdeTab.EDITOR
        val text = _codeValue.value.text
        val lines = text.lines()
        var charIndex = 0
        for (i in 0 until (diag.line - 1).coerceAtMost(lines.size - 1)) {
            charIndex += lines[i].length + 1
        }
        val targetIndex = (charIndex + (diag.column - 1)).coerceIn(0, text.length)
        _codeValue.value = _codeValue.value.copy(
            selection = TextRange(targetIndex, targetIndex)
        )
    }

    private fun saveCurrentFileInternal() {
        val current = _currentFile.value
        val updated = current.copy(
            content = _codeValue.value.text,
            isModified = false,
            lastModified = System.currentTimeMillis()
        )
        _currentFile.value = updated
        _files.value = _files.value.map { if (it.id == updated.id) updated else it }
    }

    private fun persistFilesToDisk() {
        if (_defaultProjectFolder.value != null) return
        val filesToPersist = _files.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val dir = File(context.filesDir, "java_workspace")
                if (!dir.exists()) dir.mkdirs()

                // Save each file
                for (file in filesToPersist) {
                    val target = File(dir, file.name)
                    target.writeText(file.content)
                }
            } catch (_: Exception) {}
        }
    }

    private fun loadProjectFolder(folderUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = getApplication<Application>().contentResolver
                val treeDocumentId = DocumentsContract.getTreeDocumentId(folderUri)
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, treeDocumentId)
                val loaded = mutableListOf<ProjectFile>()
                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                )

                resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val modifiedColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameColumn) ?: continue
                        if (!name.endsWith(".java", ignoreCase = true)) continue
                        if (cursor.getString(mimeColumn) == DocumentsContract.Document.MIME_TYPE_DIR) continue

                        val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                            folderUri,
                            cursor.getString(idColumn)
                        )
                        val content = resolver.openInputStream(documentUri)?.use { stream ->
                            stream.bufferedReader(Charsets.UTF_8).readText()
                        } ?: continue

                        loaded += ProjectFile(
                            id = documentUri.toString(),
                            name = name,
                            content = content,
                            uriString = documentUri.toString(),
                            lastModified = cursor.getLong(modifiedColumn)
                        )
                    }
                }

                if (loaded.isEmpty()) {
                    loaded += createDocumentInFolder(folderUri, ProjectFile.defaultMain())
                }

                withContext(Dispatchers.Main) {
                    if (_defaultProjectFolder.value != folderUri) return@withContext
                    val orderedFiles = loaded.sortedBy { it.name.lowercase() }
                    val selectedFile = orderedFiles.first()
                    _files.value = orderedFiles
                    _currentFile.value = selectedFile
                    _codeValue.value = TextFieldValue(selectedFile.content, selection = TextRange(0))
                    _activeTab.value = IdeTab.FILES
                    runRealtimeSyntaxAnalysis(selectedFile.content)
                    _statusMessage.value = "Loaded ${orderedFiles.size} Java file(s)"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (_defaultProjectFolder.value == folderUri) {
                        _statusMessage.value = "Could not load project folder: ${e.message}"
                    }
                }
            }
        }
    }

    private fun createDocumentInFolder(folderUri: Uri, file: ProjectFile): ProjectFile {
        val resolver = getApplication<Application>().contentResolver
        val parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
            folderUri,
            DocumentsContract.getTreeDocumentId(folderUri)
        )
        val documentUri = DocumentsContract.createDocument(
            resolver,
            parentDocumentUri,
            "text/x-java",
            file.name
        ) ?: throw IllegalStateException("Could not create ${file.name}")

        resolver.openOutputStream(documentUri, "wt")?.use { output ->
            output.write(file.content.toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("Could not write ${file.name}")

        return file.copy(
            id = documentUri.toString(),
            uriString = documentUri.toString(),
            isModified = false
        )
    }

    private fun loadPersistedFiles() {
        val context = getApplication<Application>()
        val dir = File(context.filesDir, "java_workspace")
        if (dir.exists() && dir.isDirectory) {
            val addressTestsDiskFile = File(dir, "AddressTests.java")
            if (addressTestsDiskFile.exists()) {
                addressTestsDiskFile.delete()
            }
            val loaded = dir.listFiles { _, name -> name.endsWith(".java") && name != "AddressTests.java" }?.map { f ->
                ProjectFile(
                    name = f.name,
                    content = f.readText(),
                    lastModified = f.lastModified()
                )
            }.orEmpty()

            if (loaded.isNotEmpty()) {
                _files.value = loaded
                _currentFile.value = loaded.first()
                _codeValue.value = TextFieldValue(loaded.first().content)
                return
            }
        }

        // Defaults if no persisted files
        val defaults = ProjectFile.sampleTemplates()
        _files.value = defaults
        _currentFile.value = defaults.first()
        _codeValue.value = TextFieldValue(defaults.first().content)
        persistFilesToDisk()
    }
}
