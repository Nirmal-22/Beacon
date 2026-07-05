package com.beacon.domain

import com.beacon.data.Identity
import com.beacon.nearby.protocol.HelpPostPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HelpCategory(val key: String, val emoji: String, val label: String) {
    CHARGER("charger", "🔌", "Charger"),
    WATCH_MY_STUFF("watch", "🎒", "Watch my stuff"),
    PLAYER_NEEDED("player", "🏸", "Player needed"),
    RIDE("ride", "🚗", "Ride / directions"),
    OTHER("other", "🙋", "Something else");

    companion object {
        fun fromKey(key: String): HelpCategory = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

/**
 * Live "Need Help Nearby" feed: my open requests plus every unexpired request
 * heard from peers. TTL is enforced locally by [prune] — there is no server
 * to expire anything. Pure Kotlin.
 */
class HelpBoard(
    private val identity: Identity,
    private val now: () -> Long = System::currentTimeMillis,
) {

    data class HelpPost(
        val id: String,
        val category: HelpCategory,
        val text: String,
        val expiresAt: Long,
        val posterId: String,
        val posterName: String,
    )

    private val _posts = MutableStateFlow<List<HelpPost>>(emptyList())
    val posts: StateFlow<List<HelpPost>> = _posts.asStateFlow()

    /** Create my request and return the wire payload to broadcast. */
    @Synchronized
    fun createPost(category: HelpCategory, text: String, ttlMinutes: Int): HelpPostPayload {
        val payload = HelpPostPayload(
            id = IdGen.newMessageId(),
            category = category.key,
            text = text.trim(),
            expiresAt = now() + ttlMinutes * 60_000L,
        )
        upsert(payload, identity.sessionId, identity.effectiveName)
        return payload
    }

    /** @return true if it was mine to cancel (callers then broadcast the cancel). */
    @Synchronized
    fun cancel(id: String): Boolean {
        val mine = _posts.value.any { it.id == id && it.posterId == identity.sessionId }
        if (mine) _posts.value = _posts.value.filterNot { it.id == id }
        return mine
    }

    @Synchronized
    fun onRemotePost(senderId: String, senderName: String, body: String?) {
        val payload = HelpPostPayload.decode(body) ?: return
        if (payload.expiresAt <= now() || payload.text.isBlank()) return
        upsert(payload, senderId, senderName)
    }

    /** Only the original poster's cancel counts. */
    @Synchronized
    fun onRemoteCancel(senderId: String, id: String?) {
        _posts.value = _posts.value.filterNot { it.id == id && it.posterId == senderId }
    }

    @Synchronized
    fun prune() {
        val t = now()
        if (_posts.value.any { it.expiresAt <= t }) {
            _posts.value = _posts.value.filter { it.expiresAt > t }
        }
    }

    /** For re-broadcasting to endpoints that connect after I posted. */
    @Synchronized
    fun myActivePayloads(): List<HelpPostPayload> = _posts.value
        .filter { it.posterId == identity.sessionId && it.expiresAt > now() }
        .map { HelpPostPayload(it.id, it.category.key, it.text, it.expiresAt) }

    private fun upsert(payload: HelpPostPayload, posterId: String, posterName: String) {
        val post = HelpPost(
            id = payload.id,
            category = HelpCategory.fromKey(payload.category),
            text = payload.text,
            expiresAt = payload.expiresAt,
            posterId = posterId,
            posterName = posterName,
        )
        _posts.value = (_posts.value.filterNot { it.id == post.id } + post)
            .sortedBy { it.expiresAt }
    }
}
