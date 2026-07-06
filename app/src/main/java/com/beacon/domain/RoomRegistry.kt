package com.beacon.domain

import com.beacon.data.Identity
import com.beacon.model.RoomInfo
import com.beacon.nearby.protocol.RoomsAnnouncePayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * The room truth for this device, reduced from two sources: the local user's
 * memberships and the latest ROOM_ANNOUNCE snapshot per connected peer.
 * Pure Kotlin (no Android imports) so the whole join/leave/disconnect/death
 * matrix is unit-testable.
 *
 * Death rule: a room exists while `isJoined || some connected peer announces
 * it`. When neither holds it simply vanishes from the map — callers watching
 * the flow treat a disappeared non-dm code as "room died" and purge messages.
 */
class RoomRegistry(private val identity: Identity) {

    private data class PeerAnnounce(
        val sessionId: String,
        val senderName: String,
        val rooms: List<RoomsAnnouncePayload.RoomRef>,
    )

    private val myRooms = LinkedHashMap<String, String>() // code -> name
    private val peerAnnounces = HashMap<String, PeerAnnounce>() // endpointId -> snapshot

    private val _rooms = MutableStateFlow<Map<String, RoomInfo>>(emptyMap())
    val rooms: StateFlow<Map<String, RoomInfo>> = _rooms.asStateFlow()

    /** @return true if membership changed (callers then broadcast a fresh announce). */
    @Synchronized
    fun join(nameOrCode: String): Boolean {
        val code = codeFor(nameOrCode)
        if (code.isEmpty()) return false
        // Prefer the display name a nearby announcement already carries.
        return joinWithCode(code, _rooms.value[code]?.name ?: nameOrCode.trim())
    }

    /** Join under an explicit pre-agreed code (e.g. help-post rooms). */
    @Synchronized
    fun joinWithCode(code: String, name: String): Boolean {
        if (code.isEmpty() || myRooms.containsKey(code)) return false
        myRooms[code] = name
        reduce()
        return true
    }

    @Synchronized
    fun leave(code: String): Boolean {
        if (myRooms.remove(code) == null) return false
        reduce()
        return true
    }

    @Synchronized
    fun onAnnounce(endpointId: String, senderId: String, senderName: String, body: String?) {
        val payload = RoomsAnnouncePayload.decode(body) ?: return
        peerAnnounces[endpointId] = PeerAnnounce(senderId, senderName, payload.rooms)
        reduce()
    }

    @Synchronized
    fun onDisconnected(endpointId: String) {
        if (peerAnnounces.remove(endpointId) != null) reduce()
    }

    /** Snapshot of the local user's rooms, ready to be sent as ROOM_ANNOUNCE. */
    @Synchronized
    fun announceBody(): String = RoomsAnnouncePayload(
        myRooms.map { (code, name) -> RoomsAnnouncePayload.RoomRef(code, name) }
    ).encode()

    /** Endpoints that should receive a message for [roomCode]. */
    @Synchronized
    fun targetsFor(roomCode: String): List<String> =
        peerAnnounces.filterValues { peer -> peer.rooms.any { it.code == roomCode } }.keys.toList()

    private fun reduce() {
        val result = HashMap<String, RoomInfo>()
        myRooms.forEach { (code, name) ->
            result[code] = RoomInfo(
                code = code,
                name = name,
                members = mapOf(identity.sessionId to identity.effectiveName),
                isJoined = true,
            )
        }
        peerAnnounces.values.forEach { peer ->
            peer.rooms.forEach { ref ->
                val existing = result[ref.code]
                result[ref.code] = RoomInfo(
                    code = ref.code,
                    name = existing?.name ?: ref.name,
                    members = (existing?.members ?: emptyMap()) + (peer.sessionId to peer.senderName),
                    isJoined = existing?.isJoined ?: false,
                )
            }
        }
        _rooms.value = result
    }

    companion object {
        /**
         * Rooms are joined by human name; the code is its slug so "Library –
         * Silent Floor" and "library silent floor" land in the same room.
         */
        fun codeFor(nameOrCode: String): String =
            nameOrCode.trim().lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .take(48)
    }
}
