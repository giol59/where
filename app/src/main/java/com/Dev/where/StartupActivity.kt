package com.dev.where

import android.app.Activity
import android.os.Bundle
import com.dev.where.receiver.registerLocationUpdates

class StartupActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerLocationUpdates(applicationContext)
        finish()
    }
}