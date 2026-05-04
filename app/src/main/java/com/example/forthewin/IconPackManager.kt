package com.example.forthewin

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

/**
 * IconPackManager
 *
 * Loads icon packs compatible with:
 * - Icon Pack Studio (ADW/Nova/Apex filter format)
 * - Any pack exposing org.adw.launcher.THEMES or com.novalauncher.THEME intent
 *
 * Usage:
 *   val mgr = IconPackManager(context)
 *   mgr.loadPack("com.iconpackstudio.pack.xxx")
 *   val icon: Drawable? = mgr.getIconForPackage("com.spotify.music")
 */
class IconPackManager(private val context: Context) {

    companion object {
        private const val TAG = "IconPackManager"
        private const val PREFS = "icon_pack_prefs"
        private const val KEY_PACK = "selected_pack"

        // Intent actions that icon packs register
        private val PACK_INTENTS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.teslacoilsw.launcher.THEME",
            "com.anddoes.launcher.THEME",
            "net.oneplus.launcher.icons.ACTION_PICK_ICON",
            "com.icon.pack.ADW",
            "com.icon.pack.GO",
            "com.icon.pack.NOVA"
        )
    }

    // ComponentInfo string → drawable name, loaded from appfilter.xml
    private val iconMap = HashMap<String, String>()
    private var packResources: Resources? = null
    private var packPackageName: String? = null

    init {
        val savedPack = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PACK, null)
        if (savedPack != null) {
            loadPack(savedPack)
        }
    }

    /** Returns list of (label, packageName) for all installed icon packs */
    fun getInstalledPacks(): List<Pair<String, String>> {
        val pm = context.packageManager
        val packs = mutableListOf<Pair<String, String>>()
        val seen = mutableSetOf<String>()

        for (action in PACK_INTENTS) {
            val intent = Intent(action)
            val resolveInfos = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
            for (info in resolveInfos) {
                val pkg = info.activityInfo.packageName
                if (seen.add(pkg)) {
                    val label = pm.getApplicationLabel(
                        pm.getApplicationInfo(pkg, 0)
                    ).toString()
                    packs.add(Pair(label, pkg))
                }
            }
        }
        return packs.sortedBy { it.first }
    }

    /** Load an icon pack by package name. Call this when user selects a pack. */
    fun loadPack(packageName: String): Boolean {
        return try {
            packResources = context.packageManager.getResourcesForApplication(packageName)
            packPackageName = packageName
            iconMap.clear()
            parseAppFilter(packageName)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_PACK, packageName).apply()
            Log.d(TAG, "Loaded icon pack: $packageName — ${iconMap.size} mappings")
            true
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Icon pack not found: $packageName", e)
            clearPack()
            false
        }
    }

    /** Clear the active icon pack, revert to system icons */
    fun clearPack() {
        iconMap.clear()
        packResources = null
        packPackageName = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_PACK).apply()
    }

    /** Get the currently loaded pack package name, or null */
    fun getActivePack(): String? = packPackageName

    /**
     * Get icon for a package name.
     * Tries to match via appfilter.xml mapping, then falls back to
     * drawable name heuristics, then returns null (caller should use system icon).
     */
    fun getIconForPackage(packageName: String, activityName: String? = null): Drawable? {
        val res = packResources ?: return null
        val pkg = packPackageName ?: return null

        // Build component key the same way appfilter.xml stores it
        val componentKey = if (activityName != null) {
            "ComponentInfo{$packageName/$activityName}"
        } else {
            // Try all stored keys that start with this package
            iconMap.keys.firstOrNull { it.startsWith("ComponentInfo{$packageName/") }
                ?: "ComponentInfo{$packageName/$packageName}"
        }

        val drawableName = iconMap[componentKey]
            ?: iconMap["ComponentInfo{$packageName/$packageName}"]
            ?: guessDrawableName(packageName)
            ?: return null

        return loadDrawable(res, pkg, drawableName)
    }

    /** Get icon directly by drawable name (for custom tiles etc.) */
    fun getIconByName(drawableName: String): Drawable? {
        val res = packResources ?: return null
        val pkg = packPackageName ?: return null
        return loadDrawable(res, pkg, drawableName)
    }

    /** True if a pack is currently loaded */
    fun isPackLoaded(): Boolean = packResources != null && iconMap.isNotEmpty()

    // ── Private ──────────────────────────────────────────────────────────────

    private fun parseAppFilter(packageName: String) {
        val res = packResources ?: return
        val filterId = res.getIdentifier("appfilter", "xml", packageName)
        if (filterId == 0) {
            Log.w(TAG, "No appfilter.xml found in $packageName")
            // Some packs use a raw xml folder name "drawable" only — try alternate
            parseDrawableXml(packageName)
            return
        }

        try {
            val parser: XmlPullParser = res.getXml(filterId)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (component != null && drawable != null) {
                        iconMap[component] = drawable
                    }
                }
                event = parser.next()
            }
        } catch (e: XmlPullParserException) {
            Log.e(TAG, "Failed to parse appfilter.xml", e)
        } catch (e: IOException) {
            Log.e(TAG, "IO error reading appfilter.xml", e)
        }
    }

    /** Some packs use drawable.xml instead of appfilter.xml */
    private fun parseDrawableXml(packageName: String) {
        val res = packResources ?: return
        val drawableId = res.getIdentifier("drawable", "xml", packageName)
        if (drawableId == 0) return
        try {
            val parser: XmlPullParser = res.getXml(drawableId)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    val drawable = parser.getAttributeValue(null, "drawable")
                        ?: parser.getAttributeValue(null, "name")
                    if (drawable != null) {
                        // No component mapping here — store by drawable name only
                        // Used for fallback guessing
                        iconMap["__drawable__$drawable"] = drawable
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse drawable.xml", e)
        }
    }

    /** Try common naming conventions before giving up */
    private fun guessDrawableName(packageName: String): String? {
        val res = packResources ?: return null
        val pkg = packPackageName ?: return null

        // Common patterns icon pack devs use
        val candidates = listOf(
            packageName.replace(".", "_"),                     // com_spotify_music
            packageName.substringAfterLast("."),               // music
            packageName.replace(".", "_").lowercase(),
            packageName.substringAfterLast(".").lowercase()
        )

        for (name in candidates) {
            val id = res.getIdentifier(name, "drawable", pkg)
            if (id != 0) return name
        }
        return null
    }

    private fun loadDrawable(res: Resources, pkg: String, drawableName: String): Drawable? {
        return try {
            val id = res.getIdentifier(drawableName, "drawable", pkg)
            if (id == 0) null
            else res.getDrawable(id, null)
        } catch (e: Resources.NotFoundException) {
            null
        }
    }
}
