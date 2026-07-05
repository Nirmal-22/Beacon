package com.beacon.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.beacon.MainActivity
import com.beacon.R

class NotificationHelper(private val context: Context) {

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_desc)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun buildServiceNotification(peerCount: Int): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = when (peerCount) {
            0 -> "Looking for people nearby…"
            1 -> "1 person nearby"
            else -> "$peerCount people nearby"
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Beacon is active")
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    fun update(peerCount: Int) {
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildServiceNotification(peerCount))
    }

    companion object {
        const val CHANNEL_ID = "beacon_active"
        const val NOTIFICATION_ID = 1
    }
}
