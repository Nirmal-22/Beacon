package com.beacon.data

import com.beacon.data.db.MessageEntity
import com.beacon.model.RoomInfo
import com.beacon.nearby.protocol.BeaconEnvelope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EphemeralityManagerTest {

    private val dao = FakeDao()
    private val transport = FakeTransport()
    private val identity = FakeIdentity()
    private val rooms = MutableStateFlow<Map<String, RoomInfo>>(emptyMap())

    private fun room(code: String, joined: Boolean = true) =
        RoomInfo(code, code, mapOf("s" to "S"), joined)

    private fun oldMessage(msgId: String, ts: Long) = MessageEntity(
        msgId = msgId, roomCode = "dm:a:b", senderId = "s", senderName = "S",
        text = "x", timestamp = ts, isMine = false,
    )

    @Test
    fun `start sweep deletes messages past retention`() = runTest {
        val repo = ChatRepository(dao, transport, identity, backgroundScope)
        val now = 1_000_000_000L
        dao.insert(oldMessage("old", now - 25L * 60 * 60 * 1000))
        dao.insert(oldMessage("fresh", now - 1L * 60 * 60 * 1000))

        EphemeralityManager(repo, rooms, backgroundScope, now = { now })

        val remaining = repo.messagesFor("dm:a:b").first { list -> list.none { it.msgId == "old" } }
        assertEquals(listOf("fresh"), remaining.map { it.msgId })
    }

    @Test
    fun `room death purges its messages and unread state`() = runTest {
        val repo = ChatRepository(dao, transport, identity, backgroundScope)
        transport.inbound.subscriptionCount.first { it > 0 }
        EphemeralityManager(repo, rooms, backgroundScope, now = { 0 })

        rooms.value = mapOf("cafe" to room("cafe"))
        transport.inbound.emit(
            "ep-1" to BeaconEnvelope(
                type = BeaconEnvelope.TYPE_CHAT_MSG,
                msgId = "m-1", senderId = "s1", senderName = "Them",
                roomCode = "cafe", ts = 1, body = "hey",
            )
        )
        repo.messagesFor("cafe").first { it.isNotEmpty() }
        assertEquals(1, repo.unreadCounts.value["cafe"])

        rooms.value = emptyMap() // last member gone -> room died

        repo.messagesFor("cafe").first { it.isEmpty() }
        assertTrue(repo.unreadCounts.value.isEmpty())
    }

    @Test
    fun `dm chats are not purged by room changes`() = runTest {
        val repo = ChatRepository(dao, transport, identity, backgroundScope)
        EphemeralityManager(repo, rooms, backgroundScope, now = { 0 })
        dao.insert(oldMessage("keep", 0))

        rooms.value = mapOf("cafe" to room("cafe"))
        rooms.value = emptyMap()

        assertEquals(1, repo.messagesFor("dm:a:b").first { it.isNotEmpty() }.size)
    }
}
