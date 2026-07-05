package com.beacon.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beacon.AppContainer
import com.beacon.domain.IdGen
import com.beacon.model.Peer
import com.beacon.nearby.NearbyManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    val peers: StateFlow<List<Peer>> = container.nearbyManager.peers
        .map { it.values.sortedBy { peer -> peer.displayName.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val status: StateFlow<NearbyManager.Status> = container.nearbyManager.status

    val myDisplayName: String get() = container.identityRepository.displayName

    /** DM room code shared by both devices without negotiation. */
    fun dmRoomCodeFor(peer: Peer): String =
        IdGen.dmRoomCode(container.identityRepository.sessionId, peer.sessionId)
}
