package com.example.compiler

class JavaSyntaxAnalyzer {

    data class AnalysisResult(
        val diagnostics: List<JavaDiagnostic>,
        val hasMainMethod: Boolean,
        val primaryClassName: String?,
        val lineCount: Int
    )

    fun analyze(code: String, expectedFileName: String? = null): AnalysisResult {
        val diagnostics = mutableListOf<JavaDiagnostic>()
        val lines = code.lines()
        val lineCount = lines.size

        // 1. Check brackets & parentheses balance
        val bracketStack = ArrayDeque<BracketInfo>()
        var inBlockComment = false
        var blockCommentStartLine = 1

        for ((lineIdx, lineText) in lines.withIndex()) {
            val lineNum = lineIdx + 1
            var i = 0
            var inString = false
            var inChar = false

            while (i < lineText.length) {
                val c = lineText[i]

                if (inBlockComment) {
                    if (c == '*' && i + 1 < lineText.length && lineText[i + 1] == '/') {
                        inBlockComment = false
                        i += 2
                        continue
                    }
                    i++
                    continue
                }

                if (inString) {
                    if (c == '\\' && i + 1 < lineText.length) {
                        i += 2
                        continue
                    }
                    if (c == '"') {
                        inString = false
                    }
                    i++
                    continue
                }

                if (inChar) {
                    if (c == '\\' && i + 1 < lineText.length) {
                        i += 2
                        continue
                    }
                    if (c == '\'') {
                        inChar = false
                    }
                    i++
                    continue
                }

                // Check comment start
                if (c == '/' && i + 1 < lineText.length) {
                    if (lineText[i + 1] == '/') {
                        // Single line comment, skip rest of line
                        break
                    } else if (lineText[i + 1] == '*') {
                        inBlockComment = true
                        blockCommentStartLine = lineNum
                        i += 2
                        continue
                    }
                }

                if (c == '"') {
                    inString = true
                    i++
                    continue
                }

                if (c == '\'') {
                    inChar = true
                    i++
                    continue
                }

                // Brackets
                when (c) {
                    '{', '(', '[' -> {
                        bracketStack.addLast(BracketInfo(c, lineNum, i + 1))
                    }
                    '}', ')', ']' -> {
                        if (bracketStack.isEmpty()) {
                            diagnostics.add(
                                JavaDiagnostic(
                                    line = lineNum,
                                    column = i + 1,
                                    message = "Unmatched closing bracket '$c'",
                                    severity = DiagnosticSeverity.ERROR,
                                    snippet = lineText.trim(),
                                    source = "Syntax Checker"
                                )
                            )
                        } else {
                            val open = bracketStack.removeLast()
                            if (!isMatching(open.char, c)) {
                                diagnostics.add(
                                    JavaDiagnostic(
                                        line = lineNum,
                                        column = i + 1,
                                        message = "Mismatched bracket: expected '${expectedCloser(open.char)}' to match '${open.char}' on line ${open.line}, but found '$c'",
                                        severity = DiagnosticSeverity.ERROR,
                                        snippet = lineText.trim(),
                                        source = "Syntax Checker"
                                    )
                                )
                            }
                        }
                    }
                }
                i++
            }

            // Check if string was left open on this line
            if (inString) {
                diagnostics.add(
                    JavaDiagnostic(
                        line = lineNum,
                        column = lineText.length,
                        message = "Unclosed string literal",
                        severity = DiagnosticSeverity.ERROR,
                        snippet = lineText.trim(),
                        source = "Syntax Checker"
                    )
                )
            }
        }

        if (inBlockComment) {
            diagnostics.add(
                JavaDiagnostic(
                    line = blockCommentStartLine,
                    column = 1,
                    message = "Unclosed block comment '/*'",
                    severity = DiagnosticSeverity.ERROR,
                    source = "Syntax Checker"
                )
            )
        }

        // Any leftover unclosed brackets
        while (!bracketStack.isEmpty()) {
            val unclosed = bracketStack.removeLast()
            diagnostics.add(
                JavaDiagnostic(
                    line = unclosed.line,
                    column = unclosed.column,
                    message = "Unclosed bracket '${unclosed.char}', expected '${expectedCloser(unclosed.char)}'",
                    severity = DiagnosticSeverity.ERROR,
                    source = "Syntax Checker"
                )
            )
        }

        // 2. Check for missing semicolons on statements
        for ((lineIdx, lineText) in lines.withIndex()) {
            val lineNum = lineIdx + 1
            val trimmed = lineText.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
                continue
            }

            // Simple heuristic for statements that must end with semicolon
            if (shouldHaveSemicolon(trimmed)) {
                if (!trimmed.endsWith(";") && !trimmed.endsWith("{") && !trimmed.endsWith("}") && !trimmed.endsWith(":") && !trimmed.endsWith(",")) {
                    // Check next line isn't a continuation (e.g. builder chain, operator)
                    val nextLine = lines.getOrNull(lineIdx + 1)?.trim().orEmpty()
                    if (!nextLine.startsWith(".") && !nextLine.startsWith("+") && !nextLine.startsWith("?") && !nextLine.startsWith(":")) {
                        diagnostics.add(
                            JavaDiagnostic(
                                line = lineNum,
                                column = lineText.length.coerceAtLeast(1),
                                message = "Missing ';' at end of statement",
                                severity = DiagnosticSeverity.WARNING,
                                snippet = trimmed,
                                source = "Linter"
                            )
                        )
                    }
                }
            }
        }

        // 3. Detect primary class and main method
        var primaryClassName: String? = null
        var hasMainMethod = false

        val classRegex = Regex("""(?:public\s+)?(?:final\s+)?class\s+([A-Za-z0-9_]+)""")
        val mainRegex = Regex("""(?:public\s+)?static\s+void\s+main\s*\(\s*String\s*\[\s*\]\s*([A-Za-z0-9_]+)?\s*\)""")

        for (line in lines) {
            val classMatch = classRegex.find(line)
            if (classMatch != null && primaryClassName == null) {
                primaryClassName = classMatch.groupValues[1]
            }
            if (mainRegex.containsMatchIn(line)) {
                hasMainMethod = true
            }
        }

        // 4. Check public class name matching expected file name
        if (expectedFileName != null && expectedFileName.endsWith(".java")) {
            val expectedClass = expectedFileName.removeSuffix(".java")
            val publicClassRegex = Regex("""public\s+(?:final\s+)?class\s+([A-Za-z0-9_]+)""")
            for ((lineIdx, line) in lines.withIndex()) {
                val match = publicClassRegex.find(line)
                if (match != null) {
                    val publicClassName = match.groupValues[1]
                    if (publicClassName != expectedClass) {
                        diagnostics.add(
                            JavaDiagnostic(
                                line = lineIdx + 1,
                                column = 1,
                                message = "Public class '$publicClassName' should be declared in a file named '$publicClassName.java'",
                                severity = DiagnosticSeverity.WARNING,
                                snippet = line.trim(),
                                source = "Compiler"
                            )
                        )
                    }
                }
            }
        }

        // Sort diagnostics by line number
        diagnostics.sortBy { it.line }

        return AnalysisResult(
            diagnostics = diagnostics,
            hasMainMethod = hasMainMethod,
            primaryClassName = primaryClassName,
            lineCount = lineCount
        )
    }

    private fun shouldHaveSemicolon(line: String): Boolean {
        if (line.startsWith("package ") || line.startsWith("import ")) return true
        if (line.startsWith("return ") || line == "return" || line == "break" || line == "continue") return true
        if (line.startsWith("System.out.") || line.startsWith("System.err.")) return true
        if (line.startsWith("int ") || line.startsWith("double ") || line.startsWith("float ") ||
            line.startsWith("boolean ") || line.startsWith("char ") || line.startsWith("long ") ||
            line.startsWith("String ") || line.startsWith("List<") || line.startsWith("Map<") ||
            line.startsWith("Set<") || line.startsWith("Scanner ")
        ) {
            return !line.contains("(") || line.contains("=") // is variable declaration, not method signature
        }
        return false
    }

    private data class BracketInfo(val char: Char, val line: Int, val column: Int)

    private fun isMatching(open: Char, close: Char): Boolean {
        return (open == '(' && close == ')') ||
                (open == '{' && close == '}') ||
                (open == '[' && close == ']')
    }

    private fun expectedCloser(open: Char): Char {
        return when (open) {
            '(' -> ')'
            '{' -> '}'
            '[' -> ']'
            else -> '?'
        }
    }
}
