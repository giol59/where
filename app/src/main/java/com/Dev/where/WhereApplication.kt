package com.dev.where

import android.app.Application
import com.dev.where.receiver.registerLocationUpdates
import android.util.Log

class WhereApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("WhereApp", "Application onCreate — avvio registerLocationUpdates")
        registerLocationUpdates(this)
        Log.d("WhereApp", "Application onCreate — registerLocationUpdates chiamata")
    }
}