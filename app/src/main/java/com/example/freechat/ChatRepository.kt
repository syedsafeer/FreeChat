package com.example.freechat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ConnectionState {
    DISCONNECTED,
    HOSTING, // Added HOSTING state
    CONNECTING,
    CONNECTED
}

object ChatRepository {
    val messages = mutableListOf<Message>()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    fun updateConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }
}
