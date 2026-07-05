package com.beacon.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beacon.AppContainer
import com.beacon.model.ChatMessage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val container: AppContainer,
    private val roomCode: String,
) : ViewModel() {

    val messages: StateFlow<List<ChatMessage>> =
        container.chatRepository.messagesFor(roomCode)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** For DMs: is the other person still connected? Drives the offline banner. */
    val peerOnline: StateFlow<Boolean> = container.nearbyManager.peers
        .map { peers ->
            !roomCode.startsWith("dm:") ||
                peers.values.any { roomCode.contains(it.sessionId) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun send(text: String) {
        viewModelScope.launch { container.chatRepository.send(roomCode, text) }
    }
}
