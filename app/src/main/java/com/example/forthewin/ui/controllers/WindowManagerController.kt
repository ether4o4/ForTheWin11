package com.example.forthewin.ui.controllers

import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.example.forthewin.R

class WindowManagerController(
    private val context: Context,
    private val container: FrameLayout,
    private val onWindowAdded: (String, Int, View) -> Unit,
    private val onWindowRemoved: (View) -> Unit
) {
    private val windows = mutableListOf<View>()

    fun openWindow(title: String, iconRes: Int, contentInitializer: (FrameLayout) -> Unit) {
        val inflater = LayoutInflater.from(context)
        val window = inflater.inflate(R.layout.layout_floating_window, container, false)

        window.findViewById<TextView>(R.id.window_title).text = title
        window.findViewById<ImageView>(R.id.window_icon).setImageResource(iconRes)

        // Close button
        window.findViewById<View>(R.id.btn_window_close).setOnClickListener {
            closeWindow(window)
        }

        // Minimize — hide window, keep taskbar entry
        window.findViewById<View>(R.id.btn_window_minimize)?.setOnClickListener {
            window.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(150)
                .withEndAction { window.visibility = View.GONE }.start()
        }

        // Maximize — toggle fill vs default size
        window.findViewById<View>(R.id.btn_window_maximize)?.setOnClickListener {
            val lp = window.layoutParams as FrameLayout.LayoutParams
            val screenW = container.width
            val screenH = container.height
            if (window.width < screenW - 40) {
                // Maximize
                lp.width = screenW
                lp.height = screenH
                window.layoutParams = lp
                window.x = 0f
                window.y = 0f
            } else {
                // Restore
                val w = (screenW * 0.92f).toInt()
                val h = (screenH * 0.72f).toInt()
                lp.width = w
                lp.height = h
                window.layoutParams = lp
                window.x = (screenW - w) / 2f
                window.y = (screenH - h) / 4f
            }
        }

        val content = window.findViewById<FrameLayout>(R.id.window_content)
        contentInitializer(content)

        setupDrag(window)

        // Default size: 92% wide, 72% tall — portrait friendly
        val screenW = container.width.let { if (it > 0) it else 800 }
        val screenH = container.height.let { if (it > 0) it else 1200 }
        val winW = (screenW * 0.92f).toInt()
        val winH = (screenH * 0.72f).toInt()
        val lp = FrameLayout.LayoutParams(winW, winH)
        window.layoutParams = lp

        container.addView(window)
        windows.add(window)
        onWindowAdded(title, iconRes, window)

        // Center after layout pass
        window.post {
            window.x = (container.width - window.width) / 2f
            window.y = (container.height - window.height) / 4f
            window.bringToFront()
            // Entrance animation
            window.alpha = 0f
            window.scaleX = 0.92f
            window.scaleY = 0.92f
            window.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start()
        }
    }

    private fun closeWindow(window: View) {
        window.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(150)
            .withEndAction {
                container.removeView(window)
                windows.remove(window)
                onWindowRemoved(window)
            }.start()
    }

    private fun setupDrag(window: View) {
        val titleBar = window.findViewById<View>(R.id.window_title_bar)
        var dX = 0f
        var dY = 0f

        titleBar.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = window.x - event.rawX
                    dY = window.y - event.rawY
                    window.bringToFront()
                    v.performClick()
                }
                MotionEvent.ACTION_MOVE -> {
                    val newX = (event.rawX + dX).coerceIn(0f, (container.width - window.width).toFloat())
                    val newY = (event.rawY + dY).coerceIn(0f, (container.height - window.height).toFloat())
                    window.x = newX
                    window.y = newY
                }
            }
            true
        }
    }

    fun bringToFront(window: View) {
        window.bringToFront()
    }
}
