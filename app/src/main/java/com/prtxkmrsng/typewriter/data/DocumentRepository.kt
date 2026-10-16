package com.prtxkmrsng.typewriter.data

import android.content.Context
import android.content.SharedPreferences

data class Document(
    val id: String = "default_doc",
    val title: String = "Untitled",
    val content: String = ""
)

class DocumentRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("typewriter_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CONTENT = "doc_content"
        private const val KEY_TITLE = "doc_title"
    }

    fun loadDocument(): Document {
        val content = prefs.getString(KEY_CONTENT, "") ?: ""
        val title = prefs.getString(KEY_TITLE, "Untitled") ?: "Untitled"
        return Document(content = content, title = title)
    }

    fun saveDocument(document: Document) {
        prefs.edit()
            .putString(KEY_CONTENT, document.content)
            .putString(KEY_TITLE, document.title)
            .apply()
    }

    fun clearDocument() {
        prefs.edit().remove(KEY_CONTENT).remove(KEY_TITLE).apply()
    }
}
