package com.example.forthewin

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import java.io.InputStream

object WallpaperHelper {

    fun setFromUri(context: Context, uri: Uri): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val wm = WallpaperManager.getInstance(context)
                val mgr = wm.createWallpaperManager()
                val intent = mgr.setWallpaperRequestIntent(uri)
                context.startActivity(intent)
                true
            } else {
                val wm = WallpaperManager.getInstance(context)
                val input: InputStream = context.contentResolver.openInputStream(uri) ?: return false
                wm.setStream(input)
                input.close()
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun setFromBitmap(context: Context, bitmap: Bitmap): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val wm = WallpaperManager.getInstance(context)
                val mgr = wm.createWallpaperManager()
                val intent = mgr.setWallpaperRequestIntent(null)
                context.startActivity(intent)
                true
            } else {
                val wm = WallpaperManager.getInstance(context)
                wm.setBitmap(bitmap)
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getCurrentDrawable(context: Context) =
        WallpaperManager.getInstance(context).drawable
}