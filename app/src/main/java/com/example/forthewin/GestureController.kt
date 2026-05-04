package com.example.forthewin

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * GestureController — swipe gestures on the desktop layer.
 *   Swipe UP   → open Start Menu
 *   Swipe DOWN → open Control Center
 *   Swipe LEFT/RIGHT → future: switch desktops
 *   Double tap → exit edit mode / deselect
 */
class GestureController(
    context: Context,
    private val onSwipeUp: () -> Unit,
    private val onSwipeDown: () -> Unit,
    private val onSwipeLeft: () -> Unit = {},
    private val onSwipeRight: () -> Unit = {},
    private val onDoubleTap: () -> Unit = {}
) {
    private val SWIPE_THRESHOLD = 80f
    private val SWIPE_VELOCITY = 100f

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            val e1 = e1 ?: return false
            val dx = e2.x - e1.x
            val dy = e2.y - e1.y
            return when {
                abs(dy) > abs(dx) && abs(dy) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY -> {
                    if (dy < 0) onSwipeUp() else onSwipeDown()
                    true
                }
                abs(dx) > abs(dy) && abs(dx) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY -> {
                    if (dx < 0) onSwipeLeft() else onSwipeRight()
                    true
                }
                else -> false
            }
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            onDoubleTap()
            return true
        }

        override fun onDown(e: MotionEvent) = true
    })

    /** Attach to any View's touch listener */
    fun attachTo(view: View) {
        view.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            false // don't consume — let children still receive events
        }
    }

    /** Process a MotionEvent directly (call from onTouchEvent override) */
    fun onTouch(event: MotionEvent): Boolean = detector.onTouchEvent(event)
}
