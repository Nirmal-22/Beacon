package com.beacon.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beacon.AppContainer
import com.beacon.domain.DmGate
import com.beacon.domain.DmGate.Entry
import com.beacon.domain.IntentTag
import com.beacon.domain.PeerJournal
import com.beacon.model.ChatMessage
import com.beacon.nearby.protocol.BeaconEnvelope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val container: AppContainer,
    private val roomCode: String,
) : ViewModel() {

    private val repo = container.chatRepository

    val messages: StateFlow<List<ChatMessage>> =
        repo.messagesFor(roomCode)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** For DMs: is the other person still connected? Drives the offline banner. */
    val peerOnline: StateFlow<Boolean> = container.nearbyManager.peers
        .map { peers ->
            !roomCode.startsWith("dm:") ||
                peers.values.any { roomCode.contains(it.sessionId) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val isGroupRoom: Boolean = !roomCode.startsWith("dm:")

    /** The other side's sessionId for DMs (dm:<a>:<b>); null for group rooms. */
    private val dmPeerId: String? =
        if (isGroupRoom) null
        else roomCode.split(":").drop(1)
            .firstOrNull { it != container.identityRepository.sessionId }

    /** Icebreaker gate for this DM; group rooms are always open. */
    val gate: StateFlow<DmGate.Entry> = container.dmGate.entries
        .map { entries ->
            if (dmPeerId == null) Entry(DmGate.State.UNLOCKED)
            else entries[dmPeerId] ?: Entry(DmGate.State.LOCKED)
        }
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            Entry(if (isGroupRoom) DmGate.State.UNLOCKED else DmGate.State.LOCKED),
        )

    fun sendIcebreaker(emoji: String) {
        dmPeerId?.let { container.meshRouter.sendIcebreaker(it, emoji) }
    }

    fun replyIcebreaker(accepted: Boolean) {
        dmPeerId?.let { container.meshRouter.replyIcebreaker(it, accepted) }
    }

    fun dmPeerIntent(): IntentTag? =
        dmPeerId?.let { container.intentBoard.peerIntents.value[it] }

    fun dmPeerMeet(): PeerJournal.Meet? =
        dmPeerId?.let { container.peerJournal.meets.value[it] }

    /** Block & report (no backend: they vanish and their envelopes drop). */
    fun blockPeer() {
        dmPeerId?.let {
            container.blockList.block(it)
            repo.markRead(roomCode)
        }
    }

    /** Presence line for group rooms: "3 here — Me, A, B". Null for DMs. */
    val presenceLine: StateFlow<String?> = container.roomRegistry.rooms
        .map { rooms ->
            if (!isGroupRoom) return@map null
            val room = rooms[roomCode] ?: return@map "room ended"
            val names = room.members.values.sorted()
            "${names.size} here — ${names.joinToString(", ")}"
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Member names for the tap-the-title members sheet (group rooms). */
    val members: StateFlow<List<String>> = container.roomRegistry.rooms
        .map { rooms ->
            if (!isGroupRoom) emptyList()
            else rooms[roomCode]?.members?.values?.sorted().orEmpty()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _typingName = MutableStateFlow<String?>(null)
    val typingName: StateFlow<String?> = _typingName.asStateFlow()

    private var typingExpiry: Job? = null
    private var lastTypingSentAt = 0L

    init {
        container.nearbyManager.inbound
            .onEach { (_, envelope) ->
                if (envelope.type == BeaconEnvelope.TYPE_TYPING && envelope.roomCode == roomCode) {
                    _typingName.value = envelope.senderName
                    typingExpiry?.cancel()
                    typingExpiry = viewModelScope.launch {
                        delay(TYPING_VISIBLE_MS)
                        _typingName.value = null
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    /** Chat is on screen: suppress its notifications and clear unread. */
    fun setActive(active: Boolean) {
        repo.activeRoomCode.value = if (active) roomCode else null
        if (active) repo.markRead(roomCode)
    }

    /** Called on every keystroke; throttled to one TYPING signal per interval. */
    fun onInputChanged() {
        val now = System.currentTimeMillis()
        if (now - lastTypingSentAt >= TYPING_SEND_INTERVAL_MS) {
            lastTypingSentAt = now
            repo.sendTyping(roomCode)
        }
    }

    fun send(text: String) {
        viewModelScope.launch { repo.send(roomCode, text) }
    }

    /** Leave a group room; the caller navigates back. */
    fun leaveRoom() {
        if (isGroupRoom) container.meshRouter.leaveRoom(roomCode)
    }

    private companion object {
        const val TYPING_VISIBLE_MS = 4_000L
        const val TYPING_SEND_INTERVAL_MS = 2_000L
    }
}
