package com.example.forthewin

import android.content.Context
import android.content.SharedPreferences

/**
 * ThemeManager — persists and provides the current theme state.
 *
 * Modes:
 *   isDark  = false → Light (white background, dark text)
 *   isDark  = true  → Dark  (dark background, light text)
 *
 * Accent:
 *   accentRed = true  → Win11 Red   (#E81123) — DEFAULT (matches WinX red theme)
 *   accentRed = false → Win11 Blue  (#0078D4)
 */
object ThemeManager {
    private const val PREFS = "theme_prefs"
    private const val KEY_DARK   = "is_dark"
    private const val KEY_RED    = "accent_red"
    private const val KEY_START_ICON_SIZE = "start_icon_size"
    private const val KEY_START_COLUMNS  = "start_columns"

    // Colors — Red theme (WinX style)
    const val RED_ACCENT   = 0xFFE81123.toInt()
    const val RED_DIM      = 0x33E81123
    const val RED_MEDIUM   = 0x88E81123.toInt()

    // Colors — Blue theme (classic Win11)
    const val BLUE_ACCENT  = 0xFF0078D4.toInt()
    const val BLUE_DIM     = 0x330078D4

    // Taskbar dark purple (matching WinX)
    const val TASKBAR_DARK = 0xFF1B1035.toInt()
    const val TASKBAR_LIGHT = 0xFFF3F3F3.toInt()

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isDark(ctx: Context)      = prefs(ctx).getBoolean(KEY_DARK, false)
    fun isRedAccent(ctx: Context) = prefs(ctx).getBoolean(KEY_RED, true) // RED by default

    fun setDark(ctx: Context, dark: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_DARK, dark).apply()

    fun setRedAccent(ctx: Context, red: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_RED, red).apply()

    /** The currently active accent color int */
    fun accent(ctx: Context): Int = if (isRedAccent(ctx)) RED_ACCENT else BLUE_ACCENT
    fun accentDim(ctx: Context): Int = if (isRedAccent(ctx)) RED_DIM else BLUE_DIM

    /** Surface background for panels */
    fun surfaceBg(ctx: Context): Int =
        if (isDark(ctx)) 0xFF1C1C1E.toInt() else 0xFFF5F5F5.toInt()

    fun panelText(ctx: Context): Int =
        if (isDark(ctx)) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()

    fun panelTextSecondary(ctx: Context): Int =
        if (isDark(ctx)) 0x99FFFFFF.toInt() else 0x99000000.toInt()

    fun cardBg(ctx: Context): Int =
        if (isDark(ctx)) 0xFF2C2C2E.toInt() else 0xFFFAFAFA.toInt()

    /** Taskbar background — dark indigo/purple by default for red theme */
    fun taskbarBg(ctx: Context): Int {
        if (isDark(ctx)) return 0xE6202023.toInt()
        return if (isRedAccent(ctx)) TASKBAR_DARK else 0xFFFFFFFF.toInt()
    }

    /** Taskbar text colors */
    fun taskbarTextPrimary(ctx: Context): Int {
        return if (isDark(ctx) || isRedAccent(ctx)) 0xFFFFFFFF.toInt() else 0xFF1A1A1A.toInt()
    }

    fun taskbarTextSecondary(ctx: Context): Int {
        return if (isDark(ctx) || isRedAccent(ctx)) 0xAAFFFFFF.toInt() else 0x88000000.toInt()
    }

    fun taskbarIconTint(ctx: Context): Int {
        return if (isDark(ctx) || isRedAccent(ctx)) 0xFFFFFFFF.toInt() else 0xFF444444.toInt()
    }

    // Start menu icon size (dp)
    fun startIconSize(ctx: Context): Int = prefs(ctx).getInt(KEY_START_ICON_SIZE, 42)
    fun setStartIconSize(ctx: Context, dp: Int) =
        prefs(ctx).edit().putInt(KEY_START_ICON_SIZE, dp).apply()

    // Start menu pinned grid columns
    fun startColumns(ctx: Context): Int = prefs(ctx).getInt(KEY_START_COLUMNS, 3)
    fun setStartColumns(ctx: Context, cols: Int) =
        prefs(ctx).edit().putInt(KEY_START_COLUMNS, cols).apply()
}
