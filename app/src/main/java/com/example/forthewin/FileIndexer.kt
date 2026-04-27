package com.example.forthewin

import android.content.Context
import android.os.Environment
import java.io.File

data class IndexedFile(
    val name: String,
    val path: String,
    val lastModified: Long,
    val extension: String
)

class FileIndexer(private val context: Context) {

    fun getRecentFiles(limit: Int = 6): List<IndexedFile> {
        val root = Environment.getExternalStorageDirectory()
        val files = mutableListOf<IndexedFile>()
        
        // Scan specific common folders for productivity feel
        val foldersToScan = listOf(
            File(root, "Download"),
            File(root, "Documents"),
            File(root, "DCIM/Screenshots")
        )

        for (folder in foldersToScan) {
            if (folder.exists() && folder.isDirectory) {
                folder.listFiles()?.forEach { file ->
                    if (file.isFile && !file.name.startsWith(".")) {
                        files.add(
                            IndexedFile(
                                name = file.name,
                                path = file.absolutePath,
                                lastModified = file.lastModified(),
                                extension = file.extension
                            )
                        )
                    }
                }
            }
        }

        return files.sortedByDescending { it.lastModified }.take(limit)
    }
}
