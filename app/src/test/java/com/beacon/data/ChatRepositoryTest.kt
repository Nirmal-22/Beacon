package com.beacon.data

import com.beacon.data.db.MessageDao
import com.beacon.data.db.MessageEntity
import com.beacon.domain.IdGen
import com.beacon.model.Peer
import com.beacon.nearby.MeshTransport
import com.beacon.nearby.protocol.BeaconEnvelope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeDao : MessageDao {
    val rows = LinkedHashMap<String, MessageEntity>()
    private val state = MutableStateFlow<List<MessageEntity>>(emptyList())

    override suspend fun insert(message: MessageEntity) {
        if (!rows.containsKey(message.msgId)) rows[message.msgId] = message
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

private class FakeTransport : MeshTransport {
    override val peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    override val inbound = MutableSharedFlow<Pair<String, BeaconEnvelope>>(extraBufferCapacity = 16)
    val sent = mutableListOf<Pair<BeaconEnvelope, List<String>>>()

    override fun send(envelope: BeaconEnvelope, endpointIds: List<String>) {
        sent += envelope to endpointIds
    }
}

private class FakeIdentity(
    override val sessionId: String = "aaaa-local",
    override val displayName: String = "Me",
) : Identity

class ChatRepositoryTest {

    private val dao = FakeDao()
    private val transport = FakeTransport()
    private val identity = FakeIdentity()

    @Test
    fun `send writes a local echo and targets the dm peer only`() = runTest {
        val repo = ChatRepository(dao, transport, identity, backgroundScope)
        val other = Peer("ep-1", "bbbb-remote", "Them", isConnected = true)
        val bystander = Peer("ep-2", "cccc-other", "Bystander", isConnected = true)
        transport.peers.value = mapOf("ep-1" to other, "ep-2" to bystander)
        val dm = IdGen.dmRoomCode(identity.sessionId, other.sessionId)

        repo.send(dm, "  hello  ")

        val stored = repo.messagesFor(dm).first().single()
        assertEquals("hello", stored.text)
        assertTrue(stored.isMine)

        val (envelope, targets) = transport.sent.single()
        assertEquals("hello", envelope.body)
        assertEquals(dm, envelope.roomCode)
        assertEquals(listOf("ep-1"), targets)
    }

    @Test
    fun `blank messages are dropped`() = runTest {
        val repo = ChatRepository(dao, transport, identity, backgroundScope)
        repo.send("dm:a:b", "   ")
        assertTrue(transport.sent.isEmpty())
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `inbound chat messages are persisted`() = runTest {
        val repo = ChatRepository(dao, transport, identity, backgroundScope)
        transport.inbound.subscriptionCount.first { it > 0 }

        val envelope = BeaconEnvelope(
            type = BeaconEnvelope.TYPE_CHAT_MSG,
            msgId = "m-1",
            senderId = "bbbb-remote",
            senderName = "Them",
            roomCode = "dm:a:b",
            ts = 42,
            body = "hi",
        )
        transport.inbound.emit("ep-1" to envelope)

        // Background collectors only run while the test body is suspended,
        // so wait for the row to appear instead of advancing the scheduler.
        val stored = repo.messagesFor("dm:a:b").first { it.isNotEmpty() }.single()
        assertEquals("hi", stored.text)
        assertEquals(false, stored.isMine)
    }

    @Test
    fun `re-delivered payloads are idempotent on msgId`() = runTest {
        val repo = ChatRepository(dao, transport, identity, backgroundScope)
        transport.inbound.subscriptionCount.first { it > 0 }

        val envelope = BeaconEnvelope(
            type = BeaconEnvelope.TYPE_CHAT_MSG,
            msgId = "dup-1",
            senderId = "bbbb-remote",
            senderName = "Them",
            roomCode = "dm:a:b",
            ts = 42,
            body = "hi",
        )
        transport.inbound.emit("ep-1" to envelope)
        transport.inbound.emit("ep-1" to envelope)
        // Delivery is ordered, so once the sentinel lands both dups were processed.
        transport.inbound.emit("ep-1" to envelope.copy(msgId = "sentinel", body = "end"))

        val messages = repo.messagesFor("dm:a:b").first { list -> list.any { it.msgId == "sentinel" } }
        assertEquals(2, messages.size)
    }

    @Test
    fun `non-chat envelopes are ignored`() = runTest {
        val repo = ChatRepository(dao, transport, identity, backgroundScope)
        transport.inbound.subscriptionCount.first { it > 0 }

        transport.inbound.emit(
            "ep-1" to BeaconEnvelope(
                type = "SOME_FUTURE_TYPE",
                msgId = "x-1",
                senderId = "s",
                senderName = "n",
                roomCode = "dm:a:b",
                ts = 1,
                body = "ignored",
            )
        )
        transport.inbound.emit(
            "ep-1" to BeaconEnvelope(
                type = BeaconEnvelope.TYPE_CHAT_MSG,
                msgId = "sentinel",
                senderId = "s",
                senderName = "n",
                roomCode = "dm:a:b",
                ts = 2,
                body = "end",
            )
        )

        val messages = repo.messagesFor("dm:a:b").first { it.isNotEmpty() }
        assertEquals(listOf("sentinel"), messages.map { it.msgId })
    }
}
