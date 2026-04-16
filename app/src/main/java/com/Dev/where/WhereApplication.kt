package com.Dev.where

import android.app.Application
import android.util.Log

class WhereApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("WhereApp", "Application onCreate")
    }
}