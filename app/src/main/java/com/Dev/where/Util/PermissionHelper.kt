package com.Dev.where.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Centralizza tutta la logica permessi dell'app.
 * Usato dalla MainActivity per controllare lo stato e guidare l'utente.
 */
object  PermissionHelper {

    // ─── Permessi richiesti in runtime ────────────────────────────────────────

    /**
     * Permessi da richiedere nella prima schermata (STEP 1 del flow).
     * ACCESS_FINE_LOCATION e COARSE insieme — il sistema mostra un solo dialog.
     */
    val LOCATION_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    /**
     * ACCESS_BACKGROUND_LOCATION va richiesto SEPARATAMENTE dopo che
     * FINE/COARSE sono già stati concessi. Su Android 11+ il sistema
     * rimanda direttamente alle impostazioni — non esiste un dialog in-app.
     */
    val BACKGROUND_LOCATION_PERMISSION = Manifest.permission.ACCESS_BACKGROUND_LOCATION

    // ─── Check stato permessi ──────────────────────────────────────────────────

    fun hasFineLocation(context: Context): Boolean =
        hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasBackgroundLocation(context: Context): Boolean =
        hasPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    fun hasAllRequired(context: Context): Boolean =
        hasFineLocation(context) && hasBackgroundLocation(context)

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    // ─── Intent helper per impostazioni sistema ────────────────────────────────

    /**
     * Apre la schermata Battery Optimization del sistema.
     * Necessario perché su Android 6+ non si può bypassare via codice —
     * l'utente deve farlo manualmente.
     * REQUEST_IGNORE_BATTERY_OPTIMIZATIONS nel manifest permette questo intent diretto.
     */
    fun buildBatteryOptimizationIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    /**
     * Apre le impostazioni app per il permesso background location.
     * Usato come fallback se l'utente ha già negato il permesso una volta.
     */
    fun buildAppSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    // ─── Privato ───────────────────────────────────────────────────────────────

    private fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}