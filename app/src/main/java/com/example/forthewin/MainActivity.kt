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

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private var allInstalledApps = mutableListOf<AppModel>()
    private lateinit var statsManager: SystemStatsManager
    private lateinit var fileIndexer: FileIndexer
    private val handler = Handler(Looper.getMainLooper())

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

    // ── Widget picker — 2-step flow: pick → configure (optional) → create ──
    private val widgetConfigureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val appWidgetId = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            ?: widgetHostManager.pendingWidgetId
        if (result.resultCode == RESULT_OK && appWidgetId != -1) {
            finalizeWidget(appWidgetId)
        } else if (appWidgetId != -1) {
            widgetHostManager.deleteWidgetId(appWidgetId)
        }
    }

    private val widgetPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val appWidgetId = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            ?: widgetHostManager.pendingWidgetId
        if (result.resultCode == RESULT_OK && appWidgetId != -1) {
            // Check if this widget needs a configure step
            val needsConfigure = widgetHostManager.launchConfigureIfNeeded(appWidgetId, widgetConfigureLauncher)
            if (!needsConfigure) {
                finalizeWidget(appWidgetId)
            }
            // else: widgetConfigureLauncher will handle it
        } else if (appWidgetId != -1) {
            widgetHostManager.deleteWidgetId(appWidgetId)
        }
    }

    private fun finalizeWidget(appWidgetId: Int) {
        val hostView = widgetHostManager.createWidgetView(appWidgetId)
        if (hostView != null) {
            widgetManager.addWidget(hostView)
        } else {
            widgetHostManager.deleteWidgetId(appWidgetId)
            Snackbar.make(binding.root, "Widget failed to load", Snackbar.LENGTH_SHORT).show()
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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarMain.toolbar)
        supportActionBar?.hide()

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

        val navHostFragment =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment?)!!
        val navController = navHostFragment.navController

        binding.navView?.let {
            appBarConfiguration = AppBarConfiguration(
                setOf(R.id.nav_transform, R.id.nav_reflow, R.id.nav_slideshow, R.id.nav_settings),
                binding.drawerLayout
            )
            setupActionBarWithNavController(navController, appBarConfiguration)
            it.setupWithNavController(navController)
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
            Snackbar.make(binding.root, "Task View", Snackbar.LENGTH_SHORT).show()
        }

        taskbarRoot.findViewById<View>(R.id.btn_taskbar_explorer)?.setOnClickListener { openFileExplorer() }
        taskbarRoot.findViewById<View>(R.id.btn_taskbar_browser)?.setOnClickListener  { openBrowser() }
        taskbarRoot.findViewById<View>(R.id.btn_taskbar_settings)?.setOnClickListener {
            findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.nav_settings)
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
            findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.nav_settings)
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
        val isDark = ThemeManager.isDark(this)
        val bg = if (isDark) 0xE6202023.toInt() else 0xFFFFFFFF.toInt()
        val textPri = if (isDark) 0xFFFFFFFF.toInt() else 0xFF1A1A1A.toInt()
        val textSec = if (isDark) 0x88FFFFFF.toInt() else 0x88000000.toInt()
        val iconTint = if (isDark) 0xFFFFFFFF.toInt() else 0xFF444444.toInt()

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
    }

    private fun applyThemeToStartMenu() {
        val sm = binding.appBarMain.contentMain.startMenuPanel ?: return
        if (sm.root.visibility != View.VISIBLE) return
        // Refresh items with current theme
        populateAllAppsList(sm)
        populatePinnedApps(sm)
        populateRecommended(sm)
    }

    fun refreshStartMenu() {
        val sm = binding.appBarMain.contentMain.startMenuPanel ?: return
        if (sm.root.visibility == View.VISIBLE) {
            populatePinnedApps(sm)
            populateAllAppsList(sm)
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
                4 -> findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.nav_settings)
            }
            true
        }
        popup.show()
    }

    private fun pickWallpaper() {
        // Option 1: system chooser
        try {
            val intent = Intent(Intent.ACTION_SET_WALLPAPER)
            startActivity(Intent.createChooser(intent, "Choose Wallpaper"))
        } catch (e: Exception) {
            // Option 2: gallery picker → apply ourselves
            wallpaperPickerLauncher.launch("image/*")
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

            val sidebarContainer = v.findViewById<LinearLayout>(R.id.explorer_sidebar_container)
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
                sidebarContainer.addView(item)
            }

            val quickGrid = v.findViewById<GridLayout>(R.id.quick_access_grid)
            listOf(
                Triple("Desktop", 0xFFE74856.toInt(), R.drawable.ic_win11_folder),
                Triple("Documents", 0xFFFFB900.toInt(), R.drawable.ic_win11_folder),
                Triple("Downloads", 0xFF00B294.toInt(), R.drawable.ic_win11_folder),
                Triple("Pictures", 0xFFE74856.toInt(), R.drawable.ic_win11_folder),
                Triple("Music", 0xFFFFB900.toInt(), R.drawable.ic_win11_folder),
                Triple("Videos", 0xFF8764B8.toInt(), R.drawable.ic_win11_folder)
            ).forEach { (name, color, iconRes) ->
                val item = layoutInflater.inflate(R.layout.item_recommended_file, quickGrid, false)
                val spec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                val lp = GridLayout.LayoutParams(spec, spec).apply { width = 0 }
                item.layoutParams = lp
                item.findViewById<TextView>(R.id.file_name).text = name
                item.findViewById<TextView>(R.id.file_subtitle).text = "Stored locally"
                item.findViewById<ImageView>(R.id.file_icon).apply {
                    setImageResource(iconRes); setColorFilter(color, PorterDuff.Mode.SRC_IN)
                }
                quickGrid.addView(item)
            }

            val recentList = v.findViewById<LinearLayout>(R.id.recent_files_list)
            val recents = fileIndexer.getRecentFiles(8)
            if (recents.isEmpty()) {
                recentList.addView(TextView(this).apply {
                    text = "No recent files. Open documents, images, or spreadsheets and they'll appear here."
                    setTextColor(0x88000000.toInt()); textSize = 12f; setPadding(8, 8, 8, 8)
                })
            } else {
                recents.forEach { file ->
                    val item = layoutInflater.inflate(R.layout.item_recommended_file, recentList, false)
                    item.findViewById<TextView>(R.id.file_name).text = file.name
                    item.findViewById<TextView>(R.id.file_subtitle).text = "${file.extension.uppercase()} · Recent"
                    item.findViewById<ImageView>(R.id.file_icon).setImageResource(android.R.drawable.ic_menu_edit)
                    recentList.addView(item)
                }
            }
            v.findViewById<TextView>(R.id.explorer_item_count)?.text = "6 folders"
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
                filterStartMenuContent(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        start.btnPower.setOnClickListener { showPowerMenu(it) }
        start.btnSettingsQuick.setOnClickListener {
            toggleStartMenu(false)
            findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.nav_settings)
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

    private fun filterStartMenuContent(query: String) {
        val sm = binding.appBarMain.contentMain.startMenuPanel ?: return
        val container = sm.allAppsList
        container.removeAllViews()
        val filtered = if (query.isEmpty()) allInstalledApps.sortedBy { it.label.lowercase() }
            else allInstalledApps.filter { it.label.contains(query, ignoreCase = true) }.sortedBy { it.label.lowercase() }
        filtered.forEach { app ->
            val v = layoutInflater.inflate(R.layout.item_app_list_row, container, false)
            v.findViewById<ImageView>(R.id.app_row_icon).setImageDrawable(app.bestIcon())
            v.findViewById<TextView>(R.id.app_row_label).text = app.label
            v.setOnClickListener { launchApp(app.packageName); toggleStartMenu(false) }
            v.setOnLongClickListener { showAppContextMenu(v, app); true }
            container.addView(v)
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
        val cols = ThemeManager.startColumns(this)
        grid.columnCount = cols
        val maxItems = cols * 3 // 3 rows of pinned apps
        val priority = listOf("File Explorer","Microsoft Edge","Notepad","Photos","Settings","Calculator","Camera","Maps","Clock","YouTube","Gmail","Chrome")
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
                findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.nav_settings)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
