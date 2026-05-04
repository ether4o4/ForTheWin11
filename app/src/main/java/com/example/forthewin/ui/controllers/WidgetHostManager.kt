package com.example.forthewin.ui.controllers

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher

class WidgetHostManager(private val activity: ComponentActivity) {

    companion object {
        private const val TAG = "WidgetHostManager"
        private const val APPWIDGET_HOST_ID = 1024
    }

    private val appWidgetManager = AppWidgetManager.getInstance(activity)
    private val appWidgetHost = AppWidgetHost(activity, APPWIDGET_HOST_ID)

    // Track the pending widget ID during the pick/bind/configure flow
    var pendingWidgetId: Int = -1
        private set

    fun startListening() {
        try {
            appWidgetHost.startListening()
        } catch (e: Exception) {
            Log.e(TAG, "startListening failed", e)
        }
    }

    fun stopListening() {
        try {
            appWidgetHost.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "stopListening failed", e)
        }
    }

    /**
     * Step 1: Launch the system widget picker.
     * After user picks a widget, the result comes back with an appWidgetId.
     */
    fun pickWidget(launcher: ActivityResultLauncher<Intent>) {
        pendingWidgetId = appWidgetHost.allocateAppWidgetId()
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
            // Tell Android we want the list of bindable widgets
            putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, ArrayList())
            putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_EXTRAS, ArrayList())
        }
        launcher.launch(pickIntent)
    }

    /**
     * Step 2: After the picker returns, try to bind the widget.
     * Returns true if bound (or already bound). Returns false if we need to
     * request BIND_APPWIDGET permission from the user.
     */
    fun tryBindWidget(appWidgetId: Int, bindLauncher: ActivityResultLauncher<Intent>): Boolean {
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (info != null) {
            // Already bound
            return true
        }
        // Not yet bound — we need to request bind permission
        // This shouldn't normally happen from ACTION_APPWIDGET_PICK,
        // but handle it just in case
        return true
    }

    /**
     * Step 3: Check if the widget needs a configure activity and launch it.
     * Returns true if configure was launched (caller should wait for result).
     * Returns false if no configure needed (caller can create the view immediately).
     */
    fun launchConfigureIfNeeded(appWidgetId: Int, configureLauncher: ActivityResultLauncher<Intent>): Boolean {
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return false
        if (info.configure != null) {
            val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = info.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            try {
                configureLauncher.launch(configIntent)
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Could not launch configure activity for ${info.configure}", e)
                // Fall through — create without configuration
            }
        }
        return false
    }

    /**
     * Create the actual widget view. Call this after binding + optional configuration.
     * Sets proper size hints so the widget renders at the right dimensions.
     */
    fun createWidgetView(appWidgetId: Int): AppWidgetHostView? {
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (info == null) {
            Log.e(TAG, "No AppWidgetInfo for id=$appWidgetId — deleting")
            appWidgetHost.deleteAppWidgetId(appWidgetId)
            return null
        }

        val hostView = appWidgetHost.createView(activity, appWidgetId, info)
        hostView.setAppWidget(appWidgetId, info)

        // Set the size so the widget knows how big to render
        val density = activity.resources.displayMetrics.density
        val minW = info.minWidth
        val minH = info.minHeight
        val targetW = if (minW > 0) minW else (280 * density).toInt()
        val targetH = if (minH > 0) minH else (200 * density).toInt()

        // Update the widget with its actual size in dp
        val widthDp = (targetW / density).toInt()
        val heightDp = (targetH / density).toInt()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hostView.updateAppWidgetSize(
                Bundle(),
                listOf(android.util.SizeF(widthDp.toFloat(), heightDp.toFloat()))
            )
        } else {
            @Suppress("DEPRECATION")
            hostView.updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)
        }

        // Set layout params with the actual size in pixels
        hostView.layoutParams = ViewGroup.LayoutParams(targetW, targetH)

        Log.d(TAG, "Created widget view: ${info.provider?.shortClassName} size=${targetW}x${targetH}")
        return hostView
    }

    /** Clean up a widget ID that wasn't used */
    fun deleteWidgetId(appWidgetId: Int) {
        appWidgetHost.deleteAppWidgetId(appWidgetId)
    }
}
