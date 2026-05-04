package com.example.forthewin.ui.controllers

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.SizeF
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

    var pendingWidgetId: Int = -1
        private set

    fun startListening() {
        try { appWidgetHost.startListening() }
        catch (e: Exception) { Log.e(TAG, "startListening", e) }
    }

    fun stopListening() {
        try { appWidgetHost.stopListening() }
        catch (e: Exception) { Log.e(TAG, "stopListening", e) }
    }

    /** Launch the system widget picker */
    fun pickWidget(launcher: ActivityResultLauncher<Intent>) {
        try {
            pendingWidgetId = appWidgetHost.allocateAppWidgetId()
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
            }
            launcher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "pickWidget failed", e)
            safeDeleteId(pendingWidgetId)
            pendingWidgetId = -1
        }
    }

    /**
     * Check if widget needs a configure activity. Returns true if launched.
     */
    fun launchConfigureIfNeeded(appWidgetId: Int, launcher: ActivityResultLauncher<Intent>): Boolean {
        try {
            val info = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return false
            val configComp = info.configure ?: return false

            // Verify the configure activity actually exists before launching
            val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = configComp
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val resolveInfo = activity.packageManager.resolveActivity(configIntent, 0)
            if (resolveInfo == null) {
                Log.w(TAG, "Configure activity $configComp not resolvable, skipping")
                return false
            }

            launcher.launch(configIntent)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "launchConfigure failed", e)
            return false
        }
    }

    /** Create the widget view with proper sizing. Returns null on failure. */
    fun createWidgetView(appWidgetId: Int): AppWidgetHostView? {
        return try {
            val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
            if (info == null) {
                Log.e(TAG, "No info for widget $appWidgetId")
                return null
            }

            val hostView = appWidgetHost.createView(activity, appWidgetId, info)
            hostView.setAppWidget(appWidgetId, info)

            val density = activity.resources.displayMetrics.density

            // info.minWidth/minHeight are already in dp
            val wDp = if (info.minWidth > 0) info.minWidth else 280
            val hDp = if (info.minHeight > 0) info.minHeight else 200

            // Tell the widget its size
            try {
                hostView.updateAppWidgetSize(
                    Bundle(), listOf(SizeF(wDp.toFloat(), hDp.toFloat()))
                )
            } catch (e: Exception) {
                Log.w(TAG, "updateAppWidgetSize (new API) failed, trying legacy", e)
                try {
                    @Suppress("DEPRECATION")
                    hostView.updateAppWidgetSize(null, wDp, hDp, wDp, hDp)
                } catch (e2: Exception) {
                    Log.w(TAG, "updateAppWidgetSize (legacy) also failed", e2)
                }
            }

            // Convert dp to px for layout params
            val wPx = (wDp * density).toInt()
            val hPx = (hDp * density).toInt()
            hostView.layoutParams = ViewGroup.LayoutParams(wPx, hPx)

            Log.d(TAG, "Created widget: ${info.provider?.shortClassName} ${wDp}x${hDp}dp")
            hostView
        } catch (e: Exception) {
            Log.e(TAG, "createWidgetView crashed for id=$appWidgetId", e)
            null
        }
    }

    /** Safely delete a widget ID — won't crash on invalid IDs */
    fun safeDeleteId(id: Int) {
        if (id == -1) return
        try { appWidgetHost.deleteAppWidgetId(id) }
        catch (e: Exception) { Log.w(TAG, "deleteWidgetId($id) failed", e) }
    }
}
