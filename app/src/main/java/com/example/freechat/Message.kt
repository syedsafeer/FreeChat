package com.example.freechat

import java.io.File

enum class MessageType {
    TEXT, VOICE
}

enum class Sender {
    ME, OTHER
}

data class Message(
    val type: MessageType,
    val sender: Sender,
    val content: String, // Text message or File path
    val timestamp: Long = System.currentTimeMillis()
)

