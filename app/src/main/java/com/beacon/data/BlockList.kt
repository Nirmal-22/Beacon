package com.beacon.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Blocked peers, by sessionId. With no backend, "report" and "block" collapse
 * into the same local action: the person disappears from every surface and
 * their envelopes are dropped. Persisted, though session ids naturally rotate
 * — blocking is mostly a within-session shield, which matches ephemerality.
 */
class BlockList(context: Context) {

    private val prefs = context.getSharedPreferences("beacon_blocks", Context.MODE_PRIVATE)

    private val _blocked = MutableStateFlow(prefs.getStringSet(KEY, emptySet()).orEmpty().toSet())
    val blocked: StateFlow<Set<String>> = _blocked.asStateFlow()

    fun isBlocked(sessionId: String): Boolean = sessionId in _blocked.value

    fun block(sessionId: String) {
        _blocked.value = _blocked.value + sessionId
        persist()
    }

    fun unblockAll() {
        _blocked.value = emptySet()
        persist()
    }

    private fun persist() = prefs.edit { putStringSet(KEY, _blocked.value) }

    private companion object {
        const val KEY = "blocked_session_ids"
    }
}
