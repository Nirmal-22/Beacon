package com.beacon.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.beacon.BeaconApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Keeps advertising/discovery alive while the app is backgrounded, as a
 * connectedDevice foreground service with a persistent notification showing
 * how many people are in range.
 */
class BeaconService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var notifications: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        notifications = NotificationHelper(this)
        notifications.createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val container = (application as BeaconApp).container

        val notification = notifications.buildServiceNotification(
            container.nearbyManager.peers.value.size
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID, notification)
        }

        container.nearbyManager.start()
        container.nearbyManager.peers
            .onEach { notifications.update(it.size) }
            .launchIn(scope)

        return START_STICKY
    }

    override fun onDestroy() {
        (application as BeaconApp).container.nearbyManager.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.beacon.action.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, BeaconService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BeaconService::class.java))
        }
    }
}
