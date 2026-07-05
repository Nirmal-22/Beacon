package com.beacon.data

/** Read side of the local identity, small enough to fake in JVM tests. */
interface Identity {
    val sessionId: String
    val displayName: String

    /** What peers see: the display name, or an anonymous handle when hidden. */
    val effectiveName: String get() = displayName
}
