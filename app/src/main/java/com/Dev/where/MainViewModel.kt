package com.dev.where

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

data class GpsPoint(
    val lat: Double,
    val lng: Double,
    val accuracy: Float,
    val timestamp: String,
    val count: Int  // quanti punti ricevuti in questa sessione
)

data class WhereUiState(
    val isTracking: Boolean = false,
    val gpsAvailable: Boolean = false,
    val lastPoint: GpsPoint? = null,
    val log: List<String> = emptyList()  // log visivo per debug
)

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(WhereUiState())
    val uiState: StateFlow<WhereUiState> = _uiState.asStateFlow()

    private var pointCount = 0
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // ─── Chiamato dalla MainActivity quando arriva il broadcast GPS ───────────

    fun onLocationReceived(lat: Double, lng: Double, accuracy: Float, timeMs: Long) {
        pointCount++
        val point = GpsPoint(
            lat       = lat,
            lng       = lng,
            accuracy  = accuracy,
            timestamp = dateFormat.format(Date(timeMs)),
            count     = pointCount
        )
        val logEntry = "#$pointCount ${point.timestamp} → ${formatLat(lat)}, ${formatLng(lng)} ±${accuracy.toInt()}m"

        _uiState.value = _uiState.value.copy(
            lastPoint = point,
            log = (listOf(logEntry) + _uiState.value.log).take(20) // ultimi 20
        )
    }

    fun onGpsAvailabilityChanged(available: Boolean) {
        _uiState.value = _uiState.value.copy(gpsAvailable = available)
    }

    fun onTrackingStarted() {
        _uiState.value = _uiState.value.copy(isTracking = true)
        addLog("▶ Tracker avviato")
    }

    fun onTrackingStopped() {
        _uiState.value = _uiState.value.copy(isTracking = false)
        pointCount = 0
        addLog("■ Tracker fermato")
    }

    // ─── Privato ──────────────────────────────────────────────────────────────

    private fun addLog(msg: String) {
        _uiState.value = _uiState.value.copy(
            log = (listOf(msg) + _uiState.value.log).take(20)
        )
    }

    private fun formatLat(lat: Double) = "%.6f".format(lat)
    private fun formatLng(lng: Double) = "%.6f".format(lng)
}
