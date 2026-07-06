package com.beacon.nearby

import android.content.Context
import android.util.Log
import com.beacon.data.IdentityRepository
import com.beacon.domain.IdGen
import com.beacon.model.Peer
import com.beacon.nearby.protocol.BeaconEnvelope
import com.beacon.nearby.protocol.ProtocolCodec
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Wrapper around Nearby Connections. Advertises and discovers simultaneously
 * under [SERVICE_ID] (P2P_CLUSTER) and connects to every Beacon endpoint in
 * range — the full-mesh policy: a "room" is a local filter over the mesh, not
 * a transport concept.
 *
 * Connection race: when both sides discover each other at the same time, only
 * the side [IdGen.shouldInitiateConnection] picks calls requestConnection; the
 * other waits for onConnectionInitiated. Failures retry once after a short
 * jittered delay.
 */
class NearbyManager(
    context: Context,
    private val identity: IdentityRepository,
    private val scope: CoroutineScope,
) : MeshTransport {

    enum class Status { IDLE, ACTIVE, ERROR }

    private val client = Nearby.getConnectionsClient(context.applicationContext)

    private val _status = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Connected peers that completed the HELLO exchange, keyed by endpointId. */
    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    override val peers: StateFlow<Map<String, Peer>> = _peers.asStateFlow()

    /** Decoded non-HELLO envelopes, paired with the sending endpointId. */
    private val _inbound = MutableSharedFlow<Pair<String, BeaconEnvelope>>(extraBufferCapacity = 64)
    override val inbound: SharedFlow<Pair<String, BeaconEnvelope>> = _inbound.asSharedFlow()

    /** Endpoints seen by discovery but not yet connected: endpointId -> session prefix. */
    private val discovered = mutableMapOf<String, String>()

    /** Last inbound payload per connected endpoint — liveness signal. */
    private val lastSeenAt = mutableMapOf<String, Long>()

    private var heartbeatJob: Job? = null

    fun start() {
        if (_status.value == Status.ACTIVE) return
        _status.value = Status.ACTIVE
        startAdvertising()
        startDiscovery()
        heartbeatJob = scope.launch { heartbeatLoop() }
    }

    fun stop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        client.stopAllEndpoints()
        client.stopAdvertising()
        client.stopDiscovery()
        discovered.clear()
        lastSeenAt.clear()
        _peers.value = emptyMap()
        _status.value = Status.IDLE
    }

    /**
     * Nearby's own keep-alive can take minutes to flag a dead link, which
     * left ghost peers on screen. Every tick we (1) prove we're alive to
     * everyone, (2) drop peers that have been silent past the window, and
     * (3) retry endpoints that are discovered but never got connected.
     */
    private suspend fun heartbeatLoop() {
        while (true) {
            delay(HEARTBEAT_INTERVAL_MS)
            if (_status.value != Status.ACTIVE) continue

            sendToAll(
                BeaconEnvelope(
                    type = BeaconEnvelope.TYPE_HEARTBEAT,
                    msgId = IdGen.newMessageId(),
                    senderId = identity.sessionId,
                    senderName = identity.effectiveName,
                    ts = System.currentTimeMillis(),
                )
            )

            val cutoff = System.currentTimeMillis() - STALE_PEER_MS
            _peers.value.keys
                .filter { (lastSeenAt[it] ?: 0L) < cutoff }
                .forEach { endpointId ->
                    Log.i(TAG, "pruning silent peer $endpointId")
                    client.disconnectFromEndpoint(endpointId)
                    lastSeenAt.remove(endpointId)
                    _peers.update { it - endpointId }
                }

            // Connections that never happened (missed callbacks, failed race):
            // discovery won't re-fire onEndpointFound while they stay in range.
            discovered
                .filterKeys { it !in _peers.value }
                .forEach { (endpointId, prefix) ->
                    if (IdGen.shouldInitiateConnection(identity.sessionId, prefix)) {
                        requestConnection(endpointId, isRetry = true)
                    }
                }
        }
    }

    override fun send(envelope: BeaconEnvelope, endpointIds: List<String>) {
        if (endpointIds.isEmpty()) return
        client.sendPayload(endpointIds, Payload.fromBytes(ProtocolCodec.encode(envelope)))
    }

    fun sendToAll(envelope: BeaconEnvelope) = send(envelope, _peers.value.keys.toList())

    /**
     * Re-advertise and re-introduce after the local identity changed (e.g.
     * anonymous mode toggled): new endpointInfo for future discoverers, fresh
     * HELLO so already-connected peers update the name they show.
     */
    fun refreshIdentity() {
        if (_status.value != Status.ACTIVE) return
        client.stopAdvertising()
        startAdvertising()
        _peers.value.keys.forEach { sendHello(it) }
    }

    private fun startAdvertising() {
        client.startAdvertising(
            identity.endpointInfo(),
            SERVICE_ID,
            connectionCallback,
            AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build(),
        ).addOnFailureListener { e ->
            Log.w(TAG, "startAdvertising failed, retrying", e)
            retryLater { if (_status.value == Status.ACTIVE) startAdvertising() }
        }
    }

    private fun startDiscovery() {
        client.startDiscovery(
            SERVICE_ID,
            discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build(),
        ).addOnFailureListener { e ->
            Log.w(TAG, "startDiscovery failed, retrying", e)
            retryLater { if (_status.value == Status.ACTIVE) startDiscovery() }
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val decoded = IdGen.decodeEndpointInfo(info.endpointName) ?: return
            val (remotePrefix, _) = decoded
            discovered[endpointId] = remotePrefix
            if (IdGen.shouldInitiateConnection(identity.sessionId, remotePrefix)) {
                requestConnection(endpointId)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            discovered.remove(endpointId)
        }
    }

    private fun requestConnection(endpointId: String, isRetry: Boolean = false) {
        client.requestConnection(identity.endpointInfo(), endpointId, connectionCallback)
            .addOnFailureListener { e ->
                if (!isRetry && discovered.containsKey(endpointId) &&
                    !_peers.value.containsKey(endpointId)
                ) {
                    Log.w(TAG, "requestConnection to $endpointId failed, retrying once", e)
                    retryLater { requestConnection(endpointId, isRetry = true) }
                }
            }
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Open-by-design public mesh: the product's trust gate is the
            // icebreaker (M5), not the transport, so accept immediately.
            client.acceptConnection(endpointId, payloadCallback)
            IdGen.decodeEndpointInfo(info.endpointName)?.let { (prefix, _) ->
                discovered[endpointId] = prefix
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    lastSeenAt[endpointId] = System.currentTimeMillis()
                    sendHello(endpointId)
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> discovered.remove(endpointId)
                else -> {
                    // Usually the simultaneous-connect race; the tie-break
                    // should prevent it, but retry once if still in range.
                    if (discovered.containsKey(endpointId)) {
                        retryLater {
                            if (IdGen.shouldInitiateConnection(
                                    identity.sessionId, discovered[endpointId] ?: return@retryLater
                                )
                            ) requestConnection(endpointId, isRetry = true)
                        }
                    }
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            lastSeenAt.remove(endpointId)
            _peers.update { it - endpointId }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            lastSeenAt[endpointId] = System.currentTimeMillis()
            val bytes = payload.asBytes() ?: return
            val envelope = ProtocolCodec.decode(bytes) ?: return
            when (envelope.type) {
                BeaconEnvelope.TYPE_HEARTBEAT -> Unit // liveness recorded above
                BeaconEnvelope.TYPE_HELLO -> _peers.update { current ->
                    // A session may reappear under a fresh endpoint (app
                    // restart, radio reconnect) before Nearby reports the old
                    // link dead — drop stale twins so a person appears once.
                    current.filterValues { it.sessionId != envelope.senderId } +
                        (endpointId to Peer(
                            endpointId = endpointId,
                            sessionId = envelope.senderId,
                            displayName = envelope.senderName,
                            isConnected = true,
                        ))
                }
                else -> _inbound.tryEmit(endpointId to envelope)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // BYTES payloads arrive whole; nothing to track.
        }
    }

    private fun sendHello(endpointId: String) {
        send(
            BeaconEnvelope(
                type = BeaconEnvelope.TYPE_HELLO,
                msgId = IdGen.newMessageId(),
                senderId = identity.sessionId,
                senderName = identity.effectiveName,
                ts = System.currentTimeMillis(),
            ),
            listOf(endpointId),
        )
    }

    private fun retryLater(block: () -> Unit) {
        scope.launch {
            delay(RETRY_BASE_MS + Random.nextLong(RETRY_JITTER_MS))
            block()
        }
    }

    companion object {
        const val SERVICE_ID = "com.beacon.nearby"
        private const val TAG = "NearbyManager"
        private const val RETRY_BASE_MS = 2_000L
        private const val RETRY_JITTER_MS = 1_000L
        private const val HEARTBEAT_INTERVAL_MS = 15_000L
        private const val STALE_PEER_MS = 45_000L
    }
}
