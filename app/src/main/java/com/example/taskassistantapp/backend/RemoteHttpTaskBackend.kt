package com.example.taskassistantapp.backend

import com.example.taskassistantapp.Task
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class RemoteHttpTaskBackend(
    private val baseUrl: String = "http://10.0.2.2:8000"
) : TaskBackend {

    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override fun fetchTasks(callback: (BackendResult) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/tasks")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(
                    BackendResult(
                        success = false,
                        message = "Failed to fetch tasks: ${e.message}"
                    )
                )
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        callback(
                            BackendResult(
                                success = false,
                                message = "Server error: ${response.code}"
                            )
                        )
                        return
                    }

                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val tasksJson = json.getJSONArray("tasks")
                    val parsedTasks = parseTasks(tasksJson)

                    callback(
                        BackendResult(
                            success = true,
                            message = "Tasks loaded",
                            tasks = parsedTasks
                        )
                    )
                }
            }
        })
    }

    override fun addTask(taskText: String, callback: (BackendResult) -> Unit) {
        val payload = JSONObject().apply {
            put("text", taskText)
        }

        val request = Request.Builder()
            .url("$baseUrl/tasks")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(
                    BackendResult(
                        success = false,
                        message = "Failed to add task: ${e.message}"
                    )
                )
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        callback(
                            BackendResult(
                                success = false,
                                message = "Server error: ${response.code}"
                            )
                        )
                        return
                    }

                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val tasksJson = json.getJSONArray("tasks")
                    val parsedTasks = parseTasks(tasksJson)

                    callback(
                        BackendResult(
                            success = true,
                            message = "Task added",
                            tasks = parsedTasks
                        )
                    )
                }
            }
        })
    }

    override fun completeTasks(taskIds: List<Int>, callback: (BackendResult) -> Unit) {
        val payload = JSONObject().apply {
            put("ids", JSONArray(taskIds))
        }

        val request = Request.Builder()
            .url("$baseUrl/tasks/complete")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(
                    BackendResult(
                        success = false,
                        message = "Failed to complete tasks: ${e.message}"
                    )
                )
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        callback(
                            BackendResult(
                                success = false,
                                message = "Server error: ${response.code}"
                            )
                        )
                        return
                    }

                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val tasksJson = json.getJSONArray("tasks")
                    val parsedTasks = parseTasks(tasksJson)

                    callback(
                        BackendResult(
                            success = true,
                            message = "Selected tasks completed",
                            tasks = parsedTasks
                        )
                    )
                }
            }
        })
    }

    override fun deleteTasks(taskIds: List<Int>, callback: (BackendResult) -> Unit) {
        val payload = JSONObject().apply {
            put("ids", JSONArray(taskIds))
        }

        val request = Request.Builder()
            .url("$baseUrl/tasks/delete")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(
                    BackendResult(
                        success = false,
                        message = "Failed to delete tasks: ${e.message}"
                    )
                )
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        callback(
                            BackendResult(
                                success = false,
                                message = "Server error: ${response.code}"
                            )
                        )
                        return
                    }

                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val tasksJson = json.getJSONArray("tasks")
                    val parsedTasks = parseTasks(tasksJson)

                    callback(
                        BackendResult(
                            success = true,
                            message = "Selected tasks deleted",
                            tasks = parsedTasks
                        )
                    )
                }
            }
        })
    }

    private fun parseTasks(tasksJson: JSONArray): MutableList<Task> {
        val parsed = mutableListOf<Task>()

        for (i in 0 until tasksJson.length()) {
            val item = tasksJson.getJSONObject(i)
            parsed.add(
                Task(
                    id = item.getInt("id"),
                    text = item.getString("text"),
                    completed = item.getBoolean("completed"),
                    selected = false
                )
            )
        }

        return parsed
    }

    override fun executeTranscript(transcript: String, callback: (BackendResult) -> Unit) {
        callback(
            BackendResult(
                success = false,
                message = "Transcript execution is not supported by the remote backend.",
                transcript = transcript,
                parsedIntent = null
            )
        )
    }
}

