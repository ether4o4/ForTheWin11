package com.example.forthewin.ui.controllers

import android.content.Context
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.example.forthewin.AppModel
import com.example.forthewin.IconPackManager

class DesktopManager(
    private val context: Context,
    private val iconPackManager: IconPackManager,
    private val lifecycleOwner: LifecycleOwner,
    private val gridLayout: GridLayout
) {

    private var apps: List<AppModel> = emptyList()

    init {
        iconPackManager.iconPackChanged.observe(lifecycleOwner, Observer { _ ->
            refreshIcons()
        })
    }

    fun setApps(apps: List<AppModel>) {
        this.apps = apps
        renderIcons()
    }

    fun refreshIcons() {
        for (i in 0 until gridLayout.childCount) {
            val child = gridLayout.getChildAt(i)
            val app = child.tag as? AppModel ?: continue
            val iconView = child as? ImageView ?: continue
            iconView.setImageDrawable(app.resolvedIcon(iconPackManager))
        }
    }

    private fun renderIcons() {
        gridLayout.removeAllViews()
        for (app in apps) {
            val view = ImageView(context)
            view.setImageDrawable(app.resolvedIcon(iconPackManager))
            view.tag = app
            view.layoutParams = ViewGroup.LayoutParams(96, 96)
            view.setOnClickListener { }
            gridLayout.addView(view)
        }
    }
}