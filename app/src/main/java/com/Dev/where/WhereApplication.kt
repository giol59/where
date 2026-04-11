package com.dev.where

import android.app.Application
import com.dev.where.receiver.registerLocationUpdates

class WhereApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        registerLocationUpdates(this)
    }
}