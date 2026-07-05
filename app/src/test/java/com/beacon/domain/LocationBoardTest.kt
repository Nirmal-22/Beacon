package com.beacon.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationBoardTest {

    private var now = 1_000_000L
    private val board = LocationBoard(now = { now })

    @Test
    fun `not sharing means no broadcast body even with a fix`() {
        board.onMyLocation(12.9, 77.6)
        assertNull(board.myLocationBody())
    }

    @Test
    fun `sharing produces a parseable body and stopping clears it`() {
        board.setSharing(true)
        board.onMyLocation(12.9716, 77.5946)
        assertEquals("12.9716,77.5946", board.myLocationBody())

        board.setSharing(false)
        assertNull(board.myLocationBody())
        assertNull(board.myPosition.value)
    }

    @Test
    fun `remote pins appear, update, and clear on empty body`() {
        board.onRemoteLocation("s1", "Them", "12.9,77.6")
        assertEquals(12.9, board.pins.value.getValue("s1").lat, 1e-9)

        board.onRemoteLocation("s1", "Them", "13.0,77.7")
        assertEquals(13.0, board.pins.value.getValue("s1").lat, 1e-9)

        board.onRemoteLocation("s1", "Them", null)
        assertTrue(board.pins.value.isEmpty())
    }

    @Test
    fun `garbage and out-of-range coordinates are dropped`() {
        board.onRemoteLocation("s1", "X", "not,numbers")
        board.onRemoteLocation("s2", "X", "95.0,10.0")
        board.onRemoteLocation("s3", "X", "10.0,200.0")
        board.onRemoteLocation("s4", "X", "10.0")
        assertTrue(board.pins.value.isEmpty())
    }

    @Test
    fun `stale pins age out on prune`() {
        board.onRemoteLocation("s1", "Old", "12.9,77.6")
        now += LocationBoard.PIN_MAX_AGE_MS + 1
        board.onRemoteLocation("s2", "Fresh", "13.0,77.7")
        board.prune()
        assertEquals(setOf("s2"), board.pins.value.keys)
    }
}
