package com.example.forthewin.ui.controllers

import android.content.Context
import android.view.DragEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.example.forthewin.AppModel
import com.example.forthewin.IconPackManager
import com.example.forthewin.R

/**
 * DesktopManager
 * Owns the desktop icon grid: renders icons, handles drag-to-reorder,
 * long-press for edit mode (shows X delete buttons), tap to launch.
 */
class DesktopManager(
    private val context: Context,
    private val grid: FrameLayout,          // Absolute-position container for free drag
    private val iconPackManager: IconPackManager,
    private val onLaunch: (String) -> Unit
) {
    private val icons = mutableListOf<DesktopIconData>()
    private var editMode = false
    private val iconViews = mutableMapOf<String, View>() // packageName -> view

    data class DesktopIconData(
        val app: AppModel,
        var x: Float,
        var y: Float
    )

    private val iconSizePx: Int get() = (80 * context.resources.displayMetrics.density).toInt()
    private val iconSpacingPx: Int get() = (8 * context.resources.displayMetrics.density).toInt()

    fun setIcons(apps: List<AppModel>) {
        icons.clear()
        // Default positions: column down the left side
        apps.forEachIndexed { i, app ->
            val x = iconSpacingPx.toFloat()
            val y = (i * (iconSizePx + iconSpacingPx)).toFloat()
            icons.add(DesktopIconData(app, x, y))
        }
        renderAll()
    }

    fun setEditMode(enabled: Boolean) {
        editMode = enabled
        iconViews.values.forEach { view ->
            val deleteBtn = view.findViewById<View>(R.id.btn_delete_icon)
            deleteBtn?.visibility = if (enabled) View.VISIBLE else View.GONE
            if (enabled) startWiggle(view) else stopWiggle(view)
        }
    }

    fun isEditMode() = editMode

    private fun renderAll() {
        grid.removeAllViews()
        iconViews.clear()
        icons.forEach { addIconView(it) }
    }

    private fun addIconView(data: DesktopIconData) {
        val view = android.view.LayoutInflater.from(context)
            .inflate(R.layout.item_desktop_icon, grid, false)

        val iconImg = view.findViewById<ImageView>(R.id.icon_image)
        val label = view.findViewById<TextView>(R.id.icon_label)
        val deleteBtn = view.findViewById<View>(R.id.btn_delete_icon)

        iconImg.setImageDrawable(data.app.resolvedIcon(iconPackManager))
        label.text = data.app.label

        // Position
        val lp = FrameLayout.LayoutParams(iconSizePx, iconSizePx + (24 * context.resources.displayMetrics.density).toInt())
        lp.leftMargin = data.x.toInt()
        lp.topMargin = data.y.toInt()
        view.layoutParams = lp

        // Tap → launch (only if not edit mode)
        view.setOnClickListener {
            if (!editMode) onLaunch(data.app.packageName)
        }

        // Long press → enter edit mode
        view.setOnLongClickListener {
            setEditMode(true)
            true
        }

        // Delete button
        deleteBtn.setOnClickListener {
            removeIcon(data.app.packageName)
        }

        // Drag to reorder
        var downX = 0f
        var downY = 0f
        var isDragging = false

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX - data.x
                    downY = event.rawY - data.y
                    isDragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = Math.abs(event.rawX - (downX + data.x))
                    val dy = Math.abs(event.rawY - (downY + data.y))
                    if (dx > 10 || dy > 10) {
                        isDragging = true
                        val newX = (event.rawX - downX).coerceAtLeast(0f)
                            .coerceAtMost((grid.width - iconSizePx).toFloat())
                        val newY = (event.rawY - downY).coerceAtLeast(0f)
                            .coerceAtMost((grid.height - iconSizePx).toFloat())
                        data.x = newX
                        data.y = newY
                        val lp2 = v.layoutParams as FrameLayout.LayoutParams
                        lp2.leftMargin = newX.toInt()
                        lp2.topMargin = newY.toInt()
                        v.layoutParams = lp2
                    }
                    isDragging
                }
                MotionEvent.ACTION_UP -> {
                    isDragging = false
                    false
                }
                else -> false
            }
        }

        if (editMode) {
            deleteBtn.visibility = View.VISIBLE
            startWiggle(view)
        }

        grid.addView(view)
        iconViews[data.app.packageName] = view
    }

    private fun removeIcon(packageName: String) {
        val idx = icons.indexOfFirst { it.app.packageName == packageName }
        if (idx >= 0) {
            icons.removeAt(idx)
            val view = iconViews.remove(packageName)
            view?.let { grid.removeView(it) }
        }
        if (icons.isEmpty()) setEditMode(false)
    }

    private fun startWiggle(view: View) {
        val wiggle = android.animation.ObjectAnimator.ofFloat(view, "rotation", -3f, 3f)
        wiggle.duration = 150
        wiggle.repeatMode = android.animation.ObjectAnimator.REVERSE
        wiggle.repeatCount = android.animation.ObjectAnimator.INFINITE
        wiggle.tag = "wiggle"
        wiggle.start()
    }

    private fun stopWiggle(view: View) {
        view.clearAnimation()
        view.rotation = 0f
    }
}
