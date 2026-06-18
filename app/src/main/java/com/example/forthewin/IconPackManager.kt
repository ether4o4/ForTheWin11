package com.example.forthewin

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.xmlpull.v1.XmlPullParser

class IconPackManager(private val context: Context) {

    private var currentPackPackage: String? = null
    private val iconCache = mutableMapOf<String, Drawable>()

    private val _iconPackChanged = MutableLiveData<String?>()
    val iconPackChanged: LiveData<String?> = _iconPackChanged

    fun loadIconPack(packageName: String) {
        currentPackPackage = packageName
        iconCache.clear()

        try {
            val pm = context.packageManager
            val packResources = pm.getResourcesForApplication(packageName)

            val filterPaths = listOf("appfilter.xml", "res/xml/appfilter.xml")
            for (path in filterPaths) {
                val resId = packResources.getIdentifier(
                    path.removeSuffix(".xml"), "xml", packageName
                )
                if (resId != 0) {
                    parseAppfilter(packResources.getXml(resId), pm, packageName)
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        _iconPackChanged.postValue(packageName)
    }

    fun getIconForPackage(packageName: String, activityName: String): Drawable? {
        val fullComponent = "$packageName/$activityName"
        iconCache[fullComponent]?.let { return it }
        iconCache[packageName]?.let { return it }
        return null
    }

    fun getCurrentPackPackage(): String? = currentPackPackage

    private fun parseAppfilter(parser: XmlPullParser, pm: PackageManager, packPkg: String) {
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                val component = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                if (component != null && drawable != null) {
                    try {
                        val res = pm.getResourcesForApplication(packPkg)
                        val resId = res.getIdentifier(drawable, "drawable", packPkg)
                        if (resId != 0) {
                            iconCache[component] = res.getDrawable(resId)
                        }
                    } catch (_: Exception) {}
                }
            }
            eventType = parser.next()
        }
    }
}