package com.example.forthewin

import android.app.Application

class LauncherApplication : Application() {

    lateinit var iconPackManager: IconPackManager
        private set

    override fun onCreate() {
        super.onCreate()
        iconPackManager = IconPackManager(this)
    }
}
