package com.example.forthewin

import android.app.admin.DevicePolicyManager
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.res.ResourcesCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.forthewin.databinding.ActivityMainBinding
import com.example.forthewin.databinding.LayoutStartMenuBinding
import com.example.forthewin.services.NotificationService
import com.example.forthewin.ui.controllers.TaskbarManager
import com.example.forthewin.ui.controllers.WidgetHostManager
import com.example.forthewin.ui.controllers.WindowManagerController
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var appBarConfiguration: AppBarConfiguration = AppBarConfiguration(emptySet())
    private lateinit var binding: ActivityMainBinding
    private var allInstalledApps = mutableListOf<AppModel>()
    private lateinit var statsManager: SystemStatsManager
    private lateinit var fileIndexer: FileIndexer
    private val handler = Handler(Looper.getMainLooper())
    private var splashDismissed = false

    private lateinit var windowManager: WindowManagerController
    private lateinit var taskbarManager: TaskbarManager
    private lateinit var widgetHostManager: WidgetHostManager
    private lateinit var iconPackManager: IconPackManager
    private lateinit var desktopManager: com.example.forthewin.ui.controllers.DesktopManager
    private lateinit var widgetManager: com.example.forthewin.ui.controllers.WidgetManager
    private lateinit var quickSettings: QuickSettingsManager
    private lateinit var gestureController: GestureController

    // Live notifications
    private data class LiveNotif(val key: String, val pkg: String, val title: String, val text: String, val time: String)
    private val liveNotifications = LinkedHashMap<String, LiveNotif>()

    private fun AppModel.bestIcon() = resolvedIcon(iconPackManager)

    // ── Wallpaper picker ──────────────────────────────────────────────
    private val wallpaperPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val ok = WallpaperHelper.setFromUri(this, uri)
            Snackbar.make(binding.root, if (ok) "Wallpaper set!" else "Couldn't set wallpaper", Snackbar.LENGTH_SHORT).show()
        }
    }

    // ── Widget picker — 3-step flow: pick → bind permission → configure (optional) → create ──
    private val widgetConfigureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val appWidgetId = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
        val id = if (appWidgetId != -1) appWidgetId else widgetHostManager.pendingWidgetId
        try {
            if (result.resultCode == RESULT_OK && id != -1) {
                finalizeWidget(id)
            } else {
                widgetHostManager.safeDeleteId(id)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "widgetConfigure callback", e)
            widgetHostManager.safeDeleteId(id)
        }
    }

    // Bind permission result — the system asks user "Allow this launcher to use this widget?"
    private val widgetBindPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val appWidgetId = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
        val id = if (appWidgetId != -1) appWidgetId else widgetHostManager.pendingWidgetId
        try {
            if (result.resultCode == RESULT_OK && id != -1) {
                // Bind succeeded, now check if configure is needed
                val needsConfigure = widgetHostManager.launchConfigureIfNeeded(id, widgetConfigureLauncher)
                if (!needsConfigure) {
                    finalizeWidget(id)
                }
            } else {
                widgetHostManager.safeDeleteId(id)
                Snackbar.make(binding.root, "Widget permission denied", Snackbar.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "widgetBindPermission callback", e)
            widgetHostManager.safeDeleteId(id)
        }
    }

    private val widgetPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val appWidgetId = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
        val id = if (appWidgetId != -1) appWidgetId else widgetHostManager.pendingWidgetId
        try {
            if (result.resultCode == RESULT_OK && id != -1) {
                // After picking, we need to BIND the widget (third-party apps can't just use BIND_APPWIDGET)
                val info = widgetHostManager.getWidgetProvider(id)
                if (info?.provider != null) {
                    val boundDirectly = widgetHostManager.bindWidgetOrRequestPermission(
                        id, info.provider, widgetBindPermissionLauncher
                    )
                    if (boundDirectly) {
                        // Already bound, proceed to configure or finalize
                        val needsConfigure = widgetHostManager.launchConfigureIfNeeded(id, widgetConfigureLauncher)
                        if (!needsConfigure) {
                            finalizeWidget(id)
                        }
                    }
                    // If not bound directly, widgetBindPermissionLauncher will handle the result
                } else {
                    widgetHostManager.safeDeleteId(id)
                    Snackbar.make(binding.root, "Widget info not available", Snackbar.LENGTH_SHORT).show()
                }
            } else {
                widgetHostManager.safeDeleteId(id)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "widgetPicker callback", e)
            widgetHostManager.safeDeleteId(id)
            Snackbar.make(binding.root, "Widget picker error", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun finalizeWidget(appWidgetId: Int) {
        try {
            val hostView = widgetHostManager.createWidgetView(appWidgetId)
            if (hostView != null) {
                widgetManager.addWidget(hostView)
            } else {
                widgetHostManager.safeDeleteId(appWidgetId)
                Snackbar.make(binding.root, "Widget failed to load", Snackbar.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "finalizeWidget", e)
            widgetHostManager.safeDeleteId(appWidgetId)
            Snackbar.make(binding.root, "Widget error: ${e.message}", Snackbar.LENGTH_SHORT).show()
        }
    }

    // ── Notification receiver ─────────────────────────────────────────
    private val notifReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                NotificationService.ACTION_NOTIF_POSTED -> {
                    val key   = intent.getStringExtra(NotificationService.EXTRA_KEY)   ?: return
                    val pkg   = intent.getStringExtra(NotificationService.EXTRA_PKG)   ?: ""
                    val title = intent.getStringExtra(NotificationService.EXTRA_TITLE) ?: pkg
                    val text  = intent.getStringExtra(NotificationService.EXTRA_TEXT)  ?: ""
                    val time  = intent.getStringExtra(NotificationService.EXTRA_WHEN)  ?: ""
                    liveNotifications[key] = LiveNotif(key, pkg, title, text, time)
                    updateNotificationPanel()
                    updateNotificationBadge(liveNotifications.size)
                }
                NotificationService.ACTION_NOTIF_REMOVED -> {
                    val key = intent.getStringExtra(NotificationService.EXTRA_KEY) ?: return
                    liveNotifications.remove(key)
                    updateNotificationPanel()
                    updateNotificationBadge(liveNotifications.size)
                }
            }
        }
    }

    // ── onCreate ──────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Transparent system bars
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // NeverSoft Services CRT boot splash — a full-screen WebView rendering the
        // brand terminal splash from assets. It fades out and hands off to the
        // launcher after boot. Purely a visual overlay; it never blocks the app.
        setupNeverSoftSplash()

        // Surface any setup failure on-screen instead of force-closing the launcher.
        try {
            initLauncher()
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Launcher init failed", e)
            showFatalError(e)
            return
        }
    }

    // ── NeverSoft boot splash ─────────────────────────────────────────
    // Renders the brand CRT terminal splash (assets/neversoft_splash.html) in a
    // full-screen WebView layered over the launcher. The splash calls
    // window.__neverSoftSplashDone() when its boot finishes; a Handler fallback
    // guarantees dismissal even if that never fires. Runs once, then GONE.
    private fun setupNeverSoftSplash() {
        val splash = binding.neversoftSplash
        try {
            splash.setBackgroundColor(android.graphics.Color.BLACK)
            splash.isVerticalScrollBarEnabled = false
            splash.isHorizontalScrollBarEnabled = false
            splash.settings.javaScriptEnabled = true
            splash.addJavascriptInterface(object {
                @android.webkit.JavascriptInterface
                fun done() {
                    handler.post { dismissNeverSoftSplash() }
                }
            }, "NeverSoftSplashBridge")
            splash.webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Bridge the page's completion hook to the native dismissal.
                    view?.evaluateJavascript(
                        "window.__neverSoftSplashDone = function(){" +
                            "try{NeverSoftSplashBridge.done();}catch(e){}};",
                        null
                    )
                }
            }
            splash.loadUrl("file:///android_asset/neversoft_splash.html")
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Splash setup failed", e)
            dismissNeverSoftSplash()
            return
        }
        // Robust fallback: dismiss after the splash's ~2.5s boot even if the
        // page's completion hook never fires. Never traps the launcher.
        splash.postDelayed({ dismissNeverSoftSplash() }, 2900)
    }

    private fun dismissNeverSoftSplash() {
        if (splashDismissed) return
        splashDismissed = true
        val splash = binding.neversoftSplash
        splash.animate()
            .alpha(0f)
            .setDuration(320)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                splash.visibility = View.GONE
                try { splash.destroy() } catch (e: Exception) { }
            }
            .start()
    }

    private fun initLauncher() {
        statsManager  = SystemStatsManager(this)
        fileIndexer   = FileIndexer(this)
        iconPackManager = (application as LauncherApplication).iconPackManager
        quickSettings = QuickSettingsManager(this, window)

        setupManagers()
        checkPermissions()
        loadApps()
        populateDesktopIcons()
        startUpdates()
        setupTaskbar()
        setupControlCenter()
        applyThemeToControlCenter()
        applyThemeToTaskbar()
        setupGestures()

        widgetHostManager.startListening()

        LocalBroadcastManager.getInstance(this).registerReceiver(
            notifReceiver,
            IntentFilter().apply {
                addAction(NotificationService.ACTION_NOTIF_POSTED)
                addAction(NotificationService.ACTION_NOTIF_REMOVED)
            }
        )

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment
        val navController = navHostFragment?.navController

        // Navigation setup without action bar (no toolbar)
        if (navController != null) {
            appBarConfiguration = AppBarConfiguration(
                setOf(R.id.nav_transform, R.id.nav_reflow, R.id.nav_slideshow, R.id.nav_settings),
                binding.drawerLayout
            )
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val startMenu     = binding.appBarMain.contentMain.startMenuPanel?.root
                val controlCenter = binding.appBarMain.contentMain.controlCenterPanel?.root
                when {
                    startMenu?.visibility == View.VISIBLE     -> toggleStartMenu(false)
                    controlCenter?.visibility == View.VISIBLE -> toggleControlCenter(false)
                    desktopManager.isEditMode()               -> desktopManager.setEditMode(false)
                    else -> {
                        val wc = binding.appBarMain.contentMain.floatingWindowContainer
                        if (wc != null && wc.childCount > 0) wc.removeViewAt(wc.childCount - 1)
                    }
                }
            }
        })
    }

    // ── Managers init ─────────────────────────────────────────────────
    private fun setupManagers() {
        taskbarManager = TaskbarManager(
            this,
            binding.appBarMain.contentMain.customTaskbar!!
        ) { launchApp(it) }

        windowManager = WindowManagerController(
            this,
            binding.appBarMain.contentMain.floatingWindowContainer!!,
            { title, icon, win -> taskbarManager.addWindowIcon(title, icon, win) },
            { win -> taskbarManager.removeWindowIcon(win) }
        )
        widgetHostManager = WidgetHostManager(this)

        desktopManager = com.example.forthewin.ui.controllers.DesktopManager(
            this,
            binding.appBarMain.contentMain.desktopIconsGrid!!,
            iconPackManager
        ) { launchApp(it) }

        widgetManager = com.example.forthewin.ui.controllers.WidgetManager(
            this,
            binding.appBarMain.contentMain.floatingWindowContainer!!
        )
    }

    private fun checkPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
    }

    // ── Gestures ──────────────────────────────────────────────────────
    private fun setupGestures() {
        val desktopLayer = binding.appBarMain.contentMain.desktopIconsGrid ?: return
        gestureController = GestureController(
            context        = this,
            onSwipeUp      = { 
                val start = binding.appBarMain.contentMain.startMenuPanel ?: return@GestureController
                if (start.root.visibility == View.GONE) {
                    toggleControlCenter(false)
                    toggleStartMenu(true)
                    setupStartMenuActions(start)
                }
            },
            onSwipeDown    = {
                val cc = binding.appBarMain.contentMain.controlCenterPanel
                if (cc?.root?.visibility == View.GONE) {
                    toggleStartMenu(false)
                    toggleControlCenter(true)
                }
            },
            onDoubleTap    = { if (desktopManager.isEditMode()) desktopManager.setEditMode(false) }
        )
        gestureController.attachTo(desktopLayer)
        
        // Long-press desktop → context menu
        desktopLayer.setOnLongClickListener {
            if (!desktopManager.isEditMode()) showDesktopContextMenu(it)
            true
        }
    }

    // ── Taskbar ───────────────────────────────────────────────────────
    private fun setupTaskbar() {
        val taskbarRoot       = binding.appBarMain.contentMain.customTaskbar?.root ?: return
        val startMenuBinding  = binding.appBarMain.contentMain.startMenuPanel ?: return
        val controlCenterBind = binding.appBarMain.contentMain.controlCenterPanel ?: return
        val startMenu         = startMenuBinding.root
        val controlCenter     = controlCenterBind.root

        taskbarRoot.findViewById<View>(R.id.btn_start_orb)?.setOnClickListener {
            if (startMenu.visibility == View.GONE) {
                toggleControlCenter(false); toggleStartMenu(true)
                setupStartMenuActions(startMenuBinding)
            } else toggleStartMenu(false)
        }

        taskbarRoot.findViewById<View>(R.id.btn_taskbar_search)?.setOnClickListener {
            // Focus the search in start menu (open if needed)
            if (startMenu.visibility == View.GONE) {
                toggleControlCenter(false); toggleStartMenu(true)
                setupStartMenuActions(startMenuBinding)
            }
            startMenuBinding.startSearchInput.requestFocus()
        }

        taskbarRoot.findViewById<View>(R.id.btn_taskbar_taskview)?.setOnClickListener {
            showTaskView()
        }

        taskbarRoot.findViewById<View>(R.id.btn_taskbar_explorer)?.setOnClickListener { openFileExplorer() }
        taskbarRoot.findViewById<View>(R.id.btn_taskbar_browser)?.setOnClickListener  { openBrowser() }
        taskbarRoot.findViewById<View>(R.id.btn_taskbar_settings)?.setOnClickListener {
            try { findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.nav_settings) } catch (e: Exception) { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
        }

        val openCC = { ->
            if (controlCenter.visibility == View.GONE) {
                toggleStartMenu(false); toggleControlCenter(true)
            } else toggleControlCenter(false)
        }
        taskbarRoot.findViewById<View>(R.id.taskbar_system_tray)?.setOnClickListener { openCC() }
        taskbarRoot.findViewById<View>(R.id.btn_notifications)?.setOnClickListener   { openCC() }

        // Resize by dragging top strip
        setupTaskbarResize(taskbarRoot)

        // Long-press taskbar → desktop context
        taskbarRoot.setOnLongClickListener { showDesktopContextMenu(it); true }
    }

    private fun setupTaskbarResize(taskbarRoot: View) {
        val density = resources.displayMetrics.density
        var startY = -1f
        var startH = 0

        taskbarRoot.setOnTouchListener { v, event ->
            val topZone = 12 * density
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (event.y < topZone) {
                        startY = event.rawY; startH = v.height; true
                    } else false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (startY >= 0f) {
                        val newH = (startH + (startY - event.rawY))
                            .coerceIn(44 * density, 72 * density).toInt()
                        v.layoutParams = v.layoutParams.also { it.height = newH }
                        true
                    } else false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { startY = -1f; false }
                else -> false
            }
        }
    }

    // ── Control Center ────────────────────────────────────────────────
    private fun setupControlCenter() {
        val panel = binding.appBarMain.contentMain.controlCenterPanel ?: return
        val root  = panel.root

        // Brightness slider — real brightness
        root.findViewById<SeekBar>(R.id.brightness_slider)?.apply {
            progress = quickSettings.getBrightness()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, v: Int, user: Boolean) {
                    if (user) quickSettings.setBrightness(v)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        // Volume slider — real AudioManager
        root.findViewById<SeekBar>(R.id.volume_slider)?.apply {
            progress = quickSettings.getVolume()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, v: Int, user: Boolean) {
                    if (user) quickSettings.setVolume(v)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        // Wifi toggle
        root.findViewById<View>(R.id.toggle_wifi)?.setOnClickListener {
            if (quickSettings.isWifiEnabled()) quickSettings.openWifiSettings()
            else quickSettings.openWifiSettings()
        }

        // Bluetooth toggle
        root.findViewById<View>(R.id.toggle_bluetooth)?.setOnClickListener {
            quickSettings.openBluetoothSettings()
        }

        // Airplane mode → Settings
        root.findViewById<View>(R.id.toggle_airplane)?.setOnClickListener {
            startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }

        // Night light
        root.findViewById<View>(R.id.toggle_night)?.setOnClickListener {
            startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }

        // Clear all
        root.findViewById<View>(R.id.btn_clear_all)?.setOnClickListener {
            liveNotifications.clear()
            updateNotificationPanel()
            updateNotificationBadge(0)
        }

        // More settings
        root.findViewById<View>(R.id.btn_more_settings)?.setOnClickListener {
            toggleControlCenter(false)
            try { findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.nav_settings) } catch (e: Exception) { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
        }

        // Resize handle
        setupControlCenterResize(root)

        // Initial state
        updateNotificationPanel()
        refreshControlCenterStatus(root)
    }

    private fun refreshControlCenterStatus(root: View) {
        val battery = quickSettings.getBatteryPercent()
        val charging = quickSettings.isCharging()
        val batteryIcon = when {
            battery >= 80 -> "🔋"
            battery >= 40 -> "🔋"
            else          -> "🪫"
        }
        val chargingStr = if (charging) " ⚡" else ""
        root.findViewById<TextView>(R.id.battery_percent)?.text = "$batteryIcon $battery%$chargingStr"

        // Wifi toggle visual state
        val wifiOn = quickSettings.isWifiEnabled()
        root.findViewById<View>(R.id.toggle_wifi)?.background?.setTint(
            if (wifiOn) ThemeManager.accentDim(this) else 0xFFF0F0F2.toInt()
        )
        root.findViewById<TextView>(R.id.lbl_wifi)?.setTextColor(
            if (wifiOn) ThemeManager.accent(this) else 0x88000000.toInt()
        )
        root.findViewById<ImageView>(R.id.ic_wifi)?.imageTintList =
            ColorStateList.valueOf(if (wifiOn) ThemeManager.accent(this) else 0x88000000.toInt())

        // BT visual state
        val btOn = quickSettings.isBluetoothEnabled()
        root.findViewById<View>(R.id.toggle_bluetooth)?.background?.setTint(
            if (btOn) ThemeManager.accentDim(this) else 0xFFF0F0F2.toInt()
        )
        root.findViewById<TextView>(R.id.lbl_bluetooth)?.setTextColor(
            if (btOn) ThemeManager.accent(this) else 0x88000000.toInt()
        )
    }

    private fun setupControlCenterResize(panelRoot: View) {
        val resizeHandle = panelRoot.findViewById<View>(R.id.cc_resize_handle) ?: return
        val density = resources.displayMetrics.density
        var startX = 0f; var startW = 0
        resizeHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN  -> { startX = event.rawX; startW = panelRoot.width; true }
                MotionEvent.ACTION_MOVE  -> {
                    val newW = (startW + (startX - event.rawX))
                        .coerceIn(260 * density, 440 * density).toInt()
                    panelRoot.layoutParams = panelRoot.layoutParams.also { it.width = newW }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { startX = 0f; true }
                else -> false
            }
        }
    }

    // ── Theme ─────────────────────────────────────────────────────────
    fun applyThemeToControlCenter() {
        val panel  = binding.appBarMain.contentMain.controlCenterPanel?.root ?: return
        val accent = ThemeManager.accent(this)
        val isDark = ThemeManager.isDark(this)
        val bg     = ThemeManager.surfaceBg(this)
        val text   = ThemeManager.panelText(this)
        val textSec= ThemeManager.panelTextSecondary(this)

        panel.findViewById<TextView>(R.id.cc_notif_header)?.setTextColor(text)
        panel.findViewById<TextView>(R.id.btn_clear_all)?.setTextColor(accent)
        panel.findViewById<TextView>(R.id.btn_more_settings)?.setTextColor(accent)

        listOf(R.id.brightness_slider, R.id.volume_slider).forEach { id ->
            val sb = panel.findViewById<SeekBar>(id) ?: return@forEach
            sb.progressTintList = ColorStateList.valueOf(accent)
            sb.thumbTintList    = ColorStateList.valueOf(accent)
        }

        // Tray badge
        val taskbarRoot = binding.appBarMain.contentMain.customTaskbar?.root
        taskbarRoot?.findViewById<TextView>(R.id.notification_count)
            ?.backgroundTintList = ColorStateList.valueOf(accent)
    }

    fun applyTheme(dark: Boolean, redAccent: Boolean) {
        ThemeManager.setDark(this, dark)
        ThemeManager.setRedAccent(this, redAccent)
        applyThemeToControlCenter()
        applyThemeToTaskbar()
        applyThemeToStartMenu()
    }

    private fun applyThemeToTaskbar() {
        val taskbarRoot = binding.appBarMain.contentMain.customTaskbar?.root ?: return
        val bg = ThemeManager.taskbarBg(this)
        val textPri = ThemeManager.taskbarTextPrimary(this)
        val textSec = ThemeManager.taskbarTextSecondary(this)
        val iconTint = ThemeManager.taskbarIconTint(this)

        // Find the main LinearLayout inside FrameLayout (skip top border View)
        if (taskbarRoot is FrameLayout) {
            for (i in 0 until taskbarRoot.childCount) {
                val child = taskbarRoot.getChildAt(i)
                if (child is LinearLayout) {
                    child.setBackgroundColor(bg)
                    break
                }
            }
        }

        // Clock & date
        taskbarRoot.findViewById<TextView>(R.id.taskbar_time)?.setTextColor(textPri)
        taskbarRoot.findViewById<TextView>(R.id.taskbar_date)?.setTextColor(textSec)

        // Tray icons
        taskbarRoot.findViewById<ImageView>(R.id.tray_network)?.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN)
        taskbarRoot.findViewById<ImageView>(R.id.tray_volume)?.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN)

        // Start orb tint (white on dark taskbar) — tint the inner logo ImageView,
        // not its FrameLayout wrapper (btn_start_orb), which is not an ImageView.
        taskbarRoot.findViewById<ImageView>(R.id.start_orb_icon)?.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN)
    }

    private fun applyThemeToStartMenu() {
        val sm = binding.appBarMain.contentMain.startMenuPanel ?: return
        if (sm.root.visibility != View.VISIBLE) return
        populateAllAppsList(sm)
        populatePinnedApps(sm)
        populateRecommended(sm)
    }

    fun refreshStartMenu() {
        val sm = binding.appBarMain.contentMain.startMenuPanel ?: return
        if (sm.root.visibility == View.VISIBLE) {
            populateAllAppsList(sm)
            populatePinnedApps(sm)
            populateRecommended(sm)
        }
    }

    // ── Notification panel ────────────────────────────────────────────
    private fun updateNotificationPanel() {
        val panel = binding.appBarMain.contentMain.controlCenterPanel?.root ?: return
        val list  = panel.findViewById<LinearLayout>(R.id.notification_list) ?: return
        val empty = panel.findViewById<View>(R.id.cc_notif_empty)

        list.removeAllViews()

        if (liveNotifications.isEmpty()) {
            empty?.visibility = View.VISIBLE
        } else {
            empty?.visibility = View.GONE
            val accent    = ThemeManager.accent(this)
            val cardBg    = ThemeManager.cardBg(this)
            val textPri   = ThemeManager.panelText(this)
            val textSec   = ThemeManager.panelTextSecondary(this)
            val isDark    = ThemeManager.isDark(this)

            liveNotifications.values.reversed().take(12).forEach { notif ->
                val card = layoutInflater.inflate(R.layout.item_notification_cc, list, false)

                // Dynamic card background matching theme
                val bgColor = if (isDark) 0xFF2C2C2E.toInt() else 0xFFFAFAFA.toInt()
                val stroke  = if (isDark) 0x33FFFFFF else 0x14000000
                val shape = android.graphics.drawable.GradientDrawable().apply {
                    setColor(bgColor)
                    cornerRadius = 10f * resources.displayMetrics.density
                    setStroke((1f * resources.displayMetrics.density).toInt(), stroke)
                }
                card.background = shape

                // Real app icon
                val iconView = card.findViewById<ImageView>(R.id.notif_icon)
                try {
                    iconView?.setImageDrawable(packageManager.getApplicationIcon(notif.pkg))
                    iconView?.clearColorFilter()
                } catch (e: Exception) {
                    iconView?.setImageResource(android.R.drawable.ic_menu_info_details)
                    iconView?.setColorFilter(accent)
                }

                // App name (human-readable label)
                card.findViewById<TextView>(R.id.notif_app_name)?.apply {
                    text = try {
                        packageManager.getApplicationLabel(
                            packageManager.getApplicationInfo(notif.pkg, 0)
                        ).toString()
                    } catch (e: Exception) { notif.pkg }
                    setTextColor(textSec)
                }
                card.findViewById<TextView>(R.id.notif_title)?.apply {
                    text = notif.title; setTextColor(textPri)
                }
                card.findViewById<TextView>(R.id.notif_content)?.apply {
                    text = notif.text; setTextColor(textSec)
                }
                card.findViewById<TextView>(R.id.notif_time)?.apply {
                    text = notif.time; setTextColor(textSec)
                }
                card.findViewById<View>(R.id.notif_dot)?.backgroundTintList =
                    ColorStateList.valueOf(accent)

                // Dismiss
                card.findViewById<View>(R.id.notif_dismiss)?.setOnClickListener {
                    liveNotifications.remove(notif.key)
                    card.animate().alpha(0f).translationX(48f).setDuration(200)
                        .withEndAction { list.removeView(card); updateNotificationPanel(); updateNotificationBadge(liveNotifications.size) }
                        .start()
                }

                list.addView(card)
            }
        }
    }

    private fun updateNotificationBadge(count: Int) {
        val badge = binding.appBarMain.contentMain.customTaskbar?.root
            ?.findViewById<TextView>(R.id.notification_count) ?: return
        badge.text = if (count > 9) "9+" else count.toString()
        badge.visibility = if (count > 0) View.VISIBLE else View.INVISIBLE
    }

    // ── Task View ──────────────────────────────────────────────────
    private fun showTaskView() {
        val windowContainer = binding.appBarMain.contentMain.floatingWindowContainer ?: return
        val childCount = windowContainer.childCount
        if (childCount == 0) {
            Snackbar.make(binding.root, "No open windows", Snackbar.LENGTH_SHORT).show()
            return
        }

        // Create a task view overlay
        val overlay = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xCC1A1A2E.toInt())
            elevation = 100f
            isClickable = true
            isFocusable = true
        }

        val scrollView = android.widget.HorizontalScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = android.view.Gravity.CENTER }
            isHorizontalScrollBarEnabled = false
        }

        val cardsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        // Header
        val header = TextView(this).apply {
            text = "Task View"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(32, 48, 32, 16)
        }

        // Collect window info
        for (i in 0 until childCount) {
            val win = windowContainer.getChildAt(i) ?: continue
            if (win.visibility == View.GONE) continue

            val title = win.findViewById<TextView>(R.id.window_title)?.text?.toString() ?: "Window ${i + 1}"

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                val shape = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFF2C2C3E.toInt())
                    cornerRadius = 12f * resources.displayMetrics.density
                    setStroke(2, 0xFF0078D4.toInt())
                }
                background = shape
                val dp = resources.displayMetrics.density
                layoutParams = LinearLayout.LayoutParams((180 * dp).toInt(), (140 * dp).toInt()).apply {
                    setMargins((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)
                }
                elevation = 8f
            }

            val titleView = TextView(this).apply {
                text = title
                textSize = 13f
                setTextColor(0xFFFFFFFF.toInt())
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, 0, 0, 8)
            }

            val preview = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0
                ).apply { weight = 1f }
                setBackgroundColor(0xFF3C3C4E.toInt())
            }

            card.addView(titleView)
            card.addView(preview)

            // Click card to bring window to front and close task view
            val windowRef = win
            card.setOnClickListener {
                windowRef.visibility = View.VISIBLE
                windowRef.alpha = 1f
                windowRef.scaleX = 1f
                windowRef.scaleY = 1f
                windowRef.bringToFront()
                windowContainer.removeView(overlay)
            }

            cardsContainer.addView(card)
        }

        scrollView.addView(cardsContainer)

        overlay.addView(header)
        overlay.addView(scrollView)

        // Tap background to dismiss
        overlay.setOnClickListener {
            windowContainer.removeView(overlay)
        }

        windowContainer.addView(overlay)
        overlay.alpha = 0f
        overlay.animate().alpha(1f).setDuration(200).start()
    }

    // ── Desktop context menu ──────────────────────────────────────────
    private fun showDesktopContextMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Add Widget")
        popup.menu.add(0, 2, 0, "Change Wallpaper")
        popup.menu.add(0, 3, 0, "Display Settings")
        popup.menu.add(0, 4, 0, "Personalize")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> widgetHostManager.pickWidget(widgetPickerLauncher)
                2 -> pickWallpaper()
                3 -> startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                4 -> try { findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.nav_settings) } catch (e: Exception) { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
            }
            true
        }
        popup.show()
    }

    private fun pickWallpaper() {
        try {
            // On API 34+, use the photo picker directly since ACTION_SET_WALLPAPER
            // may not work reliably with all launchers
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                // Use the new Photo Picker on Android 14+
                val intent = android.provider.MediaStore.createWriteRequest(
                    contentResolver, emptyList()
                )
                // Fallback: launch gallery picker and apply wallpaper ourselves
                wallpaperPickerLauncher.launch("image/*")
            } else {
                // Try system wallpaper chooser first
                val intent = Intent(Intent.ACTION_SET_WALLPAPER)
                startActivity(Intent.createChooser(intent, "Choose Wallpaper"))
            }
        } catch (e: Exception) {
            // Final fallback: gallery picker → apply ourselves
            try {
                wallpaperPickerLauncher.launch("image/*")
            } catch (e2: Exception) {
                Snackbar.make(binding.root, "Cannot open image picker", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    // ── App context menu (long-press in start menu) ───────────────────
    private fun showAppContextMenu(anchor: View, app: AppModel) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Open")
        popup.menu.add(0, 2, 0, "Add to Home Screen")
        popup.menu.add(0, 3, 0, "App info")
        popup.menu.add(0, 4, 0, "Uninstall")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { launchApp(app.packageName); toggleStartMenu(false) }
                2 -> {
                    desktopManager.addSingleIcon(app); toggleStartMenu(false)
                    Snackbar.make(binding.root, "${app.label} added to desktop", Snackbar.LENGTH_SHORT).show()
                }
                3 -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${app.packageName}")
                })
                4 -> startActivity(Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.parse("package:${app.packageName}")
                })
            }
            true
        }
        popup.show()
    }

    // ── Toggles ───────────────────────────────────────────────────────
    private fun toggleStartMenu(show: Boolean) {
        val sm = binding.appBarMain.contentMain.startMenuPanel?.root ?: return
        if (show) {
            sm.visibility = View.VISIBLE
            sm.translationY = 180f; sm.alpha = 0f
            sm.animate().translationY(0f).alpha(1f).setDuration(350)
                .setInterpolator(DecelerateInterpolator()).start()
        } else {
            sm.animate().translationY(180f).alpha(0f).setDuration(250)
                .withEndAction { sm.visibility = View.GONE }.start()
        }
    }

    private fun toggleControlCenter(show: Boolean) {
        val panel = binding.appBarMain.contentMain.controlCenterPanel?.root ?: return
        if (show) {
            panel.visibility = View.VISIBLE
            panel.translationY = 120f; panel.alpha = 0f
            panel.animate().translationY(0f).alpha(1f).setDuration(320)
                .setInterpolator(DecelerateInterpolator()).start()
            refreshControlCenterStatus(panel)
            updateNotificationPanel()
        } else {
            panel.animate().translationY(120f).alpha(0f).setDuration(250)
                .withEndAction { panel.visibility = View.GONE }.start()
        }
    }

    // ── App loading ───────────────────────────────────────────────────
    private fun loadApps() {
        allInstalledApps.clear()
        val i = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        packageManager.queryIntentActivities(i, 0).forEach { ri ->
            allInstalledApps.add(AppModel(
                ri.loadLabel(packageManager).toString(),
                ri.activityInfo.packageName,
                ri.activityInfo.name,
                ri.activityInfo.loadIcon(packageManager)
            ))
        }
        allInstalledApps.sortBy { it.label.lowercase() }
    }

    // ── Tick updates ──────────────────────────────────────────────────
    private fun startUpdates() {
        val runnable = object : Runnable {
            override fun run() {
                updateTaskbarTray()
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(runnable)

        // Battery update every 30s
        val battRunnable = object : Runnable {
            override fun run() {
                val panel = binding.appBarMain.contentMain.controlCenterPanel?.root
                if (panel != null) refreshControlCenterStatus(panel)
                updateTrayBattery()
                handler.postDelayed(this, 30_000)
            }
        }
        handler.postDelayed(battRunnable, 5_000)
    }

    private fun updateTrayBattery() {
        val tray = binding.appBarMain.contentMain.customTaskbar?.root ?: return
        val pct  = quickSettings.getBatteryPercent()
        val icon = if (pct < 20) "🪫" else "🔋"
        tray.findViewById<TextView>(R.id.tray_battery)?.text = icon
    }

    private fun updateTaskbarTray() {
        val taskbar = binding.appBarMain.contentMain.customTaskbar ?: return
        val cal  = Calendar.getInstance()
        val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
        val date = SimpleDateFormat("M/d/yyyy", Locale.getDefault()).format(cal.time)
        taskbar.root.findViewById<TextView>(R.id.taskbar_time)?.text = time
        taskbar.root.findViewById<TextView>(R.id.taskbar_date)?.text = date
    }

    private fun updateWidgets() {
        val widgets = binding.appBarMain.contentMain.sidebarWidgets ?: return
        val cal = Calendar.getInstance()
        widgets.findViewById<TextView>(R.id.clock_hours)?.text =
            String.format("%02d", cal.get(Calendar.HOUR_OF_DAY))
        widgets.findViewById<TextView>(R.id.clock_minutes)?.text =
            String.format("%02d", cal.get(Calendar.MINUTE))
        widgets.findViewById<TextView>(R.id.clock_date)?.text =
            SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(cal.time).uppercase()
        val cpu = statsManager.getCpuUsage()
        val ram = statsManager.getMemoryUsage()
        widgets.findViewById<TextView>(R.id.cpu_value)?.text = "$cpu%"
        widgets.findViewById<ProgressBar>(R.id.cpu_progress)?.progress = cpu
        widgets.findViewById<TextView>(R.id.ram_value)?.text = "$ram%"
        widgets.findViewById<ProgressBar>(R.id.ram_progress)?.progress = ram
    }

    // ── Desktop ───────────────────────────────────────────────────────
    private fun populateDesktopIcons() {
        desktopManager.setIcons(allInstalledApps.take(6))
    }

    // ── App windows ───────────────────────────────────────────────────
    private fun openFileExplorer() {
        openFloatingWindow("File Explorer", R.drawable.ic_win11_folder) { container ->
            val v = layoutInflater.inflate(R.layout.layout_file_explorer, container, false)
            container.addView(v)

            // Current path state
            var currentPath = android.os.Environment.getExternalStorageDirectory()

            // Path mapping for sidebar
            val pathMap = mapOf(
                "Home" to android.os.Environment.getExternalStorageDirectory(),
                "Desktop" to java.io.File(android.os.Environment.getExternalStorageDirectory(), "Desktop"),
                "Documents" to android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS),
                "Downloads" to android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                "Pictures" to android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES),
                "Music" to android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC),
                "Videos" to android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES),
                "This PC" to java.io.File("/storage")
            )

            val sidebarContainer = v.findViewById<LinearLayout>(R.id.explorer_sidebar_container)
            val quickGrid = v.findViewById<GridLayout>(R.id.quick_access_grid)
            val recentList = v.findViewById<LinearLayout>(R.id.recent_files_list)
            val itemCountView = v.findViewById<TextView>(R.id.explorer_item_count)

            // Function to display files in the content area
            fun navigateTo(path: java.io.File) {
                currentPath = path
                quickGrid.removeAllViews()
                recentList.removeAllViews()

                // Update breadcrumb-like item count
                val files = try { path.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) } catch (e: Exception) { null }

                if (files == null || files.isEmpty()) {
                    recentList.addView(TextView(this).apply {
                        text = if (files == null) "Cannot access this folder. Grant storage permission in Settings." else "This folder is empty."
                        setTextColor(0x88000000.toInt()); textSize = 12f; setPadding(8, 16, 8, 8)
                    })
                    itemCountView?.text = "0 items"
                    return
                }

                itemCountView?.text = "${files.size} items"

                // Show files as grid items (folders) and list items (files)
                val folders = files.filter { it.isDirectory }
                val regularFiles = files.filter { it.isFile }

                // Show folders in the grid
                folders.take(12).forEach { folder ->
                    val item = layoutInflater.inflate(R.layout.item_recommended_file, quickGrid, false)
                    val spec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    val lp = GridLayout.LayoutParams(spec, spec).apply { width = 0 }
                    item.layoutParams = lp
                    item.findViewById<TextView>(R.id.file_name).text = folder.name
                    item.findViewById<TextView>(R.id.file_subtitle).text = "${folder.listFiles()?.size ?: 0} items"
                    item.findViewById<ImageView>(R.id.file_icon).apply {
                        setImageResource(R.drawable.ic_win11_folder)
                        setColorFilter(0xFFFFB900.toInt(), PorterDuff.Mode.SRC_IN)
                    }
                    // Click to navigate into folder
                    item.setOnClickListener { navigateTo(folder) }
                    quickGrid.addView(item)
                }

                // Show files in list
                regularFiles.take(20).forEach { file ->
                    val item = layoutInflater.inflate(R.layout.item_recommended_file, recentList, false)
                    item.findViewById<TextView>(R.id.file_name).text = file.name
                    val sizeStr = when {
                        file.length() < 1024 -> "${file.length()} B"
                        file.length() < 1024 * 1024 -> "${file.length() / 1024} KB"
                        else -> "${file.length() / (1024 * 1024)} MB"
                    }
                    item.findViewById<TextView>(R.id.file_subtitle).text = "$sizeStr · ${file.extension.uppercase().ifEmpty { "File" }}"
                    val iconRes = when (file.extension.lowercase()) {
                        "jpg", "jpeg", "png", "gif", "webp" -> android.R.drawable.ic_menu_gallery
                        "mp3", "wav", "ogg", "flac" -> android.R.drawable.ic_lock_silent_mode_off
                        "mp4", "mkv", "avi" -> android.R.drawable.ic_menu_slideshow
                        "pdf", "doc", "docx", "txt" -> android.R.drawable.ic_menu_edit
                        "apk" -> android.R.drawable.sym_def_app_icon
                        else -> android.R.drawable.ic_menu_more
                    }
                    item.findViewById<ImageView>(R.id.file_icon).setImageResource(iconRes)
                    // Open file with system
                    item.setOnClickListener {
                        try {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                this, "$packageName.fileprovider", file
                            )
                            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, contentResolver.getType(uri) ?: "*/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(openIntent)
                        } catch (e: Exception) {
                            // Fallback: try direct URI
                            try {
                                val uri = android.net.Uri.fromFile(file)
                                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "*/*")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                startActivity(openIntent)
                            } catch (e2: Exception) {
                                Snackbar.make(binding.root, "Cannot open: ${file.name}", Snackbar.LENGTH_SHORT).show()
                            }
                        }
                    }
                    recentList.addView(item)
                }
            }

            // Setup sidebar navigation
            listOf(
                Pair("Home", R.drawable.ic_win11_folder),
                Pair("Desktop", android.R.drawable.ic_menu_view),
                Pair("Documents", android.R.drawable.ic_menu_agenda),
                Pair("Downloads", android.R.drawable.ic_menu_save),
                Pair("Pictures", android.R.drawable.ic_menu_gallery),
                Pair("Music", android.R.drawable.ic_lock_silent_mode_off),
                Pair("Videos", android.R.drawable.ic_menu_slideshow),
                Pair("This PC", android.R.drawable.ic_menu_info_details)
            ).forEach { (label, icon) ->
                val item = layoutInflater.inflate(R.layout.item_explorer_sidebar, sidebarContainer, false)
                item.findViewById<TextView>(R.id.sidebar_label).text = label
                item.findViewById<ImageView>(R.id.sidebar_icon).setImageResource(icon)
                if (label == "Home") {
                    item.findViewById<View>(R.id.active_indicator).visibility = View.VISIBLE
                    item.setBackgroundColor(0x110078D4)
                }
                // Click sidebar to navigate
                item.setOnClickListener {
                    // Update active state
                    for (i in 0 until sidebarContainer.childCount) {
                        val child = sidebarContainer.getChildAt(i)
                        child.setBackgroundColor(0x00000000)
                        child.findViewById<View>(R.id.active_indicator)?.visibility = View.GONE
                    }
                    item.setBackgroundColor(0x110078D4)
                    item.findViewById<View>(R.id.active_indicator)?.visibility = View.VISIBLE

                    val target = pathMap[label] ?: currentPath
                    if (!target.exists()) target.mkdirs()
                    navigateTo(target)
                }
                sidebarContainer.addView(item)
            }

            // Back button (address bar back arrow)
            v.findViewById<View>(R.id.explorer_back_btn)?.setOnClickListener {
                val parent = currentPath.parentFile
                if (parent != null && parent.canRead()) {
                    navigateTo(parent)
                }
            }

            // Initial load
            navigateTo(currentPath)
        }
    }

    private fun openBrowser() {
        openFloatingWindow("Browser", R.drawable.ic_win11_edge) { container ->
            val wv = WebView(this)
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            wv.settings.setSupportZoom(true)
            wv.settings.builtInZoomControls = true
            wv.settings.displayZoomControls = false
            wv.loadUrl("https://www.google.com")
            container.addView(wv)
        }
    }

    private fun openNotepad() {
        openFloatingWindow("Notepad", android.R.drawable.ic_menu_edit) { container ->
            container.setBackgroundColor(Color.WHITE)
            val et = EditText(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                hint = "Start typing…"; setTextColor(Color.BLACK)
                setHintTextColor(0x88000000.toInt()); setBackgroundColor(Color.TRANSPARENT)
                gravity = android.view.Gravity.TOP; setPadding(20, 20, 20, 20); textSize = 14f
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            }
            container.addView(et)
        }
    }

    private fun openFloatingWindow(title: String, iconRes: Int, contentInit: (FrameLayout) -> Unit) {
        windowManager.openWindow(title, iconRes, contentInit)
    }

    // ── Refresh (from settings) ───────────────────────────────────────
    fun refreshIcons() {
        desktopManager.setIcons(allInstalledApps.take(6))
        val sm = binding.appBarMain.contentMain.startMenuPanel
        if (sm?.root?.visibility == View.VISIBLE) populatePinnedApps(sm)
        taskbarManager.refreshIcons(iconPackManager)
    }

    // ── Start Menu internals ──────────────────────────────────────────
    private fun setupStartMenuActions(start: LayoutStartMenuBinding) {
        populateAllAppsList(start)
        populateRecommended(start)
        populatePinnedApps(start)

        start.startSearchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                filterStartMenuContent(s.toString(), start)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        start.btnPower.setOnClickListener { showPowerMenu(it) }
        start.btnSettingsQuick.setOnClickListener {
            toggleStartMenu(false)
            try { findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.nav_settings) } catch (e: Exception) { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
        }
        start.btnLockQuick.setOnClickListener {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            try { dpm.lockNow() } catch (e: Exception) {
                Snackbar.make(binding.root, "Need Device Admin for Lock", Snackbar.LENGTH_LONG).show()
            }
        }
        start.btnMoonQuick.setOnClickListener {
            Snackbar.make(binding.root, "Sleep mode", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun populateAllAppsList(start: LayoutStartMenuBinding) {
        val container = start.allAppsList
        container.removeAllViews()
        allInstalledApps.sortedBy { it.label.lowercase() }.forEach { app ->
            val v = layoutInflater.inflate(R.layout.item_app_list_row, container, false)
            v.findViewById<ImageView>(R.id.app_row_icon).setImageDrawable(app.bestIcon())
            v.findViewById<TextView>(R.id.app_row_label).text = app.label
            v.setOnClickListener { launchApp(app.packageName); toggleStartMenu(false) }
            v.setOnLongClickListener { showAppContextMenu(v, app); true }
            container.addView(v)
        }
    }

    private fun filterStartMenuContent(query: String, start: LayoutStartMenuBinding) {
        val allAppsScroll = start.allAppsScroll
        val searchResultsList = start.searchResultsList
        val pinnedSection = start.pinnedSection

        if (query.isEmpty()) {
            // Show split layout, hide search results
            allAppsScroll.visibility = View.GONE
            pinnedSection.visibility = View.VISIBLE
            return
        }

        // Show search results, hide split layout
        allAppsScroll.visibility = View.VISIBLE
        pinnedSection.visibility = View.GONE

        searchResultsList.removeAllViews()
        val filtered = allInstalledApps.filter { it.label.contains(query, ignoreCase = true) }
            .sortedBy { it.label.lowercase() }
        filtered.forEach { app ->
            val v = layoutInflater.inflate(R.layout.item_app_list_row, searchResultsList, false)
            v.findViewById<ImageView>(R.id.app_row_icon).setImageDrawable(app.bestIcon())
            v.findViewById<TextView>(R.id.app_row_label).text = app.label
            v.setOnClickListener { launchApp(app.packageName); toggleStartMenu(false) }
            v.setOnLongClickListener { showAppContextMenu(v, app); true }
            searchResultsList.addView(v)
        }
        if (filtered.isEmpty()) {
            searchResultsList.addView(TextView(this).apply {
                text = "No results for \"$query\""
                setTextColor(0x88000000.toInt()); textSize = 13f; setPadding(16, 24, 16, 24)
            })
        }
    }

    private fun showPowerMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menu.add("Sleep"); popup.menu.add("Shut down"); popup.menu.add("Restart")
        popup.setOnMenuItemClickListener {
            Snackbar.make(binding.root, "${it.title} initiated…", Snackbar.LENGTH_SHORT).show(); true
        }
        popup.show()
    }

    private fun populatePinnedApps(start: LayoutStartMenuBinding) {
        val grid = start.pinnedAppsGrid
        grid.removeAllViews()
        val cols = 3  // 3 columns matching WinX right panel
        grid.columnCount = cols
        val maxItems = cols * 4 // 4 rows of pinned apps (12 total)
        val priority = listOf("File Explorer","Microsoft Edge","Notepad","Photos","Settings","Calculator","Camera","Maps","Clock","YouTube","Gmail","Chrome","Phone","Messages","Play Store")
        allInstalledApps.filter { it.label in priority }
            .plus(allInstalledApps.filter { it.label !in priority })
            .distinctBy { it.packageName }.take(maxItems)
            .forEach { app -> addAppToGrid(grid, app) }
    }

    private fun addAppToGrid(grid: GridLayout, app: AppModel) {
        val v = layoutInflater.inflate(R.layout.item_desktop_icon, grid, false)
        val rowSpec = GridLayout.spec(GridLayout.UNDEFINED)
        val colSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        v.layoutParams = GridLayout.LayoutParams(rowSpec, colSpec).apply { width = 0 }
        val label = v.findViewById<TextView>(R.id.icon_label)
        label.text = app.label
        label.setTextColor(0xFF1A1A1A.toInt())  // dark text for light start menu
        label.setShadowLayer(0f, 0f, 0f, 0)     // remove shadow for light bg
        val iconView = v.findViewById<ImageView>(R.id.icon_image)
        val iconSizeDp = ThemeManager.startIconSize(this)
        val iconSizePx = (iconSizeDp * resources.displayMetrics.density).toInt()
        iconView.layoutParams.width = iconSizePx
        iconView.layoutParams.height = iconSizePx
        iconView.setImageDrawable(app.bestIcon())
        v.setOnClickListener { launchApp(app.packageName); toggleStartMenu(false) }
        v.setOnLongClickListener { showAppContextMenu(v, app); true }
        grid.addView(v)
    }

    private fun populateRecommended(start: LayoutStartMenuBinding) {
        val container = start.recommendedContainer
        container.removeAllViews()
        val recents = fileIndexer.getRecentFiles()
        if (recents.isEmpty()) {
            allInstalledApps.take(4).forEach { app ->
                val v = layoutInflater.inflate(R.layout.item_recommended_file, container, false)
                v.findViewById<TextView>(R.id.file_name).text = app.label
                v.findViewById<TextView>(R.id.file_subtitle).text = "Recently used"
                v.findViewById<ImageView>(R.id.file_icon).setImageDrawable(app.bestIcon())
                v.setOnClickListener { launchApp(app.packageName); toggleStartMenu(false) }
                container.addView(v)
            }
        } else {
            recents.forEach { file ->
                val v = layoutInflater.inflate(R.layout.item_recommended_file, container, false)
                v.findViewById<TextView>(R.id.file_name).text = file.name
                v.findViewById<TextView>(R.id.file_subtitle).text = file.extension.uppercase()
                v.setOnClickListener { toggleStartMenu(false) }
                container.addView(v)
            }
        }
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) startActivity(intent)
        else Snackbar.make(binding.root, "App not found", Snackbar.LENGTH_SHORT).show()
    }

    // ── Fatal-error fallback ──────────────────────────────────────────
    // If launcher setup throws, show the stack trace on-screen instead of
    // letting the process force-close — so the failure can be diagnosed.
    private fun showFatalError(e: Throwable) {
        val trace = java.io.StringWriter().also { e.printStackTrace(java.io.PrintWriter(it)) }.toString()
        val message = "ForTheWin failed to start.\n\n$trace"
        val text = TextView(this).apply {
            text = message
            setTextColor(0xFFFFFFFF.toInt())
            setTextIsSelectable(true)
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val scroll = android.widget.ScrollView(this).apply {
            setBackgroundColor(0xFF1B1035.toInt())
            addView(text)
        }
        setContentView(scroll)
    }

    // ── System ────────────────────────────────────────────────────────
    override fun onDestroy() {
        super.onDestroy()
        widgetHostManager.stopListening()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(notifReceiver)
        handler.removeCallbacksAndMessages(null)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.overflow, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.nav_settings -> {
                try { findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.nav_settings) } catch (e: Exception) { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return try {
            val navController = findNavController(R.id.nav_host_fragment_content_main)
            navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
        } catch (e: Exception) {
            super.onSupportNavigateUp()
        }
    }
}
