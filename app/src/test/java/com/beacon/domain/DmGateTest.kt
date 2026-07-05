package com.beacon.domain

import com.beacon.domain.DmGate.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DmGateTest {

    private val gate = DmGate()

    @Test
    fun `everything starts locked`() {
        assertEquals(State.LOCKED, gate.stateFor("someone"))
        assertFalse(gate.isUnlocked("someone"))
    }

    @Test
    fun `request then acceptance unlocks`() {
        gate.onRequestSent("p", "☕")
        assertEquals(State.SENT, gate.stateFor("p"))
        gate.onReplyReceived("p", accepted = true)
        assertTrue(gate.isUnlocked("p"))
    }

    @Test
    fun `decline resets to locked so retry is possible`() {
        gate.onRequestSent("p", "☕")
        gate.onReplyReceived("p", accepted = false)
        assertEquals(State.LOCKED, gate.stateFor("p"))
    }

    @Test
    fun `incoming request waits for my reply`() {
        gate.onRequestReceived("p", "🎮")
        assertEquals(State.RECEIVED, gate.stateFor("p"))
        assertEquals("🎮", gate.entries.value["p"]?.emoji)
        gate.onLocalReply("p", accepted = true)
        assertTrue(gate.isUnlocked("p"))
    }

    @Test
    fun `crossing requests unlock without a reply`() {
        gate.onRequestSent("p", "☕")
        gate.onRequestReceived("p", "👍")
        assertTrue(gate.isUnlocked("p"))
    }

    @Test
    fun `late request cannot re-lock an unlocked chat`() {
        gate.unlock("p")
        gate.onRequestReceived("p", "👍")
        assertTrue(gate.isUnlocked("p"))
    }

    @Test
    fun `help-response path unlocks directly`() {
        gate.unlock("p")
        assertTrue(gate.isUnlocked("p"))
    }
}
