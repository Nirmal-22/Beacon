package com.beacon.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdGenTest {

    @Test
    fun `dm room code is identical regardless of argument order`() {
        val a = "aaaa1111-0000-0000-0000-000000000000"
        val b = "bbbb2222-0000-0000-0000-000000000000"
        assertEquals(IdGen.dmRoomCode(a, b), IdGen.dmRoomCode(b, a))
        assertEquals("dm:$a:$b", IdGen.dmRoomCode(b, a))
    }

    @Test
    fun `session ids are unique per call`() {
        assertNotEquals(IdGen.newSessionId(), IdGen.newSessionId())
    }

    @Test
    fun `endpoint info round trips`() {
        val sessionId = IdGen.newSessionId()
        val info = IdGen.encodeEndpointInfo(sessionId, "Nirmal")
        val (prefix, name) = IdGen.decodeEndpointInfo(info)!!
        assertEquals(IdGen.sessionPrefix(sessionId), prefix)
        assertEquals("Nirmal", name)
    }

    @Test
    fun `endpoint info keeps names with pipes intact`() {
        val sessionId = IdGen.newSessionId()
        val info = IdGen.encodeEndpointInfo(sessionId, "a|b")
        val (_, name) = IdGen.decodeEndpointInfo(info)!!
        assertEquals("a|b", name)
    }

    @Test
    fun `endpoint info caps long display names`() {
        val info = IdGen.encodeEndpointInfo(IdGen.newSessionId(), "x".repeat(200))
        assertEquals(IdGen.SESSION_PREFIX_LENGTH + 1 + 40, info.length)
    }

    @Test
    fun `malformed endpoint info is rejected`() {
        assertNull(IdGen.decodeEndpointInfo(""))
        assertNull(IdGen.decodeEndpointInfo("no-separator"))
        assertNull(IdGen.decodeEndpointInfo("short|name"))
        assertNull(IdGen.decodeEndpointInfo("12345678|"))
    }

    @Test
    fun `exactly one side initiates when prefixes differ`() {
        val small = "00000000-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        val large = "ffffffff-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
        assertTrue(IdGen.shouldInitiateConnection(small, IdGen.sessionPrefix(large)))
        assertFalse(IdGen.shouldInitiateConnection(large, IdGen.sessionPrefix(small)))
    }

    @Test
    fun `equal prefixes fall back to both initiating`() {
        val id = "12345678-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        assertTrue(IdGen.shouldInitiateConnection(id, "12345678"))
    }
}
