package com.beacon

import android.content.Context
import com.beacon.data.ChatRepository
import com.beacon.data.EphemeralityManager
import com.beacon.data.IdentityRepository
import com.beacon.data.MeshRouter
import com.beacon.data.db.BeaconDatabase
import com.beacon.domain.RoomRegistry
import com.beacon.nearby.NearbyManager
import com.beacon.service.MessagesNotifier
import com.beacon.service.PeerAlerter
import com.beacon.ui.navigation.ChatRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Manual dependency container — one instance held by [BeaconApp].
 * Deliberately no DI framework: the object graph is small enough to read.
 */
class AppContainer(appContext: Context) {

    /** App-lifetime scope for repositories collecting the mesh. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val identityRepository = IdentityRepository(appContext)

    val database = BeaconDatabase.build(appContext)

    val nearbyManager = NearbyManager(appContext, identityRepository, appScope)

    val roomRegistry = RoomRegistry(identityRepository)

    val meshRouter = MeshRouter(nearbyManager, identityRepository, roomRegistry, appScope)

    val chatRepository = ChatRepository(
        dao = database.messageDao(),
        nearby = nearbyManager,
        identity = identityRepository,
        scope = appScope,
        alerts = MessagesNotifier(appContext) { roomCode, sender ->
            if (roomCode.startsWith("dm:")) sender
            else roomRegistry.rooms.value[roomCode]?.name ?: sender
        },
        groupTargets = { roomCode -> roomRegistry.targetsFor(roomCode) },
    )

    /** Chats requested from outside the UI (notification taps). */
    val pendingChatOpens = MutableSharedFlow<ChatRoute>(extraBufferCapacity = 4)

    @Suppress("unused") // alive for its side effects: expiry sweep + room-death purge
    private val ephemerality = EphemeralityManager(chatRepository, roomRegistry.rooms, appScope)

    @Suppress("unused") // alive for its side effect: the new-peer sonar ping
    private val peerAlerter = PeerAlerter(appContext, nearbyManager.peers, appScope)
}
