package com.prtxkmrsng.typewriter.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MarkdownDocument(
    val uri: Uri,
    val name: String,
    val lastModified: Long
)

class DocumentRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("typewriter_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FOLDER_URI = "folder_uri"
        private const val KEY_THEME = "selected_theme"
        private const val KEY_LAST_DOC_URI = "last_doc_uri"
        private const val KEY_HARDCORE_MODE = "hardcore_mode"
    }

    fun getSavedFolderUri(): Uri? {
        val uriString = prefs.getString(KEY_FOLDER_URI, null) ?: return null
        val uri = Uri.parse(uriString)
        val hasPermission = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }
        return if (hasPermission) uri else null
    }

    fun saveFolderUri(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
        prefs.edit().putString(KEY_FOLDER_URI, uri.toString()).apply()
    }

    fun getSavedThemeName(): String = prefs.getString(KEY_THEME, "LIGHT") ?: "LIGHT"

    fun saveThemeName(themeName: String) {
        prefs.edit().putString(KEY_THEME, themeName).apply()
    }

    fun getHardcoreMode(): Boolean = prefs.getBoolean(KEY_HARDCORE_MODE, false)

    fun saveHardcoreMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HARDCORE_MODE, enabled).apply()
    }

    fun getLastDocUri(): Uri? = prefs.getString(KEY_LAST_DOC_URI, null)?.let { Uri.parse(it) }

    fun saveLastDocUri(uri: Uri) {
        prefs.edit().putString(KEY_LAST_DOC_URI, uri.toString()).apply()
    }

    suspend fun listMarkdownDocuments(folderUri: Uri): List<MarkdownDocument> =
        withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext emptyList()
            root.listFiles()
                .filter { it.isFile && (it.name?.endsWith(".md", ignoreCase = true) == true) }
                .map {
                    MarkdownDocument(
                        uri = it.uri,
                        name = it.name ?: "Untitled.md",
                        lastModified = it.lastModified()
                    )
                }
                .sortedByDescending { it.lastModified }
        }

    suspend fun createNewDocument(folderUri: Uri, rawTitle: String): MarkdownDocument? =
        withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext null
            val cleanName = rawTitle.trim()
                .replace(Regex("[^a-zA-Z0-9._ -]"), "")
                .ifBlank { "Untitled" }
            val fileName = if (cleanName.endsWith(".md", ignoreCase = true)) cleanName else "$cleanName.md"

            val existing = root.findFile(fileName)
            val docFile = existing ?: root.createFile("text/markdown", fileName) ?: return@withContext null
            if (existing == null) {
                val initialHeader = "# ${fileName.removeSuffix(".md")}\n\n"
                writeDocumentContent(docFile.uri, initialHeader)
            }
            MarkdownDocument(
                uri = docFile.uri,
                name = docFile.name ?: fileName,
                lastModified = docFile.lastModified()
            )
        }

    suspend fun readDocumentContent(fileUri: Uri): String = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(fileUri)?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun writeDocumentContent(fileUri: Uri, content: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(fileUri, "wt")?.bufferedWriter()?.use {
                    it.write(content)
                }
                true
            } catch (e: Exception) {
                false
            }
        }
}
