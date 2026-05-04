package com.example.forthewin

import android.graphics.drawable.Drawable

data class AppModel(
    val label: String,
    val packageName: String,
    val activityName: String,       // fully qualified activity class name
    val icon: Drawable              // system icon (always set)
) {
    /**
     * Returns the best available icon: icon pack drawable if loaded, else system icon.
     * Pass the IconPackManager from LauncherApplication.
     */
    fun resolvedIcon(iconPackManager: IconPackManager): Drawable {
        return iconPackManager.getIconForPackage(packageName, activityName) ?: icon
    }
}
