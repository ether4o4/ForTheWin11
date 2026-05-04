package com.example.forthewin

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.InputStream

/**
 * Handles wallpaper reading and setting.
 * Uses the system WallpaperManager — requires SET_WALLPAPER permission (already in manifest).
 */
object WallpaperHelper {

    /** Apply a wallpaper from URI (from gallery picker result) */
    fun setFromUri(context: Context, uri: Uri): Boolean {
        return try {
            val wm = WallpaperManager.getInstance(context)
            val input: InputStream = context.contentResolver.openInputStream(uri) ?: return false
            wm.setStream(input)
            input.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Apply a bitmap directly (e.g. color/gradient generated wallpaper) */
    fun setFromBitmap(context: Context, bitmap: Bitmap): Boolean {
        return try {
            val wm = WallpaperManager.getInstance(context)
            wm.setBitmap(bitmap)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Get system wallpaper as drawable (for preview in picker) */
    fun getCurrentDrawable(context: Context) =
        WallpaperManager.getInstance(context).drawable
}
