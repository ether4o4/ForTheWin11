package com.example.forthewin

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.provider.Settings
import android.view.Window
import android.view.WindowManager

/**
 * QuickSettingsManager — real hardware toggles for control center.
 * Wifi, Bluetooth, Brightness, Volume, Battery level reads.
 */
class QuickSettingsManager(private val context: Context, private val window: Window) {

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // ── BRIGHTNESS ─────────────────────────────────────────────────

    /** Returns 0..255 */
    fun getBrightness(): Int =
        try { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }
        catch (e: Exception) { 200 }

    /** Sets screen brightness 0..255 — requires WRITE_SETTINGS permission */
    fun setBrightness(value: Int) {
        try {
            // Window brightness (immediate, no permission)
            val lp = window.attributes
            lp.screenBrightness = value / 255f
            window.attributes = lp
            // Also try system setting (needs WRITE_SETTINGS grant)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value.coerceIn(0, 255))
        } catch (e: Exception) {
            // Fallback: window brightness only (always works)
            val lp = window.attributes
            lp.screenBrightness = value / 255f
            window.attributes = lp
        }
    }

    // ── VOLUME ─────────────────────────────────────────────────────

    /** Returns 0..100 (maps to stream max) */
    fun getVolume(): Int {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) (cur * 100 / max) else 0
    }

    fun setVolume(percent: Int) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (percent * max / 100).coerceIn(0, max)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }

    // ── WIFI ────────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    fun isWifiEnabled(): Boolean =
        try {
            (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
        } catch (e: Exception) { false }

    /** Opens wifi settings (direct toggle requires system app or DO/PO on Android 10+) */
    fun openWifiSettings() {
        context.startActivity(
            android.content.Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    // ── BLUETOOTH ───────────────────────────────────────────────────

    fun isBluetoothEnabled(): Boolean =
        try { BluetoothAdapter.getDefaultAdapter()?.isEnabled == true }
        catch (e: Exception) { false }

    fun openBluetoothSettings() {
        context.startActivity(
            android.content.Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    // ── BATTERY ─────────────────────────────────────────────────────

    fun getBatteryPercent(): Int {
        val intent = context.registerReceiver(
            null,
            android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        )
        val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else 100
    }

    fun isCharging(): Boolean {
        val intent = context.registerReceiver(
            null,
            android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        )
        val status = intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
               status == android.os.BatteryManager.BATTERY_STATUS_FULL
    }
}
