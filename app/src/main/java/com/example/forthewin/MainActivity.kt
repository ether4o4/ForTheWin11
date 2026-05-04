package com.example.forthewin

import android.app.admin.DevicePolicyManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
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
import androidx.core.content.res.ResourcesCompat
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.forthewin.databinding.ActivityMainBinding
import com.example.forthewin.databinding.LayoutStartMenuBinding
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

    private fun AppModel.bestIcon() = resolvedIcon(iconPackManager)

    private val widgetPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
            if (appWidgetId != -1) {
                widgetHostManager.createWidget(appWidgetId, binding.appBarMain.contentMain.sidebarWidgets!!)
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
        
        widgetHostManager.startListening()

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
                val startMenu = binding.appBarMain.contentMain.startMenuPanel?.root
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
            binding.appBarMain.contentMain.sidebarWidgets!!
        )
    }

    private fun checkPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }
    }

    private fun setupTaskbar() {
        // Taskbar now uses direct findViewById — new layout has no view binding IDs
        // for some elements, so we find by ID on the inflated view
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

        // Wire control center dismiss buttons
        controlCenterBinding.root.findViewById<View>(R.id.notif_dismiss_1)?.setOnClickListener { v ->
            v.parent.let { (it as? View)?.animate()?.alpha(0f)?.translationX(60f)?.setDuration(200)?.withEndAction { (it.parent as? android.view.ViewGroup)?.removeView(it) }?.start() }
        }
        controlCenterBinding.root.findViewById<View>(R.id.notif_dismiss_2)?.setOnClickListener { v ->
            v.parent.let { (it as? View)?.animate()?.alpha(0f)?.translationX(60f)?.setDuration(200)?.withEndAction { (it.parent as? android.view.ViewGroup)?.removeView(it) }?.start() }
        }
    }

    private fun toggleStartMenu(show: Boolean) {
        val startMenu = binding.appBarMain.contentMain.startMenuPanel?.root ?: return
        if (show) {
            startMenu.visibility = View.VISIBLE
            startMenu.translationY = 200f
            startMenu.alpha = 0f
            startMenu.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(400)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            startMenu.animate()
                .translationY(200f)
                .alpha(0f)
                .setDuration(300)
                .withEndAction { startMenu.visibility = View.GONE }
                .start()
        }
    }

    private fun toggleControlCenter(show: Boolean) {
        val panel = binding.appBarMain.contentMain.controlCenterPanel?.root ?: return
        if (show) {
            panel.visibility = View.VISIBLE
            panel.translationY = 200f
            panel.alpha = 0f
            panel.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(400)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            panel.animate()
                .translationY(200f)
                .alpha(0f)
                .setDuration(300)
                .withEndAction { panel.visibility = View.GONE }
                .start()
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
        // Use first 6 installed apps as desktop icons — draggable, long-press to delete
        val desktopApps = allInstalledApps.take(6)
        desktopManager.setIcons(desktopApps)
    }

    private fun openFileExplorer() {
        openFloatingWindow("File Explorer", R.drawable.ic_gallery_black_24dp) { container ->
            val explorerView = layoutInflater.inflate(R.layout.layout_file_explorer, container, false)
            container.addView(explorerView)

            // Populate Sidebar
            val sidebarContainer = explorerView.findViewById<LinearLayout>(R.id.explorer_sidebar_container)
            val sidebarItems = listOf(
                Pair("Home", android.R.drawable.ic_menu_myplaces),
                Pair("Gallery", android.R.drawable.ic_menu_gallery),
                Pair("OneDrive", android.R.drawable.ic_menu_upload),
                Pair("Desktop", android.R.drawable.ic_menu_view),
                Pair("Documents", android.R.drawable.ic_menu_agenda),
                Pair("Downloads", android.R.drawable.ic_menu_save),
                Pair("Pictures", android.R.drawable.ic_menu_gallery),
                Pair("Music", android.R.drawable.ic_lock_silent_mode_off),
                Pair("Videos", android.R.drawable.ic_menu_slideshow),
                Pair("This PC", R.drawable.ic_gallery_black_24dp),
                Pair("Network", R.drawable.ic_camera_black_24dp)
            )

            for (item in sidebarItems) {
                val itemView = layoutInflater.inflate(R.layout.item_explorer_sidebar, sidebarContainer, false)
                itemView.findViewById<TextView>(R.id.sidebar_label).text = item.first
                itemView.findViewById<ImageView>(R.id.sidebar_icon).setImageResource(item.second)
                if (item.first == "Home") {
                    itemView.findViewById<View>(R.id.active_indicator).visibility = View.VISIBLE
                    itemView.findViewById<TextView>(R.id.sidebar_label).setTextColor(Color.WHITE)
                    itemView.findViewById<ImageView>(R.id.sidebar_icon).setColorFilter(ResourcesCompat.getColor(resources, R.color.vista_red_accent, null))
                }
                sidebarContainer.addView(itemView)
            }

            // Populate Quick Access
            val quickAccessGrid = explorerView.findViewById<GridLayout>(R.id.quick_access_grid)
            val folders = listOf("Desktop", "Documents", "Downloads", "Pictures", "Music", "Videos")
            for (folder in folders) {
                val itemView = layoutInflater.inflate(R.layout.item_recommended_file, quickAccessGrid, false)
                val params = GridLayout.LayoutParams()
                params.width = (100 * resources.displayMetrics.density).toInt()
                itemView.layoutParams = params
                itemView.findViewById<TextView>(R.id.file_name).text = folder
                itemView.findViewById<TextView>(R.id.file_subtitle).text = "Stored locally"
                val iconImg = itemView.findViewById<ImageView>(R.id.file_icon)
                iconImg.setImageResource(R.drawable.ic_gallery_black_24dp)
                iconImg.setColorFilter(ResourcesCompat.getColor(resources, R.color.vista_red_accent, null), PorterDuff.Mode.SRC_IN)
                quickAccessGrid.addView(itemView)
            }

            // Populate Recent
            val recentList = explorerView.findViewById<LinearLayout>(R.id.recent_files_list)
            val recents = fileIndexer.getRecentFiles(8)
            for (file in recents) {
                val itemView = layoutInflater.inflate(R.layout.item_recommended_file, recentList, false)
                itemView.findViewById<TextView>(R.id.file_name).text = file.name
                itemView.findViewById<TextView>(R.id.file_subtitle).text = "${file.extension.uppercase()} File"
                val iconImg = itemView.findViewById<ImageView>(R.id.file_icon)
                iconImg.setImageResource(android.R.drawable.ic_menu_edit)
                iconImg.setColorFilter(ResourcesCompat.getColor(resources, R.color.vista_red_accent, null), PorterDuff.Mode.SRC_IN)
                recentList.addView(itemView)
            }
        }
    }

    private fun openNotepad() {
        openFloatingWindow("Notepad", android.R.drawable.ic_menu_edit) { container ->
            val editText = EditText(this)
            editText.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            editText.hint = "Start typing..."
            editText.setTextColor(Color.WHITE)
            editText.setBackgroundColor(Color.TRANSPARENT)
            editText.gravity = android.view.Gravity.TOP
            editText.setPadding(32, 32, 32, 32)
            container.addView(editText)
        }
    }

    private fun openTerminal() {
        openFloatingWindow("Terminal", android.R.drawable.ic_media_play) { container ->
            val terminalView = TextView(this)
            terminalView.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            terminalView.text = "Microsoft Windows [Version 10.0.22621.1702]\n(c) Microsoft Corporation. All rights reserved.\n\nC:\\Users\\redma> _"
            terminalView.setTextColor(Color.GREEN)
            terminalView.setBackgroundColor(Color.BLACK)
            terminalView.typeface = android.graphics.Typeface.MONOSPACE
            terminalView.setPadding(32, 32, 32, 32)
            container.addView(terminalView)
        }
    }

    private fun setupControlCenter() {
        val panel = binding.appBarMain.contentMain.controlCenterPanel ?: return
        panel.brightnessSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Brightness logic
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        panel.volumeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Volume logic
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
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

    /** Called from SettingsFragment after icon pack change — refreshes all icon surfaces */
    fun refreshIcons() {
        desktopManager.setIcons(allInstalledApps.take(6))
        val startMenuPanel = binding.appBarMain.contentMain.startMenuPanel
        if (startMenuPanel?.root?.visibility == View.VISIBLE) {
            populatePinnedApps(startMenuPanel)
        }
        taskbarManager.refreshIcons(iconPackManager)
    }

    override fun onDestroy() {
        super.onDestroy()
        widgetHostManager.stopListening()
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
                    .setAction("Settings") {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    }.show()
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
        val filtered = allInstalledApps.filter { it.label.contains(query, ignoreCase = true) }.take(12)
        for (app in filtered) {
            val itemView = layoutInflater.inflate(R.layout.item_desktop_icon, grid, false)
            itemView.findViewById<TextView>(R.id.icon_label).text = app.label
            val iconImg = itemView.findViewById<ImageView>(R.id.icon_image)
            iconImg.setImageDrawable(app.bestIcon())
            itemView.setOnClickListener { launchApp(app.packageName); toggleStartMenu(false) }
            grid.addView(itemView)
        }
    }

    private fun showPowerMenu(view: View) {
        val popup = androidx.appcompat.widget.PopupMenu(this, view)
        popup.menu.add("Sleep")
        popup.menu.add("Shut down")
        popup.menu.add("Restart")
        popup.setOnMenuItemClickListener { item ->
            Snackbar.make(binding.root, "${item.title} initiated...", Snackbar.LENGTH_SHORT).show()
            true
        }
        popup.show()
    }

    private fun showAllApps(show: Boolean) {
        val start = binding.appBarMain.contentMain.startMenuPanel ?: return
        if (show) {
            start.pinnedAppsGrid.removeAllViews()
            for (app in allInstalledApps) {
                val itemView = layoutInflater.inflate(R.layout.item_desktop_icon, start.pinnedAppsGrid, false)
                itemView.findViewById<TextView>(R.id.icon_label).text = app.label
                val iconImg = itemView.findViewById<ImageView>(R.id.icon_image)
                iconImg.setImageDrawable(app.bestIcon())
                itemView.setOnClickListener { launchApp(app.packageName); toggleStartMenu(false) }
                start.pinnedAppsGrid.addView(itemView)
            }
            start.btnAllAppsDropdown.text = "Pinned ▾"
            start.btnAllAppsDropdown.setOnClickListener { showAllApps(false) }
        } else {
            populatePinnedApps(start)
            start.btnAllAppsDropdown.text = "All apps ▾"
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
                itemView.findViewById<ImageView>(R.id.file_icon).setColorFilter(ResourcesCompat.getColor(resources, R.color.vista_red_accent, null), PorterDuff.Mode.SRC_IN)
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
        val targetApps = listOf("File Explorer", "Microsoft Edge", "Notepad", "Photos", "Settings", "Calculator")
        val pinned = allInstalledApps.filter { it.label in targetApps }.take(6)
        
        for (app in pinned) {
            val itemView = layoutInflater.inflate(R.layout.item_desktop_icon, grid, false)
            itemView.findViewById<TextView>(R.id.icon_label).text = app.label
            val iconImg = itemView.findViewById<ImageView>(R.id.icon_image)
            iconImg.setImageDrawable(app.bestIcon())
            itemView.setOnClickListener { launchApp(app.packageName); toggleStartMenu(false) }
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
