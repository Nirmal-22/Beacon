package com.beacon.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.beacon.MainActivity
import com.beacon.R
import com.beacon.data.MessageAlerts
import com.beacon.domain.HelpBoard

/**
 * Heads-up notifications for chat messages arriving outside the open chat.
 * One notification per room (id = roomCode hash); tapping it deep-opens that
 * chat via MainActivity intent extras.
 */
class MessagesNotifier(
    private val context: Context,
    /** Room code + sender -> notification title (room name for group rooms). */
    private val titleResolver: (roomCode: String, senderName: String) -> String =
        { _, sender -> sender },
) : MessageAlerts {

    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.messages_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
    }

    override fun onNewMessage(roomCode: String, senderName: String, text: String, unreadCount: Int) {
        val title = titleResolver(roomCode, senderName)
        val isGroup = !roomCode.startsWith("dm:")
        val tapIntent = PendingIntent.getActivity(
            context,
            roomCode.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_ROOM_CODE, roomCode)
                putExtra(MainActivity.EXTRA_ROOM_TITLE, title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(if (isGroup) "$senderName: $text" else text)
            .setNumber(unreadCount)
            .setSubText(if (unreadCount > 1) "$unreadCount new messages" else null)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        manager.notify(roomCode.hashCode(), notification)
    }

    override fun onRead(roomCode: String) {
        manager.cancel(roomCode.hashCode())
    }

    /** Someone tapped an icebreaker at us — high-priority, opens their chat. */
    fun onIcebreaker(senderName: String, emoji: String, roomCode: String) {
        val tapIntent = PendingIntent.getActivity(
            context,
            roomCode.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_ROOM_CODE, roomCode)
                putExtra(MainActivity.EXTRA_ROOM_TITLE, senderName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$senderName wants to chat $emoji")
            .setContentText("Open to accept or decline")
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .build()
        manager.notify(roomCode.hashCode(), notification)
    }

    /** Someone nearby asked for help — the whole point of pillar 2. */
    fun onHelpPost(post: HelpBoard.HelpPost) {
        val tapIntent = PendingIntent.getActivity(
            context,
            post.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${post.category.emoji} ${post.posterName} needs help nearby")
            .setContentText(post.text)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .build()
        manager.notify(post.id.hashCode(), notification)
    }

    private companion object {
        const val CHANNEL_ID = "beacon_messages"
    }
}
