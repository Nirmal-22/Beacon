package com.beacon.ui.chat

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
    val listState = rememberLazyListState()

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
                    Column {
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
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(messages.asReversed(), key = { it.msgId }) { message ->
                    MessageBubble(message)
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
                    IconButton(
                        onClick = {
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

@Composable
private fun MessageBubble(message: ChatMessage) {
    val align = if (message.isMine) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor =
        if (message.isMine) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = align,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!message.isMine) {
                    Text(
                        message.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
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
