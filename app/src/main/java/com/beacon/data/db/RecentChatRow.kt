package com.beacon.data.db

/** Projection for the Recent chats list. [peerName] is null for never-replied chats. */
data class RecentChatRow(
    val roomCode: String,
    val lastText: String,
    val lastTs: Long,
    val lastMine: Boolean,
    val peerName: String?,
)
