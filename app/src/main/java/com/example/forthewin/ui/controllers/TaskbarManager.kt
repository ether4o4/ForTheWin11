package com.example.forthewin.ui.controllers

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.res.ResourcesCompat
import com.example.forthewin.R
import com.example.forthewin.databinding.LayoutCustomTaskbarBinding

class TaskbarManager(
    private val context: Context,
    private val binding: LayoutCustomTaskbarBinding,
    private val onAppClick: (String) -> Unit
) {
    private val taskbarIcons = mutableMapOf<View, ImageView>()

    fun addWindowIcon(title: String, iconRes: Int, window: View) {
        val icon = ImageView(context)
        val density = context.resources.displayMetrics.density
        val size = (48 * density).toInt()
        val margin = (4 * density).toInt()
        val padding = (8 * density).toInt()
        
        val params = LinearLayout.LayoutParams(size, size)
        params.setMargins(margin, 0, margin, 0)
        icon.layoutParams = params
        icon.setImageResource(iconRes)
        icon.setPadding(padding, padding, padding, padding)
        icon.background = ResourcesCompat.getDrawable(context.resources, R.drawable.sidebar_item_active, null)
        icon.backgroundTintList = ColorStateList.valueOf(ResourcesCompat.getColor(context.resources, R.color.vista_red_accent, null))
        icon.tag = window
        
        icon.setOnClickListener {
            window.visibility = if (window.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (window.visibility == View.VISIBLE) {
                window.bringToFront()
            }
        }
        
        binding.taskbarItemsContainer.addView(icon)
        taskbarIcons[window] = icon
    }

    fun removeWindowIcon(window: View) {
        taskbarIcons[window]?.let { icon ->
            binding.taskbarItemsContainer.removeView(icon)
            taskbarIcons.remove(window)
        }
    }
}
