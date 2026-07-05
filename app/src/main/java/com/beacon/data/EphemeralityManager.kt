package com.beacon.data

import com.beacon.model.RoomInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Enforces "ephemeral by default":
 *  - chat history older than [retentionMs] is swept on start and periodically;
 *  - when a group room dies (drops out of the registry — last member gone),
 *    its messages and unread state are purged immediately.
 *
 * Session identity is already ephemeral (new sessionId every process), so
 * this class completes the story for stored data.
 */
class EphemeralityManager(
    private val chat: ChatRepository,
    rooms: StateFlow<Map<String, RoomInfo>>,
    scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val retentionMs: Long = DEFAULT_RETENTION_MS,
    private val sweepIntervalMs: Long = DEFAULT_SWEEP_INTERVAL_MS,
) {

    private var aliveGroupRooms = emptySet<String>()

    init {
        scope.launch {
            while (true) {
                chat.sweepOlderThan(now() - retentionMs)
                delay(sweepIntervalMs)
            }
        }
        rooms
            .onEach { current ->
                val alive = current.keys.filterNot { it.startsWith("dm:") }.toSet()
                val died = aliveGroupRooms - alive
                aliveGroupRooms = alive
                died.forEach { code ->
                    chat.deleteRoom(code)
                    chat.markRead(code) // clears unread count + cancels notification
                }
            }
            .launchIn(scope)
    }

    companion object {
        const val DEFAULT_RETENTION_MS = 24L * 60 * 60 * 1000
        const val DEFAULT_SWEEP_INTERVAL_MS = 15L * 60 * 1000
    }
}
