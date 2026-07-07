package com.beacon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Session-stable identity color + initials. Ephemeral identities have no
 * photos; a consistent color per person makes lists and chats readable at a
 * glance ("the teal one is agustine").
 */
private val AvatarPalette = listOf(
    Color(0xFFE57373), Color(0xFFBA68C8), Color(0xFF7986CB), Color(0xFF4FC3F7),
    Color(0xFF4DB6AC), Color(0xFF81C784), Color(0xFFFFB74D), Color(0xFFA1887F),
    Color(0xFF90A4AE), Color(0xFFF06292),
)

fun avatarColor(seed: String): Color =
    AvatarPalette[Math.floorMod(seed.hashCode(), AvatarPalette.size)]

fun initialsOf(name: String): String {
    val words = name.split(" ", "-").filter { it.isNotBlank() && it.first().isLetterOrDigit() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> "${words[0].first()}${words[1].first()}".uppercase()
    }
}

@Composable
fun Avatar(name: String, seed: String, size: Dp = 40.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(avatarColor(seed), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initialsOf(name),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
    }
}
