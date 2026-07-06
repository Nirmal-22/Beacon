package com.beacon.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PeerJournalTest {

    private var now = 5_000L
    private val journal = PeerJournal(now = { now })

    @Test
    fun `origins upgrade but never downgrade`() {
        journal.noteSeen("p")
        assertEquals("Discovered nearby", journal.meets.value.getValue("p").origin)

        journal.noteSharedRoom("p", "Demo Cafe")
        assertEquals("Met in room \"Demo Cafe\"", journal.meets.value.getValue("p").origin)

        journal.noteSeen("p") // re-discovery must not erase the room story
        assertEquals("Met in room \"Demo Cafe\"", journal.meets.value.getValue("p").origin)

        journal.noteHelp("p", "USB-C charger?")
        assertEquals(
            "Met via help request \"USB-C charger?\"",
            journal.meets.value.getValue("p").origin,
        )

        journal.noteSharedRoom("p", "Other Room") // help story outranks rooms
        assertEquals(
            "Met via help request \"USB-C charger?\"",
            journal.meets.value.getValue("p").origin,
        )
    }

    @Test
    fun `first seen timestamp survives origin upgrades`() {
        journal.noteSeen("p")
        now += 60_000
        journal.noteHelp("p", "help!")
        assertEquals(5_000L, journal.meets.value.getValue("p").firstSeenAt)
    }
}
