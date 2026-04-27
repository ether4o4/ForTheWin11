package com.example.forthewin.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        Log.d("NotificationService", "Notification posted: ${sbn?.packageName}")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        Log.d("NotificationService", "Notification removed: ${sbn?.packageName}")
    }
}
