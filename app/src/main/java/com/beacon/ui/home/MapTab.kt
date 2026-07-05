package com.beacon.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Opt-in map: pins for everyone nearby who chose to be seen. Viewing needs no
 * permission — only flipping "Show me on the map" asks for location.
 */
@Composable
fun MapTab(viewModel: HomeViewModel) {
    val context = LocalContext.current
    val sharing by viewModel.mapSharing.collectAsStateWithLifecycle()
    val pins by viewModel.mapPins.collectAsStateWithLifecycle()
    val myPosition by viewModel.myMapPosition.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) viewModel.setMapSharing(true)
    }

    fun toggleSharing(enable: Boolean) {
        if (!enable) {
            viewModel.setMapSharing(false)
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.setMapSharing(true)
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

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
                        else "You're not on anyone's map",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = sharing, onCheckedChange = { toggleSharing(it) })
            }
        }

        if (pins.isEmpty() && !sharing) {
            Text(
                "Nobody is sharing their spot right now. Pins appear here when " +
                    "someone nearby turns theirs on — GPS is rough indoors.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
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
        // Center once on the first known position (mine, else first pin).
        val firstCenter = myPosition ?: pins.values.firstOrNull()?.let { it.lat to it.lon }
        LaunchedEffect(firstCenter != null) {
            firstCenter?.let { (lat, lon) -> mapView.controller.setCenter(GeoPoint(lat, lon)) }
        }

        AndroidView(
            factory = { mapView },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            update = { map ->
                map.overlays.removeAll { it is Marker }
                myPosition?.let { (lat, lon) ->
                    map.overlays.add(
                        Marker(map).apply {
                            position = GeoPoint(lat, lon)
                            title = "You"
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
                map.invalidate()
            },
        )
    }
}
