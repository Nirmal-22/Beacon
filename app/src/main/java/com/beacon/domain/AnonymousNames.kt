package com.beacon.domain

/**
 * Deterministic per-session anonymous handle, e.g. "Anon Fox 🦊". Derived from
 * the sessionId so it stays stable for the session but changes every app run —
 * consistent with ephemeral identity. Pure JVM.
 */
object AnonymousNames {

    private val ANIMALS = listOf(
        "Fox" to "🦊", "Owl" to "🦉", "Panda" to "🐼", "Wolf" to "🐺",
        "Koala" to "🐨", "Tiger" to "🐯", "Penguin" to "🐧", "Whale" to "🐋",
        "Falcon" to "🪶", "Otter" to "🦦", "Lynx" to "🐈", "Raven" to "🐦",
    )

    fun forSession(sessionId: String): String {
        val (animal, emoji) = ANIMALS[Math.floorMod(sessionId.hashCode(), ANIMALS.size)]
        return "Anon $animal $emoji"
    }
}
