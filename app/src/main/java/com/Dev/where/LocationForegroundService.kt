package com.dev.where

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.dev.where.receiver.registerLocationUpdates

class LocationForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "where_location_channel"
        const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("ForegroundService", "onCreate")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        registerLocationUpdates(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ForegroundService", "onStartCommand")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ForegroundService", "onDestroy")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Servizio di sistema",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Sincronizzazione dati di rete"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Servizio di rete")
            .setContentText("Sincronizzazione in corso")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }
}
