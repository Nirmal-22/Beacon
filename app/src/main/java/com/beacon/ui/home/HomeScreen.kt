package com.beacon.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beacon.domain.HelpBoard
import com.beacon.domain.HelpCategory
import com.beacon.domain.IntentTag
import com.beacon.model.Peer
import com.beacon.model.RoomInfo
import com.beacon.nearby.NearbyManager
import com.beacon.service.BeaconService
import kotlinx.coroutines.delay

private enum class HomeTab { PEOPLE, ROOMS, HELP }

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
    val myRooms by viewModel.myRooms.collectAsStateWithLifecycle()
    val nearbyRooms by viewModel.nearbyRooms.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(HomeTab.PEOPLE) }
    var createRoomOpen by remember { mutableStateOf(false) }
    var postHelpOpen by remember { mutableStateOf(false) }
    val helpPosts by viewModel.helpPosts.collectAsStateWithLifecycle()
    val blockedCount by viewModel.blockedCount.collectAsStateWithLifecycle()
    val radarOn = status == NearbyManager.Status.ACTIVE

    val dmUnread = unread.filterKeys { it.startsWith("dm:") }.values.sum()
    val roomUnread = unread.filterKeys { !it.startsWith("dm:") }.values.sum()

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
                    IconButton(onClick = { menuOpen = true }) {
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
                        if (blockedCount > 0) {
                            DropdownMenuItem(
                                text = { Text("Unblock all ($blockedCount)") },
                                leadingIcon = {
                                    Icon(Icons.Default.Block, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    viewModel.unblockAll()
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == HomeTab.PEOPLE,
                    onClick = { tab = HomeTab.PEOPLE },
                    icon = {
                        CountBadge(dmUnread) { Icon(Icons.Default.Person, contentDescription = null) }
                    },
                    label = { Text("People") },
                )
                NavigationBarItem(
                    selected = tab == HomeTab.ROOMS,
                    onClick = { tab = HomeTab.ROOMS },
                    icon = {
                        CountBadge(roomUnread) { Icon(Icons.Default.Groups, contentDescription = null) }
                    },
                    label = { Text("Rooms") },
                )
                NavigationBarItem(
                    selected = tab == HomeTab.HELP,
                    onClick = { tab = HomeTab.HELP },
                    icon = {
                        CountBadge(helpPosts.count { it.posterId != viewModel.mySessionId }) {
                            Icon(Icons.Default.Campaign, contentDescription = null)
                        }
                    },
                    label = { Text("Help") },
                )
            }
        },
        floatingActionButton = {
            when {
                !radarOn -> Unit
                tab == HomeTab.ROOMS -> FloatingActionButton(onClick = { createRoomOpen = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Create room")
                }
                tab == HomeTab.HELP -> FloatingActionButton(onClick = { postHelpOpen = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Ask for help")
                }
                else -> Unit
            }
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
                tab == HomeTab.PEOPLE -> PeopleTab(peers, unread, viewModel, onOpenChat)
                tab == HomeTab.ROOMS -> RoomsTab(myRooms, nearbyRooms, unread, viewModel, onOpenChat)
                else -> HelpTab(helpPosts, viewModel, onOpenChat)
            }
        }
    }

    if (createRoomOpen) {
        CreateRoomDialog(
            onDismiss = { createRoomOpen = false },
            onCreate = { name ->
                createRoomOpen = false
                val code = viewModel.joinRoom(name)
                onOpenChat(code, name.trim())
            },
        )
    }
    if (postHelpOpen) {
        PostHelpDialog(
            onDismiss = { postHelpOpen = false },
            onPost = { category, text, ttl ->
                postHelpOpen = false
                viewModel.postHelp(category, text, ttl)
            },
        )
    }
}

@Composable
private fun CountBadge(count: Int, content: @Composable () -> Unit) {
    if (count > 0) {
        BadgedBox(badge = { Badge { Text("$count") } }) { content() }
    } else {
        content()
    }
}

@Composable
private fun PeopleTab(
    peers: List<Peer>,
    unread: Map<String, Int>,
    viewModel: HomeViewModel,
    onOpenChat: (String, String) -> Unit,
) {
    val myIntent by viewModel.myIntent.collectAsStateWithLifecycle()
    val peerIntents by viewModel.peerIntents.collectAsStateWithLifecycle()

    IntentChipRow(myIntent) { viewModel.setIntent(it) }

    if (peers.isEmpty()) {
        ScanningState()
        return
    }
    // Matching intents float to the top — that's the discovery feature.
    val sorted = peers.sortedByDescending {
        myIntent != null && peerIntents[it.sessionId] == myIntent
    }
    Text(
        text = "Nearby",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    LazyColumn {
        items(sorted, key = { it.endpointId }) { peer ->
            val dm = viewModel.dmRoomCodeFor(peer)
            val intent = peerIntents[peer.sessionId]
            val matches = myIntent != null && intent == myIntent
            Card(
                onClick = { onOpenChat(dm, peer.displayName) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                ListItem(
                    leadingContent = {
                        if (intent != null) {
                            Text(intent.emoji, style = MaterialTheme.typography.headlineSmall)
                        } else {
                            Icon(Icons.Default.Person, contentDescription = null)
                        }
                    },
                    headlineContent = { Text(peer.displayName) },
                    supportingContent = {
                        Text(
                            when {
                                matches -> "Also here for ${intent!!.label} — say hi!"
                                intent != null -> "Here for ${intent.label}"
                                else -> "Tap to chat"
                            },
                            color = if (matches) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        unread[dm]?.takeIf { it > 0 }?.let { Badge { Text("$it") } }
                    },
                )
            }
        }
    }
}

@Composable
private fun IntentChipRow(myIntent: IntentTag?, onSelect: (IntentTag?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("I'm here for:", style = MaterialTheme.typography.labelMedium)
        IntentTag.entries.forEach { tag ->
            FilterChip(
                selected = myIntent == tag,
                onClick = { onSelect(if (myIntent == tag) null else tag) },
                label = { Text("${tag.emoji} ${tag.label}") },
            )
        }
    }
}

@Composable
private fun RoomsTab(
    myRooms: List<RoomInfo>,
    nearbyRooms: List<RoomInfo>,
    unread: Map<String, Int>,
    viewModel: HomeViewModel,
    onOpenChat: (String, String) -> Unit,
) {
    if (myRooms.isEmpty() && nearbyRooms.isEmpty()) {
        RoomsEmptyState()
        return
    }
    LazyColumn {
        if (myRooms.isNotEmpty()) {
            item { SectionHeader("My rooms") }
            items(myRooms, key = { "my-" + it.code }) { room ->
                RoomRow(
                    room = room,
                    unreadCount = unread.getOrDefault(room.code, 0),
                    actionIcon = null,
                ) { onOpenChat(room.code, room.name) }
            }
        }
        if (nearbyRooms.isNotEmpty()) {
            item { SectionHeader("Nearby rooms") }
            items(nearbyRooms, key = { "near-" + it.code }) { room ->
                RoomRow(room = room, unreadCount = 0, actionIcon = Icons.AutoMirrored.Filled.Login) {
                    viewModel.joinRoom(room.name)
                    onOpenChat(room.code, room.name)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun RoomRow(
    room: RoomInfo,
    unreadCount: Int,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        ListItem(
            leadingContent = { Icon(Icons.Default.Groups, contentDescription = null) },
            headlineContent = { Text(room.name) },
            supportingContent = {
                Text(
                    if (room.memberCount == 1) "1 person here" else "${room.memberCount} people here"
                )
            },
            trailingContent = {
                when {
                    unreadCount > 0 -> Badge { Text("$unreadCount") }
                    actionIcon != null -> Icon(actionIcon, contentDescription = "Join")
                }
            },
        )
    }
}

@Composable
private fun HelpTab(
    posts: List<HelpBoard.HelpPost>,
    viewModel: HomeViewModel,
    onOpenChat: (String, String) -> Unit,
) {
    // Re-render countdowns every 30 s.
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowTick = System.currentTimeMillis()
        }
    }

    if (posts.isEmpty()) {
        HelpEmptyState()
        return
    }
    LazyColumn {
        items(posts, key = { it.id }) { post ->
            val mine = post.posterId == viewModel.mySessionId
            val minutesLeft = ((post.expiresAt - nowTick) / 60_000L).coerceAtLeast(0)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                ListItem(
                    leadingContent = {
                        Text(post.category.emoji, style = MaterialTheme.typography.headlineSmall)
                    },
                    headlineContent = { Text(post.text) },
                    supportingContent = {
                        Text(
                            (if (mine) "Your request" else post.posterName) +
                                " · expires in ${minutesLeft}m"
                        )
                    },
                    trailingContent = {
                        if (mine) {
                            TextButton(onClick = { viewModel.cancelHelp(post.id) }) {
                                Text("Cancel")
                            }
                        } else {
                            Button(onClick = {
                                val dm = viewModel.respondToHelp(post)
                                onOpenChat(dm, post.posterName)
                            }) {
                                Text("I can help")
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PostHelpDialog(
    onDismiss: () -> Unit,
    onPost: (HelpCategory, String, Int) -> Unit,
) {
    var category by remember { mutableStateOf(HelpCategory.CHARGER) }
    var text by remember { mutableStateOf("") }
    var ttl by remember { mutableStateOf(15f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ask people nearby") },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HelpCategory.entries.forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text("${c.emoji} ${c.label}") },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 120) text = it },
                    label = { Text("What do you need?") },
                    placeholder = { Text("USB-C charger for 20 minutes?") },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Disappears after ${ttl.toInt()} minutes",
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(value = ttl, onValueChange = { ttl = it }, valueRange = 10f..30f)
            }
        },
        confirmButton = {
            Button(
                onClick = { onPost(category, text, ttl.toInt()) },
                enabled = text.isNotBlank(),
            ) { Text("Post") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun HelpEmptyState() {
    CenteredState {
        Icon(
            Icons.Default.Campaign,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("Nobody needs help right now", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Need a charger, someone to watch your bag, one more player? " +
                "Post with + — only people within range see it, and it expires on its own.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CreateRoomDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create or join a room") },
        text = {
            Column {
                Text(
                    "Anyone nearby who enters the same name lands in the same room. " +
                        "The room disappears when the last person leaves.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 48) name = it },
                    label = { Text("Room name") },
                    placeholder = { Text("Library — Silent Floor") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Join") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
private fun CenteredState(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun ScanningState() {
    CenteredState {
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

@Composable
private fun RoomsEmptyState() {
    CenteredState {
        Icon(
            Icons.Default.Groups,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("No rooms yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Create one with + and tell people around you its name — " +
                "\"Train 12628 Coach B2\", \"Library Silent Floor\"…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RadarOffState(onTurnOn: () -> Unit) {
    CenteredState {
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
