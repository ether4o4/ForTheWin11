package com.example.forthewin.ui.controllers

import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import com.example.forthewin.R

/**
 * WidgetManager
 * Manages draggable, dismissable widgets on the desktop sidebar.
 * Each widget is wrapped in a draggable container with an X dismiss button.
 */
class WidgetManager(
    private val context: Context,
    private val container: LinearLayout   // sidebar_widgets LinearLayout
) {

    private val activeWidgets = mutableListOf<View>()

    /**
     * Add a widget view to the sidebar. It gets wrapped in a
     * draggable card with a dismiss (X) button.
     */
    fun addWidget(widgetView: View) {
        val wrapper = LayoutInflater.from(context)
            .inflate(R.layout.layout_widget_wrapper, null, false)

        val contentSlot = wrapper.findViewById<FrameLayout>(R.id.widget_content_slot)
        val dismissBtn = wrapper.findViewById<View>(R.id.btn_widget_dismiss)

        contentSlot.addView(widgetView)

        dismissBtn.setOnClickListener {
            removeWidget(wrapper)
        }

        // Make wrapper draggable within the parent FrameLayout
        // (convert sidebar to FrameLayout-backed drag surface)
        makeDraggable(wrapper)

        container.addView(wrapper)
        activeWidgets.add(wrapper)
    }

    /**
     * Remove a widget from the container.
     */
    fun removeWidget(wrapper: View) {
        wrapper.animate()
            .alpha(0f)
            .translationX(100f)
            .setDuration(250)
            .withEndAction {
                container.removeView(wrapper)
                activeWidgets.remove(wrapper)
            }.start()
    }

    /**
     * Attach touch drag to a view so it can be moved within its parent.
     */
    private fun makeDraggable(view: View) {
        var downX = 0f
        var downY = 0f

        view.setOnTouchListener { v, event ->
            // Only intercept drag on the title bar area, not on widget content
            val titleBar = v.findViewById<View>(R.id.widget_drag_handle)
            if (titleBar == null) return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX - v.x
                    downY = event.rawY - v.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val parent = v.parent as? ViewGroup ?: return@setOnTouchListener false
                    val newX = (event.rawX - downX).coerceIn(0f, (parent.width - v.width).toFloat())
                    val newY = (event.rawY - downY).coerceIn(0f, (parent.height - v.height).toFloat())
                    v.x = newX
                    v.y = newY
                    true
                }
                else -> false
            }
        }
    }

    fun getWidgetCount() = activeWidgets.size
}
