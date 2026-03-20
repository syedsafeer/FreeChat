package com.example.freechat

import androidx.lifecycle.ViewModel
import java.io.File

class ChatViewModel : ViewModel() {
    val messages = mutableListOf<Message>()

    fun addMessage(message: Message) {
        messages.add(message)
    }

    fun clearMessages() {
        // Optional: If we want to support clearing
        messages.clear()
    }
}

