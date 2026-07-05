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
    fun `inbound message is ACKed to its sender and counts as unread`() = runTest {
        val repo = ChatRepository(dao, transport, identity, backgroundScope)
        transport.inbound.subscriptionCount.first { it > 0 }

        transport.inbound.emit(
            "ep-1" to BeaconEnvelope(
                type = BeaconEnvelope.TYPE_CHAT_MSG,
                msgId = "m-1",
                senderId = "bbbb-remote",
                senderName = "Them",
                roomCode = "dm:a:b",
                ts = 1,
                body = "hi",
            )
        )
        repo.messagesFor("dm:a:b").first { it.isNotEmpty() }

        val (ack, targets) = transport.sent.single()
        assertEquals(BeaconEnvelope.TYPE_ACK, ack.type)
        assertEquals("m-1", ack.body)
        assertEquals(listOf("ep-1"), targets)
        assertEquals(mapOf("dm:a:b" to 1), repo.unreadCounts.value)

        repo.markRead("dm:a:b")
        assertTrue(repo.unreadCounts.value.isEmpty())
    }

    @Test
    fun `messages for the open chat are not counted unread`() = runTest {
        val repo = ChatRepository(dao, transport, identity, backgroundScope)
        transport.inbound.subscriptionCount.first { it > 0 }
        repo.activeRoomCode.value = "dm:a:b"

        transport.inbound.emit(
            "ep-1" to BeaconEnvelope(
                type = BeaconEnvelope.TYPE_CHAT_MSG,
                msgId = "m-2",
                senderId = "bbbb-remote",
                senderName = "Them",
                roomCode = "dm:a:b",
                ts = 1,
                body = "hi",
            )
        )
        repo.messagesFor("dm:a:b").first { it.isNotEmpty() }

        assertTrue(repo.unreadCounts.value.isEmpty())
    }

    @Test
    fun `ACK marks the original message delivered`() = runTest {
        val repo = ChatRepository(dao, transport, identity, backgroundScope)
        transport.inbound.subscriptionCount.first { it > 0 }
        repo.send("dm:a:b", "hello")
        val sentId = transport.sent.single().first.msgId

        transport.inbound.emit(
            "ep-1" to BeaconEnvelope(
                type = BeaconEnvelope.TYPE_ACK,
                msgId = "ack-1",
                senderId = "bbbb-remote",
                senderName = "Them",
                roomCode = "dm:a:b",
                ts = 2,
                body = sentId,
            )
        )

        val delivered = repo.messagesFor("dm:a:b").first { list -> list.any { it.delivered } }
        assertTrue(delivered.single().delivered)
    }

    @Test
    fun `opening a chat sends a READ receipt to the dm peer`() = runTest {
        val repo = ChatRepository(dao, transport, identity, backgroundScope)
        val other = Peer("ep-1", "bbbb-remote", "Them", isConnected = true)
        transport.peers.value = mapOf("ep-1" to other)
        val dm = IdGen.dmRoomCode(identity.sessionId, other.sessionId)

        repo.markRead(dm)

        val (read, targets) = transport.sent.single()
        assertEquals(BeaconEnvelope.TYPE_READ, read.type)
        assertEquals(dm, read.roomCode)
        assertEquals(listOf("ep-1"), targets)
    }

    @Test
    fun `incoming READ marks own messages read up to its timestamp`() = runTest {
        val repo = ChatRepository(dao, transport, identity, backgroundScope)
        transport.inbound.subscriptionCount.first { it > 0 }
        repo.send("dm:a:b", "one")
        repo.send("dm:a:b", "two")

        transport.inbound.emit(
            "ep-1" to BeaconEnvelope(
                type = BeaconEnvelope.TYPE_READ,
                msgId = "r-1",
                senderId = "bbbb-remote",
                senderName = "Them",
                roomCode = "dm:a:b",
                ts = System.currentTimeMillis() + 1_000,
            )
        )

        val messages = repo.messagesFor("dm:a:b").first { list -> list.all { it.readByPeer } }
        assertEquals(2, messages.size)
        assertTrue(messages.all { it.delivered && it.readByPeer })
    }

    @Test
    fun `message arriving in the open chat is read-receipted immediately`() = runTest {
        val repo = ChatRepository(dao, transport, identity, backgroundScope)
        transport.inbound.subscriptionCount.first { it > 0 }
        repo.activeRoomCode.value = "dm:a:b"

        transport.inbound.emit(
            "ep-1" to BeaconEnvelope(
                type = BeaconEnvelope.TYPE_CHAT_MSG,
                msgId = "m-9",
                senderId = "bbbb-remote",
                senderName = "Them",
                roomCode = "dm:a:b",
                ts = 1,
                body = "hi",
            )
        )
        repo.messagesFor("dm:a:b").first { it.isNotEmpty() }

        val types = transport.sent.map { it.first.type }
        assertEquals(listOf(BeaconEnvelope.TYPE_ACK, BeaconEnvelope.TYPE_READ), types)
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
