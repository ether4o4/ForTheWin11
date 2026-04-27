package com.example.forthewin

import android.app.Application
import android.content.Intent
import com.example.forthewin.services.LauncherService

class LauncherApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Start background system service
        val serviceIntent = Intent(this, LauncherService::class.java)
        startService(serviceIntent)
    }
}
