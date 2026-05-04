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
 *   accentRed = false → Win11 Blue  (#0078D4)
 *   accentRed = true  → Win11 Red   (#E74856)
 */
object ThemeManager {
    private const val PREFS = "theme_prefs"
    private const val KEY_DARK   = "is_dark"
    private const val KEY_RED    = "accent_red"

    // Colors
    const val BLUE_ACCENT  = 0xFF0078D4.toInt()
    const val RED_ACCENT   = 0xFFE74856.toInt()
    const val BLUE_DIM     = 0x330078D4.toInt()
    const val RED_DIM      = 0x33E74856.toInt()

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isDark(ctx: Context)      = prefs(ctx).getBoolean(KEY_DARK, false)
    fun isRedAccent(ctx: Context) = prefs(ctx).getBoolean(KEY_RED,  false)

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

    fun taskbarBg(ctx: Context): Int =
        if (isDark(ctx)) 0xE6202023.toInt() else 0xFFFFFFFF.toInt()
}
