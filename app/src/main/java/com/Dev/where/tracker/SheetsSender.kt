package com.dev.where.tracker

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object SheetsSender {

    private const val TAG = "SheetsSender"
    private const val ENDPOINT = "https://script.google.com/macros/s/AKfycbxbIfCAjoLvRZAFuvb0QQ9GUx-Ugoje24qArFoA9z7lXTaWYKUDNFfma--IxQzg69Lmdw/exec"

    private val client = OkHttpClient()
    private val JSON_TYPE = "application/json".toMediaType()

    fun send(lat: Double, lng: Double, accuracy: Float, timestamp: Long) {
        val body = """{"deviceId":"where_device_01","lat":$lat,"lng":$lng,"accuracy":$accuracy,"timestamp":"${java.util.Date(timestamp).toInstant()}"}"""

        val request = Request.Builder()
            .url(ENDPOINT)
            .post(body.toRequestBody(JSON_TYPE))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "POST fallito: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d(TAG, "POST risposta: $responseBody")
                response.close()
            }
        })
    }
}