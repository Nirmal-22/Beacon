package com.beacon.model

data class ChatMessage(
    val msgId: String,
    val roomCode: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long,
    val isMine: Boolean,
)
