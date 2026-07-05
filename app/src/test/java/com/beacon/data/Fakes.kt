package com.beacon.data

import com.beacon.data.db.MessageDao
import com.beacon.data.db.MessageEntity
import com.beacon.model.Peer
import com.beacon.nearby.MeshTransport
import com.beacon.nearby.protocol.BeaconEnvelope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeDao : MessageDao {
    val rows = LinkedHashMap<String, MessageEntity>()
    private val state = MutableStateFlow<List<MessageEntity>>(emptyList())

    override suspend fun insert(message: MessageEntity) {
        if (!rows.containsKey(message.msgId)) rows[message.msgId] = message
        state.value = rows.values.toList()
    }

    override suspend fun markDelivered(msgId: String) {
        rows[msgId]?.let { rows[msgId] = it.copy(delivered = true) }
        state.value = rows.values.toList()
    }

    override suspend fun markReadByPeer(roomCode: String, upToTs: Long) {
        rows.replaceAll { _, m ->
            if (m.roomCode == roomCode && m.isMine && m.timestamp <= upToTs) {
                m.copy(readByPeer = true, delivered = true)
            } else m
        }
        state.value = rows.values.toList()
    }

    override fun messagesFor(roomCode: String): Flow<List<MessageEntity>> =
        state.map { list -> list.filter { it.roomCode == roomCode }.sortedBy { it.timestamp } }

    override suspend fun deleteRoom(roomCode: String) {
        rows.values.removeIf { it.roomCode == roomCode }
        state.value = rows.values.toList()
    }

    override suspend fun deleteOlderThan(cutoff: Long) {
        rows.values.removeIf { it.timestamp < cutoff }
        state.value = rows.values.toList()
    }
}

class FakeTransport : MeshTransport {
    override val peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    override val inbound = MutableSharedFlow<Pair<String, BeaconEnvelope>>(extraBufferCapacity = 16)
    val sent = mutableListOf<Pair<BeaconEnvelope, List<String>>>()

    override fun send(envelope: BeaconEnvelope, endpointIds: List<String>) {
        sent += envelope to endpointIds
    }
}

class FakeIdentity(
    override val sessionId: String = "aaaa-local",
    override val displayName: String = "Me",
) : Identity
