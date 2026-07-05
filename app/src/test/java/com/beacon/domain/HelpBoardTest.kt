package com.beacon.domain

import com.beacon.data.FakeIdentity
import com.beacon.nearby.protocol.HelpPostPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpBoardTest {

    private var now = 1_000_000L
    private val board = HelpBoard(FakeIdentity(), now = { now })

    @Test
    fun `created post appears with my identity and correct expiry`() {
        board.createPost(HelpCategory.CHARGER, "  USB-C charger?  ", ttlMinutes = 20)
        val post = board.posts.value.single()
        assertEquals("USB-C charger?", post.text)
        assertEquals("aaaa-local", post.posterId)
        assertEquals(now + 20 * 60_000, post.expiresAt)
    }

    @Test
    fun `remote posts arrive and expired or blank ones are rejected`() {
        board.onRemotePost("s1", "Them", HelpPostPayload("h1", "ride", "To T2?", now + 60_000).encode())
        board.onRemotePost("s2", "X", HelpPostPayload("h2", "ride", "late", now - 1).encode())
        board.onRemotePost("s3", "Y", HelpPostPayload("h3", "ride", "  ", now + 60_000).encode())
        board.onRemotePost("s4", "Z", "garbage")

        assertEquals(listOf("h1"), board.posts.value.map { it.id })
        assertEquals(HelpCategory.RIDE, board.posts.value.single().category)
    }

    @Test
    fun `prune drops posts past their ttl`() {
        board.createPost(HelpCategory.OTHER, "short", ttlMinutes = 10)
        board.createPost(HelpCategory.OTHER, "long", ttlMinutes = 30)
        now += 15 * 60_000
        board.prune()
        assertEquals(listOf("long"), board.posts.value.map { it.text })
    }

    @Test
    fun `only my own posts can be cancelled by me`() {
        val mine = board.createPost(HelpCategory.CHARGER, "mine", 10)
        board.onRemotePost("s1", "Them", HelpPostPayload("theirs", "ride", "x", now + 60_000).encode())

        assertTrue(board.cancel(mine.id))
        assertFalse(board.cancel("theirs"))
        assertEquals(listOf("theirs"), board.posts.value.map { it.id })
    }

    @Test
    fun `remote cancel only works for the original poster`() {
        board.onRemotePost("s1", "Them", HelpPostPayload("h1", "ride", "x", now + 60_000).encode())

        board.onRemoteCancel("someone-else", "h1")
        assertEquals(1, board.posts.value.size)

        board.onRemoteCancel("s1", "h1")
        assertTrue(board.posts.value.isEmpty())
    }

    @Test
    fun `re-broadcast payloads cover only my unexpired posts`() {
        board.createPost(HelpCategory.CHARGER, "mine", 10)
        board.onRemotePost("s1", "Them", HelpPostPayload("h1", "ride", "x", now + 60_000).encode())
        assertEquals(listOf("mine"), board.myActivePayloads().map { it.text })
    }

    @Test
    fun `duplicate post ids upsert instead of duplicating`() {
        val p = HelpPostPayload("h1", "ride", "x", now + 60_000)
        board.onRemotePost("s1", "Them", p.encode())
        board.onRemotePost("s1", "Them", p.encode())
        assertEquals(1, board.posts.value.size)
    }
}
