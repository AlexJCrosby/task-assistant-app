package com.example.taskassistantapp.backend

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class AudioReceiverManager(
    private val context: Context,
    private val port: Int = 5001,
    private val onStatus: (String) -> Unit,
    private val onAudioReceived: (ReceivedAudio) -> Unit,
    private val onError: (String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    private val running = AtomicBoolean(false)

    fun start() {
        if (running.get()) return

        running.set(true)
        serverThread = Thread {
            try {
                serverSocket = ServerSocket(port)
                onStatus("Audio receiver listening on port $port")

                while (running.get()) {
                    val client = serverSocket?.accept() ?: break
                    handleClient(client)
                }
            } catch (e: Exception) {
                if (running.get()) {
                    onError("Receiver error: ${e.message}")
                }
            } finally {
                stopInternal()
            }
        }.apply {
            name = "AudioReceiverThread"
            start()
        }
    }

    fun stop() {
        running.set(false)
        stopInternal()
    }

    private fun stopInternal() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            try {
                onStatus("Incoming audio connection from ${client.inetAddress.hostAddress}")

                val input = client.getInputStream()
                val headerJson = readJsonLine(input)
                val header = parseHeader(headerJson)

                validateHeader(header)

                val wavBytes = readExact(input, header.audioSize)
                val wavFile = saveReceivedWav(wavBytes, header)

                onAudioReceived(
                    ReceivedAudio(
                        header = header,
                        wavFile = wavFile
                    )
                )

                sendStatus(client, ok = true, message = "Audio received and saved")
            } catch (e: Exception) {
                onError("Audio receive failed: ${e.message}")
                try {
                    sendStatus(client, ok = false, message = e.message ?: "Unknown error")
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun readJsonLine(input: InputStream, maxBytes: Int = 4096): String {
        val buffer = ByteArrayOutputStream()

        while (buffer.size() < maxBytes) {
            val value = input.read()
            if (value == -1) {
                throw IllegalStateException("Socket closed while reading JSON header")
            }

            if (value.toByte() == '\n'.code.toByte()) {
                break
            }

            buffer.write(value)
        }

        if (buffer.size() >= maxBytes) {
            throw IllegalArgumentException("JSON header exceeded maximum allowed size")
        }

        return buffer.toString(Charsets.UTF_8.name())
    }

    private fun parseHeader(jsonLine: String): AudioHeader {
        val json = JSONObject(jsonLine)

        return AudioHeader(
            type = json.getString("type"),
            format = json.getString("format"),
            sampleRate = json.getInt("sample_rate"),
            channels = json.getInt("channels"),
            sampleWidth = json.getInt("sample_width"),
            audioSize = json.getInt("audio_size"),
            deviceId = json.getString("device_id"),
            sequence = json.optInt("sequence", 0)
        )
    }

    private fun validateHeader(header: AudioHeader) {
        if (header.type != "audio_upload") {
            throw IllegalArgumentException("Unsupported type: ${header.type}")
        }

        if (header.format != "wav") {
            throw IllegalArgumentException("Unsupported format: ${header.format}")
        }

        if (header.sampleRate != 16000) {
            throw IllegalArgumentException("Expected 16000 Hz audio, got ${header.sampleRate}")
        }

        if (header.channels != 1) {
            throw IllegalArgumentException("Expected mono audio, got ${header.channels}")
        }

        if (header.sampleWidth != 2) {
            throw IllegalArgumentException("Expected 16-bit PCM audio, got ${header.sampleWidth}")
        }

        if (header.audioSize <= 44) {
            throw IllegalArgumentException("Audio payload too small to be a WAV file: ${header.audioSize}")
        }
    }

    private fun readExact(input: InputStream, numBytes: Int): ByteArray {
        val result = ByteArray(numBytes)
        var totalRead = 0

        while (totalRead < numBytes) {
            val read = input.read(result, totalRead, numBytes - totalRead)
            if (read == -1) {
                throw IllegalStateException(
                    "Socket closed before expected payload was fully received. Remaining: ${numBytes - totalRead} bytes"
                )
            }
            totalRead += read
        }

        return result
    }

    private fun saveReceivedWav(wavBytes: ByteArray, header: AudioHeader): File {
        val outputDir = File(context.filesDir, "received_audio")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val filename = "${header.deviceId}_recording_%04d.wav".format(header.sequence)
        val wavFile = File(outputDir, filename)
        wavFile.writeBytes(wavBytes)
        return wavFile
    }

    private fun sendStatus(socket: Socket, ok: Boolean, message: String) {
        val payload = JSONObject().apply {
            put("ok", ok)
            put("message", message)
        }.toString() + "\n"

        socket.getOutputStream().write(payload.toByteArray(Charsets.UTF_8))
        socket.getOutputStream().flush()
    }
}