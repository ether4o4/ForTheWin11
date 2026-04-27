package com.example.forthewin.ui.controllers

import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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

        window.findViewById<View>(R.id.btn_window_close).setOnClickListener {
            removeWindow(window)
        }

        val content = window.findViewById<FrameLayout>(R.id.window_content)
        contentInitializer(content)

        setupDrag(window)
        
        container.addView(window)
        windows.add(window)
        onWindowAdded(title, iconRes, window)

        window.post {
            window.x = (container.width - window.width) / 2f
            window.y = (container.height - window.height) / 2f
            bringToFront(window)
        }
    }

    private fun removeWindow(window: View) {
        container.removeView(window)
        windows.remove(window)
        onWindowRemoved(window)
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
                    bringToFront(window)
                    v.performClick()
                }
                MotionEvent.ACTION_MOVE -> {
                    window.animate()
                        .x(event.rawX + dX)
                        .y(event.rawY + dY)
                        .setDuration(0)
                        .start()
                }
            }
            true
        }
    }

    fun bringToFront(window: View) {
        window.bringToFront()
        // Update visual state of other windows if needed
    }
}
