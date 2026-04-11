package com.dev.where.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gps_points")
data class GpsPoint(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val lat: Double,
    val lng: Double,
    val accuracy: Float,
    val timestamp: Long,
    val sent: Boolean = false
)

