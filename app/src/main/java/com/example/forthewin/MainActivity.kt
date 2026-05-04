package com.example.forthewin

import android.app.admin.DevicePolicyManager
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
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

    // Live notifications from NotificationListenerService
    private data class LiveNotif(val key: String, val pkg: String, val title: String, val text: String, val time: String)
    private val liveNotifications = LinkedHashMap<String, LiveNotif>()

    private fun AppModel.bestIcon() = resolvedIcon(iconPackManager)

    // ── Widget picker ───────────────────────────────────────────────────
    private val widgetPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
            if (appWidgetId != -1) {
                val hostView = widgetHostManager.createWidgetView(appWidgetId)
                if (hostView != null) widgetManager.addWidget(hostView)
            }
        }
    }

    // ── Notification broadcast receiver ────────────────────────────────
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
                }
                NotificationService.ACTION_NOTIF_REMOVED -> {
                    val key = intent.getStringExtra(NotificationService.EXTRA_KEY) ?: return
                    liveNotifications.remove(key)
                    updateNotificationPanel()
                }
                NotificationService.ACTION_NOTIF_LIST -> {
                    updateNotificationPanel()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarMain.toolbar)
        supportActionBar?.hide()

        statsManager = SystemStatsManager(this)
        fileIndexer = FileIndexer(this)
        iconPackManager = (application as LauncherApplication).iconPackManager

        setupManagers()
        checkPermissions()
        loadApps()
        populateDesktopIcons()
        startWidgetUpdates()
        setupTaskbar()
        setupControlCenter()
        applyThemeToControlCenter()

        widgetHostManager.startListening()

        // Register notification receiver
        val filter = IntentFilter().apply {
            addAction(NotificationService.ACTION_NOTIF_POSTED)
            addAction(NotificationService.ACTION_NOTIF_REMOVED)
            addAction(NotificationService.ACTION_NOTIF_LIST)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(notifReceiver, filter)

        val navHostFragment =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment?)!!
        val navController = navHostFragment.navController

        binding.navView?.let {
            appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.nav_transform, R.id.nav_reflow, R.id.nav_slideshow, R.id.nav_settings
                ),
                binding.drawerLayout
            )
            setupActionBarWithNavController(navController, appBarConfiguration)
            it.setupWithNavController(navController)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val startMenu    = binding.appBarMain.contentMain.startMenuPanel?.root
                val controlCenter = binding.appBarMain.contentMain.controlCenterPanel?.root

                if (startMenu?.visibility == View.VISIBLE) {
                    toggleStartMenu(false)
                } else if (controlCenter?.visibility == View.VISIBLE) {
                    toggleControlCenter(false)
                } else if (desktopManager.isEditMode()) {
                    desktopManager.setEditMode(false)
                } else {
                    val windowContainer = binding.appBarMain.contentMain.floatingWindowContainer
                    if (windowContainer != null && windowContainer.childCount > 0) {
                        windowContainer.removeViewAt(windowContainer.childCount - 1)
                    }
                }
            }
        })
    }

    private fun setupManagers() {
        taskbarManager = TaskbarManager(this, binding.appBarMain.contentMain.customTaskbar!!) { packageName ->
            launchApp(packageName)
        }
        windowManager = WindowManagerController(
            this,
            binding.appBarMain.contentMain.floatingWindowContainer!!,
            { title, icon, window -> taskbarManager.addWindowIcon(title, icon, window) },
            { window -> taskbarManager.removeWindowIcon(window) }
        )
        widgetHostManager = WidgetHostManager(this)

        desktopManager = com.example.forthewin.ui.controllers.DesktopManager(
            this,
            binding.appBarMain.contentMain.desktopIconsGrid!!,
            iconPackManager
        ) { packageName -> launchApp(packageName) }

        widgetManager = com.example.forthewin.ui.controllers.WidgetManager(
            this,
            binding.appBarMain.contentMain.floatingWindowContainer!!
        )
    }

    private fun checkPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }
    }

    // ── Theme ─────────────────────────────────────────────────────────

    fun applyThemeToControlCenter() {
        val panel = binding.appBarMain.contentMain.controlCenterPanel?.root ?: return
        val dark = ThemeManager.isDark(this)
        val accent = ThemeManager.accent(this)
        val bg = ThemeManager.surfaceBg(this)
        val textPrimary = ThemeManager.panelText(this)
        val textSecondary = ThemeManager.panelTextSecondary(this)

        // Panel background
        val scrollView = panel.findViewById<View>(R.id.cc_scroll)
        scrollView?.background?.setTint(bg)

        // Header text
        panel.findViewById<TextView>(R.id.cc_notif_header)?.setTextColor(textPrimary)

        // Clear all color
        panel.findViewById<TextView>(R.id.btn_clear_all)?.setTextColor(accent)

        // Sliders
        listOf(R.id.brightness_slider, R.id.volume_slider).forEach { id ->
            val sb = panel.findViewById<SeekBar>(id)
            sb?.progressTintList = ColorStateList.valueOf(accent)
            sb?.thumbTintList = ColorStateList.valueOf(accent)
        }

        // Toggle on background accent color
        val toggleOnBg = if (dark)
            android.graphics.drawable.GradientDrawable().apply {
                setColor((accent and 0x00FFFFFF) or 0x44000000)
                cornerRadius = 8f * resources.displayMetrics.density
                setStroke((1f * resources.displayMetrics.density).toInt(),
                    (accent and 0x00FFFFFF) or 0x66000000)
            }
        else null // use drawable resource for light

        panel.findViewById<View>(R.id.toggle_wifi)?.let { v ->
            v.backgroundTintList = ColorStateList.valueOf(
                if (dark) (accent and 0x00FFFFFF) or 0x44000000 else (accent and 0x00FFFFFF) or 0x22000000
            )
            v.findViewById<ImageView>(R.id.ic_wifi)?.imageTintList = ColorStateList.valueOf(accent)
            v.findViewById<TextView>(R.id.lbl_wifi)?.setTextColor(accent)
        }

        // Notification empty state
        panel.findViewById<TextView>(R.id.cc_notif_empty)?.setTextColor(textSecondary)

        // Apply to notification list items
        updateNotificationPanel()
    }

    // ── Notification Panel ─────────────────────────────────────────────

    private fun updateNotificationPanel() {
        val panel = binding.appBarMain.contentMain.controlCenterPanel?.root ?: return
        val list = panel.findViewById<LinearLayout>(R.id.notification_list) ?: return
        val emptyView = panel.findViewById<TextView>(R.id.cc_notif_empty)

        list.removeAllViews()

        if (liveNotifications.isEmpty()) {
            emptyView?.visibility = View.VISIBLE
            // Update badge
            updateNotificationBadge(0)
        } else {
            emptyView?.visibility = View.GONE
            val dark = ThemeManager.isDark(this)
            val accent = ThemeManager.accent(this)
            val cardBg = ThemeManager.cardBg(this)
            val textPrimary = ThemeManager.panelText(this)
            val textSecondary = ThemeManager.panelTextSecondary(this)

            liveNotifications.values.reversed().take(10).forEach { notif ->
                val card = layoutInflater.inflate(R.layout.item_notification_cc, list, false)

                card.setBackgroundColor(cardBg)
                card.background?.let {
                    val bg = android.graphics.drawable.GradientDrawable().apply {
                        setColor(cardBg)
                        cornerRadius = 8f * resources.displayMetrics.density
                        setStroke(
                            (1f * resources.displayMetrics.density).toInt(),
                            if (dark) 0x33FFFFFF else 0x18000000
                        )
                    }
                    card.background = bg
                }

                // Try to get app icon
                val iconView = card.findViewById<ImageView>(R.id.notif_icon)
                try {
                    iconView?.setImageDrawable(packageManager.getApplicationIcon(notif.pkg))
                    iconView?.clearColorFilter()
                } catch (e: Exception) {
                    iconView?.setImageResource(android.R.drawable.ic_menu_info_details)
                    iconView?.setColorFilter(accent)
                }

                card.findViewById<TextView>(R.id.notif_app_name)?.apply {
                    text = try {
                        packageManager.getApplicationLabel(
                            packageManager.getApplicationInfo(notif.pkg, 0)
                        ).toString()
                    } catch (e: Exception) { notif.pkg }
                    setTextColor(textSecondary)
                }
                card.findViewById<TextView>(R.id.notif_title)?.apply {
                    text = notif.title
                    setTextColor(textPrimary)
                }
                card.findViewById<TextView>(R.id.notif_content)?.apply {
                    text = notif.text
                    setTextColor(textSecondary)
                }
                card.findViewById<TextView>(R.id.notif_time)?.apply {
                    text = notif.time
                    setTextColor(textSecondary)
                }

                // Dismiss button
                card.findViewById<View>(R.id.notif_dismiss)?.setOnClickListener {
                    liveNotifications.remove(notif.key)
                    card.animate().alpha(0f).translationX(60f).setDuration(200)
                        .withEndAction { list.removeView(card); updateNotificationPanel() }
                        .start()
                }

                // Blue/red dot indicator
                card.findViewById<View>(R.id.notif_dot)?.backgroundTintList =
                    ColorStateList.valueOf(accent)

                list.addView(card)
            }
            updateNotificationBadge(liveNotifications.size)
        }
    }

    private fun updateNotificationBadge(count: Int) {
        val taskbarRoot = binding.appBarMain.contentMain.customTaskbar?.root ?: return
        val badge = taskbarRoot.findViewById<TextView>(R.id.notification_count)
        badge?.text = if (count > 9) "9+" else count.toString()
        badge?.visibility = if (count > 0) View.VISIBLE else View.INVISIBLE
    }

    // ── Taskbar ────────────────────────────────────────────────────────

    private fun setupTaskbar() {
        val taskbarRoot = binding.appBarMain.contentMain.customTaskbar?.root ?: return
        val startMenuBinding = binding.appBarMain.contentMain.startMenuPanel ?: return
        val controlCenterBinding = binding.appBarMain.contentMain.controlCenterPanel ?: return
        val startMenu = startMenuBinding.root
        val controlCenter = controlCenterBinding.root

        taskbarRoot.findViewById<View>(R.id.btn_start_orb)?.setOnClickListener {
            if (startMenu.visibility == View.GONE) {
                toggleControlCenter(false)
                toggleStartMenu(true)
                setupStartMenuActions(startMenuBinding)
            } else {
                toggleStartMenu(false)
            }
        }

        taskbarRoot.findViewById<View>(R.id.btn_taskbar_search)?.setOnClickListener {
            Snackbar.make(binding.root, "Search", Snackbar.LENGTH_SHORT).show()
        }

        taskbarRoot.findViewById<View>(R.id.btn_taskbar_taskview)?.setOnClickListener {
            Snackbar.make(binding.root, "Task View", Snackbar.LENGTH_SHORT).show()
        }

        taskbarRoot.findViewById<View>(R.id.btn_taskbar_explorer)?.setOnClickListener {
            openFileExplorer()
        }

        taskbarRoot.findViewById<View>(R.id.btn_taskbar_browser)?.setOnClickListener {
            openBrowser()
        }

        taskbarRoot.findViewById<View>(R.id.btn_taskbar_settings)?.setOnClickListener {
            findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.nav_settings)
        }

        taskbarRoot.findViewById<View>(R.id.taskbar_system_tray)?.setOnClickListener {
            if (controlCenter.visibility == View.GONE) {
                toggleStartMenu(false)
                toggleControlCenter(true)
            } else {
                toggleControlCenter(false)
            }
        }

        taskbarRoot.findViewById<View>(R.id.btn_notifications)?.setOnClickListener {
            if (controlCenter.visibility == View.GONE) {
                toggleStartMenu(false)
                toggleControlCenter(true)
            } else {
                toggleControlCenter(false)
            }
        }

        // ── Taskbar resize (drag top edge to resize height) ────────────
        setupTaskbarResize(taskbarRoot)

        // ── Taskbar long-press for context menu ────────────────────────
        taskbarRoot.setOnLongClickListener {
            showDesktopContextMenu(it)
            true
        }
    }

    private fun setupTaskbarResize(taskbarRoot: View) {
        // The top edge of the taskbar container — drag up to make taller, down to shrink
        val minHeightDp = 44f
        val maxHeightDp = 72f
        val density = resources.displayMetrics.density
        var startY = 0f
        var startHeight = 0

        taskbarRoot.setOnTouchListener { v, event ->
            // Only activate in the top 12dp strip for resize
            val topZonePx = 12 * density
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (event.y < topZonePx) {
                        startY = event.rawY
                        startHeight = v.height
                        true
                    } else false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (startY > 0f) {
                        val delta = startY - event.rawY
                        val newH = (startHeight + delta)
                            .coerceIn(minHeightDp * density, maxHeightDp * density)
                            .toInt()
                        val lp = v.layoutParams
                        lp.height = newH
                        v.layoutParams = lp
                        true
                    } else false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    startY = 0f
                    false
                }
                else -> false
            }
        }
    }

    // ── Control Center resize ──────────────────────────────────────────

    private fun setupControlCenterResize(panelRoot: View) {
        val resizeHandle = panelRoot.findViewById<View>(R.id.cc_resize_handle) ?: return
        val density = resources.displayMetrics.density
        val minWidthDp = 260f
        val maxWidthDp = 440f
        var startX = 0f
        var startWidth = 0

        resizeHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startWidth = panelRoot.width
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val delta = startX - event.rawX
                    val newW = (startWidth + delta)
                        .coerceIn(minWidthDp * density, maxWidthDp * density)
                        .toInt()
                    val lp = panelRoot.layoutParams
                    lp.width = newW
                    panelRoot.layoutParams = lp
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    startX = 0f
                    true
                }
                else -> false
            }
        }
    }

    // ── Desktop context menu (long-press) ─────────────────────────────

    private fun showDesktopContextMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("Add Widget")
        popup.menu.add("Change Wallpaper")
        popup.menu.add("Display Settings")
        popup.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                "Add Widget" -> widgetHostManager.pickWidget(widgetPickerLauncher)
                "Change Wallpaper" -> {
                    val intent = Intent(Intent.ACTION_SET_WALLPAPER)
                    startActivity(Intent.createChooser(intent, "Select Wallpaper"))
                }
                "Display Settings" -> findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.nav_settings)
            }
            true
        }
        popup.show()
    }

    // ── Desktop icon long-press (right-click) ─────────────────────────

    private fun setupDesktopLongPress() {
        val desktopLayer = binding.appBarMain.contentMain.desktopIconsGrid ?: return
        desktopLayer.setOnLongClickListener {
            if (!desktopManager.isEditMode()) {
                showDesktopContextMenu(it)
            }
            true
        }
    }

    // ── toggles ───────────────────────────────────────────────────────

    private fun toggleStartMenu(show: Boolean) {
        val startMenu = binding.appBarMain.contentMain.startMenuPanel?.root ?: return
        if (show) {
            startMenu.visibility = View.VISIBLE
            startMenu.translationY = 200f
            startMenu.alpha = 0f
            startMenu.animate()
                .translationY(0f).alpha(1f)
                .setDuration(400).setInterpolator(DecelerateInterpolator()).start()
        } else {
            startMenu.animate()
                .translationY(200f).alpha(0f)
                .setDuration(300).withEndAction { startMenu.visibility = View.GONE }.start()
        }
    }

    private fun toggleControlCenter(show: Boolean) {
        val panel = binding.appBarMain.contentMain.controlCenterPanel?.root ?: return
        if (show) {
            panel.visibility = View.VISIBLE
            panel.translationY = 200f
            panel.alpha = 0f
            panel.animate()
                .translationY(0f).alpha(1f)
                .setDuration(400).setInterpolator(DecelerateInterpolator()).start()
            // Refresh notifications when opened
            updateNotificationPanel()
        } else {
            panel.animate()
                .translationY(200f).alpha(0f)
                .setDuration(300).withEndAction { panel.visibility = View.GONE }.start()
        }
    }

    private fun loadApps() {
        allInstalledApps.clear()
        val i = Intent(Intent.ACTION_MAIN, null)
        i.addCategory(Intent.CATEGORY_LAUNCHER)
        val availableActivities = packageManager.queryIntentActivities(i, 0)
        for (ri in availableActivities) {
            val app = AppModel(
                ri.loadLabel(packageManager).toString(),
                ri.activityInfo.packageName,
                ri.activityInfo.name,
                ri.activityInfo.loadIcon(packageManager)
            )
            allInstalledApps.add(app)
        }
        allInstalledApps.sortBy { it.label.lowercase() }
    }

    private fun startWidgetUpdates() {
        val runnable = object : Runnable {
            override fun run() {
                updateWidgets()
                updateTaskbarTray()
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(runnable)
    }

    private fun updateWidgets() {
        val widgets = binding.appBarMain.contentMain.sidebarWidgets ?: return
        val calendar = Calendar.getInstance()
        val hours = String.format("%02d", calendar.get(Calendar.HOUR_OF_DAY))
        val minutes = String.format("%02d", calendar.get(Calendar.MINUTE))
        val date = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(calendar.time)

        widgets.findViewById<TextView>(R.id.clock_hours)?.text = hours
        widgets.findViewById<TextView>(R.id.clock_minutes)?.text = minutes
        widgets.findViewById<TextView>(R.id.clock_date)?.text = date.uppercase()

        val cpuUsage = statsManager.getCpuUsage()
        val ramUsage = statsManager.getMemoryUsage()
        widgets.findViewById<TextView>(R.id.cpu_value)?.text = "$cpuUsage%"
        widgets.findViewById<ProgressBar>(R.id.cpu_progress)?.progress = cpuUsage
        widgets.findViewById<TextView>(R.id.ram_value)?.text = "$ramUsage%"
        widgets.findViewById<ProgressBar>(R.id.ram_progress)?.progress = ramUsage
    }

    private fun updateTaskbarTray() {
        val taskbar = binding.appBarMain.contentMain.customTaskbar ?: return
        val calendar = Calendar.getInstance()
        val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(calendar.time)
        val date = SimpleDateFormat("M/d/yyyy", Locale.getDefault()).format(calendar.time)

        taskbar.root.findViewById<TextView>(R.id.taskbar_time)?.text = time
        taskbar.root.findViewById<TextView>(R.id.taskbar_date)?.text = date
    }

    private fun populateDesktopIcons() {
        val desktopApps = allInstalledApps.take(6)
        desktopManager.setIcons(desktopApps)
        setupDesktopLongPress()
    }

    private fun openFileExplorer() {
        openFloatingWindow("File Explorer", R.drawable.ic_win11_folder) { container ->
            val explorerView = layoutInflater.inflate(R.layout.layout_file_explorer, container, false)
            container.addView(explorerView)

            val sidebarContainer = explorerView.findViewById<LinearLayout>(R.id.explorer_sidebar_container)
            val sidebarItems = listOf(
                Pair("Home", R.drawable.ic_win11_folder),
                Pair("Gallery", android.R.drawable.ic_menu_gallery),
                Pair("OneDrive", android.R.drawable.ic_menu_upload),
                Pair("Desktop", android.R.drawable.ic_menu_view),
                Pair("Documents", android.R.drawable.ic_menu_agenda),
                Pair("Downloads", android.R.drawable.ic_menu_save),
                Pair("Pictures", android.R.drawable.ic_menu_gallery),
                Pair("Music", android.R.drawable.ic_lock_silent_mode_off),
                Pair("Videos", android.R.drawable.ic_menu_slideshow),
                Pair("This PC", android.R.drawable.ic_menu_info_details),
                Pair("Network", android.R.drawable.ic_menu_share)
            )
            for (item in sidebarItems) {
                val v = layoutInflater.inflate(R.layout.item_explorer_sidebar, sidebarContainer, false)
                v.findViewById<TextView>(R.id.sidebar_label).text = item.first
                v.findViewById<ImageView>(R.id.sidebar_icon).setImageResource(item.second)
                if (item.first == "Home") {
                    v.findViewById<View>(R.id.active_indicator).visibility = View.VISIBLE
                    v.setBackgroundColor(0x110078D4)
                }
                sidebarContainer.addView(v)
            }

            val quickGrid = explorerView.findViewById<GridLayout>(R.id.quick_access_grid)
            val folders = listOf(
                Pair("Desktop", 0xFFE74856.toInt()),
                Pair("Documents", 0xFFFFB900.toInt()),
                Pair("Downloads", 0xFF00B294.toInt()),
                Pair("Pictures", 0xFFE74856.toInt()),
                Pair("Music", 0xFFFFB900.toInt()),
                Pair("Videos", 0xFF8764B8.toInt())
            )
            for ((name, color) in folders) {
                val v = layoutInflater.inflate(R.layout.item_recommended_file, quickGrid, false)
                val spec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                val lp = GridLayout.LayoutParams(spec, spec)
                lp.width = 0
                v.layoutParams = lp
                v.findViewById<TextView>(R.id.file_name).text = name
                v.findViewById<TextView>(R.id.file_subtitle).text = "Stored locally"
                val icon = v.findViewById<ImageView>(R.id.file_icon)
                icon.setImageResource(R.drawable.ic_win11_folder)
                icon.setColorFilter(color, PorterDuff.Mode.SRC_IN)
                quickGrid.addView(v)
            }

            val recentList = explorerView.findViewById<LinearLayout>(R.id.recent_files_list)
            val recents = fileIndexer.getRecentFiles(8)
            if (recents.isEmpty()) {
                val empty = TextView(this)
                empty.text = "After you've opened some files, we'll show the most recent ones here."
                empty.setTextColor(0x88000000.toInt())
                empty.textSize = 12f
                recentList.addView(empty)
            } else {
                for (file in recents) {
                    val v = layoutInflater.inflate(R.layout.item_recommended_file, recentList, false)
                    v.findViewById<TextView>(R.id.file_name).text = file.name
                    v.findViewById<TextView>(R.id.file_subtitle).text = "${file.extension.uppercase()} · Documents"
                    v.findViewById<ImageView>(R.id.file_icon).setImageResource(android.R.drawable.ic_menu_edit)
                    recentList.addView(v)
                }
            }
            explorerView.findViewById<TextView>(R.id.explorer_item_count)?.text = "${folders.size} items"
        }
    }

    private fun openNotepad() {
        openFloatingWindow("Notepad", android.R.drawable.ic_menu_edit) { container ->
            container.setBackgroundColor(Color.WHITE)
            val editText = EditText(this)
            editText.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            editText.hint = "Start typing..."
            editText.setTextColor(Color.BLACK)
            editText.setHintTextColor(0x88000000.toInt())
            editText.setBackgroundColor(Color.TRANSPARENT)
            editText.gravity = android.view.Gravity.TOP
            editText.setPadding(24, 24, 24, 24)
            editText.textSize = 14f
            container.addView(editText)
        }
    }

    private fun setupControlCenter() {
        val panel = binding.appBarMain.contentMain.controlCenterPanel ?: return
        val root = panel.root

        panel.brightnessSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        panel.volumeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Clear all button
        root.findViewById<View>(R.id.btn_clear_all)?.setOnClickListener {
            liveNotifications.clear()
            updateNotificationPanel()
        }

        // More settings link
        root.findViewById<View>(R.id.btn_more_settings)?.setOnClickListener {
            toggleControlCenter(false)
            findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.nav_settings)
        }

        // Resize handle
        setupControlCenterResize(root)

        // Initial notification state
        updateNotificationPanel()
    }

    private fun openBrowser() {
        openFloatingWindow("Web Browser", R.drawable.ic_camera_black_24dp) { container ->
            val webView = WebView(this)
            webView.settings.javaScriptEnabled = true
            webView.loadUrl("https://www.google.com")
            container.addView(webView)
        }
    }

    private fun openFloatingWindow(title: String, iconRes: Int, contentInitializer: (FrameLayout) -> Unit) {
        windowManager.openWindow(title, iconRes, contentInitializer)
    }

    fun refreshIcons() {
        desktopManager.setIcons(allInstalledApps.take(6))
        val startMenuPanel = binding.appBarMain.contentMain.startMenuPanel
        if (startMenuPanel?.root?.visibility == View.VISIBLE) {
            populatePinnedApps(startMenuPanel)
        }
        taskbarManager.refreshIcons(iconPackManager)
    }

    /** Called from SettingsFragment — apply theme changes live */
    fun applyTheme(dark: Boolean, redAccent: Boolean) {
        ThemeManager.setDark(this, dark)
        ThemeManager.setRedAccent(this, redAccent)
        applyThemeToControlCenter()
        // Tint taskbar accent
        val taskbarRoot = binding.appBarMain.contentMain.customTaskbar?.root
        val accent = ThemeManager.accent(this)
        taskbarRoot?.findViewById<TextView>(R.id.notification_count)
            ?.backgroundTintList = ColorStateList.valueOf(accent)
    }

    override fun onDestroy() {
        super.onDestroy()
        widgetHostManager.stopListening()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(notifReceiver)
    }

    private fun setupStartMenuActions(startMenuBinding: LayoutStartMenuBinding) {
        val start = startMenuBinding
        populateSidebar(start)
        populateRecommended(start)
        populatePinnedApps(start)
        populateQuickAccess(start)

        start.startSearchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterStartMenuContent(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        start.btnPower.setOnClickListener { showPowerMenu(it) }
        start.btnAllAppsDropdown.setOnClickListener { showAllApps(true) }

        start.btnSettingsQuick.setOnClickListener {
            toggleStartMenu(false)
            findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.nav_settings)
        }

        start.btnLockQuick.setOnClickListener {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            try {
                dpm.lockNow()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Device Admin required for Lock", Snackbar.LENGTH_LONG)
                    .setAction("Settings") { startActivity(Intent(Settings.ACTION_SETTINGS)) }.show()
            }
        }

        start.btnMoonQuick.setOnClickListener {
            Snackbar.make(binding.root, "Entering Sleep Mode...", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun filterStartMenuContent(query: String) {
        val startMenuPanel = binding.appBarMain.contentMain.startMenuPanel ?: return
        val grid = startMenuPanel.pinnedAppsGrid
        if (query.isEmpty()) {
            populatePinnedApps(startMenuPanel)
            return
        }
        grid.removeAllViews()
        grid.columnCount = 4
        val filtered = allInstalledApps.filter { it.label.contains(query, ignoreCase = true) }.take(12)
        for (app in filtered) {
            val itemView = layoutInflater.inflate(R.layout.item_desktop_icon, grid, false)
            val spec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            val lp = GridLayout.LayoutParams(spec, spec)
            lp.width = 0
            itemView.layoutParams = lp
            itemView.findViewById<TextView>(R.id.icon_label).text = app.label
            itemView.findViewById<ImageView>(R.id.icon_image).setImageDrawable(app.bestIcon())
            itemView.setOnClickListener { launchApp(app.packageName); toggleStartMenu(false) }
            itemView.setOnLongClickListener { showAppContextMenu(itemView, app); true }
            grid.addView(itemView)
        }
    }

    private fun showPowerMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menu.add("Sleep")
        popup.menu.add("Shut down")
        popup.menu.add("Restart")
        popup.setOnMenuItemClickListener { item ->
            Snackbar.make(binding.root, "${item.title} initiated...", Snackbar.LENGTH_SHORT).show()
            true
        }
        popup.show()
    }

    /** Long-press context menu for an app icon (in start menu or app drawer) */
    private fun showAppContextMenu(anchor: View, app: AppModel) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("Open")
        popup.menu.add("Add to Home Screen")
        popup.menu.add("App info")
        popup.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                "Open" -> { launchApp(app.packageName); toggleStartMenu(false) }
                "Add to Home Screen" -> {
                    desktopManager.addSingleIcon(app)
                    toggleStartMenu(false)
                    Snackbar.make(binding.root, "${app.label} added to desktop", Snackbar.LENGTH_SHORT).show()
                }
                "App info" -> {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${app.packageName}")
                    })
                }
            }
            true
        }
        popup.show()
    }

    private fun showAllApps(show: Boolean) {
        val start = binding.appBarMain.contentMain.startMenuPanel ?: return
        if (show) {
            start.pinnedAppsGrid.removeAllViews()
            start.pinnedAppsGrid.columnCount = 4
            for (app in allInstalledApps) {
                val itemView = layoutInflater.inflate(R.layout.item_desktop_icon, start.pinnedAppsGrid, false)
                val spec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                val lp = GridLayout.LayoutParams(spec, spec)
                lp.width = 0
                itemView.layoutParams = lp
                itemView.findViewById<TextView>(R.id.icon_label).text = app.label
                itemView.findViewById<ImageView>(R.id.icon_image).setImageDrawable(app.bestIcon())
                itemView.setOnClickListener { launchApp(app.packageName); toggleStartMenu(false) }
                itemView.setOnLongClickListener { showAppContextMenu(itemView, app); true }
                start.pinnedAppsGrid.addView(itemView)
            }
            start.btnAllAppsDropdown.text = "Pinned  ‹"
            start.btnAllAppsDropdown.setOnClickListener { showAllApps(false) }
        } else {
            populatePinnedApps(start)
            start.btnAllAppsDropdown.text = "All apps  ›"
            start.btnAllAppsDropdown.setOnClickListener { showAllApps(true) }
        }
    }

    private fun populateSidebar(binding: LayoutStartMenuBinding) {
        val container = binding.sidebarContainer
        container.removeAllViews()
        val categories = listOf(
            Pair("All", android.R.drawable.ic_dialog_dialer),
            Pair("Pinned", android.R.drawable.ic_menu_myplaces),
            Pair("Productivity", android.R.drawable.ic_menu_agenda),
            Pair("Development", android.R.drawable.ic_menu_edit),
            Pair("Media", android.R.drawable.ic_menu_gallery),
            Pair("Utilities", android.R.drawable.ic_menu_manage),
            Pair("System", android.R.drawable.ic_menu_preferences),
            Pair("Folders", android.R.drawable.ic_menu_directions)
        )
        for (cat in categories) {
            val itemView = layoutInflater.inflate(R.layout.item_sidebar_category, container, false)
            val label = itemView.findViewById<TextView>(R.id.sidebar_item_label)
            val icon = itemView.findViewById<ImageView>(R.id.sidebar_item_icon)
            label.text = cat.first
            icon.setImageResource(cat.second)
            if (cat.first == "All") {
                itemView.background = ResourcesCompat.getDrawable(resources, R.drawable.sidebar_item_active, null)
                label.setTextColor(ResourcesCompat.getColor(resources, R.color.white, null))
                icon.setColorFilter(ResourcesCompat.getColor(resources, R.color.vista_red_accent, null))
            }
            itemView.setOnClickListener {
                Snackbar.make(binding.root, "Category: ${cat.first}", Snackbar.LENGTH_SHORT).show()
            }
            container.addView(itemView)
        }
        val footer = layoutInflater.inflate(R.layout.layout_sidebar_footer, container, false)
        container.addView(footer)
    }

    private fun populateRecommended(binding: LayoutStartMenuBinding) {
        val container = binding.recommendedContainer
        container.removeAllViews()
        val recentFiles = fileIndexer.getRecentFiles()
        if (recentFiles.isEmpty()) {
            val topApps = allInstalledApps.take(4)
            for (app in topApps) {
                val itemView = layoutInflater.inflate(R.layout.item_recommended_file, container, false)
                itemView.findViewById<TextView>(R.id.file_name).text = app.label
                itemView.findViewById<TextView>(R.id.file_subtitle).text = "Recently installed"
                itemView.findViewById<ImageView>(R.id.file_icon).setImageDrawable(app.bestIcon())
                itemView.setOnClickListener { launchApp(app.packageName); toggleStartMenu(false) }
                container.addView(itemView)
            }
        } else {
            for (file in recentFiles) {
                val itemView = layoutInflater.inflate(R.layout.item_recommended_file, container, false)
                itemView.findViewById<TextView>(R.id.file_name).text = file.name
                itemView.findViewById<TextView>(R.id.file_subtitle).text = "Type: ${file.extension}"
                itemView.findViewById<ImageView>(R.id.file_icon).setImageResource(R.drawable.ic_gallery_black_24dp)
                itemView.findViewById<ImageView>(R.id.file_icon).setColorFilter(
                    ResourcesCompat.getColor(resources, R.color.vista_red_accent, null), PorterDuff.Mode.SRC_IN
                )
                itemView.setOnClickListener {
                    Snackbar.make(binding.root, "Opening ${file.name}", Snackbar.LENGTH_SHORT).show()
                    toggleStartMenu(false)
                }
                container.addView(itemView)
            }
        }
    }

    private fun populatePinnedApps(binding: LayoutStartMenuBinding) {
        val grid = binding.pinnedAppsGrid
        grid.removeAllViews()
        grid.columnCount = 4
        val targetApps = listOf("File Explorer", "Microsoft Edge", "Notepad", "Photos", "Settings", "Calculator", "Camera", "Maps", "Clock", "YouTube", "Gmail", "Chrome")
        val pinned = allInstalledApps.filter { it.label in targetApps }
            .plus(allInstalledApps.filter { it.label !in targetApps })
            .distinctBy { it.packageName }
            .take(12)

        for (app in pinned) {
            val itemView = layoutInflater.inflate(R.layout.item_desktop_icon, grid, false)
            val spec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            val lp = GridLayout.LayoutParams(spec, spec)
            lp.width = 0
            itemView.layoutParams = lp
            itemView.findViewById<TextView>(R.id.icon_label).text = app.label
            itemView.findViewById<ImageView>(R.id.icon_image).setImageDrawable(app.bestIcon())
            itemView.setOnClickListener { launchApp(app.packageName); toggleStartMenu(false) }
            itemView.setOnLongClickListener { showAppContextMenu(itemView, app); true }
            grid.addView(itemView)
        }
    }

    private fun populateQuickAccess(binding: LayoutStartMenuBinding) {
        val container = binding.quickAccessContainer
        container.removeAllViews()
        val folderItems = listOf("Downloads", "Documents", "Pictures", "Videos")
        for (folder in folderItems) {
            val itemView = layoutInflater.inflate(R.layout.item_recommended_file, container, false)
            val params = itemView.layoutParams
            params.width = (140 * resources.displayMetrics.density).toInt()
            itemView.layoutParams = params
            itemView.findViewById<TextView>(R.id.file_name).text = folder
            itemView.findViewById<TextView>(R.id.file_subtitle).visibility = View.GONE
            val iconImg = itemView.findViewById<ImageView>(R.id.file_icon)
            iconImg.setImageResource(R.drawable.ic_gallery_black_24dp)
            iconImg.setColorFilter(ResourcesCompat.getColor(resources, R.color.vista_red_accent, null), PorterDuff.Mode.SRC_IN)
            itemView.setOnClickListener { Snackbar.make(binding.root, "Opening $folder...", Snackbar.LENGTH_SHORT).show() }
            container.addView(itemView)
        }
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) startActivity(intent)
        else Snackbar.make(binding.root, "App not found", Snackbar.LENGTH_SHORT).show()
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
