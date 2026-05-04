# ForTheWin11 - Current Fix Batch

## Issues to fix:
1. [x] Wiggle bug — icons keep wiggling after drag (need tag-based animator.cancel)
2. [ ] NotificationService — broadcast live notifs via LocalBroadcastManager
3. [ ] ThemeManager — SharedPrefs for light/dark + accent color (blue/red)
4. [ ] Control center — live notification list + theme-aware colors (white/red theme from ref)
5. [ ] Resize handles — control center panel, taskbar height, start menu width
6. [ ] Long-press app icons in start menu → PopupMenu (Open / Add to Home Screen)
7. [ ] DesktopManager.addSingleIcon already exists — good
8. [ ] setupManagers() already uses floatingWindowContainer for WidgetManager — good

## Files to touch:
- DesktopManager.kt — fix wiggle cancel + setEditMode stop on ACTION_UP after move
- NotificationService.kt — broadcast sbn data 
- NEW: ThemeManager.kt
- MainActivity.kt — register broadcast receiver, apply theme, resize touch, long-press popup, theme toggle
- layout_control_center.xml — theme-aware + real notif list container + resize handle
- content_main.xml — resize handle on taskbar/control center
- colors.xml — add red accent theme colors
- fragment_settings.xml — add theme picker buttons (Light/Dark, Blue/Red)

## Key decisions:
- Theme stored in SharedPrefs: "theme_mode" (light/dark), "accent_color" (blue/red)
- Resize via touch on dedicated handle strip — drag up/down to resize
- NotificationService broadcasts via LocalBroadcastManager with action "com.forthewin.NEW_NOTIFICATION"
- Control center width: user drags left edge handle, min 260dp max 420dp
