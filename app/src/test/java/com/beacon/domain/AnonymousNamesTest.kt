package com.beacon.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnonymousNamesTest {

    @Test
    fun `handle is stable for a session`() {
        val id = "12345678-aaaa-bbbb-cccc-dddddddddddd"
        assertEquals(AnonymousNames.forSession(id), AnonymousNames.forSession(id))
    }

    @Test
    fun `handle has the anonymous prefix`() {
        assertTrue(AnonymousNames.forSession("any-session").startsWith("Anon "))
    }

    @Test
    fun `negative hash codes do not crash`() {
        // String hashCodes are frequently negative; floorMod must handle them.
        val handle = AnonymousNames.forSession("polygenelubricants")
        assertTrue(handle.startsWith("Anon "))
    }
}
