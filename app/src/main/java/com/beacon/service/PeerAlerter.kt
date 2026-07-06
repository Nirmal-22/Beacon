package com.beacon.service

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.beacon.model.Peer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * The sonar "ping": a soft tone + haptic tick whenever a new person appears
 * on the mesh. Deliberately quiet — a moment of delight, not an alarm.
 */
class PeerAlerter(
    context: Context,
    peers: StateFlow<Map<String, Peer>>,
    scope: CoroutineScope,
) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private var known = emptySet<String>()

    init {
        peers
            .onEach { current ->
                // Keyed by person (sessionId), not transport endpoint — a radio
                // reconnect of the same person shouldn't ping again.
                val sessions = current.values.map { it.sessionId }.toSet()
                val fresh = sessions - known
                // Only ping for arrivals after the first snapshot, not on app start.
                if (known.isNotEmpty() && fresh.isNotEmpty()) ping()
                known = sessions
            }
            .launchIn(scope)
    }

    private fun ping() {
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, VOLUME)
                .startTone(ToneGenerator.TONE_PROP_BEEP2, TONE_MS)
            vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: RuntimeException) {
            Log.w("PeerAlerter", "ping failed", e)
        }
    }

    private companion object {
        const val VOLUME = 60
        const val TONE_MS = 150
    }
}
