package com.dev.where.tracker

import android.content.Context
import android.util.Log
import com.dev.where.db.GpsPoint
import com.dev.where.db.WhereDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object SheetsSender {

    private const val TAG = "SheetsSender"
    private const val ENDPOINT = "https://script.google.com/macros/s/AKfycbxbIfCAjoLvRZAFuvb0QQ9GUx-Ugoje24qArFoA9z7lXTaWYKUDNFfma--IxQzg69Lmdw/exec"

    private val client = OkHttpClient()
    private val JSON_TYPE = "application/json".toMediaType()
    private val scope = CoroutineScope(Dispatchers.IO)

    fun saveAndSend(context: Context, lat: Double, lng: Double, accuracy: Float, timestamp: Long) {
        scope.launch {
            val dao = WhereDatabase.getInstance(context).gpsPointDao()
            val id = dao.insert(
                GpsPoint(lat = lat, lng = lng, accuracy = accuracy, timestamp = timestamp)
            )
            Log.d(TAG, "Salvato su Room → id=$id")
            sendPoint(context, id, lat, lng, accuracy, timestamp)
        }
    }

    fun retrySend(context: Context) {
        scope.launch {
            val dao = WhereDatabase.getInstance(context).gpsPointDao()
            val pending = dao.getPending()
            Log.d(TAG, "Retry: ${pending.size} punti pending")
            pending.forEach { point ->
                sendPoint(context, point.id, point.lat, point.lng, point.accuracy, point.timestamp)
            }
        }
    }

    private fun sendPoint(context: Context, id: Long, lat: Double, lng: Double, accuracy: Float, timestamp: Long) {
        val body = """{"deviceId":"where_device_01","lat":$lat,"lng":$lng,"accuracy":$accuracy,"timestamp":"${java.util.Date(timestamp).toInstant()}"}"""

        val request = Request.Builder()
            .url(ENDPOINT)
            .post(body.toRequestBody(JSON_TYPE))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "POST fallito id=$id: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d(TAG, "POST ok id=$id: $responseBody")
                response.close()
                if (responseBody?.contains("\"ok\"") == true) {
                    scope.launch {
                        val dao = WhereDatabase.getInstance(context).gpsPointDao()
                        dao.markSent(id)
                    }
                }
            }
        })
    }
}