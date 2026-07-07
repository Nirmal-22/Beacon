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
        val hash = sessionId.hashCode()
        val (animal, emoji) = ANIMALS[Math.floorMod(hash, ANIMALS.size)]
        // Numbered suffix: 12 animals alone collide fast with a few phones in
        // range (two Anon Otters happened in the field). Animal+number gives
        // ~1080 handles — still no coordination, collisions now rare.
        val number = Math.floorMod(hash / ANIMALS.size, 90) + 10
        return "Anon $animal-$number $emoji"
    }
}
