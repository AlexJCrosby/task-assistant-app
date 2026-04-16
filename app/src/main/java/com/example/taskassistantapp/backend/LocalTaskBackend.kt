package com.example.taskassistantapp.backend

import android.content.Context
import com.example.taskassistantapp.Task
import org.json.JSONArray
import org.json.JSONObject

class LocalTaskBackend(
    context: Context
) : TaskBackend {

    private val prefs = context.getSharedPreferences("task_storage", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TASKS = "tasks_json"
        private const val KEY_NEXT_ID = "next_id"
    }

    override fun fetchTasks(callback: (BackendResult) -> Unit) {
        callback(
            BackendResult(
                success = true,
                message = "Tasks loaded",
                tasks = loadTasks()
            )
        )
    }

    override fun addTask(taskText: String, callback: (BackendResult) -> Unit) {
        val cleaned = cleanTaskText(taskText)

        if (cleaned.isBlank()) {
            callback(
                BackendResult(
                    success = false,
                    message = "Enter a task first",
                    tasks = loadTasks()
                )
            )
            return
        }

        val tasks = loadTasks()
        val nextId = getNextId()

        val newTask = Task(
            id = nextId,
            text = cleaned,
            completed = false,
            selected = false
        )

        tasks.add(newTask)
        saveTasks(tasks)
        setNextId(nextId + 1)

        callback(
            BackendResult(
                success = true,
                message = "Task added",
                tasks = loadTasks()
            )
        )
    }

    override fun completeTasks(taskIds: List<Int>, callback: (BackendResult) -> Unit) {
        if (taskIds.isEmpty()) {
            callback(
                BackendResult(
                    success = false,
                    message = "No tasks selected",
                    tasks = loadTasks()
                )
            )
            return
        }

        val tasks = loadTasks()
        val idSet = taskIds.toSet()

        tasks.replaceAll { task ->
            if (task.id in idSet) task.copy(completed = true, selected = false)
            else task.copy(selected = false)
        }

        saveTasks(tasks)

        callback(
            BackendResult(
                success = true,
                message = "Selected tasks completed",
                tasks = loadTasks()
            )
        )
    }

    override fun deleteTasks(taskIds: List<Int>, callback: (BackendResult) -> Unit) {
        if (taskIds.isEmpty()) {
            callback(
                BackendResult(
                    success = false,
                    message = "No tasks selected",
                    tasks = loadTasks()
                )
            )
            return
        }

        val tasks = loadTasks()
        val idSet = taskIds.toSet()

        tasks.removeAll { it.id in idSet }
        saveTasks(tasks)

        callback(
            BackendResult(
                success = true,
                message = "Selected tasks deleted",
                tasks = loadTasks()
            )
        )
    }

    override fun executeTranscript(transcript: String, callback: (BackendResult) -> Unit) {
        val command = parseIntent(transcript)

        when (command.intent) {
            IntentType.HELP -> {
                callback(
                    BackendResult(
                        success = true,
                        message = "Available commands: add task, complete task, delete task, list tasks, help",
                        tasks = loadTasks(),
                        transcript = transcript,
                        parsedIntent = command.intent.name
                    )
                )
            }

            IntentType.LIST_TASKS -> {
                callback(
                    BackendResult(
                        success = true,
                        message = "Listed tasks.",
                        tasks = loadTasks(),
                        transcript = transcript,
                        parsedIntent = command.intent.name
                    )
                )
            }

            IntentType.ADD_TASK -> {
                val cleaned = cleanTaskText(command.taskText.orEmpty())
                if (cleaned.isBlank()) {
                    callback(
                        BackendResult(
                            success = false,
                            message = "Add task failed: no task text detected.",
                            tasks = loadTasks(),
                            transcript = transcript,
                            parsedIntent = command.intent.name
                        )
                    )
                    return
                }

                val tasks = loadTasks()
                val nextId = getNextId()

                val newTask = Task(
                    id = nextId,
                    text = cleaned,
                    completed = false,
                    selected = false
                )

                tasks.add(newTask)
                saveTasks(tasks)
                setNextId(nextId + 1)

                callback(
                    BackendResult(
                        success = true,
                        message = "Added task #${newTask.id}: \"${newTask.text}\"",
                        tasks = loadTasks(),
                        transcript = transcript,
                        parsedIntent = command.intent.name
                    )
                )
            }

            IntentType.COMPLETE_TASK -> {
                val tasks = loadTasks()
                val match = findTask(tasks, command.taskText.orEmpty())

                if (match == null) {
                    callback(
                        BackendResult(
                            success = false,
                            message = "No task matched: \"${command.taskText.orEmpty()}\"",
                            tasks = tasks,
                            transcript = transcript,
                            parsedIntent = command.intent.name
                        )
                    )
                    return
                }

                val updated = tasks.map { task ->
                    if (task.id == match.id) task.copy(completed = true, selected = false)
                    else task.copy(selected = false)
                }.toMutableList()

                saveTasks(updated)

                callback(
                    BackendResult(
                        success = true,
                        message = "Marked task #${match.id} complete: \"${match.text}\"",
                        tasks = loadTasks(),
                        transcript = transcript,
                        parsedIntent = command.intent.name
                    )
                )
            }

            IntentType.DELETE_TASK -> {
                val tasks = loadTasks()
                val match = findTask(tasks, command.taskText.orEmpty())

                if (match == null) {
                    callback(
                        BackendResult(
                            success = false,
                            message = "No task matched: \"${command.taskText.orEmpty()}\"",
                            tasks = tasks,
                            transcript = transcript,
                            parsedIntent = command.intent.name
                        )
                    )
                    return
                }

                tasks.removeAll { it.id == match.id }
                saveTasks(tasks)

                callback(
                    BackendResult(
                        success = true,
                        message = "Deleted task #${match.id}: \"${match.text}\"",
                        tasks = loadTasks(),
                        transcript = transcript,
                        parsedIntent = command.intent.name
                    )
                )
            }

            IntentType.UNKNOWN -> {
                callback(
                    BackendResult(
                        success = false,
                        message = "Sorry, I did not understand that command.",
                        tasks = loadTasks(),
                        transcript = transcript,
                        parsedIntent = command.intent.name
                    )
                )
            }
        }
    }

    private fun loadTasks(): MutableList<Task> {
        val json = prefs.getString(KEY_TASKS, "[]") ?: "[]"
        val array = JSONArray(json)
        val tasks = mutableListOf<Task>()

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            tasks.add(
                Task(
                    id = item.getInt("id"),
                    text = item.getString("text"),
                    completed = item.getBoolean("completed"),
                    selected = false
                )
            )
        }

        return tasks
    }

    private fun saveTasks(tasks: MutableList<Task>) {
        val array = JSONArray()

        tasks.forEach { task ->
            val obj = JSONObject().apply {
                put("id", task.id)
                put("text", task.text)
                put("completed", task.completed)
            }
            array.put(obj)
        }

        prefs.edit().putString(KEY_TASKS, array.toString()).apply()
    }

    private fun getNextId(): Int {
        return prefs.getInt(KEY_NEXT_ID, 1)
    }

    private fun setNextId(nextId: Int) {
        prefs.edit().putInt(KEY_NEXT_ID, nextId).apply()
    }

    private fun findTask(tasks: MutableList<Task>, query: String): Task? {
        val cleaned = cleanTaskText(query)
        if (cleaned.isBlank()) return null

        val idMatch = Regex("\\b(?:task\\s*)?(\\d+)\\b").find(cleaned)
        if (idMatch != null) {
            val taskId = idMatch.groupValues[1].toIntOrNull()
            if (taskId != null) {
                tasks.firstOrNull { it.id == taskId }?.let { return it }
            }
        }

        tasks.firstOrNull { it.text.equals(cleaned, ignoreCase = true) }?.let { return it }
        tasks.firstOrNull { it.text.contains(cleaned, ignoreCase = true) }?.let { return it }

        return null
    }

    private fun cleanTaskText(text: String): String {
        return text
            .trim()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("^[\\-:,. ]+"), "")
            .replace(Regex("[\\-:,. ]+$"), "")
    }

    private fun normaliseText(text: String): String {
        return text
            .lowercase()
            .trim()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
    }

    private fun stripPrefix(text: String, prefixes: List<String>): String {
        for (prefix in prefixes.sortedByDescending { it.length }) {
            if (text.startsWith(prefix)) {
                return text.removePrefix(prefix).trim()
            }
        }
        return text.trim()
    }

    private fun parseIntent(transcript: String): ParsedCommand {
        val rawText = transcript.trim()
        val text = normaliseText(transcript)

        if (text.isBlank()) {
            return ParsedCommand(
                intent = IntentType.UNKNOWN,
                rawText = rawText
            )
        }

        val helpPhrases = listOf(
            "help",
            "ask for help",
            "what can i say",
            "what can you do",
            "show help"
        )

        val listPhrases = listOf(
            "list tasks",
            "show tasks",
            "read tasks",
            "what are my tasks",
            "what tasks do i have",
            "show my tasks",
            "list my tasks"
        )

        val addPrefixes = listOf(
            "add task",
            "create task",
            "new task",
            "add"
        )

        val completePrefixes = listOf(
            "task complete",
            "complete task",
            "mark task complete",
            "mark complete",
            "finish task",
            "complete",
            "done"
        )

        val deletePrefixes = listOf(
            "delete task",
            "remove task",
            "delete",
            "remove"
        )

        if (helpPhrases.any { text == it || text.startsWith(it) }) {
            return ParsedCommand(
                intent = IntentType.HELP,
                rawText = rawText
            )
        }

        if (listPhrases.any { text == it || text.startsWith(it) }) {
            return ParsedCommand(
                intent = IntentType.LIST_TASKS,
                rawText = rawText
            )
        }

        if (addPrefixes.any { text.startsWith(it) }) {
            return ParsedCommand(
                intent = IntentType.ADD_TASK,
                taskText = stripPrefix(text, addPrefixes),
                rawText = rawText
            )
        }

        if (completePrefixes.any { text.startsWith(it) }) {
            return ParsedCommand(
                intent = IntentType.COMPLETE_TASK,
                taskText = stripPrefix(text, completePrefixes),
                rawText = rawText
            )
        }

        if (deletePrefixes.any { text.startsWith(it) }) {
            return ParsedCommand(
                intent = IntentType.DELETE_TASK,
                taskText = stripPrefix(text, deletePrefixes),
                rawText = rawText
            )
        }

        return ParsedCommand(
            intent = IntentType.UNKNOWN,
            rawText = rawText
        )
    }
}