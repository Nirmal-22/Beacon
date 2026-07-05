package com.beacon.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beacon.AppContainer
import com.beacon.domain.AnonymousNames
import com.beacon.domain.IdGen
import com.beacon.domain.IntentTag
import com.beacon.domain.RoomRegistry
import com.beacon.model.Peer
import com.beacon.model.RoomInfo
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

    val anonymous: StateFlow<Boolean> = container.identityRepository.anonymous

    /** Unread message count keyed by room code (dm:* and group rooms alike). */
    val unreadCounts: StateFlow<Map<String, Int>> = container.chatRepository.unreadCounts

    /** Rooms I'm in, then rooms announced nearby that I could join. */
    val myRooms: StateFlow<List<RoomInfo>> = container.roomRegistry.rooms
        .map { rooms -> rooms.values.filter { it.isJoined }.sortedBy { it.name.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val nearbyRooms: StateFlow<List<RoomInfo>> = container.roomRegistry.rooms
        .map { rooms -> rooms.values.filter { !it.isJoined }.sortedBy { it.name.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val myIntent: StateFlow<IntentTag?> = container.intentBoard.myIntent

    /** Peer intents keyed by sessionId. */
    val peerIntents: StateFlow<Map<String, IntentTag>> = container.intentBoard.peerIntents

    fun setIntent(tag: IntentTag?) = container.meshRouter.setIntent(tag)

    val anonymousHandle: String
        get() = AnonymousNames.forSession(container.identityRepository.sessionId)

    fun setAnonymous(value: Boolean) {
        container.identityRepository.setAnonymous(value)
        container.nearbyManager.refreshIdentity()
    }

    /** Create-or-join by human name; returns the room code to navigate to. */
    fun joinRoom(nameOrCode: String): String {
        container.meshRouter.joinRoom(nameOrCode)
        return RoomRegistry.codeFor(nameOrCode)
    }

    /** DM room code shared by both devices without negotiation. */
    fun dmRoomCodeFor(peer: Peer): String =
        IdGen.dmRoomCode(container.identityRepository.sessionId, peer.sessionId)
}
