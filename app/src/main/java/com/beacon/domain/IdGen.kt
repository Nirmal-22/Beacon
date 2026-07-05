package com.beacon.domain

import java.util.UUID

/**
 * Identity and ID derivation rules. Pure JVM — keep free of Android imports
 * so everything here stays unit-testable without an emulator.
 */
object IdGen {

    /** Length of the sessionId prefix carried in advertising endpointInfo. */
    const val SESSION_PREFIX_LENGTH = 8

    fun newSessionId(): String = UUID.randomUUID().toString()

    fun newMessageId(): String = UUID.randomUUID().toString()

    fun sessionPrefix(sessionId: String): String = sessionId.take(SESSION_PREFIX_LENGTH)

    /**
     * A 1:1 chat is a degenerate room whose code is derived the same way on
     * both devices, so the two sides agree without negotiation.
     */
    fun dmRoomCode(sessionA: String, sessionB: String): String =
        "dm:${minOf(sessionA, sessionB)}:${maxOf(sessionA, sessionB)}"

    /**
     * Advertising endpointInfo: "<sessionPrefix>|<displayName>". Kept small —
     * Nearby caps endpointInfo at ~131 bytes.
     */
    fun encodeEndpointInfo(sessionId: String, displayName: String): String =
        "${sessionPrefix(sessionId)}|${displayName.take(40)}"

    /** @return prefix to name, or null if the endpointInfo is not Beacon's format. */
    fun decodeEndpointInfo(info: String): Pair<String, String>? {
        val sep = info.indexOf('|')
        if (sep != SESSION_PREFIX_LENGTH) return null
        val prefix = info.substring(0, sep)
        val name = info.substring(sep + 1)
        if (name.isEmpty()) return null
        return prefix to name
    }

    /**
     * Deterministic tie-break for the simultaneous-connect race in
     * P2P_CLUSTER: only the side with the lexicographically smaller session
     * prefix initiates requestConnection; the other waits for
     * onConnectionInitiated. On the (negligible) chance of equal prefixes,
     * both initiate and the failure-retry path resolves it.
     */
    fun shouldInitiateConnection(localSessionId: String, remotePrefix: String): Boolean =
        sessionPrefix(localSessionId) <= remotePrefix
}
