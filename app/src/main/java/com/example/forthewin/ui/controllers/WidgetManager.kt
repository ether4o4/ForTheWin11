package com.example.forthewin.ui.controllers

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.example.forthewin.R

/**
 * WidgetManager — adds dismissable, freely draggable widgets to the desktop.
 * Container should be the floating_window_container or desktop FrameLayout.
 */
class WidgetManager(
    private val context: Context,
    private val container: FrameLayout
) {
    private val activeWidgets = mutableListOf<View>()

    fun addWidget(widgetView: View) {
        val wrapper = LayoutInflater.from(context)
            .inflate(R.layout.layout_widget_wrapper, container, false)

        val contentSlot = wrapper.findViewById<FrameLayout>(R.id.widget_content_slot)
        val dismissBtn = wrapper.findViewById<View>(R.id.btn_widget_dismiss)

        val density = context.resources.displayMetrics.density

        // Use the widget's own size if it's an AppWidgetHostView
        val widgetW: Int
        val widgetH: Int
        if (widgetView is AppWidgetHostView) {
            val info = widgetView.appWidgetInfo
            val minW = info?.minWidth ?: 0
            val minH = info?.minHeight ?: 0
            widgetW = if (minW > 0) minW else (280 * density).toInt()
            widgetH = if (minH > 0) minH else (200 * density).toInt()
        } else {
            widgetW = (280 * density).toInt()
            widgetH = (200 * density).toInt()
        }

        // Remove from any previous parent
        (widgetView.parent as? ViewGroup)?.removeView(widgetView)

        // Set the widget view size and add it
        widgetView.layoutParams = FrameLayout.LayoutParams(widgetW, widgetH)
        contentSlot.addView(widgetView)

        // Size the content slot to match
        contentSlot.layoutParams = FrameLayout.LayoutParams(widgetW, widgetH)

        dismissBtn.setOnClickListener { removeWidget(wrapper) }

        makeDraggable(wrapper)

        // Position: stagger each new widget slightly
        val offset = (activeWidgets.size * 20 * density).toInt()
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        wrapper.layoutParams = lp
        wrapper.x = (40 + offset).toFloat()
        wrapper.y = (100 + offset).toFloat()
        wrapper.elevation = 8f

        container.addView(wrapper)
        activeWidgets.add(wrapper)

        // Entrance animation
        wrapper.alpha = 0f
        wrapper.scaleX = 0.85f
        wrapper.scaleY = 0.85f
        wrapper.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start()
    }

    fun removeWidget(wrapper: View) {
        wrapper.animate()
            .alpha(0f)
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setDuration(180)
            .withEndAction {
                container.removeView(wrapper)
                activeWidgets.remove(wrapper)
            }.start()
    }

    private fun makeDraggable(view: View) {
        var downX = 0f
        var downY = 0f
        var moved = false

        // Drag handle = the dismiss bar area at top
        val handle = view.findViewById<View>(R.id.widget_drag_handle) ?: view

        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX - view.x
                    downY = event.rawY - view.y
                    moved = false
                    view.bringToFront()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val newX = (event.rawX - downX).coerceAtLeast(0f)
                    val newY = (event.rawY - downY).coerceAtLeast(0f)
                    view.x = newX
                    view.y = newY
                    moved = true
                    true
                }
                MotionEvent.ACTION_UP -> moved
                else -> false
            }
        }
    }

    fun getWidgetCount() = activeWidgets.size
}
