package com.beacon.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.Color
import com.beacon.domain.DmGate
import com.beacon.model.ChatMessage
import com.beacon.ui.components.PeerDetailsDialog
import com.beacon.ui.components.avatarColor
import java.text.DateFormat
import java.util.Date

/** Fixed so "read" looks the same on every phone regardless of Material You. */
private val ReadBlue = Color(0xFF34B7F1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    title: String,
    onBack: () -> Unit,
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val peerOnline by viewModel.peerOnline.collectAsStateWithLifecycle()
    val typingName by viewModel.typingName.collectAsStateWithLifecycle()
    val presenceLine by viewModel.presenceLine.collectAsStateWithLifecycle()
    val gate by viewModel.gate.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var chatMenuOpen by remember { mutableStateOf(false) }
    var blockConfirmOpen by remember { mutableStateOf(false) }
    var detailsOpen by remember { mutableStateOf(false) }
    var membersOpen by remember { mutableStateOf(false) }
    val members by viewModel.members.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    if (membersOpen) {
        AlertDialog(
            onDismissRequest = { membersOpen = false },
            title = { Text(if (members.size == 1) "1 person here" else "${members.size} people here") },
            text = {
                Column {
                    members.forEach { name ->
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (members.isEmpty()) {
                        Text(
                            "Nobody here anymore — the room ends when the last person leaves.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { membersOpen = false }) { Text("Close") } },
        )
    }

    if (detailsOpen) {
        PeerDetailsDialog(
            name = title,
            intent = viewModel.dmPeerIntent(),
            meet = viewModel.dmPeerMeet(),
            onDismiss = { detailsOpen = false },
            onBlock = { blockConfirmOpen = true },
        )
    }

    if (blockConfirmOpen) {
        AlertDialog(
            onDismissRequest = { blockConfirmOpen = false },
            title = { Text("Block $title?") },
            text = {
                Text(
                    "They disappear from your lists and can't message you. " +
                        "Blocking also reports them on this device."
                )
            },
            confirmButton = {
                Button(onClick = {
                    blockConfirmOpen = false
                    viewModel.blockPeer()
                    onBack()
                }) { Text("Block") }
            },
            dismissButton = {
                TextButton(onClick = { blockConfirmOpen = false }) { Text("Cancel") }
            },
        )
    }

    // While this chat is visible its messages don't notify; unread clears.
    LifecycleResumeEffect(Unit) {
        viewModel.setActive(true)
        onPauseOrDispose { viewModel.setActive(false) }
    }

    // Reversed list: index 0 is the newest message, pinned to the bottom.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Group rooms: tapping the title shows who's here.
                    Column(
                        modifier = if (viewModel.isGroupRoom) {
                            Modifier.clickable { membersOpen = true }
                        } else Modifier,
                    ) {
                        Text(title)
                        val subtitle = when {
                            typingName != null ->
                                if (viewModel.isGroupRoom) "$typingName is typing…" else "typing…"
                            viewModel.isGroupRoom -> presenceLine
                            !peerOnline -> "out of range"
                            else -> null
                        }
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                color = if (typingName != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (viewModel.isGroupRoom) {
                        IconButton(onClick = {
                            viewModel.leaveRoom()
                            onBack()
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Leave room",
                            )
                        }
                    } else {
                        IconButton(onClick = { chatMenuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = chatMenuOpen,
                            onDismissRequest = { chatMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("View details") },
                                leadingIcon = {
                                    Icon(Icons.Default.Info, contentDescription = null)
                                },
                                onClick = {
                                    chatMenuOpen = false
                                    detailsOpen = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Say thanks 🙏") },
                                onClick = {
                                    chatMenuOpen = false
                                    viewModel.sayThanks()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Block & report") },
                                leadingIcon = {
                                    Icon(Icons.Default.Block, contentDescription = null)
                                },
                                onClick = {
                                    chatMenuOpen = false
                                    blockConfirmOpen = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            val listItems = remember(messages) { buildChatItems(messages) }
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(listItems.asReversed(), key = { it.key }) { item ->
                    when (item) {
                        is ChatItem.Day -> DaySeparator(item.label)
                        is ChatItem.Msg -> MessageBubble(item.message)
                    }
                }
            }
            when (gate.state) {
                DmGate.State.UNLOCKED -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            if (it.isNotBlank()) viewModel.onInputChanged()
                        },
                        placeholder = { Text("Message") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                    )
                    val haptics = LocalHapticFeedback.current
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.send(input)
                            input = ""
                        },
                        enabled = input.isNotBlank(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }

                DmGate.State.LOCKED -> GatePanel("Break the ice — one tap, they accept, chat opens:") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf("👍", "☕", "🎮", "📍").forEach { emoji ->
                            TextButton(onClick = { viewModel.sendIcebreaker(emoji) }) {
                                Text(emoji, style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }
                }

                DmGate.State.SENT -> GatePanel(
                    "Icebreaker ${gate.emoji.orEmpty()} sent — waiting for $title to accept…"
                ) {}

                DmGate.State.RECEIVED -> GatePanel(
                    "$title wants to chat ${gate.emoji.orEmpty()}"
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { viewModel.replyIcebreaker(true) }) { Text("Accept") }
                        TextButton(onClick = {
                            viewModel.replyIcebreaker(false)
                            onBack()
                        }) { Text("Decline") }
                    }
                }
            }
        }
    }
}

@Composable
private fun GatePanel(text: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

/** Messages interleaved with day separators; keys stay unique for LazyColumn. */
private sealed class ChatItem(val key: String) {
    class Msg(val message: ChatMessage) : ChatItem(message.msgId)
    class Day(val label: String) : ChatItem("day-$label")
}

private fun buildChatItems(messages: List<ChatMessage>): List<ChatItem> {
    val format = DateFormat.getDateInstance(DateFormat.MEDIUM)
    val items = mutableListOf<ChatItem>()
    var lastDay: String? = null
    messages.forEach { message ->
        val day = format.format(Date(message.timestamp))
        if (day != lastDay) {
            items += ChatItem.Day(day)
            lastDay = day
        }
        items += ChatItem.Msg(message)
    }
    return items
}

@Composable
private fun DaySeparator(label: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(message: ChatMessage) {
    val align = if (message.isMine) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor =
        if (message.isMine) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = align,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = bubbleColor,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        clipboard.setText(AnnotatedString(message.text))
                    },
                ),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!message.isMine) {
                    Text(
                        message.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        // Session-stable identity color, matching their avatar.
                        color = avatarColor(message.senderId),
                    )
                }
                Text(message.text, style = MaterialTheme.typography.bodyLarge)
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        DateFormat.getTimeInstance(DateFormat.SHORT)
                            .format(Date(message.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (message.isMine) {
                        // WhatsApp convention: ✓ sent, ✓✓ delivered, blue ✓✓ read.
                        // ReadBlue is a fixed color on purpose — monochrome system
                        // palettes (Nothing OS) would otherwise hide the read state.
                        Text(
                            if (message.delivered || message.readByPeer) " ✓✓" else " ✓",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (message.readByPeer) {
                                ReadBlue
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}
