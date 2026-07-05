package com.beacon.domain

import com.beacon.data.Identity
import com.beacon.nearby.protocol.RoomsAnnouncePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class TestIdentity(
    override val sessionId: String = "me-session",
    override val displayName: String = "Me",
) : Identity

class RoomRegistryTest {

    private val registry = RoomRegistry(TestIdentity())

    private fun announceBody(vararg rooms: Pair<String, String>) =
        RoomsAnnouncePayload(rooms.map { RoomsAnnouncePayload.RoomRef(it.first, it.second) }).encode()

    @Test
    fun `codes are slugs so name variants meet in one room`() {
        assertEquals("library-silent-floor", RoomRegistry.codeFor("Library — Silent Floor"))
        assertEquals("library-silent-floor", RoomRegistry.codeFor("  library SILENT floor "))
        assertEquals("train-12628-coach-b2", RoomRegistry.codeFor("Train 12628 / Coach B2"))
    }

    @Test
    fun `joining creates the room with me as its only member`() {
        assertTrue(registry.join("Starbucks Indiranagar"))
        val room = registry.rooms.value.getValue("starbucks-indiranagar")
        assertTrue(room.isJoined)
        assertEquals(mapOf("me-session" to "Me"), room.members)
        assertEquals("Starbucks Indiranagar", room.name)
    }

    @Test
    fun `joining twice is a no-op`() {
        assertTrue(registry.join("cafe"))
        assertFalse(registry.join("cafe"))
    }

    @Test
    fun `peer announce adds their rooms and members`() {
        registry.join("cafe")
        registry.onAnnounce("ep-1", "them-session", "Them", announceBody("cafe" to "cafe"))

        val room = registry.rooms.value.getValue("cafe")
        assertEquals(2, room.memberCount)
        assertEquals("Them", room.members["them-session"])
    }

    @Test
    fun `announce snapshots replace older ones instead of accumulating`() {
        registry.onAnnounce("ep-1", "s1", "Them", announceBody("cafe" to "cafe", "gym" to "gym"))
        registry.onAnnounce("ep-1", "s1", "Them", announceBody("gym" to "gym"))

        assertNull(registry.rooms.value["cafe"]) // left it; nobody else there -> dead
        assertTrue(registry.rooms.value.containsKey("gym"))
    }

    @Test
    fun `disconnect strips the peer everywhere and empty rooms die`() {
        registry.join("cafe")
        registry.onAnnounce("ep-1", "s1", "Them", announceBody("cafe" to "cafe", "gym" to "gym"))

        registry.onDisconnected("ep-1")

        val rooms = registry.rooms.value
        assertEquals(setOf("cafe"), rooms.keys) // gym had only them -> dead
        assertEquals(1, rooms.getValue("cafe").memberCount)
    }

    @Test
    fun `room survives while I am in it even if all peers leave`() {
        registry.join("cafe")
        registry.onAnnounce("ep-1", "s1", "Them", announceBody("cafe" to "cafe"))
        registry.onDisconnected("ep-1")
        assertTrue(registry.rooms.value.getValue("cafe").isJoined)
    }

    @Test
    fun `leaving my last room while nobody announces it kills it`() {
        registry.join("cafe")
        assertTrue(registry.leave("cafe"))
        assertTrue(registry.rooms.value.isEmpty())
    }

    @Test
    fun `nearby room I have not joined is visible with its announced name`() {
        registry.onAnnounce("ep-1", "s1", "Them", announceBody("gym" to "Gym Buddies"))
        val room = registry.rooms.value.getValue("gym")
        assertFalse(room.isJoined)
        assertEquals("Gym Buddies", room.name)
    }

    @Test
    fun `targets are exactly the endpoints announcing the room`() {
        registry.onAnnounce("ep-1", "s1", "A", announceBody("cafe" to "cafe"))
        registry.onAnnounce("ep-2", "s2", "B", announceBody("gym" to "gym"))
        registry.onAnnounce("ep-3", "s3", "C", announceBody("cafe" to "cafe"))

        assertEquals(setOf("ep-1", "ep-3"), registry.targetsFor("cafe").toSet())
        assertEquals(listOf("ep-2"), registry.targetsFor("gym"))
        assertTrue(registry.targetsFor("nowhere").isEmpty())
    }

    @Test
    fun `announce body round-trips through the payload codec`() {
        registry.join("Cafe One")
        registry.onAnnounce("ep-9", "s9", "X", registry.announceBody())
        // Feeding my own announce back doubles nothing: same code, new member id only.
        assertEquals(2, registry.rooms.value.getValue("cafe-one").memberCount)
    }

    @Test
    fun `malformed announce bodies are ignored`() {
        registry.onAnnounce("ep-1", "s1", "Them", "not json")
        registry.onAnnounce("ep-1", "s1", "Them", null)
        assertTrue(registry.rooms.value.isEmpty())
    }
}
