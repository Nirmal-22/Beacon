package com.beacon.data

import com.beacon.domain.DmGate
import com.beacon.domain.IdGen
import com.beacon.domain.IntentBoard
import com.beacon.domain.IntentTag
import com.beacon.domain.RoomRegistry
import com.beacon.model.Peer
import com.beacon.nearby.MeshTransport
import com.beacon.nearby.protocol.BeaconEnvelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** UI-facing alert for social events that arrive while the app is elsewhere. */
fun interface SocialAlerts {
    fun onIcebreaker(peer: Peer, emoji: String, dmRoomCode: String)
}

/**
 * Routes non-chat envelopes between the mesh and the domain state holders,
 * and keeps peers in sync: every newly connected endpoint immediately gets
 * this device's current room snapshot and intent; disconnects strip presence.
 */
class MeshRouter(
    private val nearby: MeshTransport,
    private val identity: Identity,
    private val rooms: RoomRegistry,
    private val intents: IntentBoard,
    private val gate: DmGate,
    scope: CoroutineScope,
    private val alerts: SocialAlerts? = null,
) {

    private var knownEndpoints = emptySet<String>()

    init {
        nearby.inbound
            .onEach { (endpointId, envelope) -> route(endpointId, envelope) }
            .launchIn(scope)

        nearby.peers
            .onEach { current ->
                val arrived = current.keys - knownEndpoints
                val left = knownEndpoints - current.keys
                knownEndpoints = current.keys
                left.forEach { rooms.onDisconnected(it) }
                if (arrived.isNotEmpty()) {
                    nearby.send(envelope(BeaconEnvelope.TYPE_ROOM_ANNOUNCE, rooms.announceBody()), arrived.toList())
                    intents.myIntent.value?.let { tag ->
                        nearby.send(envelope(BeaconEnvelope.TYPE_INTENT, tag.key), arrived.toList())
                    }
                }
            }
            .launchIn(scope)
    }

    private fun route(endpointId: String, env: BeaconEnvelope) {
        when (env.type) {
            BeaconEnvelope.TYPE_ROOM_ANNOUNCE ->
                rooms.onAnnounce(endpointId, env.senderId, env.senderName, env.body)

            BeaconEnvelope.TYPE_INTENT ->
                intents.onPeerIntent(env.senderId, env.body)

            BeaconEnvelope.TYPE_ICEBREAKER -> {
                gate.onRequestReceived(env.senderId, env.body)
                val peer = nearby.peers.value[endpointId]
                if (peer != null && gate.stateFor(env.senderId) == DmGate.State.RECEIVED) {
                    alerts?.onIcebreaker(
                        peer,
                        env.body ?: "👍",
                        IdGen.dmRoomCode(identity.sessionId, env.senderId),
                    )
                }
            }

            BeaconEnvelope.TYPE_ICEBREAKER_REPLY ->
                gate.onReplyReceived(env.senderId, env.body == "accept")
        }
    }

    fun joinRoom(nameOrCode: String) {
        if (rooms.join(nameOrCode)) broadcastAnnounce()
    }

    fun leaveRoom(code: String) {
        if (rooms.leave(code)) broadcastAnnounce()
    }

    fun setIntent(tag: IntentTag?) {
        val key = intents.setMyIntent(tag)
        broadcast(BeaconEnvelope.TYPE_INTENT, key)
    }

    fun sendIcebreaker(peerSessionId: String, emoji: String) {
        gate.onRequestSent(peerSessionId, emoji)
        sendToSession(peerSessionId, BeaconEnvelope.TYPE_ICEBREAKER, emoji)
    }

    fun replyIcebreaker(peerSessionId: String, accepted: Boolean) {
        gate.onLocalReply(peerSessionId, accepted)
        sendToSession(peerSessionId, BeaconEnvelope.TYPE_ICEBREAKER_REPLY, if (accepted) "accept" else "decline")
    }

    /** Membership changed — every connected peer gets the fresh snapshot. */
    private fun broadcastAnnounce() = broadcast(BeaconEnvelope.TYPE_ROOM_ANNOUNCE, rooms.announceBody())

    private fun broadcast(type: String, body: String?) {
        val endpoints = nearby.peers.value.keys.toList()
        if (endpoints.isNotEmpty()) nearby.send(envelope(type, body), endpoints)
    }

    private fun sendToSession(sessionId: String, type: String, body: String?) {
        val endpoint = nearby.peers.value.values.firstOrNull { it.sessionId == sessionId }?.endpointId
            ?: return
        nearby.send(envelope(type, body), listOf(endpoint))
    }

    private fun envelope(type: String, body: String?) = BeaconEnvelope(
        type = type,
        msgId = IdGen.newMessageId(),
        senderId = identity.sessionId,
        senderName = identity.effectiveName,
        ts = System.currentTimeMillis(),
        body = body,
    )
}
