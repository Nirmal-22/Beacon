package com.beacon.ui.onboarding

import android.Manifest
import android.os.Build

/**
 * The runtime permissions Nearby Connections needs, by API level. Mirrors the
 * version-gated declarations in AndroidManifest.xml — keep the two in sync.
 */
object PermissionSteps {

    fun nearbyPermissions(sdk: Int = Build.VERSION.SDK_INT): List<String> = buildList {
        if (sdk >= 31) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        when {
            // 33+: NEARBY_WIFI_DEVICES replaces location entirely (neverForLocation).
            sdk >= 33 -> add(Manifest.permission.NEARBY_WIFI_DEVICES)
            // 29-32: Nearby needs fine location, which must be requested with coarse.
            sdk >= 29 -> {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            else -> add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    fun notificationPermission(sdk: Int = Build.VERSION.SDK_INT): String? =
        if (sdk >= 33) Manifest.permission.POST_NOTIFICATIONS else null
}
