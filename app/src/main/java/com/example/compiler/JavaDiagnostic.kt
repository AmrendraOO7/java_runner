package com.example.compiler

enum class DiagnosticSeverity {
    ERROR,
    WARNING,
    INFO
}

data class JavaDiagnostic(
    val line: Int,
    val column: Int = 1,
    val message: String,
    val severity: DiagnosticSeverity = DiagnosticSeverity.ERROR,
    val snippet: String? = null,
    val source: String = "Compiler"
)
