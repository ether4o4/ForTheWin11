package com.example.forthewin.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.forthewin.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val settingsViewModel =
            ViewModelProvider(this).get(SettingsViewModel::class.java)

        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textSettings
        settingsViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }

        setupThemeSwitch()

        return root
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
            val interpolator = android.view.animation.AccelerateDecelerateInterpolator()

            if (isNightMode) {
                // Animate to Night
                handle.animate().translationX(120f - 46f - 8f).setDuration(duration).setInterpolator(interpolator).start()
                sun.animate().alpha(0f).translationX(-46f).setDuration(duration).start()
                moon.animate().alpha(1f).translationX(0f).setDuration(duration).start()
                clouds.animate().translationY(100f).alpha(0f).setDuration(duration).start()
                stars.animate().alpha(1f).setDuration(duration).start()
                
                // Background color change
                val colorFrom = resources.getColor(com.example.forthewin.R.color.switch_day_bg, null)
                val colorTo = resources.getColor(com.example.forthewin.R.color.switch_night_bg, null)
                animateBackgroundColor(container, colorFrom, colorTo, duration)
            } else {
                // Animate to Day
                handle.animate().translationX(0f).setDuration(duration).setInterpolator(interpolator).start()
                sun.animate().alpha(1f).translationX(0f).setDuration(duration).start()
                moon.animate().alpha(0f).translationX(46f).setDuration(duration).start()
                clouds.animate().translationY(0f).alpha(1f).setDuration(duration).start()
                stars.animate().alpha(0f).setDuration(duration).start()

                // Background color change
                val colorFrom = resources.getColor(com.example.forthewin.R.color.switch_night_bg, null)
                val colorTo = resources.getColor(com.example.forthewin.R.color.switch_day_bg, null)
                animateBackgroundColor(container, colorFrom, colorTo, duration)
            }
        }
    }

    private fun animateBackgroundColor(view: View, colorFrom: Int, colorTo: Int, duration: Long) {
        val colorAnimation = android.animation.ValueAnimator.ofObject(android.animation.ArgbEvaluator(), colorFrom, colorTo)
        colorAnimation.duration = duration
        colorAnimation.addUpdateListener { animator ->
            view.backgroundTintList = android.content.res.ColorStateList.valueOf(animator.animatedValue as Int)
        }
        colorAnimation.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}