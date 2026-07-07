package com.beacon

import android.content.Context
import com.beacon.data.BlockList
import com.beacon.data.ChatRepository
import com.beacon.data.EphemeralityManager
import com.beacon.data.IdentityRepository
import com.beacon.data.LocationSharer
import com.beacon.data.MeshRouter
import com.beacon.data.SocialAlerts
import com.beacon.data.ThanksLedger
import com.beacon.data.db.BeaconDatabase
import com.beacon.domain.DmGate
import com.beacon.domain.HelpBoard
import com.beacon.domain.IntentBoard
import com.beacon.domain.LocationBoard
import com.beacon.domain.PeerJournal
import com.beacon.domain.RoomRegistry
import com.beacon.model.Peer
import com.beacon.nearby.NearbyManager
import com.beacon.service.MessagesNotifier
import com.beacon.service.PeerAlerter
import com.beacon.ui.navigation.ChatRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

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

    val intentBoard = IntentBoard()

    val dmGate = DmGate()

    val helpBoard = HelpBoard(identityRepository)

    val blockList = BlockList(appContext)

    val locationBoard = LocationBoard()

    val peerJournal = PeerJournal()

    val thanksLedger = ThanksLedger(appContext)

    private val messagesNotifier = MessagesNotifier(appContext) { roomCode, sender ->
        if (roomCode.startsWith("dm:")) sender
        else roomRegistry.rooms.value[roomCode]?.name ?: sender
    }

    val meshRouter = MeshRouter(
        nearby = nearbyManager,
        identity = identityRepository,
        rooms = roomRegistry,
        intents = intentBoard,
        gate = dmGate,
        help = helpBoard,
        locations = locationBoard,
        journal = peerJournal,
        thanks = thanksLedger,
        scope = appScope,
        alerts = object : SocialAlerts {
            override fun onIcebreaker(peer: Peer, emoji: String, dmRoomCode: String) {
                messagesNotifier.onIcebreaker(peer.displayName, emoji, dmRoomCode)
            }

            override fun onHelpPost(post: HelpBoard.HelpPost) {
                messagesNotifier.onHelpPost(post)
            }

            override fun onThanks(senderName: String) {
                messagesNotifier.onThanks(senderName)
            }
        },
        isBlocked = blockList::isBlocked,
    )

    val chatRepository = ChatRepository(
        dao = database.messageDao(),
        nearby = nearbyManager,
        identity = identityRepository,
        scope = appScope,
        alerts = messagesNotifier,
        groupTargets = { roomCode -> roomRegistry.targetsFor(roomCode) },
        isBlocked = blockList::isBlocked,
    )

    /** Chats requested from outside the UI (notification taps). */
    val pendingChatOpens = MutableSharedFlow<ChatRoute>(extraBufferCapacity = 4)

    @Suppress("unused") // alive for its side effects: expiry sweep + room-death purge
    private val ephemerality = EphemeralityManager(chatRepository, roomRegistry.rooms, appScope)

    /** GPS feed, alive only while the user shares their map position. */
    val locationSharer = LocationSharer(appContext) { lat, lon ->
        meshRouter.broadcastMyLocation(lat, lon)
    }

    init {
        // Help requests and map pins expire locally — no server does it for us.
        appScope.launch {
            while (true) {
                helpBoard.prune()
                locationBoard.prune()
                delay(30_000)
            }
        }
    }

    @Suppress("unused") // alive for its side effect: the new-peer sonar ping
    private val peerAlerter = PeerAlerter(appContext, nearbyManager.peers, appScope)
}
