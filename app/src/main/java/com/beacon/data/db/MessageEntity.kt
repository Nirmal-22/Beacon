package com.beacon.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [Index("roomCode", "timestamp")],
)
data class MessageEntity(
    @PrimaryKey val msgId: String,
    val roomCode: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long,
    val isMine: Boolean,
)
