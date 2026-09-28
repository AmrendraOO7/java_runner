package com.example.model

enum class TerminalStreamType {
    STDOUT,
    STDERR,
    STDIN,
    INFO,
    SUCCESS,
    ERROR,
    SYSTEM
}

data class TerminalEntry(
    val id: Long = System.nanoTime(),
    val type: TerminalStreamType,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
