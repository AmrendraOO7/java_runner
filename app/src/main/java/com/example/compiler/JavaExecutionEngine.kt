package com.example.compiler

import bsh.EvalError
import bsh.Interpreter
import bsh.ParseException
import bsh.TargetError
import com.example.model.TerminalEntry
import com.example.model.TerminalStreamType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PrintStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

class JavaExecutionEngine {

    private val isExecuting = AtomicBoolean(false)
    private var currentExecutionThread: Thread? = null
    private var pipedOutputStream: PipedOutputStream? = null

    val isRunning: Boolean
        get() = isExecuting.get()

    /**
     * Feeds dynamic stdin input to the currently running program.
     */
    fun sendInput(input: String): Boolean {
        return try {
            val stream = pipedOutputStream
            if (stream != null && isExecuting.get()) {
                val bytes = (input + "\n").toByteArray(Charsets.UTF_8)
                stream.write(bytes)
                stream.flush()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Interrupts and halts the running execution.
     */
    fun stopExecution() {
        if (isExecuting.get()) {
            try {
                pipedOutputStream?.close()
            } catch (_: Exception) {}
            pipedOutputStream = null
            currentExecutionThread?.interrupt()
            isExecuting.set(false)
        }
    }

    /**
     * Executes the Java code and streams output to onOutput callback.
     */
    suspend fun execute(
        code: String,
        initialStdin: String = "",
        onOutput: (TerminalEntry) -> Unit,
        onErrorDiagnostic: (JavaDiagnostic) -> Unit,
        onFinished: (exitCode: Int, durationMs: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (!isExecuting.compareAndSet(false, true)) {
            onOutput(
                TerminalEntry(
                    type = TerminalStreamType.INFO,
                    text = "A program is already running. Please wait or click Stop."
                )
            )
            return@withContext
        }

        currentExecutionThread = Thread.currentThread()
        val startTime = System.currentTimeMillis()

        onOutput(
            TerminalEntry(
                type = TerminalStreamType.SYSTEM,
                text = ">>> Compiling and executing Java program..."
            )
        )

        // Setup STDIN pipe
        val pipedIn = PipedInputStream(1024 * 16)
        val pipedOut = PipedOutputStream(pipedIn)
        pipedOutputStream = pipedOut

        // Preload any initial stdin provided
        if (initialStdin.isNotEmpty()) {
            try {
                val initialBytes = (initialStdin + "\n").toByteArray(Charsets.UTF_8)
                pipedOut.write(initialBytes)
                pipedOut.flush()
            } catch (_: Exception) {}
        }

        // Save original system streams
        val originalOut = System.out
        val originalErr = System.err
        val originalIn = System.`in`

        // Custom PrintStream to stream stdout in real-time
        val customOutStream = object : OutputStream() {
            private val buffer = StringBuilder()

            override fun write(b: Int) {
                if (b == '\n'.code) {
                    val line = buffer.toString()
                    buffer.setLength(0)
                    onOutput(TerminalEntry(type = TerminalStreamType.STDOUT, text = line))
                } else if (b != '\r'.code) {
                    buffer.append(b.toChar())
                }
            }

            override fun flush() {
                if (buffer.isNotEmpty()) {
                    val line = buffer.toString()
                    buffer.setLength(0)
                    onOutput(TerminalEntry(type = TerminalStreamType.STDOUT, text = line))
                }
            }
        }

        val customErrStream = object : OutputStream() {
            private val buffer = StringBuilder()

            override fun write(b: Int) {
                if (b == '\n'.code) {
                    val line = buffer.toString()
                    buffer.setLength(0)
                    onOutput(TerminalEntry(type = TerminalStreamType.STDERR, text = line))
                } else if (b != '\r'.code) {
                    buffer.append(b.toChar())
                }
            }

            override fun flush() {
                if (buffer.isNotEmpty()) {
                    val line = buffer.toString()
                    buffer.setLength(0)
                    onOutput(TerminalEntry(type = TerminalStreamType.STDERR, text = line))
                }
            }
        }

        val printOut = PrintStream(customOutStream, true)
        val printErr = PrintStream(customErrStream, true)

        var exitCode = 0

        try {
            System.setOut(printOut)
            System.setErr(printErr)
            System.setIn(pipedIn)

            val interpreter = Interpreter()
            interpreter.setOut(printOut)
            interpreter.setErr(printErr)

            // Preprocess code: comment out package declarations since BeanShell is an in-memory interpreter
            val preprocessedCode = preprocessCode(code)

            val mainScript = extractMainMethodScript(preprocessedCode)
            if (mainScript != null) {
                interpreter.eval(mainScript)
            } else {
                val detectedClass = findMainClass(preprocessedCode)
                interpreter.eval(preprocessedCode)
                if (detectedClass != null) {
                    interpreter.eval("$detectedClass.main(new String[0]);")
                }
            }

            printOut.flush()
            printErr.flush()
            exitCode = 0
        } catch (e: ParseException) {
            printOut.flush()
            printErr.flush()
            exitCode = 1
            val errorLine = e.errorLineNumber.takeIf { it > 0 } ?: 1
            val message = e.message ?: "Syntax parse error"
            onOutput(
                TerminalEntry(
                    type = TerminalStreamType.STDERR,
                    text = "Compilation Error at line $errorLine: $message"
                )
            )
            onErrorDiagnostic(
                JavaDiagnostic(
                    line = errorLine,
                    column = 1,
                    message = message,
                    severity = DiagnosticSeverity.ERROR,
                    source = "Java Compiler"
                )
            )
        } catch (e: TargetError) {
            printOut.flush()
            printErr.flush()
            exitCode = 1
            val target = e.target
            val targetName = target?.javaClass?.simpleName ?: "Exception"
            val targetMsg = target?.message ?: e.message ?: "Runtime error"
            val errorLine = e.errorLineNumber.takeIf { it > 0 } ?: 1

            onOutput(
                TerminalEntry(
                    type = TerminalStreamType.STDERR,
                    text = "Runtime Exception ($targetName): $targetMsg at line $errorLine"
                )
            )

            // Format stack trace lines
            target?.stackTrace?.take(6)?.forEach { elem ->
                onOutput(
                    TerminalEntry(
                        type = TerminalStreamType.STDERR,
                        text = "    at ${elem.className}.${elem.methodName}(${elem.fileName}:${elem.lineNumber})"
                    )
                )
            }

            onErrorDiagnostic(
                JavaDiagnostic(
                    line = errorLine,
                    column = 1,
                    message = "$targetName: $targetMsg",
                    severity = DiagnosticSeverity.ERROR,
                    source = "Java Runtime"
                )
            )
        } catch (e: EvalError) {
            printOut.flush()
            printErr.flush()
            exitCode = 1
            val errorLine = e.errorLineNumber.takeIf { it > 0 } ?: 1
            val msg = e.message ?: "Evaluation error"
            onOutput(
                TerminalEntry(
                    type = TerminalStreamType.STDERR,
                    text = "Execution Error at line $errorLine: $msg"
                )
            )
            onErrorDiagnostic(
                JavaDiagnostic(
                    line = errorLine,
                    column = 1,
                    message = msg,
                    severity = DiagnosticSeverity.ERROR,
                    source = "Java Interpreter"
                )
            )
        } catch (e: InterruptedException) {
            printOut.flush()
            printErr.flush()
            exitCode = 130
            onOutput(
                TerminalEntry(
                    type = TerminalStreamType.SYSTEM,
                    text = "Program execution halted by user."
                )
            )
        } catch (e: Throwable) {
            printOut.flush()
            printErr.flush()
            exitCode = 1
            val msg = e.message ?: e.toString()
            onOutput(
                TerminalEntry(
                    type = TerminalStreamType.STDERR,
                    text = "Fatal Error: $msg"
                )
            )
        } finally {
            // Flush remaining buffer
            printOut.flush()
            printErr.flush()

            // Restore original streams
            System.setOut(originalOut)
            System.setErr(originalErr)
            System.setIn(originalIn)

            try {
                pipedOut.close()
                pipedIn.close()
            } catch (_: Exception) {}
            pipedOutputStream = null
            currentExecutionThread = null
            isExecuting.set(false)

            val duration = System.currentTimeMillis() - startTime
            val statusText = if (exitCode == 0) {
                "Process finished with exit code $exitCode (completed in ${duration}ms)"
            } else {
                "Process terminated with exit code $exitCode (${duration}ms)"
            }

            onOutput(
                TerminalEntry(
                    type = if (exitCode == 0) TerminalStreamType.SUCCESS else TerminalStreamType.ERROR,
                    text = statusText
                )
            )

            onFinished(exitCode, duration)
        }
    }

    private fun preprocessCode(code: String): String {
        val lines = code.lines()
        val processed = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("package ")) {
                // Comment out package line so BeanShell evaluates in root workspace
                processed.append("// ").append(line).append("\n")
            } else {
                processed.append(line).append("\n")
            }
        }
        return processed.toString()
    }

    private fun findMainClass(code: String): String? {
        val classPattern = Pattern.compile("(?:public\\s+)?(?:final\\s+)?class\\s+([A-Za-z0-9_]+)")
        val mainPattern = Pattern.compile("(?:public\\s+)?static\\s+void\\s+main\\s*\\(")

        val lines = code.lines()
        var currentClass: String? = null
        var mainClass: String? = null

        for (line in lines) {
            val classMatcher = classPattern.matcher(line)
            if (classMatcher.find()) {
                currentClass = classMatcher.group(1)
            }
            if (mainPattern.matcher(line).find() && currentClass != null) {
                mainClass = currentClass
                break
            }
        }

        return mainClass ?: currentClass
    }
}

internal fun extractMainMethodScript(code: String): String? {
    val mainMethodPattern = Regex(
        """(?:public\s+)?static\s+void\s+main\s*\(\s*(?:final\s+)?(?:java\.lang\.)?String\s*(?:\[\s*\]\s*([A-Za-z_$][\w$]*)|([A-Za-z_$][\w$]*)\s*\[\s*\])\s*\)\s*\{"""
    )
    val mainMatch = mainMethodPattern.find(code) ?: return null
    val mainOpeningBrace = mainMatch.range.last
    val mainClosingBrace = findMatchingBrace(code, mainOpeningBrace) ?: return null
    val parameterName = mainMatch.groupValues[1].ifEmpty { mainMatch.groupValues[2] }
    val classMatch = Regex("""\bclass\s+[A-Za-z_$][\w$]*[^{}]*\{""").find(code) ?: return null
    val classOpeningBrace = classMatch.range.last
    val classClosingBrace = findMatchingBrace(code, classOpeningBrace) ?: return null
    val classBody = code.substring(classOpeningBrace + 1, classClosingBrace)
    val mainBodyStart = mainOpeningBrace - classOpeningBrace - 1
    val mainBodyEnd = mainClosingBrace - classOpeningBrace - 1
    val helperMethodPattern = Regex(
        """(?m)^[ \t]*(?:(?:public|protected|private|static|final|synchronized|strictfp)\s+)*(?:<[^{};]+>\s*)?[\w$?.<>\[\], ]+\s+([A-Za-z_$][\w$]*)\s*\([^{};]*\)\s*(?:throws\s+[\w$., ]+)?\s*\{"""
    )
    val helperMethods = helperMethodPattern.findAll(classBody).mapNotNull { method ->
        val methodName = method.groupValues[1]
        if (methodName == "main" || method.range.first in mainBodyStart..mainBodyEnd) {
            return@mapNotNull null
        }
        val methodOpeningBrace = method.range.last
        val methodClosingBrace = findMatchingBrace(classBody, methodOpeningBrace) ?: return@mapNotNull null
        val signature = method.value.substringBeforeLast('{')
            .replace(Regex("""\b(?:public|protected|private|static|final|synchronized|strictfp)\s+"""), "")
            .replace(Regex("""\s+throws\s+[\w$., ]+$"""), "")
            .trim()
        "$signature {\n${classBody.substring(methodOpeningBrace + 1, methodClosingBrace)}\n}"
    }.toList()
    val imports = code.lineSequence()
        .map(String::trim)
        .filter { it.startsWith("import ") && it.endsWith(";") }
        .joinToString("\n")

    return buildString {
        if (imports.isNotEmpty()) append(imports).append('\n')
        helperMethods.forEach { append(it).append('\n') }
        append("void __javaProgramMain(String[] ").append(parameterName).append(") {\n")
        append(code.substring(mainOpeningBrace + 1, mainClosingBrace))
        append("\n}\n__javaProgramMain(new String[0]);")
    }
}

private fun findMatchingBrace(code: String, openingBrace: Int): Int? {
    var depth = 0
    var index = openingBrace
    var inLineComment = false
    var inBlockComment = false
    var quote: Char? = null
    var escaped = false

    while (index < code.length) {
        val current = code[index]
        val next = code.getOrNull(index + 1)
        if (inLineComment) {
            if (current == '\n') inLineComment = false
            index++
            continue
        }
        if (inBlockComment) {
            if (current == '*' && next == '/') {
                inBlockComment = false
                index += 2
            } else {
                index++
            }
            continue
        }
        if (quote != null) {
            if (escaped) escaped = false
            else if (current == '\\') escaped = true
            else if (current == quote) quote = null
            index++
            continue
        }
        if (current == '/' && next == '/') {
            inLineComment = true
            index += 2
            continue
        }
        if (current == '/' && next == '*') {
            inBlockComment = true
            index += 2
            continue
        }
        if (current == '"' || current == '\'') {
            quote = current
            index++
            continue
        }
        if (current == '{') depth++
        if (current == '}' && --depth == 0) return index
        index++
    }
    return null
}
