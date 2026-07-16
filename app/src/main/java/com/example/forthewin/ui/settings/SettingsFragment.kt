package com.example.forthewin.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.forthewin.R
import com.example.forthewin.WallpaperHelper
import com.example.forthewin.ui.controllers.TaskbarManager

class SettingsFragment : Fragment() {

    private lateinit var viewModel: SettingsViewModel
    private lateinit var wallpaperPickerButton: Button
    private lateinit var iconPackSpinner: Spinner
    private lateinit var alignmentSpinner: Spinner
    private lateinit var sortSpinner: Spinner
    private lateinit var categorySpinner: Spinner

    companion object {
        private const val PICK_WALLPAPER = 1001
        private const val PICK_ICON_PACK = 1002
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this).get(SettingsViewModel::class.java)

        wallpaperPickerButton = view.findViewById(R.id.btn_wallpaper_picker)
        iconPackSpinner = view.findViewById(R.id.spinner_icon_pack)
        alignmentSpinner = view.findViewById(R.id.spinner_alignment)
        sortSpinner = view.findViewById(R.id.spinner_file_sort)
        categorySpinner = view.findViewById(R.id.spinner_file_category)

        wallpaperPickerButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            startActivityForResult(intent, PICK_WALLPAPER)
        }

        setupIconPackSpinner()
        setupAlignmentSpinner()
        setupSortSpinner()
        setupCategorySpinner()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_WALLPAPER && resultCode == android.app.Activity.RESULT_OK) {
            val uri = data?.data?.toString()
            viewModel.saveWallpaperUri(uri)
            if (uri != null) {
                WallpaperHelper.setFromUri(requireContext(), Uri.parse(uri))
            }
        }
    }

    private fun setupIconPackSpinner() {
        val items = listOf("Default", "Pick from device...")
        iconPackSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, items).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        iconPackSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos == 1) {
                    startActivityForResult(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:com.example.forthewin")
                    }, PICK_ICON_PACK)
                }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun setupAlignmentSpinner() {
        val items = TaskbarManager.Alignment.values().map { it.name }
        alignmentSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, items).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        alignmentSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                viewModel.saveAlignment(items[pos])
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun setupSortSpinner() {
        val labels = listOf("Date (newest)", "Date (oldest)", "Name (A-Z)", "Name (Z-A)", "Size (largest)", "Size (smallest)")
        val values = listOf("DATE_DESC", "DATE_ASC", "NAME_ASC", "NAME_DESC", "SIZE_DESC", "SIZE_ASC")
        sortSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, labels).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        sortSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                viewModel.saveSortOrder(values[pos])
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun setupCategorySpinner() {
        val items = listOf("All", "Documents", "Images", "Audio", "Video", "Archives")
        categorySpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, items).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                viewModel.saveCategoryFilter(if (pos == 0) null else items[pos])
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }
}