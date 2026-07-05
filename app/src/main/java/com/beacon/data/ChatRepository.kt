package com.beacon.data

import com.beacon.data.db.MessageDao
import com.beacon.data.db.MessageEntity
import com.beacon.domain.IdGen
import com.beacon.model.ChatMessage
import com.beacon.nearby.MeshTransport
import com.beacon.nearby.protocol.BeaconEnvelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * One chat pipeline for everything: a 1:1 conversation is just a room whose
 * code is derived from the two session ids ([IdGen.dmRoomCode]), so M3 group
 * rooms reuse this class unchanged.
 */
class ChatRepository(
    private val dao: MessageDao,
    private val nearby: MeshTransport,
    private val identity: Identity,
    scope: CoroutineScope,
) {

    init {
        nearby.inbound
            .onEach { (_, envelope) ->
                if (envelope.type == BeaconEnvelope.TYPE_CHAT_MSG) {
                    val roomCode = envelope.roomCode ?: return@onEach
                    val text = envelope.body ?: return@onEach
                    dao.insert(
                        MessageEntity(
                            msgId = envelope.msgId,
                            roomCode = roomCode,
                            senderId = envelope.senderId,
                            senderName = envelope.senderName,
                            text = text,
                            timestamp = envelope.ts,
                            isMine = false,
                        )
                    )
                }
            }
            .launchIn(scope)
    }

    fun messagesFor(roomCode: String): Flow<List<ChatMessage>> =
        dao.messagesFor(roomCode).map { entities ->
            entities.map {
                ChatMessage(
                    msgId = it.msgId,
                    roomCode = it.roomCode,
                    senderId = it.senderId,
                    senderName = it.senderName,
                    text = it.text,
                    timestamp = it.timestamp,
                    isMine = it.isMine,
                )
            }
        }

    suspend fun send(roomCode: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val envelope = BeaconEnvelope(
            type = BeaconEnvelope.TYPE_CHAT_MSG,
            msgId = IdGen.newMessageId(),
            senderId = identity.sessionId,
            senderName = identity.displayName,
            roomCode = roomCode,
            ts = System.currentTimeMillis(),
            body = trimmed,
        )
        // Local echo first so the UI updates instantly even with no one in range.
        dao.insert(
            MessageEntity(
                msgId = envelope.msgId,
                roomCode = roomCode,
                senderId = envelope.senderId,
                senderName = envelope.senderName,
                text = trimmed,
                timestamp = envelope.ts,
                isMine = true,
            )
        )
        nearby.send(envelope, targetsFor(roomCode))
    }

    suspend fun deleteRoom(roomCode: String) = dao.deleteRoom(roomCode)

    /**
     * DM codes embed both session ids, so route to the matching peer only.
     * Non-dm codes broadcast to the whole mesh until M3 adds ROOM_ANNOUNCE
     * membership filtering.
     */
    private fun targetsFor(roomCode: String): List<String> {
        val peers = nearby.peers.value.values
        return if (roomCode.startsWith("dm:")) {
            peers.filter { roomCode.contains(it.sessionId) }.map { it.endpointId }
        } else {
            peers.map { it.endpointId }
        }
    }
}
