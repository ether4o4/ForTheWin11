package com.example.forthewin

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import androidx.appcompat.app.AppCompatActivity

class SystemStatsManager(private val context: Context) {

    fun getMemoryUsage(): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalMem = memoryInfo.totalMem.toDouble()
        val availMem = memoryInfo.availMem.toDouble()
        val usedMem = totalMem - availMem
        return ((usedMem / totalMem) * 100).toInt()
    }

    fun getBatteryLevel(): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    // CPU is harder on modern Android without native access, so we'll simulate a dynamic load
    fun getCpuUsage(): Int {
        return (20..60).random()
    }
}
