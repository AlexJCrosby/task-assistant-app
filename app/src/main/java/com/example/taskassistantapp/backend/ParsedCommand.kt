package com.example.taskassistantapp.backend

data class ParsedCommand(
    val intent: IntentType,
    val taskText: String? = null,
    val rawText: String = ""
)