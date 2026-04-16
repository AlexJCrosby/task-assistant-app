package com.example.taskassistantapp.backend

interface TaskBackend {
    fun fetchTasks(callback: (BackendResult) -> Unit)
    fun addTask(taskText: String, callback: (BackendResult) -> Unit)
    fun completeTasks(taskIds: List<Int>, callback: (BackendResult) -> Unit)
    fun deleteTasks(taskIds: List<Int>, callback: (BackendResult) -> Unit)
}