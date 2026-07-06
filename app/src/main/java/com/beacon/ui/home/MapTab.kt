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
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Opt-in map. Three layers of honesty:
 *  - your dot: local-only, never broadcast, appears once location permission exists
 *  - people pins: only those who flipped "show me" — being seen is a choice
 *  - rooms: pinned at the centroid of members who share; a strip lists
 *    everyone in radio range who is NOT on the map, so it never feels empty.
 */
@Composable
fun MapTab(viewModel: HomeViewModel) {
    val context = LocalContext.current
    val sharing by viewModel.mapSharing.collectAsStateWithLifecycle()
    val pins by viewModel.mapPins.collectAsStateWithLifecycle()
    val roomPins by viewModel.roomPins.collectAsStateWithLifecycle()
    val myPosition by viewModel.myMapPosition.collectAsStateWithLifecycle()
    val notOnMap by viewModel.inRangeNotOnMap.collectAsStateWithLifecycle()

    fun hasLocationPermission() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> if (result.values.any { it }) viewModel.setMapSharing(true) }

    // Local-only dot: starts automatically when permission already exists.
    LaunchedEffect(Unit) { if (hasLocationPermission()) viewModel.startLocalDot() }
    DisposableEffect(Unit) { onDispose { viewModel.stopLocalDotIfNotSharing() } }

    Column(Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Show me on the map", style = MaterialTheme.typography.bodyLarge)
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "In range, not on map:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                notOnMap.forEach { name ->
                    AssistChip(onClick = {}, label = { Text(name) })
                }
            }
        }

        val mapView = remember {
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(17.0)
            }
        }
        DisposableEffect(Unit) {
            mapView.onResume()
            onDispose {
                mapView.onPause()
                mapView.onDetach()
            }
        }

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

        // Center once on the first thing that gets a position.
        val hasAnything = myPosition != null || pins.isNotEmpty() || roomPins.isNotEmpty()
        LaunchedEffect(hasAnything) { if (hasAnything) fitAll() }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
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
            SmallFloatingActionButton(
                onClick = { fitAll() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(Icons.Default.ZoomOutMap, contentDescription = "Fit everything")
            }
        }
    }
}
