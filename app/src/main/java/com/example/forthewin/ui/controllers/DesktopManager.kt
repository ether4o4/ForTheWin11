package com.example.forthewin.ui.controllers

import android.animation.ObjectAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
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
    private val grid: FrameLayout,
    private val iconPackManager: IconPackManager,
    private val onLaunch: (String) -> Unit
) {
    private val icons = mutableListOf<DesktopIconData>()
    private var editMode = false
    private val iconViews = mutableMapOf<String, View>()
    private val wiggleAnimators = mutableMapOf<String, ObjectAnimator>()

    data class DesktopIconData(
        val app: AppModel,
        var x: Float,
        var y: Float
    )

    private val iconSizePx: Int get() = (80 * context.resources.displayMetrics.density).toInt()
    private val labelHeightPx: Int get() = (24 * context.resources.displayMetrics.density).toInt()
    private val iconSpacingPx: Int get() = (8 * context.resources.displayMetrics.density).toInt()

    /** Append a single icon at the next available position and render it. */
    fun addSingleIcon(app: AppModel) {
        if (icons.any { it.app.packageName == app.packageName }) return
        val idx = icons.size
        val x = iconSpacingPx.toFloat()
        val y = (iconSpacingPx + idx * (iconSizePx + labelHeightPx + iconSpacingPx)).toFloat()
        val data = DesktopIconData(app, x, y)
        icons.add(data)
        addIconView(data)
    }

    fun setIcons(apps: List<AppModel>) {
        // Cancel all animators before clearing
        wiggleAnimators.values.forEach { it.cancel() }
        wiggleAnimators.clear()
        icons.clear()
        apps.forEachIndexed { i, app ->
            val x = iconSpacingPx.toFloat()
            val y = (i * (iconSizePx + iconSpacingPx)).toFloat()
            icons.add(DesktopIconData(app, x, y))
        }
        renderAll()
    }

    fun setEditMode(enabled: Boolean) {
        editMode = enabled
        iconViews.entries.forEach { (pkg, view) ->
            val deleteBtn = view.findViewById<View>(R.id.btn_delete_icon)
            deleteBtn?.visibility = if (enabled) View.VISIBLE else View.GONE
            if (enabled) {
                startWiggle(pkg, view)
            } else {
                stopWiggle(pkg, view)
            }
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

        val totalHeight = iconSizePx + labelHeightPx
        val lp = FrameLayout.LayoutParams(iconSizePx, totalHeight)
        lp.leftMargin = data.x.toInt()
        lp.topMargin = data.y.toInt()
        view.layoutParams = lp

        view.setOnClickListener {
            if (!editMode) onLaunch(data.app.packageName)
        }

        view.setOnLongClickListener {
            setEditMode(true)
            true
        }

        deleteBtn?.setOnClickListener {
            removeIcon(data.app.packageName)
        }

        // Drag — only move if not accidentally clicking
        var downRawX = 0f
        var downRawY = 0f
        var dragging = false
        val DRAG_THRESHOLD = 12f * context.resources.displayMetrics.density

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX - data.x
                    downRawY = event.rawY - data.y
                    dragging = false
                    // Return false so click/longclick still fire
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = Math.abs(event.rawX - (downRawX + data.x))
                    val dy = Math.abs(event.rawY - (downRawY + data.y))
                    if (!dragging && (dx > DRAG_THRESHOLD || dy > DRAG_THRESHOLD)) {
                        dragging = true
                        // Enter edit mode on drag
                        if (!editMode) setEditMode(true)
                    }
                    if (dragging) {
                        val newX = (event.rawX - downRawX)
                            .coerceAtLeast(0f)
                            .coerceAtMost((grid.width - iconSizePx).toFloat().coerceAtLeast(0f))
                        val newY = (event.rawY - downRawY)
                            .coerceAtLeast(0f)
                            .coerceAtMost((grid.height - totalHeight).toFloat().coerceAtLeast(0f))
                        data.x = newX
                        data.y = newY
                        val lp2 = v.layoutParams as FrameLayout.LayoutParams
                        lp2.leftMargin = newX.toInt()
                        lp2.topMargin = newY.toInt()
                        v.layoutParams = lp2
                        true
                    } else false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Snap wiggle back to steady rotation=0 briefly then resume if still edit mode
                    if (dragging) {
                        dragging = false
                        if (editMode) {
                            // Re-start wiggle cleanly after drag completes
                            stopWiggle(data.app.packageName, v)
                            v.postDelayed({ if (editMode) startWiggle(data.app.packageName, v) }, 50)
                        }
                        true // consume so click doesn't fire after drag
                    } else {
                        dragging = false
                        false
                    }
                }
                else -> false
            }
        }

        if (editMode) {
            deleteBtn?.visibility = View.VISIBLE
            startWiggle(data.app.packageName, view)
        }

        grid.addView(view)
        iconViews[data.app.packageName] = view
    }

    private fun removeIcon(packageName: String) {
        val idx = icons.indexOfFirst { it.app.packageName == packageName }
        if (idx >= 0) {
            icons.removeAt(idx)
            stopWiggle(packageName, iconViews[packageName])
            val view = iconViews.remove(packageName)
            view?.let { grid.removeView(it) }
        }
        if (icons.isEmpty()) setEditMode(false)
    }

    private fun startWiggle(packageName: String, view: View) {
        // Cancel any existing animator for this icon first
        wiggleAnimators[packageName]?.cancel()
        view.rotation = 0f
        val anim = ObjectAnimator.ofFloat(view, "rotation", -3f, 3f).apply {
            duration = 150
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
        wiggleAnimators[packageName] = anim
    }

    private fun stopWiggle(packageName: String, view: View?) {
        wiggleAnimators[packageName]?.cancel()
        wiggleAnimators.remove(packageName)
        view?.rotation = 0f
        view?.clearAnimation()
    }
}
