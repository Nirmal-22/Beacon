package com.beacon.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Opt-in map presence. Nobody appears here unless they broadcast a location;
 * an empty LOCATION envelope (or [PIN_MAX_AGE_MS] of silence) removes the
 * pin. My own sharing flag intentionally resets every session — appearing on
 * a map should never be a stale, forgotten default. Pure Kotlin.
 */
class LocationBoard(private val now: () -> Long = System::currentTimeMillis) {

    data class Pin(val name: String, val lat: Double, val lon: Double, val updatedAt: Long)

    private val _sharing = MutableStateFlow(false)
    val sharing: StateFlow<Boolean> = _sharing.asStateFlow()

    private val _myPosition = MutableStateFlow<Pair<Double, Double>?>(null)
    val myPosition: StateFlow<Pair<Double, Double>?> = _myPosition.asStateFlow()

    /** Peers who chose to be seen, keyed by sessionId. */
    private val _pins = MutableStateFlow<Map<String, Pin>>(emptyMap())
    val pins: StateFlow<Map<String, Pin>> = _pins.asStateFlow()

    fun setSharing(enabled: Boolean) {
        _sharing.value = enabled
    }

    /**
     * Always stored: [myPosition] powers the local-only "you are here" dot.
     * It leaves the device solely through [myLocationBody], which is gated
     * on [sharing].
     */
    fun onMyLocation(lat: Double, lon: Double) {
        _myPosition.value = lat to lon
    }

    /** @return my "lat,lon" body for (re)broadcast, or null when not sharing. */
    fun myLocationBody(): String? =
        if (_sharing.value) _myPosition.value?.let { (lat, lon) -> "$lat,$lon" } else null

    fun onRemoteLocation(sessionId: String, senderName: String, body: String?) {
        val parsed = parse(body)
        _pins.value =
            if (parsed == null) _pins.value - sessionId
            else _pins.value + (sessionId to Pin(senderName, parsed.first, parsed.second, now()))
    }

    /** Stale pins (peer walked away, radio died) age out without a goodbye. */
    fun prune() {
        val cutoff = now() - PIN_MAX_AGE_MS
        if (_pins.value.any { it.value.updatedAt < cutoff }) {
            _pins.value = _pins.value.filterValues { it.updatedAt >= cutoff }
        }
    }

    private fun parse(body: String?): Pair<Double, Double>? {
        if (body.isNullOrBlank()) return null
        val parts = body.split(",")
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return lat to lon
    }

    companion object {
        const val PIN_MAX_AGE_MS = 3L * 60 * 1000
    }
}
