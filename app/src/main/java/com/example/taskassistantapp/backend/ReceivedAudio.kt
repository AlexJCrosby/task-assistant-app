package com.example.taskassistantapp.backend

import java.io.File

data class ReceivedAudio(
    val header: AudioHeader,
    val wavFile: File
)