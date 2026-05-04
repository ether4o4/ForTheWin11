package com.example.forthewin.services

import android.app.Notification
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class NotificationService : NotificationListenerService() {

    companion object {
        const val ACTION_NOTIF_POSTED   = "com.forthewin.NOTIF_POSTED"
        const val ACTION_NOTIF_REMOVED  = "com.forthewin.NOTIF_REMOVED"
        const val ACTION_NOTIF_LIST     = "com.forthewin.NOTIF_LIST"
        const val EXTRA_PKG             = "pkg"
        const val EXTRA_TITLE           = "title"
        const val EXTRA_TEXT            = "text"
        const val EXTRA_KEY             = "key"
        const val EXTRA_WHEN            = "when"
        const val EXTRA_COUNT           = "count"

        /** Call this from MainActivity to request a full re-broadcast of current notifs */
        const val ACTION_REQUEST_LIST   = "com.forthewin.REQUEST_LIST"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn ?: return
        if (sbn.isOngoing) return // skip persistent (music, download etc.)
        broadcast(ACTION_NOTIF_POSTED, sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn ?: return
        val intent = Intent(ACTION_NOTIF_REMOVED).apply {
            putExtra(EXTRA_KEY, sbn.key)
            putExtra(EXTRA_PKG, sbn.packageName)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        broadcastAll()
    }

    private fun broadcastAll() {
        try {
            val active = activeNotifications ?: return
            val intent = Intent(ACTION_NOTIF_LIST).apply {
                putExtra(EXTRA_COUNT, active.size)
            }
            // Send list as parallel individual posted broadcasts so MainActivity can rebuild list
            active.forEach { sbn ->
                if (!sbn.isOngoing) broadcast(ACTION_NOTIF_POSTED, sbn)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        } catch (e: Exception) { /* service not ready */ }
    }

    private fun broadcast(action: String, sbn: StatusBarNotification) {
        val extras = sbn.notification?.extras ?: Bundle()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: sbn.packageName
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val body = bigText?.ifEmpty { text } ?: text

        val elapsed = System.currentTimeMillis() - sbn.postTime
        val timeStr = when {
            elapsed < 60_000       -> "${elapsed / 1000}s ago"
            elapsed < 3_600_000    -> "${elapsed / 60_000}m ago"
            else                   -> "${elapsed / 3_600_000}h ago"
        }

        val intent = Intent(action).apply {
            putExtra(EXTRA_KEY,   sbn.key)
            putExtra(EXTRA_PKG,   sbn.packageName)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_TEXT,  body)
            putExtra(EXTRA_WHEN,  timeStr)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
}
