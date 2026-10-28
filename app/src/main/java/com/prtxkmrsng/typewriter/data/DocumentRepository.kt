package com.prtxkmrsng.typewriter.data

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.prtxkmrsng.typewriter.ui.TypewriterThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil

data class MarkdownDocument(
    val uri: Uri,
    val name: String,
    val lastModified: Long,
    val pageCount: Int = 1,
    val wordCount: Int = 0
) {
    /**
     * Slowly thickens the book spine as pages are added, with a noticeable step
     * increase for every 5 pages.
     */
    val thicknessMultiplier: Float
        get() {
            val fivePageBuckets = pageCount / 5
            val extraPages = pageCount % 5
            return (1.0f + (fivePageBuckets * 0.26f) + (extraPages * 0.03f)).coerceIn(1.0f, 2.65f)
        }
}

data class ExportedFileResult(
    val fileName: String,
    val uri: Uri,
    val mimeType: String
)

class DocumentRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("typewriter_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FOLDER_URI = "folder_uri"
        private const val KEY_THEME = "selected_theme"
        private const val KEY_LAST_DOC_URI = "last_doc_uri"
        private const val KEY_HARDCORE_MODE = "hardcore_mode"
        private const val KEY_BOOKSHELF_OPEN = "bookshelf_drawer_open"
        private const val PREFIX_WIDGET_SHELF_NUM = "widget_shelf_number_"
        private const val PREFIX_WIDGET_WIDTH_DP = "widget_width_dp_"
        private const val PREFIX_WIDGET_HEIGHT_DP = "widget_height_dp_"

        private val BOLD_REGEX = Regex("\\*\\*(.+?)\\*\\*")
        private val STRIKE_REGEX = Regex("~~(.+?)~~")
        private val ITALIC_REGEX = Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)")
        private val CODE_REGEX = Regex("`([^`]+)`")
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
        prefs.edit().putString(KEY_FOLDER_URI, uri.toString()).commit()
    }

    fun getSavedThemeName(): String = prefs.getString(KEY_THEME, TypewriterThemeMode.LIGHT.name)
        ?: TypewriterThemeMode.LIGHT.name

    fun getSavedThemeMode(): TypewriterThemeMode = runCatching {
        TypewriterThemeMode.valueOf(getSavedThemeName())
    }.getOrDefault(TypewriterThemeMode.LIGHT)

    fun saveThemeName(themeName: String) {
        prefs.edit().putString(KEY_THEME, themeName).commit()
    }

    fun getHardcoreMode(): Boolean = prefs.getBoolean(KEY_HARDCORE_MODE, false)

    fun saveHardcoreMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HARDCORE_MODE, enabled).apply()
    }

    fun getBookshelfOpen(): Boolean = prefs.getBoolean(KEY_BOOKSHELF_OPEN, false)

    fun saveBookshelfOpen(open: Boolean) {
        prefs.edit().putBoolean(KEY_BOOKSHELF_OPEN, open).apply()
    }

    fun getLastDocUri(): Uri? = prefs.getString(KEY_LAST_DOC_URI, null)?.let { Uri.parse(it) }

    fun saveLastDocUri(uri: Uri) {
        prefs.edit().putString(KEY_LAST_DOC_URI, uri.toString()).apply()
    }

    /**
     * Per-Widget Contiguous Shelf Number (1, 2, 3...)
     */
    fun getWidgetShelfNumber(appWidgetId: Int): Int =
        prefs.getInt("$PREFIX_WIDGET_SHELF_NUM$appWidgetId", 0)

    fun saveWidgetShelfNumber(appWidgetId: Int, shelfNumber: Int) {
        prefs.edit().putInt("$PREFIX_WIDGET_SHELF_NUM$appWidgetId", shelfNumber.coerceAtLeast(1)).commit()
    }

    fun saveWidgetDimensionsDp(appWidgetId: Int, widthDp: Float, heightDp: Float) {
        prefs.edit()
            .putFloat("$PREFIX_WIDGET_WIDTH_DP$appWidgetId", widthDp)
            .putFloat("$PREFIX_WIDGET_HEIGHT_DP$appWidgetId", heightDp)
            .apply()
    }

    fun getWidgetDimensionsDp(appWidgetId: Int): Pair<Float, Float>? {
        val w = prefs.getFloat("$PREFIX_WIDGET_WIDTH_DP$appWidgetId", -1f)
        val h = prefs.getFloat("$PREFIX_WIDGET_HEIGHT_DP$appWidgetId", -1f)
        return if (w > 0f && h > 0f) (w to h) else null
    }

    fun deleteWidgetPrefs(appWidgetId: Int) {
        prefs.edit()
            .remove("$PREFIX_WIDGET_SHELF_NUM$appWidgetId")
            .remove("$PREFIX_WIDGET_WIDTH_DP$appWidgetId")
            .remove("$PREFIX_WIDGET_HEIGHT_DP$appWidgetId")
            .apply()
    }

    suspend fun listMarkdownDocuments(
        folderUri: Uri,
        maxCharsPerLine: Int = 68,
        linesPerPage: Int = 55
    ): List<MarkdownDocument> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext emptyList()
        root.listFiles()
            .filter { it.isFile && (it.name?.endsWith(".md", ignoreCase = true) == true) }
            .map { file ->
                val rawText = readDocumentContent(file.uri)
                val (pages, words) = computeDocumentMetrics(rawText, maxCharsPerLine, linesPerPage)
                MarkdownDocument(
                    uri = file.uri,
                    name = file.name ?: "Untitled.md",
                    lastModified = file.lastModified(),
                    pageCount = pages,
                    wordCount = words
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    private fun computeDocumentMetrics(
        rawText: String,
        maxCharsPerLine: Int,
        linesPerPage: Int
    ): Pair<Int, Int> {
        if (rawText.isBlank()) return 1 to 0
        var wrappedLines = 0
        rawText.lines().forEach { line ->
            wrappedLines += if (line.isEmpty()) {
                1
            } else {
                ceil(line.length.toDouble() / maxCharsPerLine.toDouble()).toInt().coerceAtLeast(1)
            }
        }
        val pageCount = ((wrappedLines - 1).coerceAtLeast(0) / linesPerPage) + 1

        var wordCount = 0
        var inWord = false
        for (i in rawText.indices) {
            if (rawText[i].isWhitespace()) {
                inWord = false
            } else if (!inWord) {
                inWord = true
                wordCount++
            }
        }
        return pageCount to wordCount
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
            val content = readDocumentContent(docFile.uri)
            val (pages, words) = computeDocumentMetrics(content, 68, 55)
            MarkdownDocument(
                uri = docFile.uri,
                name = docFile.name ?: fileName,
                lastModified = docFile.lastModified(),
                pageCount = pages,
                wordCount = words
            )
        }

    suspend fun deleteDocument(fileUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            DocumentFile.fromSingleUri(context, fileUri)?.delete() == true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun renameDocument(fileUri: Uri, rawNewTitle: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val cleanName = rawNewTitle.trim()
                .replace(Regex("[^a-zA-Z0-9._ -]"), "")
                .ifBlank { return@withContext null }
            val fileName = if (cleanName.endsWith(".md", ignoreCase = true)) cleanName else "$cleanName.md"
            DocumentsContract.renameDocument(context.contentResolver, fileUri, fileName)
        } catch (e: Exception) {
            null
        }
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

    suspend fun exportToHtml(
        folderUri: Uri,
        documentName: String,
        lines: List<String>,
        linesPerPage: Int = 55
    ): ExportedFileResult? = withContext(Dispatchers.IO) {
        try {
            val root = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext null
            val baseName = documentName.removeSuffix(".md").removeSuffix(".MD").ifBlank { "Manuscript" }
            val htmlFileName = "$baseName.html"

            root.findFile(htmlFileName)?.delete()
            val htmlFile = root.createFile("text/html", htmlFileName) ?: return@withContext null

            val pages = lines.chunked(linesPerPage).ifEmpty { listOf(listOf("")) }
            val htmlContent = buildString {
                appendLine("<!DOCTYPE html>")
                appendLine("<html lang=\"en\"><head><meta charset=\"UTF-8\">")
                appendLine("<title>${escapeHtml(baseName)}</title>")
                appendLine("<style>")
                appendLine(
                    """
                    body {
                        background: #23221F;
                        margin: 0;
                        padding: 32px 16px;
                        font-family: 'Courier New', Courier, monospace;
                        color: #1F1E1B;
                    }
                    .sheet {
                        max-width: 760px;
                        margin: 0 auto 36px auto;
                        background: #FDFBF5;
                        padding: 48px 64px;
                        box-shadow: 0 10px 28px rgba(0,0,0,0.45);
                        border-left: 3px solid rgba(184, 41, 33, 0.25);
                        position: relative;
                        page-break-after: always;
                    }
                    .page-header {
                        display: flex;
                        justify-content: space-between;
                        font-size: 12px;
                        color: #7A7468;
                        border-bottom: 1px dashed #D5CEC0;
                        padding-bottom: 8px;
                        margin-bottom: 24px;
                    }
                    .line {
                        font-size: 16px;
                        line-height: 26px;
                        white-space: pre-wrap;
                        min-height: 26px;
                    }
                    h1, h2, h3 {
                        color: #B82921;
                        margin: 8px 0 4px 0;
                        font-weight: bold;
                    }
                    hr.typewriter-rule {
                        border: none;
                        border-top: 2px dashed #B82921;
                        margin: 12px 0;
                        opacity: 0.5;
                    }
                    blockquote {
                        margin: 0;
                        padding-left: 12px;
                        border-left: 3px solid #B82921;
                        font-style: italic;
                        color: #4A453D;
                    }
                    code {
                        background: rgba(0,0,0,0.07);
                        color: #B82921;
                        padding: 1px 4px;
                        border-radius: 3px;
                    }
                    .footer-sig {
                        margin-top: 28px;
                        font-size: 10px;
                        color: #9A9386;
                        text-align: right;
                    }
                    @media print {
                        body { background: none; padding: 0; }
                        .sheet { box-shadow: none; margin: 0; width: 100%; }
                    }
                    """.trimIndent()
                )
                appendLine("</style></head><body>")

                pages.forEachIndexed { pageIdx, pageLines ->
                    appendLine("<div class=\"sheet\">")
                    appendLine("<div class=\"page-header\"><span>${escapeHtml(baseName.uppercase())}</span><span>PAGE ${pageIdx + 1} OF ${pages.size}</span></div>")
                    pageLines.forEach { rawLine ->
                        val trimmed = rawLine.trimStart()
                        when {
                            trimmed == "---" || trimmed == "***" ->
                                appendLine("<hr class=\"typewriter-rule\" />")
                            trimmed.startsWith("### ") ->
                                appendLine("<h3>${formatInlineHtml(trimmed.removePrefix("### "))}</h3>")
                            trimmed.startsWith("## ") ->
                                appendLine("<h2>${formatInlineHtml(trimmed.removePrefix("## "))}</h2>")
                            trimmed.startsWith("# ") ->
                                appendLine("<h1>${formatInlineHtml(trimmed.removePrefix("# "))}</h1>")
                            trimmed.startsWith("> ") ->
                                appendLine("<blockquote><div class=\"line\">${formatInlineHtml(trimmed.removePrefix("> "))}</div></blockquote>")
                            else ->
                                appendLine("<div class=\"line\">${formatInlineHtml(rawLine)}</div>")
                        }
                    }
                    appendLine("<div class=\"footer-sig\">TYPED ON TYPEWRITER • @prtxkmrsng</div>")
                    appendLine("</div>")
                }

                appendLine("</body></html>")
            }

            context.contentResolver.openOutputStream(htmlFile.uri, "wt")?.bufferedWriter()?.use {
                it.write(htmlContent)
            }
            ExportedFileResult(
                fileName = htmlFileName,
                uri = htmlFile.uri,
                mimeType = "text/html"
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun exportToPdf(
        folderUri: Uri,
        documentName: String,
        lines: List<String>,
        linesPerPage: Int = 55
    ): ExportedFileResult? = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()
        try {
            val root = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext null
            val baseName = documentName.removeSuffix(".md").removeSuffix(".MD").ifBlank { "Manuscript" }
            val pdfFileName = "$baseName.pdf"

            root.findFile(pdfFileName)?.delete()
            val pdfFile = root.createFile("application/pdf", pdfFileName) ?: return@withContext null

            val pageWidth = 612
            val pageHeight = 792
            val leftMargin = 58f
            val topMargin = 56f
            val lineHeight = 12.2f

            val paperPaint = Paint().apply {
                color = Color.rgb(253, 251, 245)
                style = Paint.Style.FILL
            }
            val marginRulePaint = Paint().apply {
                color = Color.argb(70, 184, 41, 33)
                strokeWidth = 1.2f
            }
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                textSize = 8.5f
                color = Color.rgb(120, 114, 104)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                textSize = 10f
                color = Color.rgb(31, 30, 27)
            }
            val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textSize = 11.5f
                color = Color.rgb(184, 41, 33)
            }
            val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC)
                textSize = 10f
                color = Color.rgb(75, 70, 62)
            }

            val charAdvance = bodyPaint.measureText("M")
            val pages = lines.chunked(linesPerPage).ifEmpty { listOf(listOf("")) }

            pages.forEachIndexed { pageIndex, pageLines ->
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas: Canvas = page.canvas

                canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), paperPaint)
                canvas.drawLine(leftMargin - 12f, 0f, leftMargin - 12f, pageHeight.toFloat(), marginRulePaint)

                canvas.drawText(baseName.uppercase(), leftMargin, 34f, headerPaint)
                val pageLabel = "PAGE ${pageIndex + 1} OF ${pages.size}"
                val labelWidth = headerPaint.measureText(pageLabel)
                canvas.drawText(pageLabel, pageWidth - leftMargin - labelWidth, 34f, headerPaint)

                pageLines.forEachIndexed { lineIdx, rawLine ->
                    val globalLineIdx = pageIndex * linesPerPage + lineIdx
                    val y = topMargin + (lineIdx + 1) * lineHeight
                    val trimmed = rawLine.trimStart()
                    val cleanText = stripMarkdownTokens(rawLine)

                    when {
                        trimmed == "---" || trimmed == "***" -> {
                            canvas.drawLine(
                                leftMargin,
                                y - 4f,
                                pageWidth - leftMargin,
                                y - 4f,
                                marginRulePaint
                            )
                        }
                        trimmed.startsWith("#") -> {
                            canvas.drawText(cleanText.trimStart('#', ' '), leftMargin, y, headingPaint)
                        }
                        trimmed.startsWith(">") -> {
                            canvas.drawText(cleanText.removePrefix(">").trimStart(), leftMargin + 10f, y, quotePaint)
                        }
                        else -> {
                            cleanText.forEachIndexed { colIdx, ch ->
                                if (ch != ' ') {
                                    val hash = abs(globalLineIdx * 131 + colIdx * 37 + ch.code * 17)
                                    val alpha = 195 + (hash % 61)
                                    val yJitter = ((hash % 5) - 2) * 0.14f
                                    bodyPaint.alpha = alpha
                                    canvas.drawText(
                                        ch.toString(),
                                        leftMargin + colIdx * charAdvance,
                                        y + yJitter,
                                        bodyPaint
                                    )
                                }
                            }
                            bodyPaint.alpha = 255
                        }
                    }
                }

                val footerSig = "TYPED ON TYPEWRITER • @prtxkmrsng"
                val sigWidth = headerPaint.measureText(footerSig)
                canvas.drawText(footerSig, pageWidth - leftMargin - sigWidth, pageHeight - 24f, headerPaint)

                pdfDocument.finishPage(page)
            }

            context.contentResolver.openOutputStream(pdfFile.uri, "w")?.use { out ->
                pdfDocument.writeTo(out)
            }
            ExportedFileResult(
                fileName = pdfFileName,
                uri = pdfFile.uri,
                mimeType = "application/pdf"
            )
        } catch (e: Exception) {
            null
        } finally {
            pdfDocument.close()
        }
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private fun formatInlineHtml(raw: String): String {
        var html = escapeHtml(raw)
        html = html.replace(BOLD_REGEX, "<strong>$1</strong>")
        html = html.replace(STRIKE_REGEX, "<del>$1</del>")
        html = html.replace(ITALIC_REGEX, "<em>$1</em>")
        html = html.replace(CODE_REGEX, "<code>$1</code>")
        return html
    }

    private fun stripMarkdownTokens(raw: String): String {
        return raw
            .replace(BOLD_REGEX, "$1")
            .replace(STRIKE_REGEX, "$1")
            .replace(ITALIC_REGEX, "$1")
            .replace(CODE_REGEX, "$1")
    }
}
