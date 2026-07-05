package com.beacon.nearby.protocol

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Body of a ROOM_ANNOUNCE envelope: full snapshot of the sender's rooms. */
@Serializable
data class RoomsAnnouncePayload(val rooms: List<RoomRef> = emptyList()) {

    @Serializable
    data class RoomRef(val code: String, val name: String)

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(body: String?): RoomsAnnouncePayload? = try {
            body?.let { json.decodeFromString(serializer(), it) }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
