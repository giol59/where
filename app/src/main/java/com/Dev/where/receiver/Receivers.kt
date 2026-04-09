package com.dev.where.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * STUB — dichiarato nel manifest, implementato allo STEP 6.
 * Riceve BOOT_COMPLETED e avvia il tracker.
 * Per ora è vuoto: serve solo per far compilare il progetto.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // STEP 6: qui avvieremo il LocationRequest via FusedLocationProviderClient
    }
}

/**
 * STUB — dichiarato nel manifest, implementato allo STEP 6.
 * Riceve i punti GPS via PendingIntent dal sistema.
 * Per ora è vuoto: serve solo per far compilare il progetto.
 */
class LocationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // STEP 6: qui leggeremo LocationResult.extractResult(intent)
    }
}