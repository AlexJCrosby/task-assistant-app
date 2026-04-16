package com.example.taskassistantapp.backend

import com.example.taskassistantapp.Task

data class BackendResult(
    val success: Boolean,
    val message: String,
    val tasks: MutableList<Task> = mutableListOf(),
    val transcript: String? = null,
    val parsedIntent: String? = null
)