package com.example.forthewin

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.database.Cursor
import java.io.File

data class IndexedFile(
    val name: String,
    val path: String,
    val lastModified: Long,
    val extension: String,
    val category: String = "Other",
    val sizeBytes: Long = 0
)

class FileIndexer(private val context: Context) {

    enum class SortOrder { NAME_ASC, NAME_DESC, DATE_ASC, DATE_DESC, SIZE_ASC, SIZE_DESC }

    enum class Category(val extensions: List<String>, val label: String) {
        DOCUMENTS(listOf("pdf","doc","docx","xls","xlsx","ppt","pptx","txt","csv"), "Documents"),
        IMAGES(listOf("jpg","jpeg","png","gif","bmp","webp","svg","ico"), "Images"),
        AUDIO(listOf("mp3","wav","flac","aac","ogg","wma","m4a"), "Audio"),
        VIDEO(listOf("mp4","avi","mkv","mov","wmv","flv","webm","m4v"), "Video"),
        ARCHIVES(listOf("zip","rar","7z","tar","gz","bz2"), "Archives"),
        OTHER(emptyList(), "Other")
    }

    fun categorize(ext: String): Category {
        for (cat in Category.values()) {
            if (ext.lowercase() in cat.extensions.map { it.lowercase() }) return cat
        }
        return Category.OTHER
    }

    fun getRecentFiles(
        limit: Int = 20,
        category: Category? = null,
        sortOrder: SortOrder = SortOrder.DATE_DESC
    ): List<IndexedFile> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            getRecentModern(limit, category, sortOrder)
        else
            getRecentLegacy(limit, category, sortOrder)
    }

    private fun getRecentModern(limit: Int, category: Category?, sortOrder: SortOrder): List<IndexedFile> {
        val files = mutableListOf<IndexedFile>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE
        )
        val sortCol = when (sortOrder) {
            SortOrder.DATE_DESC, SortOrder.DATE_ASC -> MediaStore.Files.FileColumns.DATE_MODIFIED
            SortOrder.SIZE_DESC, SortOrder.SIZE_ASC -> MediaStore.Files.FileColumns.SIZE
            else -> MediaStore.Files.FileColumns.DISPLAY_NAME
        }
        val sortDir = when (sortOrder) {
            SortOrder.NAME_ASC, SortOrder.DATE_ASC, SortOrder.SIZE_ASC -> "ASC"
            else -> "DESC"
        }
        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection, null, null, "$sortCol $sortDir LIMIT $limit"
        )
        cursor?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)) ?: continue
                val path = c.getString(c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)) ?: ""
                val modified = c.getLong(c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)) * 1000L
                val size = c.getLong(c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE))
                val ext = name.substringAfterLast('.', "")
                val cat = categorize(ext)
                if (category != null && cat != category) continue
                files.add(IndexedFile(name, path, modified, ext, cat.label, size))
            }
        }
        return files
    }

    private fun getRecentLegacy(limit: Int, category: Category?, sortOrder: SortOrder): List<IndexedFile> {
        val root = Environment.getExternalStorageDirectory()
        val files = mutableListOf<IndexedFile>()
        val folders = listOf(
            File(root, "Download"), File(root, "Documents"),
            File(root, "DCIM/Screenshots"), File(root, "DCIM/Camera"),
            File(root, "Music"), File(root, "Movies"), File(root, "Pictures")
        )
        for (folder in folders) {
            if (folder.exists() && folder.isDirectory) {
                folder.listFiles()?.forEach { file ->
                    if (file.isFile && !file.name.startsWith(".")) {
                        val cat = categorize(file.extension)
                        if (category != null && cat != category) return@forEach
                        files.add(IndexedFile(file.name, file.absolutePath, file.lastModified(), file.extension, cat.label, file.length()))
                    }
                }
            }
        }
        val comp: Comparator<IndexedFile> = when (sortOrder) {
            SortOrder.NAME_ASC -> compareBy { it.name.lowercase() }
            SortOrder.NAME_DESC -> compareByDescending { it.name.lowercase() }
            SortOrder.DATE_ASC -> compareBy { it.lastModified }
            SortOrder.DATE_DESC -> compareByDescending { it.lastModified }
            SortOrder.SIZE_ASC -> compareBy { it.sizeBytes }
            SortOrder.SIZE_DESC -> compareByDescending { it.sizeBytes }
        }
        return files.sortedWith(comp).take(limit)
    }
}