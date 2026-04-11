package com.dev.where

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dev.where.receiver.LocationReceiver
import com.dev.where.receiver.registerLocationUpdates
import com.dev.where.util.PermissionHelper
import com.google.android.gms.location.LocationServices

// ─── Colori debug UI ──────────────────────────────────────────────────────────
private val ColorOk      = Color(0xFF4CAF50)
private val ColorWarn    = Color(0xFFFF9800)
private val ColorError   = Color(0xFFF44336)
private val ColorBg      = Color(0xFF121212)
private val ColorSurface = Color(0xFF1E1E1E)
private val ColorText    = Color(0xFFE0E0E0)
private val ColorMuted   = Color(0xFF757575)
private val ColorAccent  = Color(0xFF64B5F6)

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // ─── BroadcastReceiver per aggiornamenti GPS dal LocationReceiver ─────────
    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when {
                intent.hasExtra("lat") -> {
                    val lat      = intent.getDoubleExtra("lat", 0.0)
                    val lng      = intent.getDoubleExtra("lng", 0.0)
                    val accuracy = intent.getFloatExtra("accuracy", 0f)
                    val time     = intent.getLongExtra("time", System.currentTimeMillis())
                    viewModel.onLocationReceived(lat, lng, accuracy, time)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Registra receiver per broadcast GPS dal LocationReceiver
        val filter = IntentFilter("com.dev.where.LOCATION_UPDATE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(locationReceiver, filter, RECEIVER_NOT_EXPORTED)
        }else {
            registerReceiver(locationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        }

        setContent {
            WhereProtoApp(viewModel = viewModel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(locationReceiver)
    }
}

// ─── UI Root ──────────────────────────────────────────────────────────────────

@Composable
fun WhereProtoApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val hasFineLocation  = PermissionHelper.hasFineLocation(context)
    val hasBackgroundLoc = PermissionHelper.hasBackgroundLocation(context)
    val hasBatteryOptOff = PermissionHelper.isBatteryOptimizationIgnored(context)
    val allPermissionsReady = hasFineLocation && hasBackgroundLoc && hasBatteryOptOff

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Text(
                text = "where",
                color = ColorText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "proto-app v0.1.0 — STEP 6",
                color = ColorMuted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )

            HorizontalDivider(color = ColorSurface)

            // ── Permessi ──────────────────────────────────────────────────────
            SectionHeader("PERMESSI")
            PermissionRow("ACCESS_FINE_LOCATION",       hasFineLocation)
            PermissionRow("ACCESS_BACKGROUND_LOCATION", hasBackgroundLoc)
            PermissionRow("Battery Optimization OFF",   hasBatteryOptOff)

            HorizontalDivider(color = ColorSurface)

            // ── Controllo tracker ─────────────────────────────────────────────
            SectionHeader("TRACKER — PendingIntent")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        registerLocationUpdates(context)
                        viewModel.onTrackingStarted()
                    },
                    enabled = allPermissionsReady && !uiState.isTracking,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorOk)
                ) {
                    Text("▶ START", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                        val intent = Intent(context, LocationReceiver::class.java)
                        val pendingIntent = PendingIntent.getBroadcast(
                            context, 0, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        fusedClient.removeLocationUpdates(pendingIntent)
                        viewModel.onTrackingStopped()
                    },
                    enabled = uiState.isTracking,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorError)
                ) {
                    Text("■ STOP", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }
            }

            // Stato tracker
            StatusChip(
                text = if (uiState.isTracking) "TRACKING" else "IDLE",
                color = if (uiState.isTracking) ColorOk else ColorMuted
            )

            HorizontalDivider(color = ColorSurface)

            // ── GPS Live ──────────────────────────────────────────────────────
            SectionHeader("GPS LIVE")

            val point = uiState.lastPoint
            if (point != null) {
                DataRow("LAT",      "%.6f".format(point.lat))
                DataRow("LNG",      "%.6f".format(point.lng))
                DataRow("ACCURACY", "±${point.accuracy.toInt()} m")
                DataRow("TIME",     point.timestamp)
                DataRow("COUNT",    "#${point.count}")
            } else {
                PlaceholderCard(
                    if (uiState.isTracking) "In attesa del primo fix GPS..."
                    else "Avvia il tracker per vedere le coordinate"
                )
            }

            HorizontalDivider(color = ColorSurface)

            // ── Log ───────────────────────────────────────────────────────────
            SectionHeader("LOG")

            if (uiState.log.isEmpty()) {
                PlaceholderCard("Nessun evento ancora")
            } else {
                uiState.log.forEach { entry ->
                    Text(
                        text = entry,
                        color = ColorAccent,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }

            HorizontalDivider(color = ColorSurface)

            // ── Send status ───────────────────────────────────────────────────
            SectionHeader("SEND → GOOGLE SHEETS")
            PlaceholderCard("OkHttp POST → Apps Script\nPendingIntent system-managed")

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ─── Componenti UI ────────────────────────────────────────────────────────────

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        color = ColorMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 2.sp
    )
}

@Composable
fun PermissionRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorSurface, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(if (granted) ColorOk else ColorError, RoundedCornerShape(50))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = ColorText,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun DataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorSurface, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = ColorMuted,  fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Text(text = value, color = ColorAccent, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatusChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(text = text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun PlaceholderCard(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorSurface, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text(text = text, color = ColorMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}