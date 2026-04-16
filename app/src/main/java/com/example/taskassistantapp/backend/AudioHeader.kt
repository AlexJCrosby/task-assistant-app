package com.example.taskassistantapp.backend

data class AudioHeader(
    val type: String,
    val format: String,
    val sampleRate: Int,
    val channels: Int,
    val sampleWidth: Int,
    val audioSize: Int,
    val deviceId: String,
    val sequence: Int = 0
)