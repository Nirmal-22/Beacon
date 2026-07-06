package com.beacon.data

import com.beacon.domain.DmGate
import com.beacon.domain.HelpBoard
import com.beacon.domain.HelpCategory
import com.beacon.domain.IdGen
import com.beacon.domain.IntentBoard
import com.beacon.domain.IntentTag
import com.beacon.domain.LocationBoard
import com.beacon.domain.PeerJournal
import com.beacon.domain.RoomRegistry
import com.beacon.model.Peer
import com.beacon.nearby.MeshTransport
import com.beacon.nearby.protocol.BeaconEnvelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** UI-facing alerts for social events that arrive while the app is elsewhere. */
interface SocialAlerts {
    fun onIcebreaker(peer: Peer, emoji: String, dmRoomCode: String)
    fun onHelpPost(post: HelpBoard.HelpPost)
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
    private val help: HelpBoard,
    private val locations: LocationBoard,
    private val journal: PeerJournal,
    scope: CoroutineScope,
    private val alerts: SocialAlerts? = null,
    private val isBlocked: (sessionId: String) -> Boolean = { false },
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
                arrived.forEach { ep -> current[ep]?.let { journal.noteSeen(it.sessionId) } }
                if (arrived.isNotEmpty()) {
                    val list = arrived.toList()
                    nearby.send(envelope(BeaconEnvelope.TYPE_ROOM_ANNOUNCE, rooms.announceBody()), list)
                    intents.myIntent.value?.let { tag ->
                        nearby.send(envelope(BeaconEnvelope.TYPE_INTENT, tag.key), list)
                    }
                    help.myActivePayloads().forEach { payload ->
                        nearby.send(envelope(BeaconEnvelope.TYPE_HELP_POST, payload.encode()), list)
                    }
                    locations.myLocationBody()?.let { body ->
                        nearby.send(envelope(BeaconEnvelope.TYPE_LOCATION, body), list)
                    }
                }
            }
            .launchIn(scope)
    }

    private fun route(endpointId: String, env: BeaconEnvelope) {
        if (isBlocked(env.senderId)) return
        when (env.type) {
            BeaconEnvelope.TYPE_ROOM_ANNOUNCE -> {
                rooms.onAnnounce(endpointId, env.senderId, env.senderName, env.body)
                // Shared room = a story worth remembering in "how we met".
                rooms.rooms.value.values
                    .firstOrNull { it.isJoined && env.senderId in it.members }
                    ?.let { journal.noteSharedRoom(env.senderId, it.name) }
            }

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

            BeaconEnvelope.TYPE_HELP_POST -> {
                val before = help.posts.value.map { it.id }.toSet()
                help.onRemotePost(env.senderId, env.senderName, env.body)
                help.posts.value.firstOrNull { it.id !in before }?.let { alerts?.onHelpPost(it) }
            }

            BeaconEnvelope.TYPE_HELP_CANCEL ->
                help.onRemoteCancel(env.senderId, env.body)

            BeaconEnvelope.TYPE_LOCATION ->
                locations.onRemoteLocation(env.senderId, env.senderName, env.body)
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

    fun postHelp(category: HelpCategory, text: String, ttlMinutes: Int) {
        val payload = help.createPost(category, text, ttlMinutes)
        broadcast(BeaconEnvelope.TYPE_HELP_POST, payload.encode())
        // The poster hosts the helpers' group chat from the start.
        rooms.joinWithCode(helpRoomCode(payload.id), helpRoomName(HelpCategory.fromKey(payload.category), payload.text))
        broadcastAnnounce()
    }

    /**
     * Default respond action: everyone who can help lands in one shared room
     * with the poster (same pre-agreed code on every device).
     * @return code to title for navigation.
     */
    fun joinHelpChat(post: HelpBoard.HelpPost): Pair<String, String> {
        val code = helpRoomCode(post.id)
        val name = helpRoomName(post.category, post.text)
        if (rooms.joinWithCode(code, name)) broadcastAnnounce()
        journal.noteHelp(post.posterId, post.text)
        return code to name
    }

    fun cancelHelp(id: String) {
        if (help.cancel(id)) broadcast(BeaconEnvelope.TYPE_HELP_CANCEL, id)
    }

    /** Fresh GPS fix while sharing — pin it locally and on every peer's map. */
    fun broadcastMyLocation(lat: Double, lon: Double) {
        locations.onMyLocation(lat, lon)
        locations.myLocationBody()?.let { broadcast(BeaconEnvelope.TYPE_LOCATION, it) }
    }

    /** Stopped sharing — everyone drops my pin now, not after the timeout. */
    fun clearMyLocation() = broadcast(BeaconEnvelope.TYPE_LOCATION, null)

    /**
     * Responding to a help post implies chat consent: both sides' gates open
     * and the caller navigates into the DM.
     */
    fun respondToHelp(post: HelpBoard.HelpPost): String {
        gate.unlock(post.posterId)
        sendToSession(post.posterId, BeaconEnvelope.TYPE_ICEBREAKER, "🙋")
        journal.noteHelp(post.posterId, post.text)
        return IdGen.dmRoomCode(identity.sessionId, post.posterId)
    }

    private fun helpRoomCode(postId: String) = "help-" + postId.take(8)

    private fun helpRoomName(category: HelpCategory, text: String) =
        "${category.emoji} ${text.take(28)}"

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
