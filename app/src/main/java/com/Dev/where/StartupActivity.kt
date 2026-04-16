package com.Dev.where

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class StartupActivity : AppCompatActivity() {

    private val requestNotification = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        checkAndRequestLocation()
    }

    private val requestLocation = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val fine = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            checkBackgroundLocation()
        } else {
            showPermissionError("Permesso posizione necessario per il funzionamento.")
        }
    }

    private val requestBackground = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            checkBatteryOptimization()
        } else {
            showBackgroundLocationDialog()
        }
    }

    private val requestBatteryOptimization = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        startServiceAndFinish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("StartupActivity", "onCreate")
        checkAndRequestNotification()
    }

    private fun checkAndRequestNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                checkAndRequestLocation()
            }
        } else {
            checkAndRequestLocation()
        }
    }

    private fun checkAndRequestLocation() {
        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fine) {
            checkBackgroundLocation()
        } else {
            requestLocation.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun checkBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                showBackgroundLocationDialog()
            } else {
                checkBatteryOptimization()
            }
        } else {
            checkBatteryOptimization()
        }
    }

    private fun checkBatteryOptimization() {
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            requestBatteryOptimization.launch(intent)
        } else {
            startServiceAndFinish()
        }
    }

    private fun showBackgroundLocationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permesso posizione")
            .setMessage("Per funzionare correttamente, vai in Impostazioni → Posizione → Consenti sempre.")
            .setPositiveButton("Apri impostazioni") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Annulla") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showPermissionError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun startServiceAndFinish() {
        Log.d("StartupActivity", "Avvio ForegroundService")
        LocationForegroundService.start(this)
        Toast.makeText(this, "Servizio avviato", Toast.LENGTH_SHORT).show()
        finish()
    }
}