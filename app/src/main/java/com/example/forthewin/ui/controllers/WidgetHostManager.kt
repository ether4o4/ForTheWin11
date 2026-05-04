package com.example.forthewin.ui.controllers

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher

class WidgetHostManager(private val activity: ComponentActivity) {

    private val APPWIDGET_HOST_ID = 1024
    private val appWidgetManager = AppWidgetManager.getInstance(activity)
    private val appWidgetHost = AppWidgetHost(activity, APPWIDGET_HOST_ID)

    fun startListening() { appWidgetHost.startListening() }
    fun stopListening() { appWidgetHost.stopListening() }

    fun pickWidget(launcher: ActivityResultLauncher<Intent>) {
        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        launcher.launch(pickIntent)
    }

    /** Returns the widget HostView — caller wraps it in WidgetManager card */
    fun createWidgetView(appWidgetId: Int): AppWidgetHostView? {
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return null
        val hostView = appWidgetHost.createView(activity, appWidgetId, info)
        hostView.setAppWidget(appWidgetId, info)
        return hostView
    }

    /** Legacy: add directly to a container (kept for compat) */
    fun createWidget(appWidgetId: Int, container: ViewGroup) {
        val view = createWidgetView(appWidgetId) ?: return
        container.addView(view)
    }
}
