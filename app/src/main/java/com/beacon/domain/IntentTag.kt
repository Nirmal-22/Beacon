package com.beacon.domain

/** The one lightweight thing a user can broadcast about why they're here. */
enum class IntentTag(val key: String, val emoji: String, val label: String) {
    COFFEE("coffee", "☕", "Coffee"),
    GAMING("gaming", "🎮", "Gaming"),
    NETWORKING("networking", "💼", "Networking"),
    FRIENDS("friends", "🤝", "Friends"),
    STUDY("study", "📚", "Study partner");

    companion object {
        fun fromKey(key: String?): IntentTag? = entries.firstOrNull { it.key == key }
    }
}
