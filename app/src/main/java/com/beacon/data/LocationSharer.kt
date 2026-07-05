package com.beacon.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log

/**
 * Feeds GPS fixes to the mesh while (and only while) the user opts into the
 * map. Plain LocationManager — no extra Play Services dependency needed for
 * a coarse "where roughly am I" pin.
 *
 * Callers must hold ACCESS_FINE_LOCATION before calling [start]; the map UI
 * requests it as part of flipping the toggle.
 */
class LocationSharer(
    context: Context,
    private val onLocation: (lat: Double, lon: Double) -> Unit,
) {

    private val manager = context.getSystemService(LocationManager::class.java)
    private var listener: LocationListener? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (listener != null) return
        val provider = when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> {
                Log.w(TAG, "no location provider enabled")
                return
            }
        }
        val l = LocationListener { location -> onLocation(location.latitude, location.longitude) }
        listener = l
        manager.requestLocationUpdates(provider, UPDATE_INTERVAL_MS, UPDATE_MIN_METERS, l, Looper.getMainLooper())
        // Immediate first pin instead of waiting for the first fresh fix.
        manager.getLastKnownLocation(provider)?.let { onLocation(it.latitude, it.longitude) }
    }

    fun stop() {
        listener?.let(manager::removeUpdates)
        listener = null
    }

    private companion object {
        const val TAG = "LocationSharer"
        const val UPDATE_INTERVAL_MS = 15_000L
        const val UPDATE_MIN_METERS = 10f
    }
}
