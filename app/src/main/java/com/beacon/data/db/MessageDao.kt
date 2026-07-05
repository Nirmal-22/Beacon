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

    @Query("DELETE FROM messages WHERE roomCode = :roomCode")
    suspend fun deleteRoom(roomCode: String)

    /** M4 expiry sweep: drop anything older than the cutoff. */
    @Query("DELETE FROM messages WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
