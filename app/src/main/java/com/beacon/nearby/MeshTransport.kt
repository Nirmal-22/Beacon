package com.beacon.nearby

import com.beacon.model.Peer
import com.beacon.nearby.protocol.BeaconEnvelope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the rest of the app sees of the mesh. [NearbyManager] is the real
 * implementation; tests substitute a fake so repositories stay JVM-testable.
 */
interface MeshTransport {
    val peers: StateFlow<Map<String, Peer>>
    val inbound: SharedFlow<Pair<String, BeaconEnvelope>>
    fun send(envelope: BeaconEnvelope, endpointIds: List<String>)
}
