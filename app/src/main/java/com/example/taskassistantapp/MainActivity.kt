package com.example.taskassistantapp

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.view.inputmethod.EditorInfo
import com.example.taskassistantapp.backend.RemoteHttpTaskBackend
import com.example.taskassistantapp.backend.TaskBackend

data class Task(
    val id: Int,
    val text: String,
    var completed: Boolean = false,
    var selected: Boolean = false
)

class TaskAdapter(
    private val context: MainActivity,
    private val tasks: MutableList<Task>
) : BaseAdapter() {

    override fun getCount(): Int = tasks.size

    override fun getItem(position: Int): Any = tasks[position]

    override fun getItemId(position: Int): Long = tasks[position].id.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.task_item, parent, false)

        val container = view.findViewById<View>(R.id.taskItemContainer)
        val taskText = view.findViewById<TextView>(R.id.taskText)
        val taskCheckbox = view.findViewById<CheckBox>(R.id.taskCheckbox)

        val task = tasks[position]

        taskText.text = task.text
        taskCheckbox.isChecked = task.completed

        if (task.selected) {
            container.setBackgroundColor(Color.parseColor("#DCD2F4"))
        } else {
            container.setBackgroundColor(Color.TRANSPARENT)
        }

        if (task.completed) {
            taskText.alpha = 0.6f
        } else {
            taskText.alpha = 1.0f
        }

        return view
    }
}

class MainActivity : AppCompatActivity() {

    private val backend: TaskBackend = RemoteHttpTaskBackend()
    private lateinit var taskList: MutableList<Task>
    private lateinit var adapter: TaskAdapter

    private lateinit var listView: ListView
    private lateinit var input: EditText
    private lateinit var addButton: Button
    private lateinit var completeButton: Button
    private lateinit var deleteButton: Button
    private lateinit var statusText: TextView

    private var isAddingTask = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listView = findViewById(R.id.taskListView)
        input = findViewById(R.id.taskInput)
        addButton = findViewById(R.id.addTaskButton)
        completeButton = findViewById(R.id.completeTasksButton)
        deleteButton = findViewById(R.id.deleteTasksButton)
        statusText = findViewById(R.id.statusText)

        taskList = mutableListOf()
        adapter = TaskAdapter(this, taskList)
        listView.adapter = adapter

        addButton.setOnClickListener {
            addTaskViaApi()
        }

        completeButton.setOnClickListener {
            completeSelectedTasksViaApi()
        }

        deleteButton.setOnClickListener {
            deleteSelectedTasksViaApi()
        }

        input.setOnEditorActionListener { _, actionId, event ->
            val isDoneAction = actionId == EditorInfo.IME_ACTION_DONE
            val isEnterKey = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN

            if (isDoneAction || isEnterKey) {
                addTaskViaApi()
                true
            } else {
                false
            }
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            taskList[position].selected = !taskList[position].selected
            adapter.notifyDataSetChanged()
            updateStatus()
        }

        fetchTasks()
    }

    private fun fetchTasks() {
        setStatus("Loading tasks...")

        backend.fetchTasks { result ->
            runOnUiThread {
                if (result.success) {
                    replaceTasks(result.tasks)
                    Toast.makeText(
                        this@MainActivity,
                        result.message,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    setStatus("Backend unavailable")
                    Toast.makeText(
                        this@MainActivity,
                        result.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun addTaskViaApi() {
        val taskText = input.text.toString().trim()

        if (taskText.isEmpty()) {
            Toast.makeText(this, "Enter a task first", Toast.LENGTH_SHORT).show()
            return
        }

        if (isAddingTask) {
            return
        }

        isAddingTask = true
        addButton.isEnabled = false
        setStatus("Adding task...")

        backend.addTask(taskText) { result ->
            runOnUiThread {
                isAddingTask = false
                addButton.isEnabled = true

                if (result.success) {
                    input.text.clear()
                    replaceTasks(result.tasks)
                } else {
                    setStatus("Add failed")
                    Toast.makeText(
                        this@MainActivity,
                        result.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun completeSelectedTasksViaApi() {
        val selectedIds = taskList.filter { it.selected }.map { it.id }

        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "No tasks selected", Toast.LENGTH_SHORT).show()
            return
        }

        setStatus("Completing tasks...")

        backend.completeTasks(selectedIds) { result ->
            runOnUiThread {
                if (result.success) {
                    replaceTasks(result.tasks)
                    Toast.makeText(
                        this@MainActivity,
                        result.message,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    setStatus("Complete failed")
                    Toast.makeText(
                        this@MainActivity,
                        result.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun deleteSelectedTasksViaApi() {
        val selectedIds = taskList.filter { it.selected }.map { it.id }

        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "No tasks selected", Toast.LENGTH_SHORT).show()
            return
        }

        setStatus("Deleting tasks...")

        backend.deleteTasks(selectedIds) { result ->
            runOnUiThread {
                if (result.success) {
                    replaceTasks(result.tasks)
                    Toast.makeText(
                        this@MainActivity,
                        result.message,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    setStatus("Delete failed")
                    Toast.makeText(
                        this@MainActivity,
                        result.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun replaceTasks(newTasks: MutableList<Task>) {
        taskList.clear()
        taskList.addAll(newTasks)
        adapter.notifyDataSetChanged()
        updateStatus()
    }

    private fun updateStatus() {
        val selectedCount = taskList.count { it.selected }
        statusText.text = "Status: $selectedCount selected"
    }

    private fun setStatus(message: String) {
        statusText.text = message
    }
}