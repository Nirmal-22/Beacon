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

    fun start() {
        if (_status.value == Status.ACTIVE) return
        _status.value = Status.ACTIVE
        startAdvertising()
        startDiscovery()
    }

    fun stop() {
        client.stopAllEndpoints()
        client.stopAdvertising()
        client.stopDiscovery()
        discovered.clear()
        _peers.value = emptyMap()
        _status.value = Status.IDLE
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
                ConnectionsStatusCodes.STATUS_OK -> sendHello(endpointId)
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
            _peers.update { it - endpointId }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            val envelope = ProtocolCodec.decode(bytes) ?: return
            if (envelope.type == BeaconEnvelope.TYPE_HELLO) {
                _peers.update {
                    it + (endpointId to Peer(
                        endpointId = endpointId,
                        sessionId = envelope.senderId,
                        displayName = envelope.senderName,
                        isConnected = true,
                    ))
                }
            } else {
                _inbound.tryEmit(endpointId to envelope)
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
    }
}
