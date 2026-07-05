package com.beacon.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The icebreaker gate: free 1:1 chat unlocks only after one side's tap is
 * accepted by the other. In-memory and keyed by peer sessionId — a gate that
 * resets each session is consistent with ephemeral identity.
 */
class DmGate {

    enum class State { LOCKED, SENT, RECEIVED, UNLOCKED }

    data class Entry(val state: State, val emoji: String? = null)

    private val _entries = MutableStateFlow<Map<String, Entry>>(emptyMap())
    val entries: StateFlow<Map<String, Entry>> = _entries.asStateFlow()

    fun stateFor(peerId: String): State = _entries.value[peerId]?.state ?: State.LOCKED

    fun isUnlocked(peerId: String): Boolean = stateFor(peerId) == State.UNLOCKED

    /** I tapped an icebreaker at them. */
    fun onRequestSent(peerId: String, emoji: String) {
        if (isUnlocked(peerId)) return
        set(peerId, Entry(State.SENT, emoji))
    }

    /** They tapped an icebreaker at me. Crossing requests just unlock. */
    fun onRequestReceived(peerId: String, emoji: String?) {
        when (stateFor(peerId)) {
            State.UNLOCKED -> Unit
            State.SENT -> set(peerId, Entry(State.UNLOCKED, emoji))
            else -> set(peerId, Entry(State.RECEIVED, emoji))
        }
    }

    /** Their answer to my request. Decline resets to LOCKED so I can retry. */
    fun onReplyReceived(peerId: String, accepted: Boolean) {
        set(peerId, if (accepted) Entry(State.UNLOCKED) else Entry(State.LOCKED))
    }

    /** My answer to their request. */
    fun onLocalReply(peerId: String, accepted: Boolean) {
        set(peerId, if (accepted) Entry(State.UNLOCKED) else Entry(State.LOCKED))
    }

    /** Direct unlock — e.g. responding to a help request implies consent. */
    fun unlock(peerId: String) = set(peerId, Entry(State.UNLOCKED))

    private fun set(peerId: String, entry: Entry) {
        _entries.value = _entries.value + (peerId to entry)
    }
}
