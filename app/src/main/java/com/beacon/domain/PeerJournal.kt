package com.beacon.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How and when this session met each peer. In-memory, session-scoped like
 * identity itself. Origins upgrade but never downgrade: plain discovery <
 * shared room < help interaction — the most meaningful story wins.
 */
class PeerJournal(private val now: () -> Long = System::currentTimeMillis) {

    data class Meet(val firstSeenAt: Long, val origin: String, val priority: Int)

    private val _meets = MutableStateFlow<Map<String, Meet>>(emptyMap())
    val meets: StateFlow<Map<String, Meet>> = _meets.asStateFlow()

    fun noteSeen(sessionId: String) =
        note(sessionId, "Discovered nearby", PRIORITY_NEARBY)

    fun noteSharedRoom(sessionId: String, roomName: String) =
        note(sessionId, "Met in room \"$roomName\"", PRIORITY_ROOM)

    fun noteHelp(sessionId: String, helpText: String) =
        note(sessionId, "Met via help request \"${helpText.take(40)}\"", PRIORITY_HELP)

    private fun note(sessionId: String, origin: String, priority: Int) {
        val current = _meets.value[sessionId]
        if (current != null && current.priority >= priority) return
        _meets.value = _meets.value + (sessionId to Meet(
            firstSeenAt = current?.firstSeenAt ?: now(),
            origin = origin,
            priority = priority,
        ))
    }

    companion object {
        const val PRIORITY_NEARBY = 0
        const val PRIORITY_ROOM = 1
        const val PRIORITY_HELP = 2
    }
}
