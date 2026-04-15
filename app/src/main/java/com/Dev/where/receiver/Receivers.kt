package com.dev.where.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dev.where.tracker.SheetsSender
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BootReceiver", "BOOT_COMPLETED ricevuto")
        registerLocationUpdates(context)
    }
}

class LocationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = LocationResult.extractResult(intent) ?: return
        val location = result.lastLocation ?: return

        Log.d("LocationReceiver", "GPS → lat=${location.latitude} lng=${location.longitude}")

        SheetsSender.saveAndSend(
            context,
            location.latitude,
            location.longitude,
            location.accuracy,
            location.time
        )
    }
}

fun registerLocationUpdates(context: Context) {
    val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    val request = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        5_000L
    )
        .setMinUpdateIntervalMillis(2_000L)
        .setWaitForAccurateLocation(false)
        .build()

    val intent = Intent("com.dev.where.LOCATION_UPDATE").apply {
        setPackage(context.packageName)
    }
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )

    try {
        fusedClient.requestLocationUpdates(request, pendingIntent)
            .addOnSuccessListener {
                Log.d("BootReceiver", "PendingIntent registrato OK")
            }
            .addOnFailureListener { e ->
                Log.e("BootReceiver", "Registrazione fallita: ${e.message} cause=${e.cause}")
                e.printStackTrace()
            }
    } catch (e: SecurityException) {
        Log.e("BootReceiver", "Permesso GPS mancante: ${e.message}")
    }
}