# ForTheWin11 — FINAL BUILD PASS

## WHAT EXISTS (solid)
- MainActivity: window manager, taskbar, start menu, desktop icons, widget host, notification receiver, theme manager
- DesktopManager: drag/drop, wiggle-cancel fixed, addSingleIcon, setEditMode
- NotificationService: live broadcast via LocalBroadcastManager
- ThemeManager: light/dark + blue/red accent
- WindowManagerController: draggable floating windows, min/max/close
- WidgetManager + WidgetHostManager: draggable widget overlays
- TaskbarManager: dynamic window icons
- Layouts: taskbar, start menu, control center, file explorer, floating window, widgets

## WHAT NEEDS REBUILDING / ADDING (this pass)

### VISUAL ENGINE — Ultra-glass 8K aesthetic
1. item_desktop_icon.xml — glass bg, frosted label pill, crisp icon shadow
2. layout_custom_taskbar.xml — blur-glass pill, translucent, sharp icons, no flat fill
3. layout_floating_window.xml — glass titlebar with RGB chrome blur, window-drop shadow
4. layout_start_menu.xml — dark glass with blur, red/blue accent sidebar active states
5. layout_control_center.xml — white frosted glass, sharp toggles, sliders
6. content_main.xml — desktop layer tweaks

### NEW DRAWABLES needed
- glass_surface_dark (20% white on dark, 1dp border)
- glass_surface_light (95% white, soft shadow, 1dp border 12% black)
- window_chrome_bg (titlebar: 93% white on light, dark on dark)
- taskbar_pill_glass (blur simulation: white 90%, border 8% black, 28dp corners)
- toggle_active_blue / toggle_active_red
- icon_bg_glass (per-icon frosted circle bg)

### NEW KT FILES
- WallpaperManager.kt — WallpaperManager.getInstance + live-set + preview crop
- QuickSettingsManager.kt — wifi/bt/airplane/nightlight real toggle logic
- ClockWidget.kt — live clock on desktop as a draggable view
- GestureController.kt — swipe up = start menu, swipe down = control center

### FUNCTIONAL UPGRADES IN MainActivity
- Real wifi toggle (WifiManager)
- Real bluetooth toggle (BluetoothAdapter)
- Real brightness control (Settings.System.SCREEN_BRIGHTNESS)
- Real volume control (AudioManager)
- Real battery level (BatteryManager intent)
- Real wallpaper picker + apply
- Swipe gestures on desktop layer
- Clock/date widget on desktop (always visible, draggable)
- App drawer as full-screen scrollable grid (replaces start menu All Apps)
- Recent apps list in taskbar

### LAYOUT POLISH
- All text: real system fonts (sans-serif-medium)
- All cards: layered elevation shadows
- Glassmorphism: semi-transparent + border on every surface
- Icons: never tinted, full color, no grey generic system icons where real icons available
- Notification cards: app icon (real), bold title, body text, app name, dismiss

## EXECUTION ORDER
1. New drawables (glass surfaces, shadows, pills) 
2. Rebuild item_desktop_icon.xml (glass icon bg)
3. Rebuild layout_custom_taskbar.xml (glass pill)
4. Rebuild layout_floating_window.xml (glass chrome)
5. Rebuild layout_start_menu.xml (dark glass)
6. Rebuild layout_control_center.xml (frosted light)
7. WallpaperManager.kt
8. QuickSettingsManager.kt  
9. GestureController.kt (swipe gestures)
10. MainActivity updates (wire all above, battery, brightness, volume, wifi, bt)
11. ClockWidget on desktop
12. Push

## DONE MARKER
[ ] All items above complete
