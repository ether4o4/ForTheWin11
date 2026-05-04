package com.example.forthewin.ui.settings

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.forthewin.LauncherApplication
import com.example.forthewin.MainActivity
import com.example.forthewin.R
import com.example.forthewin.ThemeManager
import com.example.forthewin.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val settingsViewModel = ViewModelProvider(this).get(SettingsViewModel::class.java)
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textSettings
        settingsViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }

        setupThemeSwitch()
        setupIconPackPicker()
        setupThemePicker()

        return root
    }

    private fun setupThemePicker() {
        val root = binding.root

        // Theme mode buttons
        val btnLight = root.findViewById<View>(R.id.btn_theme_light)
        val btnDark  = root.findViewById<View>(R.id.btn_theme_dark)
        // Accent buttons
        val btnBlue  = root.findViewById<View>(R.id.btn_accent_blue)
        val btnRed   = root.findViewById<View>(R.id.btn_accent_red)

        val ctx = requireContext()
        val isDark = ThemeManager.isDark(ctx)
        val isRed  = ThemeManager.isRedAccent(ctx)

        fun updateButtonStates() {
            val isDarkNow = ThemeManager.isDark(ctx)
            val isRedNow  = ThemeManager.isRedAccent(ctx)
            val accent    = ThemeManager.accent(ctx)

            btnLight?.alpha = if (!isDarkNow) 1f else 0.45f
            btnDark?.alpha  = if (isDarkNow)  1f else 0.45f
            btnBlue?.alpha  = if (!isRedNow)  1f else 0.45f
            btnRed?.alpha   = if (isRedNow)   1f else 0.45f

            // Tint selected indicator
            btnLight?.backgroundTintList = if (!isDarkNow) ColorStateList.valueOf(accent) else null
            btnDark?.backgroundTintList  = if (isDarkNow)  ColorStateList.valueOf(accent) else null
        }

        updateButtonStates()

        btnLight?.setOnClickListener {
            ThemeManager.setDark(ctx, false)
            updateButtonStates()
            (requireActivity() as? MainActivity)?.applyTheme(false, ThemeManager.isRedAccent(ctx))
        }
        btnDark?.setOnClickListener {
            ThemeManager.setDark(ctx, true)
            updateButtonStates()
            (requireActivity() as? MainActivity)?.applyTheme(true, ThemeManager.isRedAccent(ctx))
        }
        btnBlue?.setOnClickListener {
            ThemeManager.setRedAccent(ctx, false)
            updateButtonStates()
            (requireActivity() as? MainActivity)?.applyTheme(ThemeManager.isDark(ctx), false)
        }
        btnRed?.setOnClickListener {
            ThemeManager.setRedAccent(ctx, true)
            updateButtonStates()
            (requireActivity() as? MainActivity)?.applyTheme(ThemeManager.isDark(ctx), true)
        }
    }

    private fun setupIconPackPicker() {
        val iconPackBtn = binding.root.findViewById<View>(R.id.btn_icon_pack) ?: return
        val iconPackManager = (requireActivity().application as LauncherApplication).iconPackManager
        updateIconPackLabel(iconPackManager)
        iconPackBtn.setOnClickListener { showIconPackDialog(iconPackManager) }
    }

    private fun showIconPackDialog(iconPackManager: com.example.forthewin.IconPackManager) {
        val packs = iconPackManager.getInstalledPacks()
        val labels = mutableListOf<String>()
        val packages = mutableListOf<String?>()
        labels.add("System Default")
        packages.add(null)
        for ((label, pkg) in packs) {
            labels.add(label)
            packages.add(pkg)
        }
        val currentPack = iconPackManager.getActivePack()
        val checkedItem = packages.indexOf(currentPack).coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle("Icon Pack")
            .setSingleChoiceItems(labels.toTypedArray(), checkedItem) { dialog, which ->
                val selected = packages[which]
                if (selected == null) iconPackManager.clearPack()
                else iconPackManager.loadPack(selected)
                updateIconPackLabel(iconPackManager)
                dialog.dismiss()
                (requireActivity() as? MainActivity)?.refreshIcons()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateIconPackLabel(iconPackManager: com.example.forthewin.IconPackManager) {
        val iconPackBtn = binding.root.findViewById<TextView>(R.id.btn_icon_pack) ?: return
        val activePack = iconPackManager.getActivePack()
        iconPackBtn.text = if (activePack == null) {
            "Icon Pack: System Default"
        } else {
            val packs = iconPackManager.getInstalledPacks()
            val name = packs.firstOrNull { it.second == activePack }?.first ?: activePack
            "Icon Pack: $name"
        }
    }

    private var isNightMode = false

    private fun setupThemeSwitch() {
        val toggle = binding.themeToggle
        val container = toggle.themeSwitchContainer
        val handle = toggle.switchHandle
        val sun = toggle.sunImage
        val moon = toggle.moonImage
        val clouds = toggle.cloudsImage
        val stars = toggle.starsContainer

        container.setOnClickListener {
            isNightMode = !isNightMode
            val duration = 500L
            val interp = android.view.animation.AccelerateDecelerateInterpolator()

            if (isNightMode) {
                handle.animate().translationX(120f - 46f - 8f).setDuration(duration).setInterpolator(interp).start()
                sun.animate().alpha(0f).translationX(-46f).setDuration(duration).start()
                moon.animate().alpha(1f).translationX(0f).setDuration(duration).start()
                clouds.animate().translationY(100f).alpha(0f).setDuration(duration).start()
                stars.animate().alpha(1f).setDuration(duration).start()
                animateBackgroundColor(container,
                    resources.getColor(R.color.switch_day_bg, null),
                    resources.getColor(R.color.switch_night_bg, null), duration)
            } else {
                handle.animate().translationX(0f).setDuration(duration).setInterpolator(interp).start()
                sun.animate().alpha(1f).translationX(0f).setDuration(duration).start()
                moon.animate().alpha(0f).translationX(46f).setDuration(duration).start()
                clouds.animate().translationY(0f).alpha(1f).setDuration(duration).start()
                stars.animate().alpha(0f).setDuration(duration).start()
                animateBackgroundColor(container,
                    resources.getColor(R.color.switch_night_bg, null),
                    resources.getColor(R.color.switch_day_bg, null), duration)
            }
        }
    }

    private fun animateBackgroundColor(view: View, colorFrom: Int, colorTo: Int, duration: Long) {
        val anim = android.animation.ValueAnimator.ofObject(android.animation.ArgbEvaluator(), colorFrom, colorTo)
        anim.duration = duration
        anim.addUpdateListener { v ->
            view.backgroundTintList = ColorStateList.valueOf(v.animatedValue as Int)
        }
        anim.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
