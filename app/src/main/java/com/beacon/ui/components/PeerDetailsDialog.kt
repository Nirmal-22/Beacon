package com.beacon.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.beacon.domain.IntentTag
import com.beacon.domain.PeerJournal
import java.text.DateFormat
import java.util.Date

/** "View details" for a person: who, why they're here, how you met. */
@Composable
fun PeerDetailsDialog(
    name: String,
    intent: IntentTag?,
    meet: PeerJournal.Meet?,
    onDismiss: () -> Unit,
    onBlock: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Column {
                DetailLine(
                    label = "Here for",
                    value = intent?.let { "${it.emoji} ${it.label}" } ?: "No intent shared",
                )
                Spacer(Modifier.height(8.dp))
                DetailLine(label = "How you met", value = meet?.origin ?: "Discovered nearby")
                Spacer(Modifier.height(8.dp))
                DetailLine(
                    label = "First seen",
                    value = meet?.let {
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it.firstSeenAt))
                    } ?: "This session",
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Identities are ephemeral — everything here resets when they restart Beacon.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = onBlock?.let {
            {
                TextButton(onClick = {
                    onDismiss()
                    it()
                }) {
                    Text("Block & report", color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
