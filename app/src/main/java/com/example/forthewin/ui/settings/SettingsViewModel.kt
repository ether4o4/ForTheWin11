package com.example.forthewin.ui.settings

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.forthewin.ui.controllers.TaskbarManager

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREFS_NAME = "forthewin_settings"
        private const val KEY_WALLPAPER_URI = "wallpaper_uri"
        private const val KEY_ICON_PACK = "icon_pack_package"
        private const val KEY_ALIGNMENT = "start_button_alignment"
        private const val KEY_SORT_ORDER = "file_sort_order"
        private const val KEY_CATEGORY_FILTER = "file_category_filter"
    }

    private val prefs: SharedPreferences =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _wallpaperUri = MutableLiveData(prefs.getString(KEY_WALLPAPER_URI, null))
    val wallpaperUri: LiveData<String?> = _wallpaperUri

    private val _iconPackPackage = MutableLiveData(prefs.getString(KEY_ICON_PACK, null))
    val iconPackPackage: LiveData<String?> = _iconPackPackage

    private val _alignment = MutableLiveData(
        prefs.getString(KEY_ALIGNMENT, TaskbarManager.Alignment.LEFT.name)
    )
    val alignment: LiveData<String> = _alignment

    private val _sortOrder = MutableLiveData(prefs.getString(KEY_SORT_ORDER, "DATE_DESC"))
    val sortOrder: LiveData<String> = _sortOrder

    private val _categoryFilter = MutableLiveData(prefs.getString(KEY_CATEGORY_FILTER, null))
    val categoryFilter: LiveData<String?> = _categoryFilter

    fun saveWallpaperUri(uri: String?) {
        prefs.edit().putString(KEY_WALLPAPER_URI, uri).apply()
        _wallpaperUri.value = uri
    }

    fun saveIconPack(pkg: String?) {
        prefs.edit().putString(KEY_ICON_PACK, pkg).apply()
        _iconPackPackage.value = pkg
    }

    fun saveAlignment(a: String) {
        prefs.edit().putString(KEY_ALIGNMENT, a).apply()
        _alignment.value = a
    }

    fun saveSortOrder(o: String) {
        prefs.edit().putString(KEY_SORT_ORDER, o).apply()
        _sortOrder.value = o
    }

    fun saveCategoryFilter(c: String?) {
        prefs.edit().putString(KEY_CATEGORY_FILTER, c).apply()
        _categoryFilter.value = c
    }
}