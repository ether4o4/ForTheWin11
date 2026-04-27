package com.example.forthewin.ui.controllers

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

class WidgetHostManager(private val activity: ComponentActivity) {

    private val APPWIDGET_HOST_ID = 1024
    private val appWidgetManager = AppWidgetManager.getInstance(activity)
    private val appWidgetHost = AppWidgetHost(activity, APPWIDGET_HOST_ID)

    fun startListening() {
        appWidgetHost.startListening()
    }

    fun stopListening() {
        appWidgetHost.stopListening()
    }

    fun pickWidget(launcher: ActivityResultLauncher<Intent>) {
        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        launcher.launch(pickIntent)
    }

    fun createWidget(appWidgetId: Int, container: ViewGroup) {
        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
        val hostView = appWidgetHost.createView(activity, appWidgetId, appWidgetInfo)
        hostView.setAppWidget(appWidgetId, appWidgetInfo)
        container.addView(hostView)
    }
}
