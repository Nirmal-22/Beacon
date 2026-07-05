package com.beacon.model

/**
 * A temporary room as this device currently understands it. There is no
 * server: a room "exists nearby" exactly while someone in radio range
 * announces membership — the room *is* the union of its members' memories.
 */
data class RoomInfo(
    val code: String,
    val name: String,
    /** sessionId -> display name, including self when joined. */
    val members: Map<String, String>,
    val isJoined: Boolean,
) {
    val memberCount: Int get() = members.size
}
