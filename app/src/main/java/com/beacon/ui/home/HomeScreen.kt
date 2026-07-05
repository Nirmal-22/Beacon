package com.beacon.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beacon.model.Peer
import com.beacon.nearby.NearbyManager
import com.beacon.service.BeaconService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenChat: (roomCode: String, title: String) -> Unit,
) {
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val anonymous by viewModel.anonymous.collectAsStateWithLifecycle()
    val unread by viewModel.unreadCounts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val radarOn = status == NearbyManager.Status.ACTIVE

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Beacon") },
                actions = {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = null,
                        tint = if (radarOn) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    androidx.compose.material3.IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (radarOn) "Turn radar off" else "Turn radar on") },
                            leadingIcon = {
                                Icon(
                                    if (radarOn) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                if (radarOn) BeaconService.stop(context)
                                else BeaconService.start(context)
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Anonymous mode")
                                    Spacer(Modifier.size(8.dp))
                                    Switch(checked = anonymous, onCheckedChange = null)
                                }
                            },
                            leadingIcon = {
                                Icon(Icons.Default.VisibilityOff, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                viewModel.setAnonymous(!anonymous)
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (anonymous && radarOn) {
                InfoBanner("You appear as ${viewModel.anonymousHandle}")
            }
            when {
                !radarOn -> RadarOffState { BeaconService.start(context) }
                peers.isEmpty() -> ScanningState()
                else -> {
                    Text(
                        text = "Nearby",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    LazyColumn {
                        items(peers, key = { it.endpointId }) { peer ->
                            PeerRow(
                                peer = peer,
                                unreadCount = unread.getOrDefault(
                                    viewModel.dmRoomCodeFor(peer), 0
                                ),
                            ) {
                                onOpenChat(viewModel.dmRoomCodeFor(peer), peer.displayName)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoBanner(text: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun PeerRow(peer: Peer, unreadCount: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        ListItem(
            leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
            headlineContent = { Text(peer.displayName) },
            supportingContent = { Text("Tap to chat") },
            trailingContent = {
                if (unreadCount > 0) {
                    Badge { Text("$unreadCount") }
                }
            },
        )
    }
}

/** Expanding, fading sonar rings around a radar icon. */
@Composable
private fun SonarPulse(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "sonar")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sonarProgress",
    )
    val color = MaterialTheme.colorScheme.primary
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val maxRadius = size.minDimension / 2f
            // Three rings offset by a third of the cycle each.
            for (i in 0..2) {
                val p = (progress + i / 3f) % 1f
                drawCircle(
                    color = color.copy(alpha = (1f - p) * 0.35f),
                    radius = maxRadius * p,
                )
            }
        }
        Icon(
            Icons.Default.Radar,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(36.dp),
        )
    }
}

@Composable
private fun ScanningState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SonarPulse(Modifier.size(160.dp))
            Spacer(Modifier.height(24.dp))
            Text("Scanning for people nearby…", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Keep Beacon open on another phone within a few meters — " +
                    "they'll appear here automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RadarOffState(onTurnOn: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.VisibilityOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text("Radar is off", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "You're invisible and can't see anyone. Nothing is shared while off.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onTurnOn) { Text("Turn radar on") }
        }
    }
}
