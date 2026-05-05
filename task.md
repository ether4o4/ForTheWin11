# ForTheWin11 — Task Tracker

## Target: Match WinX Red Theme exactly (reference: /tmp/target_look.jpg)

### Done ✅
- [x] Removed blue AppBarLayout/Toolbar
- [x] Transparent status bar (theme + window.statusBarColor)
- [x] Dark purple taskbar (#1B1035) with white icons/text
- [x] Red accent throughout (#E81123)
- [x] Red window title bars (dark red gradient)
- [x] Red control center toggle chips
- [x] Red bloom wallpaper vector
- [x] Start menu: split layout (all apps left, pinned right)
- [x] Taskbar search bar: dark semi-transparent on dark BG
- [x] Fixed NavHostFragment crash (removed visibility=gone)
- [x] Fixed widget crash (bindAppWidgetIdIfAllowed flow)
- [x] Real file explorer with folder navigation
- [x] Task view shows open windows

### Potential crash causes still remaining
- [ ] Check if `binding.appBarMain` works after FrameLayout change
- [ ] The `navView` reference was removed but `AppBarConfiguration` still references `drawerLayout`
- [ ] DrawerLayout might need a nav_view or content inside it

### Visual issues to check after app runs
- [ ] Start menu "All apps" button (like WinX shows)  
- [ ] Control center header should be pinkish/red tinted
- [ ] File explorer colors matching red theme
