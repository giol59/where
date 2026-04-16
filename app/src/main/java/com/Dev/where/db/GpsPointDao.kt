package com.Dev.where.db

import androidx.room.*

@Dao
interface GpsPointDao {

    @Insert
    suspend fun insert(point: GpsPoint): Long

    @Query("SELECT * FROM gps_points WHERE sent = 0 ORDER BY timestamp ASC")
    suspend fun getPending(): List<GpsPoint>

    @Query("UPDATE gps_points SET sent = 1 WHERE id = :id")
    suspend fun markSent(id: Long)

    @Query("DELETE FROM gps_points WHERE sent = 1")
    suspend fun deleteSent()
}