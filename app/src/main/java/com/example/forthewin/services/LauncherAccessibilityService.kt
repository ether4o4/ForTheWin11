package com.example.forthewin.services

import android.view.accessibility.AccessibilityEvent
import android.accessibilityservice.AccessibilityService

class LauncherAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Handle gestures or window changes here
    }

    override fun onInterrupt() {
    }
}
