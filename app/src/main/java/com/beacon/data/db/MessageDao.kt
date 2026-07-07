package com.beacon.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    /** IGNORE on conflict makes inserts idempotent on msgId — resent payloads are no-ops. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE roomCode = :roomCode ORDER BY timestamp ASC")
    fun messagesFor(roomCode: String): Flow<List<MessageEntity>>

    @Query("UPDATE messages SET delivered = 1 WHERE msgId = :msgId")
    suspend fun markDelivered(msgId: String)

    /** Read receipts cover everything own up to the reader's timestamp. */
    @Query(
        "UPDATE messages SET readByPeer = 1, delivered = 1 " +
            "WHERE roomCode = :roomCode AND isMine = 1 AND timestamp <= :upToTs"
    )
    suspend fun markReadByPeer(roomCode: String, upToTs: Long)

    @Query("DELETE FROM messages WHERE roomCode = :roomCode")
    suspend fun deleteRoom(roomCode: String)

    /** M4 expiry sweep: drop anything older than the cutoff. */
    @Query("DELETE FROM messages WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    /** Own messages the peer never ACKed — candidates for redelivery. */
    @Query("SELECT * FROM messages WHERE roomCode = :roomCode AND isMine = 1 AND delivered = 0")
    suspend fun undelivered(roomCode: String): List<MessageEntity>

    /**
     * One row per DM conversation, newest first — the Recent chats list that
     * keeps history reachable while the radar (and thus the peer list) is off.
     */
    @Query(
        """
        SELECT m.roomCode AS roomCode,
               m.text AS lastText,
               m.timestamp AS lastTs,
               m.isMine AS lastMine,
               (SELECT senderName FROM messages
                WHERE roomCode = m.roomCode AND isMine = 0
                ORDER BY timestamp DESC LIMIT 1) AS peerName
        FROM messages m
        WHERE m.roomCode LIKE 'dm:%'
          AND m.timestamp = (SELECT MAX(timestamp) FROM messages WHERE roomCode = m.roomCode)
        GROUP BY m.roomCode
        ORDER BY lastTs DESC
        """
    )
    fun recentDmChats(): Flow<List<RecentChatRow>>
}
