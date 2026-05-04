package com.example.forthewin.ui.controllers

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.example.forthewin.R

/**
 * WidgetManager — adds dismissable, freely draggable widgets to the desktop.
 */
class WidgetManager(
    private val context: Context,
    private val container: FrameLayout
) {
    companion object {
        private const val TAG = "WidgetManager"
    }

    private val activeWidgets = mutableListOf<View>()

    fun addWidget(widgetView: View) {
        try {
            val wrapper = LayoutInflater.from(context)
                .inflate(R.layout.layout_widget_wrapper, container, false)

            val contentSlot = wrapper.findViewById<FrameLayout>(R.id.widget_content_slot)
            val dismissBtn = wrapper.findViewById<View>(R.id.btn_widget_dismiss)

            val density = context.resources.displayMetrics.density

            // Determine widget size in px
            val widgetWPx: Int
            val widgetHPx: Int
            if (widgetView is AppWidgetHostView && widgetView.appWidgetInfo != null) {
                val info = widgetView.appWidgetInfo
                // minWidth/minHeight are in dp — convert to px
                val minWDp = if (info.minWidth > 0) info.minWidth else 280
                val minHDp = if (info.minHeight > 0) info.minHeight else 200
                widgetWPx = (minWDp * density).toInt()
                widgetHPx = (minHDp * density).toInt()
            } else {
                widgetWPx = (280 * density).toInt()
                widgetHPx = (200 * density).toInt()
            }

            // Cap at screen size to avoid insanely large widgets
            val maxW = context.resources.displayMetrics.widthPixels - (32 * density).toInt()
            val maxH = context.resources.displayMetrics.heightPixels - (120 * density).toInt()
            val finalW = widgetWPx.coerceAtMost(maxW).coerceAtLeast((120 * density).toInt())
            val finalH = widgetHPx.coerceAtMost(maxH).coerceAtLeast((80 * density).toInt())

            // Remove from any previous parent
            try { (widgetView.parent as? ViewGroup)?.removeView(widgetView) }
            catch (e: Exception) { Log.w(TAG, "removeView from parent", e) }

            widgetView.layoutParams = FrameLayout.LayoutParams(finalW, finalH)
            contentSlot.addView(widgetView)
            contentSlot.layoutParams = FrameLayout.LayoutParams(finalW, finalH)

            dismissBtn.setOnClickListener { removeWidget(wrapper) }
            makeDraggable(wrapper)

            val offset = (activeWidgets.size * 20 * density).toInt()
            wrapper.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            wrapper.x = (40 + offset).toFloat()
            wrapper.y = (100 + offset).toFloat()
            wrapper.elevation = 8f

            container.addView(wrapper)
            activeWidgets.add(wrapper)

            wrapper.alpha = 0f; wrapper.scaleX = 0.85f; wrapper.scaleY = 0.85f
            wrapper.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start()

            Log.d(TAG, "Widget added: ${finalW}x${finalH}px")
        } catch (e: Exception) {
            Log.e(TAG, "addWidget crashed", e)
        }
    }

    fun removeWidget(wrapper: View) {
        wrapper.animate()
            .alpha(0f).scaleX(0.85f).scaleY(0.85f)
            .setDuration(180)
            .withEndAction {
                try {
                    container.removeView(wrapper)
                    activeWidgets.remove(wrapper)
                } catch (e: Exception) { Log.w(TAG, "removeWidget", e) }
            }.start()
    }

    private fun makeDraggable(view: View) {
        var downX = 0f
        var downY = 0f
        var moved = false
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
                    view.x = (event.rawX - downX).coerceAtLeast(0f)
                    view.y = (event.rawY - downY).coerceAtLeast(0f)
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
