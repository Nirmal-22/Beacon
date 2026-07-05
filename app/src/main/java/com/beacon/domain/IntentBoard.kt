package com.beacon.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Who wants what nearby: my broadcast intent and the latest intent per peer
 * (keyed by sessionId — session-scoped like everything else). Pure Kotlin.
 */
class IntentBoard {

    private val _myIntent = MutableStateFlow<IntentTag?>(null)
    val myIntent: StateFlow<IntentTag?> = _myIntent.asStateFlow()

    private val _peerIntents = MutableStateFlow<Map<String, IntentTag>>(emptyMap())
    val peerIntents: StateFlow<Map<String, IntentTag>> = _peerIntents.asStateFlow()

    /** @return the new value's key ("" when cleared), ready to broadcast. */
    fun setMyIntent(tag: IntentTag?): String {
        _myIntent.value = tag
        return tag?.key.orEmpty()
    }

    fun onPeerIntent(sessionId: String, key: String?) {
        val tag = IntentTag.fromKey(key)
        _peerIntents.value =
            if (tag == null) _peerIntents.value - sessionId
            else _peerIntents.value + (sessionId to tag)
    }
}
