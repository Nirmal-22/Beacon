package com.beacon.data

import com.beacon.data.db.MessageDao
import com.beacon.data.db.MessageEntity
import com.beacon.domain.IdGen
import com.beacon.model.ChatMessage
import com.beacon.nearby.MeshTransport
import com.beacon.nearby.protocol.BeaconEnvelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

/** UI-facing alerts for messages that arrive outside the open chat. */
interface MessageAlerts {
    fun onNewMessage(roomCode: String, senderName: String, text: String, unreadCount: Int)
    fun onRead(roomCode: String)
}

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
    private val alerts: MessageAlerts? = null,
    /** Resolves group-room recipients (RoomRegistry-backed in production). */
    private val groupTargets: (roomCode: String) -> List<String> = { emptyList() },
    private val isBlocked: (sessionId: String) -> Boolean = { false },
) {

    /** Room the user is currently looking at — its messages never alert. */
    val activeRoomCode = MutableStateFlow<String?>(null)

    private val _unread = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadCounts: StateFlow<Map<String, Int>> = _unread.asStateFlow()

    init {
        nearby.inbound
            .onEach { (endpointId, envelope) ->
                if (isBlocked(envelope.senderId)) return@onEach
                when (envelope.type) {
                    BeaconEnvelope.TYPE_CHAT_MSG -> onChatMessage(endpointId, envelope)
                    BeaconEnvelope.TYPE_ACK -> envelope.body?.let { dao.markDelivered(it) }
                    BeaconEnvelope.TYPE_READ -> envelope.roomCode?.let {
                        // Peer clock skew can under-mark; the next receipt catches up.
                        dao.markReadByPeer(it, envelope.ts)
                    }
                }
            }
            .launchIn(scope)

        // Redelivery: a DM typed while the other person was out of range sat
        // undelivered. When their session reappears, send it again — the
        // receiver's msgId idempotency makes duplicates harmless.
        var knownSessions = emptySet<String>()
        nearby.peers
            .onEach { peers ->
                val sessions = peers.values.associateBy { it.sessionId }
                val returned = sessions.keys - knownSessions
                knownSessions = sessions.keys
                returned.forEach { sessionId ->
                    val peer = sessions.getValue(sessionId)
                    val dm = IdGen.dmRoomCode(identity.sessionId, sessionId)
                    dao.undelivered(dm).forEach { msg ->
                        nearby.send(
                            BeaconEnvelope(
                                type = BeaconEnvelope.TYPE_CHAT_MSG,
                                msgId = msg.msgId,
                                senderId = msg.senderId,
                                senderName = msg.senderName,
                                roomCode = msg.roomCode,
                                ts = msg.timestamp,
                                body = msg.text,
                            ),
                            listOf(peer.endpointId),
                        )
                    }
                }
            }
            .launchIn(scope)
    }

    private suspend fun onChatMessage(endpointId: String, envelope: BeaconEnvelope) {
        val roomCode = envelope.roomCode ?: return
        val text = envelope.body ?: return
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
        // Delivery receipt back to the sender.
        nearby.send(
            envelope(BeaconEnvelope.TYPE_ACK, roomCode, body = envelope.msgId),
            listOf(endpointId),
        )
        if (activeRoomCode.value != roomCode) {
            val count = _unread.value.getOrDefault(roomCode, 0) + 1
            _unread.update { it + (roomCode to count) }
            alerts?.onNewMessage(roomCode, envelope.senderName, text, count)
        } else {
            // Chat is on screen — the sender gets a read receipt right away.
            nearby.send(envelope(BeaconEnvelope.TYPE_READ, roomCode), listOf(endpointId))
        }
    }

    fun markRead(roomCode: String) {
        _unread.update { it - roomCode }
        alerts?.onRead(roomCode)
        // Tell the room everything up to now has been seen.
        nearby.send(envelope(BeaconEnvelope.TYPE_READ, roomCode), targetsFor(roomCode))
    }

    /** DM conversations for the Recent chats list, newest first. */
    fun recentChats() = dao.recentDmChats()

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
                    delivered = it.delivered,
                    readByPeer = it.readByPeer,
                )
            }
        }

    suspend fun send(roomCode: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val env = envelope(BeaconEnvelope.TYPE_CHAT_MSG, roomCode, body = trimmed)
        // Local echo first so the UI updates instantly even with no one in range.
        dao.insert(
            MessageEntity(
                msgId = env.msgId,
                roomCode = roomCode,
                senderId = env.senderId,
                senderName = env.senderName,
                text = trimmed,
                timestamp = env.ts,
                isMine = true,
            )
        )
        nearby.send(env, targetsFor(roomCode))
    }

    /** Fire-and-forget "I'm typing" signal; receivers time it out themselves. */
    fun sendTyping(roomCode: String) {
        nearby.send(envelope(BeaconEnvelope.TYPE_TYPING, roomCode), targetsFor(roomCode))
    }

    suspend fun deleteRoom(roomCode: String) = dao.deleteRoom(roomCode)

    suspend fun sweepOlderThan(cutoff: Long) = dao.deleteOlderThan(cutoff)

    private fun envelope(type: String, roomCode: String, body: String? = null) = BeaconEnvelope(
        type = type,
        msgId = IdGen.newMessageId(),
        senderId = identity.sessionId,
        senderName = identity.effectiveName,
        roomCode = roomCode,
        ts = System.currentTimeMillis(),
        body = body,
    )

    /**
     * DM codes embed both session ids, so route to the matching peer;
     * group rooms route to whoever currently announces membership.
     */
    private fun targetsFor(roomCode: String): List<String> =
        if (roomCode.startsWith("dm:")) {
            nearby.peers.value.values
                .filter { roomCode.contains(it.sessionId) }
                .map { it.endpointId }
        } else {
            groupTargets(roomCode)
        }
}
