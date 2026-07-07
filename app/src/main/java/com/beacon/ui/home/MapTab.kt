package com.beacon.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Fullscreen map with floating controls (hideable for a clean view).
 *
 * Marker honesty, in three tiers:
 *  - solid pins: people who chose to share their exact position (+ rooms at
 *    the centroid of their sharing members)
 *  - faded pins in a ring around you: people in radio range who do NOT share
 *    — real presence, approximate placement, labeled as such
 *  - your own dot: local-only unless the share toggle is on.
 */
@Composable
fun MapTab(viewModel: HomeViewModel, mapView: MapView) {
    val context = LocalContext.current
    val sharing by viewModel.mapSharing.collectAsStateWithLifecycle()
    val pins by viewModel.mapPins.collectAsStateWithLifecycle()
    val roomPins by viewModel.roomPins.collectAsStateWithLifecycle()
    val myPosition by viewModel.myMapPosition.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    var controlsVisible by rememberSaveable { mutableStateOf(true) }

    fun hasLocationPermission() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> if (result.values.any { it }) viewModel.setMapSharing(true) }

    LaunchedEffect(Unit) { if (hasLocationPermission()) viewModel.startLocalDot() }

    // Peers connected but not sharing. They get NO marker — a pin implies a
    // position, and their toggle is off. They live in the strip instead.
    val notOnMap = peers.filter { it.sessionId !in pins }

    fun allPoints(): List<GeoPoint> = buildList {
        myPosition?.let { (lat, lon) -> add(GeoPoint(lat, lon)) }
        pins.values.forEach { add(GeoPoint(it.lat, it.lon)) }
        roomPins.forEach { (_, p) -> add(GeoPoint(p.first, p.second)) }
    }

    fun fitAll() {
        val points = allPoints()
        when {
            points.isEmpty() -> Unit
            points.size == 1 -> mapView.controller.animateTo(points.first())
            else -> mapView.zoomToBoundingBox(
                BoundingBox.fromGeoPoints(points).increaseByScale(1.5f), true
            )
        }
    }

    val hasAnything = myPosition != null || pins.isNotEmpty() || roomPins.isNotEmpty()
    LaunchedEffect(hasAnything) { if (hasAnything) fitAll() }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                map.overlays.removeAll { it is Marker }
                myPosition?.let { (lat, lon) ->
                    map.overlays.add(
                        Marker(map).apply {
                            position = GeoPoint(lat, lon)
                            title = "You" + if (!sharing) " (only you see this)" else ""
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                    )
                }
                pins.values.forEach { pin ->
                    map.overlays.add(
                        Marker(map).apply {
                            position = GeoPoint(pin.lat, pin.lon)
                            title = pin.name
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                    )
                }
                roomPins.forEach { (room, p) ->
                    map.overlays.add(
                        Marker(map).apply {
                            position = GeoPoint(p.first, p.second)
                            title = "🚪 ${room.name}"
                            snippet = if (room.memberCount == 1) "1 person here"
                            else "${room.memberCount} people here"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                    )
                }
                map.invalidate()
            },
        )

        // Floating controls, hideable for a clean fullscreen map.
        if (controlsVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Card(elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Show me on the map", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (sharing) "People nearby can see your pin"
                                else "Your dot is only visible to you",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = sharing,
                            onCheckedChange = { enable ->
                                when {
                                    !enable -> viewModel.setMapSharing(false)
                                    hasLocationPermission() -> viewModel.setMapSharing(true)
                                    else -> shareLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                        )
                                    )
                                }
                            },
                        )
                    }
                }
                if (notOnMap.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "In range, not on map:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            notOnMap.forEach { peer ->
                                AssistChip(onClick = {}, label = { Text(peer.displayName) })
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SmallFloatingActionButton(onClick = { controlsVisible = !controlsVisible }) {
                Icon(
                    if (controlsVisible) Icons.Default.LayersClear else Icons.Default.Layers,
                    contentDescription = if (controlsVisible) "Hide controls" else "Show controls",
                )
            }
            SmallFloatingActionButton(onClick = { fitAll() }) {
                Icon(Icons.Default.ZoomOutMap, contentDescription = "Fit everything")
            }
        }
    }
}
