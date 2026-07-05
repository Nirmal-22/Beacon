package com.beacon.model

/**
 * A nearby person. [endpointId] is the transport handle assigned by Nearby
 * Connections and changes between sessions; [sessionId] is Beacon's own
 * ephemeral identity, stable for the lifetime of the peer's app process.
 */
data class Peer(
    val endpointId: String,
    val sessionId: String,
    val displayName: String,
    val isConnected: Boolean,
)
