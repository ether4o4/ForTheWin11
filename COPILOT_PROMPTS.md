# Copilot Prompts — Copy/Paste These In Order

---

## PROMPT 1: Fix the google-services build error

```
My build fails with "File google-services.json is missing" but I never added the google-services plugin. Check if there's any reference to `com.google.gms.google-services` in my build files (build.gradle.kts at root or app level, settings.gradle.kts). If there is, remove it — I don't use Firebase or Google services. If there isn't, the error might come from an Android Studio template. There's already a placeholder google-services.json in app/ — if the plugin isn't explicitly applied anywhere, just make sure nothing is requiring it.
```

---

## PROMPT 2: Fix compilation errors after layout changes

```
The app_bar_main.xml was changed from a CoordinatorLayout (with AppBarLayout/Toolbar) to a plain FrameLayout. This means:

1. `binding.appBarMain` is now a FrameLayout binding, not CoordinatorLayout
2. There is NO `binding.appBarMain.toolbar` anymore — remove any reference to it
3. `binding.appBarMain.contentMain` should still work since content_main.xml is included with android:id="@+id/content_main"

Check MainActivity.kt for any remaining references to `toolbar`, `fab`, `setSupportActionBar`, or `setupActionBarWithNavController` and remove them. The app no longer uses an ActionBar at all.

Also: the NavHostFragment in content_main.xml no longer has `android:visibility="gone"` — it's just 1dp x 1dp with `defaultNavHost="false"`. The `findFragmentById` cast should use safe cast `as? NavHostFragment` not `as NavHostFragment?` with `!!`.
```

---

## PROMPT 3: Fix any view binding issues with the new start menu layout

```
layout_start_menu.xml was rewritten. It now has these IDs that the code references:

- start_search_input (EditText)
- pinned_section (LinearLayout — the split horizontal layout containing left/right panels)
- all_apps_list (LinearLayout — inside left panel, always visible)
- pinned_apps_grid (GridLayout — inside right panel)
- recommended_section (LinearLayout — inside right panel, wraps recommended_container)
- recommended_container (LinearLayout)
- all_apps_scroll (ScrollView — hidden by default, shown during search)
- search_results_list (LinearLayout — inside all_apps_scroll)
- btn_power, btn_settings_quick, btn_lock_quick, btn_moon_quick (FrameLayouts)

In MainActivity.kt, the start menu binding is accessed as:
`binding.appBarMain.contentMain.startMenuPanel` which returns `LayoutStartMenuBinding?`

Make sure all code references match these IDs. The old layout had a different structure — if you see references to IDs that don't exist in the new layout, fix them.
```

---

## PROMPT 4: Verify the app compiles and runs

```
Build the debug APK. If there are compilation errors, fix them one by one. Common issues to check:
1. View binding generated class names matching the XML IDs
2. Import statements that reference removed classes
3. Any `lateinit` vars that might not be initialized before use
4. The `appBarConfiguration` variable — it should be initialized with a default value, not lateinit

After fixing compilation, install on device/emulator and check:
- App launches without crash
- Dark purple taskbar visible at bottom
- Tapping the Windows/Start button opens the start menu
- Start menu shows all apps on left, pinned grid on right
- Red accent color throughout (not blue)
```

---

## PROMPT 5: If you still see a blue bar at the top

```
There should be NO blue bar/toolbar at the top. Check:
1. app_bar_main.xml — should be a plain FrameLayout with just one <include> for content_main. NO AppBarLayout, NO Toolbar.
2. themes.xml (both values/ and values-night/) — parent should be Theme.MaterialComponents.DayNight.NoActionBar, with windowActionBar=false, windowNoTitle=true
3. MainActivity.kt — should NOT call setSupportActionBar() anywhere
4. AndroidManifest.xml — the activity theme should be @style/Theme.ForTheWin.NoActionBar
5. The status bar color should be transparent: android:statusBarColor=@android:color/transparent in theme

If you see a colored bar, it's likely the system status bar showing a non-transparent color. Set window.statusBarColor = Color.TRANSPARENT in onCreate.
```

---

## Summary of what was changed (tell Copilot this for context)

```
This is a Windows 11 launcher app for Android. Recent changes:

- Removed AppBarLayout/Toolbar entirely (was causing blue bar)
- Changed accent from blue (#0078D4) to red (#E81123)  
- Taskbar background: dark purple (#1B1035) with white text/icons
- Window title bars: dark red gradient
- Start menu: split layout (left=all apps list, right=pinned+recommended)
- Widget hosting: uses bindAppWidgetIdIfAllowed() instead of BIND_APPWIDGET permission
- File explorer: real folder navigation using java.io.File
- NavHostFragment: 1dp x 1dp, defaultNavHost=false, safe casts everywhere
- Added FileProvider in manifest for opening files
- Theme colors in colors.xml: win11_accent is now #E81123 (red)
```
