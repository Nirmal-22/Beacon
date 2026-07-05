package com.beacon.data

import com.beacon.domain.IdGen
import com.beacon.domain.RoomRegistry
import com.beacon.nearby.MeshTransport
import com.beacon.nearby.protocol.BeaconEnvelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Routes non-chat envelopes between the mesh and the domain state holders,
 * and keeps peers in sync: every newly connected endpoint immediately gets
 * this device's current room snapshot; disconnects strip a peer's presence.
 */
class MeshRouter(
    private val nearby: MeshTransport,
    private val identity: Identity,
    private val rooms: RoomRegistry,
    scope: CoroutineScope,
) {

    private var knownEndpoints = emptySet<String>()

    init {
        nearby.inbound
            .onEach { (endpointId, envelope) ->
                when (envelope.type) {
                    BeaconEnvelope.TYPE_ROOM_ANNOUNCE ->
                        rooms.onAnnounce(endpointId, envelope.senderId, envelope.senderName, envelope.body)
                }
            }
            .launchIn(scope)

        nearby.peers
            .onEach { current ->
                val arrived = current.keys - knownEndpoints
                val left = knownEndpoints - current.keys
                knownEndpoints = current.keys
                left.forEach { rooms.onDisconnected(it) }
                if (arrived.isNotEmpty()) {
                    nearby.send(announceEnvelope(), arrived.toList())
                }
            }
            .launchIn(scope)
    }

    fun joinRoom(nameOrCode: String) {
        if (rooms.join(nameOrCode)) broadcastAnnounce()
    }

    fun leaveRoom(code: String) {
        if (rooms.leave(code)) broadcastAnnounce()
    }

    /** Membership changed — every connected peer gets the fresh snapshot. */
    private fun broadcastAnnounce() {
        val endpoints = nearby.peers.value.keys.toList()
        if (endpoints.isNotEmpty()) nearby.send(announceEnvelope(), endpoints)
    }

    private fun announceEnvelope() = BeaconEnvelope(
        type = BeaconEnvelope.TYPE_ROOM_ANNOUNCE,
        msgId = IdGen.newMessageId(),
        senderId = identity.sessionId,
        senderName = identity.effectiveName,
        ts = System.currentTimeMillis(),
        body = rooms.announceBody(),
    )
}
