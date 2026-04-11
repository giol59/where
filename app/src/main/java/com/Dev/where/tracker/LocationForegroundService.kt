package com.dev.where.tracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*

class LocationForegroundService : Service() {

    companion object {
        const val TAG = "WhereService"

        const val ACTION_START           = "com.dev.where.ACTION_START"
        const val ACTION_STOP            = "com.dev.where.ACTION_STOP"
        const val ACTION_LOCATION_UPDATE = "com.dev.where.LOCATION_UPDATE"

        const val EXTRA_LAT       = "lat"
        const val EXTRA_LNG       = "lng"
        const val EXTRA_ACCURACY  = "accuracy"
        const val EXTRA_TIME      = "time"
        const val EXTRA_AVAILABLE = "available"

        private const val CHANNEL_ID      = "where_tracker_channel"
        private const val NOTIF_ID        = 1001
        private const val INTERVAL_MS     = 5_000L
        private const val MIN_INTERVAL_MS = 2_000L
    }

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        buildLocationCallback()
        createNotificationChannel()
        Log.d(TAG, "Service onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP  -> stopTracking()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        fusedClient.removeLocationUpdates(locationCallback)
        Log.d(TAG, "Service onDestroy")
    }

    // ─── Tracking ─────────────────────────────────────────────────────────────

    private fun startTracking() {
        Log.d(TAG, "startTracking()")
        startForeground(NOTIF_ID, buildNotification())

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            INTERVAL_MS
        )
            .setMinUpdateIntervalMillis(MIN_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fusedClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Location updates requested")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permesso GPS mancante: ${e.message}")
            stopSelf()
        }
    }

    private fun stopTracking() {
        fusedClient.removeLocationUpdates(locationCallback)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "stopTracking()")
    }

    // ─── Callback GPS ─────────────────────────────────────────────────────────

    private fun buildLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                Log.d(TAG, "GPS → lat=${location.latitude} lng=${location.longitude} acc=${location.accuracy}m")

                val broadcast = Intent(ACTION_LOCATION_UPDATE).apply {
                    putExtra(EXTRA_LAT,      location.latitude)
                    putExtra(EXTRA_LNG,      location.longitude)
                    putExtra(EXTRA_ACCURACY, location.accuracy)
                    putExtra(EXTRA_TIME,     location.time)
                }
                sendBroadcast(broadcast)
                SheetsSender.send(location.latitude, location.longitude, location.accuracy, location.time)
            }


            override fun onLocationAvailability(availability: LocationAvailability) {
                Log.d(TAG, "GPS disponibile: ${availability.isLocationAvailable}")
                val broadcast = Intent(ACTION_LOCATION_UPDATE).apply {
                    putExtra(EXTRA_AVAILABLE, availability.isLocationAvailable)
                }
                sendBroadcast(broadcast)
            }
        }
    }

    // ─── Notifica foreground ──────────────────────────────────────────────────

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("where")
            .setContentText("Tracker attivo")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Where Tracker",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Canale tracker GPS proto-app"
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}