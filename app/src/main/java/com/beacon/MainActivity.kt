package com.beacon

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.beacon.ui.navigation.BeaconNavHost
import com.beacon.ui.navigation.ChatRoute
import com.beacon.ui.theme.BeaconTheme

class MainActivity : ComponentActivity() {

    private val container get() = (application as BeaconApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BeaconTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BeaconNavHost(container)
                }
            }
        }
        consumeChatIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeChatIntent(intent)
    }

    /** Notification taps carry the chat to open. */
    private fun consumeChatIntent(intent: Intent?) {
        val roomCode = intent?.getStringExtra(EXTRA_ROOM_CODE) ?: return
        val title = intent.getStringExtra(EXTRA_ROOM_TITLE) ?: "Chat"
        intent.removeExtra(EXTRA_ROOM_CODE)
        container.pendingChatOpens.tryEmit(ChatRoute(roomCode, title))
    }

    companion object {
        const val EXTRA_ROOM_CODE = "com.beacon.extra.ROOM_CODE"
        const val EXTRA_ROOM_TITLE = "com.beacon.extra.ROOM_TITLE"
    }
}
