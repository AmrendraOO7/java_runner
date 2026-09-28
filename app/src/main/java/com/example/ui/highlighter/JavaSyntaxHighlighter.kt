package com.example.ui.highlighter

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

object JavaSyntaxHighlighter {

    private val KEYWORD_COLOR = Color(0xFF569CD6) // VS Code Blue
    private val TYPE_COLOR = Color(0xFF4EC9B0)    // VS Code Teal
    private val STRING_COLOR = Color(0xFFCE9178)  // Warm Peach
    private val NUMBER_COLOR = Color(0xFFB5CEA8)  // Sage Green
    private val COMMENT_COLOR = Color(0xFF6A9955) // Olive Green
    private val ANNOTATION_COLOR = Color(0xFFDCDCAA) // Soft Yellow
    private val PUNCTUATION_COLOR = Color(0xFFD4D4D4) // Bright Text

    private val KEYWORDS = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
        "class", "const", "continue", "default", "do", "double", "else", "enum",
        "extends", "final", "finally", "float", "for", "goto", "if", "implements",
        "import", "instanceof", "int", "interface", "long", "native", "new",
        "package", "private", "protected", "public", "return", "short", "static",
        "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
        "transient", "try", "void", "volatile", "while", "true", "false", "null"
    )

    private val COMMON_TYPES = setOf(
        "String", "Scanner", "System", "Math", "Integer", "Double", "Float", "Long",
        "Boolean", "Character", "Byte", "Short", "Object", "List", "ArrayList",
        "LinkedList", "Map", "HashMap", "TreeMap", "Set", "HashSet", "TreeSet",
        "Queue", "Deque", "Stack", "Arrays", "Collections", "Stream", "Optional",
        "File", "Path", "StringBuilder", "StringBuffer", "Exception", "Throwable",
        "RuntimeException", "Thread", "Runnable", "PrintStream", "InputStream",
        "OutputStream", "Reader", "Writer", "BufferedReader", "PrintWriter"
    )

    fun highlight(text: String): AnnotatedString {
        return buildAnnotatedString {
            append(text)
            val len = text.length
            var i = 0

            while (i < len) {
                val c = text[i]

                // 1. Single-line comment //
                if (c == '/' && i + 1 < len && text[i + 1] == '/') {
                    val start = i
                    var end = text.indexOf('\n', start)
                    if (end == -1) end = len
                    addStyle(
                        SpanStyle(color = COMMENT_COLOR, fontStyle = FontStyle.Italic),
                        start,
                        end
                    )
                    i = end
                    continue
                }

                // 2. Multi-line comment /* ... */
                if (c == '/' && i + 1 < len && text[i + 1] == '*') {
                    val start = i
                    var end = text.indexOf("*/", start + 2)
                    end = if (end == -1) len else end + 2
                    addStyle(
                        SpanStyle(color = COMMENT_COLOR, fontStyle = FontStyle.Italic),
                        start,
                        end
                    )
                    i = end
                    continue
                }

                // 3. String literal "..."
                if (c == '"') {
                    val start = i
                    i++
                    while (i < len) {
                        if (text[i] == '\\' && i + 1 < len) {
                            i += 2
                            continue
                        }
                        if (text[i] == '"' || text[i] == '\n') {
                            if (text[i] == '"') i++
                            break
                        }
                        i++
                    }
                    addStyle(SpanStyle(color = STRING_COLOR), start, i)
                    continue
                }

                // 4. Character literal '.'
                if (c == '\'') {
                    val start = i
                    i++
                    while (i < len) {
                        if (text[i] == '\\' && i + 1 < len) {
                            i += 2
                            continue
                        }
                        if (text[i] == '\'' || text[i] == '\n') {
                            if (text[i] == '\'') i++
                            break
                        }
                        i++
                    }
                    addStyle(SpanStyle(color = STRING_COLOR), start, i)
                    continue
                }

                // 5. Annotation @Name
                if (c == '@') {
                    val start = i
                    i++
                    while (i < len && (text[i].isLetterOrDigit() || text[i] == '_')) {
                        i++
                    }
                    addStyle(SpanStyle(color = ANNOTATION_COLOR), start, i)
                    continue
                }

                // 6. Number literal
                if (c.isDigit()) {
                    val start = i
                    i++
                    while (i < len && (text[i].isLetterOrDigit() || text[i] == '.' || text[i] == 'x' || text[i] == 'X')) {
                        i++
                    }
                    addStyle(SpanStyle(color = NUMBER_COLOR), start, i)
                    continue
                }

                // 7. Identifier / Keyword / Type
                if (c.isLetter() || c == '_') {
                    val start = i
                    while (i < len && (text[i].isLetterOrDigit() || text[i] == '_')) {
                        i++
                    }
                    val word = text.substring(start, i)
                    if (KEYWORDS.contains(word)) {
                        addStyle(
                            SpanStyle(color = KEYWORD_COLOR, fontWeight = FontWeight.Bold),
                            start,
                            i
                        )
                    } else if (COMMON_TYPES.contains(word)) {
                        addStyle(
                            SpanStyle(color = TYPE_COLOR, fontWeight = FontWeight.Medium),
                            start,
                            i
                        )
                    }
                    continue
                }

                // 8. Punctuation / Operators
                if ("{}()[]:;,.+-*/%&|^!=<>?".contains(c)) {
                    addStyle(SpanStyle(color = PUNCTUATION_COLOR), i, i + 1)
                }

                i++
            }
        }
    }

    val visualTransformation = VisualTransformation { text ->
        TransformedText(highlight(text.text), OffsetMapping.Identity)
    }
}
