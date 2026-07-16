package com.example.forthewin.ui.controllers

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import com.example.forthewin.R

class TaskbarManager(
    private val context: Context,
    private val window: Window,
    private val taskbarView: View
) {

    companion object {
        private const val PREFS_NAME = "taskbar_prefs"
        private const val KEY_ALIGNMENT = "start_button_alignment"
    }

    enum class Alignment { LEFT, CENTER, RIGHT }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var alignment: Alignment
        get() = try {
            Alignment.valueOf(
                prefs.getString(KEY_ALIGNMENT, Alignment.LEFT.name) ?: Alignment.LEFT.name
            )
        } catch (_: Exception) { Alignment.LEFT }
        set(value) {
            prefs.edit().putString(KEY_ALIGNMENT, value.name).apply()
            applyAlignment()
        }

    init {
        applyModernInsets()
        applyAlignment()
    }

    private fun applyModernInsets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            )
        }
    }

    private fun applyAlignment() {
        val startButton = taskbarView.findViewById<View>(R.id.start_button) ?: return
        val lp = startButton.layoutParams as? FrameLayout.LayoutParams ?: return
        lp.gravity = when (alignment) {
            Alignment.LEFT -> Gravity.START or Gravity.CENTER_VERTICAL
            Alignment.CENTER -> Gravity.CENTER
            Alignment.RIGHT -> Gravity.END or Gravity.CENTER_VERTICAL
        }
        startButton.layoutParams = lp
    }
}