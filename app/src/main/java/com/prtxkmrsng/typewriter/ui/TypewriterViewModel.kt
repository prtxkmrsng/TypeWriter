package com.prtxkmrsng.typewriter.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prtxkmrsng.typewriter.audio.TypewriterSoundEngine
import com.prtxkmrsng.typewriter.data.DocumentRepository
import com.prtxkmrsng.typewriter.data.ExportedFileResult
import com.prtxkmrsng.typewriter.data.MarkdownDocument
import com.prtxkmrsng.typewriter.widget.BookshelfAppWidgetProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SaveStatus { SAVED, SAVING, UNSAVED }

@Immutable
data class TypewriterUiState(
    val folderUri: Uri? = null,
    val documents: List<MarkdownDocument> = emptyList(),
    val activeDocument: MarkdownDocument? = null,
    val lines: List<String> = listOf(""),
    val overstrikesByLine: Map<Int, Map<Int, List<Char>>> = emptyMap(),
    val cursorLine: Int = 0,
    val cursorCol: Int = 0,
    val maxCharsPerLine: Int = 68,
    val bellWarningCol: Int = 60,
    val linesPerPage: Int = 55,
    val themeMode: TypewriterThemeMode = TypewriterThemeMode.LIGHT,
    val isRedRibbon: Boolean = false,
    val isHardcoreMode: Boolean = false,
    val isPreviewMode: Boolean = false,
    val isBookshelfOpen: Boolean = false,
    val isMuted: Boolean = false,
    val saveStatus: SaveStatus = SaveStatus.SAVED,
    val strikeTriggerId: Long = 0L,
    val ribbonSpoolAngle: Float = 0f,
    val lastStruckChar: Char = ' ',
    val wordCount: Int = 0,
    val charCount: Int = 0,
    val exportStatusMessage: String? = null,
    val lastExportedFile: ExportedFileResult? = null
)

class TypewriterViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DocumentRepository(application)
    val soundEngine = TypewriterSoundEngine()

    private val _uiState = MutableStateFlow(TypewriterUiState())
    val uiState: StateFlow<TypewriterUiState> = _uiState.asStateFlow()

    private var autosaveJob: Job? = null
    private var pendingDeepLinkUriString: String? = null

    init {
        val savedTheme = runCatching {
            TypewriterThemeMode.valueOf(repository.getSavedThemeName())
        }.getOrDefault(TypewriterThemeMode.LIGHT)

        val savedFolder = repository.getSavedFolderUri()
        val hardcore = repository.getHardcoreMode()
        val shelfOpen = repository.getBookshelfOpen()

        _uiState.update {
            it.copy(
                folderUri = savedFolder,
                themeMode = savedTheme,
                isHardcoreMode = hardcore,
                isBookshelfOpen = shelfOpen
            )
        }

        if (savedFolder != null) {
            refreshDocumentsAndLoadInitial(savedFolder)
        }
    }

    override fun onCleared() {
        super.onCleared()
        soundEngine.release()
    }

    fun flushPendingSave() {
        if (_uiState.value.saveStatus == SaveStatus.UNSAVED) {
            autosaveJob?.cancel()
            viewModelScope.launch {
                saveCurrentImmediately()
            }
        }
    }

    fun forceManualSave() {
        autosaveJob?.cancel()
        viewModelScope.launch {
            soundEngine.playPlatenTick()
            saveCurrentImmediately()
        }
    }

    fun toggleBookshelfDrawer() {
        val next = !_uiState.value.isBookshelfOpen
        repository.saveBookshelfOpen(next)
        soundEngine.playPlatenTick()
        _uiState.update { it.copy(isBookshelfOpen = next) }
    }

    fun closeBookshelfDrawer() {
        if (_uiState.value.isBookshelfOpen) {
            repository.saveBookshelfOpen(false)
            _uiState.update { it.copy(isBookshelfOpen = false) }
        }
    }

    /**
     * Opens a specific note when its book spine is clicked on the Android Home Screen Widget.
     */
    fun openDocumentByUriString(uriString: String) {
        val targetUri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return
        val existingDoc = _uiState.value.documents.find { it.uri == targetUri }
        if (existingDoc != null) {
            closeBookshelfDrawer()
            openDocument(existingDoc)
        } else {
            pendingDeepLinkUriString = uriString
            val folderUri = _uiState.value.folderUri ?: repository.getSavedFolderUri()
            if (folderUri != null) {
                refreshDocumentsAndLoadInitial(folderUri)
            }
        }
    }

    fun onFolderSelected(uri: Uri) {
        repository.saveFolderUri(uri)
        _uiState.update { it.copy(folderUri = uri) }
        refreshDocumentsAndLoadInitial(uri)
    }

    private fun refreshDocumentsAndLoadInitial(folderUri: Uri) {
        viewModelScope.launch {
            val docs = repository.listMarkdownDocuments(folderUri)
            if (docs.isEmpty()) {
                _uiState.update {
                    it.copy(
                        documents = emptyList(),
                        activeDocument = null,
                        lines = listOf(""),
                        cursorLine = 0,
                        cursorCol = 0,
                        wordCount = 0,
                        charCount = 0
                    )
                }
            } else {
                _uiState.update { it.copy(documents = docs) }
                val deepLinkUri = pendingDeepLinkUriString?.let { Uri.parse(it) }
                pendingDeepLinkUriString = null
                val lastUri = deepLinkUri ?: repository.getLastDocUri()
                val targetDoc = docs.find { it.uri == lastUri } ?: docs.first()
                openDocument(targetDoc)
            }
            BookshelfAppWidgetProvider.notifyWidgetsChanged(getApplication())
        }
    }

    fun createNewDocument(title: String) {
        val folderUri = _uiState.value.folderUri ?: return
        viewModelScope.launch {
            saveCurrentImmediately()
            val newDoc = repository.createNewDocument(folderUri, title) ?: return@launch
            val docs = repository.listMarkdownDocuments(folderUri)
            _uiState.update { it.copy(documents = docs, isBookshelfOpen = false) }
            repository.saveBookshelfOpen(false)
            openDocument(newDoc)
            BookshelfAppWidgetProvider.notifyWidgetsChanged(getApplication())
        }
    }

    fun deleteDocument(doc: MarkdownDocument) {
        val folderUri = _uiState.value.folderUri ?: return
        viewModelScope.launch {
            val isDeletingActive = _uiState.value.activeDocument?.uri == doc.uri
            repository.deleteDocument(doc.uri)
            val remainingDocs = repository.listMarkdownDocuments(folderUri)
            if (remainingDocs.isEmpty()) {
                _uiState.update {
                    it.copy(
                        documents = emptyList(),
                        activeDocument = null,
                        lines = listOf(""),
                        overstrikesByLine = emptyMap(),
                        cursorLine = 0,
                        cursorCol = 0,
                        wordCount = 0,
                        charCount = 0
                    )
                }
            } else {
                _uiState.update { it.copy(documents = remainingDocs) }
                if (isDeletingActive) {
                    openDocument(remainingDocs.first())
                }
            }
            BookshelfAppWidgetProvider.notifyWidgetsChanged(getApplication())
        }
    }

    fun renameDocument(doc: MarkdownDocument, newTitle: String) {
        val folderUri = _uiState.value.folderUri ?: return
        viewModelScope.launch {
            saveCurrentImmediately()
            val newUri = repository.renameDocument(doc.uri, newTitle) ?: return@launch
            val updatedDocs = repository.listMarkdownDocuments(folderUri)
            val renamedDoc = updatedDocs.find { it.uri == newUri }
            _uiState.update {
                it.copy(
                    documents = updatedDocs,
                    activeDocument = if (it.activeDocument?.uri == doc.uri) renamedDoc ?: it.activeDocument else it.activeDocument
                )
            }
            if (renamedDoc != null) {
                repository.saveLastDocUri(renamedDoc.uri)
            }
            BookshelfAppWidgetProvider.notifyWidgetsChanged(getApplication())
        }
    }

    fun openDocument(doc: MarkdownDocument) {
        viewModelScope.launch {
            saveCurrentImmediately()
            val rawText = repository.readDocumentContent(doc.uri)
            repository.saveLastDocUri(doc.uri)
            val parsedLines = parseTextToTypewriterLines(rawText, _uiState.value.maxCharsPerLine)
            val lastLineIndex = (parsedLines.size - 1).coerceAtLeast(0)
            val lastColIndex = parsedLines[lastLineIndex].length
            val livePages = ((parsedLines.size - 1).coerceAtLeast(0) / _uiState.value.linesPerPage) + 1
            val liveWords = countWordsFast(parsedLines)

            val updatedDoc = doc.copy(pageCount = livePages, wordCount = liveWords)

            _uiState.update { state ->
                state.copy(
                    activeDocument = updatedDoc,
                    documents = state.documents.map { if (it.uri == updatedDoc.uri) updatedDoc else it },
                    lines = parsedLines,
                    overstrikesByLine = emptyMap(),
                    cursorLine = lastLineIndex,
                    cursorCol = lastColIndex,
                    saveStatus = SaveStatus.SAVED,
                    wordCount = liveWords,
                    charCount = parsedLines.sumOf { line -> line.length },
                    exportStatusMessage = null,
                    lastExportedFile = null
                )
            }
            soundEngine.playCarriageReturn()
            BookshelfAppWidgetProvider.notifyWidgetsChanged(getApplication())
        }
    }

    fun setTheme(theme: TypewriterThemeMode) {
        repository.saveThemeName(theme.name)
        _uiState.update { it.copy(themeMode = theme) }
        BookshelfAppWidgetProvider.notifyWidgetsChanged(getApplication())
    }

    fun toggleRibbonColor() {
        soundEngine.playKeyStrike()
        _uiState.update { it.copy(isRedRibbon = !it.isRedRibbon) }
    }

    fun toggleHardcoreMode() {
        val next = !_uiState.value.isHardcoreMode
        repository.saveHardcoreMode(next)
        soundEngine.playSpacebar()
        _uiState.update { it.copy(isHardcoreMode = next) }
    }

    fun togglePreviewMode() {
        soundEngine.playSpacebar()
        _uiState.update {
            it.copy(
                isPreviewMode = !it.isPreviewMode,
                exportStatusMessage = null
            )
        }
    }

    fun toggleMute() {
        val nextMute = !_uiState.value.isMuted
        soundEngine.isMuted = nextMute
        _uiState.update { it.copy(isMuted = nextMute) }
    }

    fun exportManuscriptAsPdf(onShareReady: ((ExportedFileResult) -> Unit)? = null) {
        val state = _uiState.value
        val folderUri = state.folderUri ?: return
        val docName = state.activeDocument?.name ?: "Manuscript.md"

        viewModelScope.launch {
            saveCurrentImmediately()
            _uiState.update { it.copy(exportStatusMessage = "PRINTING PDF...") }
            val result = repository.exportToPdf(
                folderUri = folderUri,
                documentName = docName,
                lines = state.lines,
                linesPerPage = state.linesPerPage
            )
            soundEngine.playMarginBell()
            _uiState.update {
                it.copy(
                    lastExportedFile = result,
                    exportStatusMessage = if (result != null) {
                        "SAVED ${result.fileName} TO MANUSCRIPT FOLDER"
                    } else {
                        "PDF EXPORT FAILED"
                    }
                )
            }
            if (result != null && onShareReady != null) {
                onShareReady(result)
            }
        }
    }

    fun exportManuscriptAsHtml(onShareReady: ((ExportedFileResult) -> Unit)? = null) {
        val state = _uiState.value
        val folderUri = state.folderUri ?: return
        val docName = state.activeDocument?.name ?: "Manuscript.md"

        viewModelScope.launch {
            saveCurrentImmediately()
            _uiState.update { it.copy(exportStatusMessage = "GENERATING HTML...") }
            val result = repository.exportToHtml(
                folderUri = folderUri,
                documentName = docName,
                lines = state.lines,
                linesPerPage = state.linesPerPage
            )
            soundEngine.playMarginBell()
            _uiState.update {
                it.copy(
                    lastExportedFile = result,
                    exportStatusMessage = if (result != null) {
                        "SAVED ${result.fileName} TO MANUSCRIPT FOLDER"
                    } else {
                        "HTML EXPORT FAILED"
                    }
                )
            }
            if (result != null && onShareReady != null) {
                onShareReady(result)
            }
        }
    }

    fun shareActiveMarkdown(onShareReady: (ExportedFileResult) -> Unit) {
        val doc = _uiState.value.activeDocument ?: return
        viewModelScope.launch {
            saveCurrentImmediately()
            onShareReady(
                ExportedFileResult(
                    fileName = doc.name,
                    uri = doc.uri,
                    mimeType = "text/markdown"
                )
            )
        }
    }

    fun onCharacterTyped(char: Char) {
        val state = _uiState.value
        if (state.isPreviewMode) return

        val currentLines = state.lines.toMutableList()
        val lineIdx = state.cursorLine.coerceIn(0, currentLines.lastIndex)
        val currentLine = currentLines[lineIdx]
        val colIdx = state.cursorCol.coerceIn(0, currentLine.length)
        var updatedOverstrikes = state.overstrikesByLine

        val updatedLine: String
        if (state.isHardcoreMode && colIdx < currentLine.length) {
            val oldChar = currentLine[colIdx]
            if (oldChar != ' ' && oldChar != char) {
                val lineMap = updatedOverstrikes[lineIdx].orEmpty().toMutableMap()
                val existingHistory = lineMap[colIdx].orEmpty()
                if (!existingHistory.contains(oldChar)) {
                    lineMap[colIdx] = (existingHistory + oldChar).takeLast(3)
                    updatedOverstrikes = updatedOverstrikes.toMutableMap().apply {
                        put(lineIdx, lineMap)
                    }
                }
            }
            updatedLine = StringBuilder(currentLine).apply {
                setCharAt(colIdx, char)
            }.toString()
        } else {
            updatedLine = StringBuilder(currentLine).apply {
                insert(colIdx, char)
            }.toString()
        }

        if (updatedLine.length > state.maxCharsPerLine) {
            val lastSpace = updatedLine.lastIndexOf(' ', startIndex = state.maxCharsPerLine - 1)
            val (keptLine, carryOver) = if (lastSpace > state.maxCharsPerLine / 2) {
                updatedLine.substring(0, lastSpace) to updatedLine.substring(lastSpace + 1)
            } else {
                updatedLine.substring(0, state.maxCharsPerLine) to updatedLine.substring(state.maxCharsPerLine)
            }

            currentLines[lineIdx] = keptLine
            val nextLineIdx = lineIdx + 1
            currentLines.add(nextLineIdx, carryOver)

            soundEngine.playCarriageReturn()
            if (nextLineIdx % state.linesPerPage == 0) {
                soundEngine.playMarginBell()
            }

            updateLinesAndScheduleSave(
                lines = currentLines,
                overstrikes = shiftOverstrikesOnInsert(updatedOverstrikes, nextLineIdx),
                newLine = nextLineIdx,
                newCol = carryOver.length,
                struckChar = char
            )
            return
        }

        currentLines[lineIdx] = updatedLine
        val nextCol = colIdx + 1

        if (nextCol == state.bellWarningCol) {
            soundEngine.playMarginBell()
        } else if (char == ' ') {
            soundEngine.playSpacebar()
        } else {
            soundEngine.playKeyStrike()
        }

        updateLinesAndScheduleSave(
            lines = currentLines,
            overstrikes = updatedOverstrikes,
            newLine = lineIdx,
            newCol = nextCol,
            struckChar = char
        )
    }

    fun onEnterPressed() {
        val state = _uiState.value
        if (state.isPreviewMode) return

        val currentLines = state.lines.toMutableList()
        val lineIdx = state.cursorLine.coerceIn(0, currentLines.lastIndex)
        val currentLine = currentLines[lineIdx]
        val colIdx = state.cursorCol.coerceIn(0, currentLine.length)
        val nextLineIdx = lineIdx + 1

        if (state.isHardcoreMode) {
            if (lineIdx == currentLines.lastIndex) {
                currentLines.add("")
            }
            soundEngine.playCarriageReturn()
            if (nextLineIdx % state.linesPerPage == 0) {
                soundEngine.playMarginBell()
            }
            updateLinesAndScheduleSave(
                lines = currentLines,
                overstrikes = state.overstrikesByLine,
                newLine = nextLineIdx,
                newCol = 0,
                struckChar = '\n',
                immediateSave = true
            )
            return
        }

        val beforeCursor = currentLine.substring(0, colIdx)
        val afterCursor = currentLine.substring(colIdx)

        currentLines[lineIdx] = beforeCursor
        currentLines.add(nextLineIdx, afterCursor)

        soundEngine.playCarriageReturn()
        if (nextLineIdx % state.linesPerPage == 0) {
            soundEngine.playMarginBell()
        }

        updateLinesAndScheduleSave(
            lines = currentLines,
            overstrikes = shiftOverstrikesOnInsert(state.overstrikesByLine, nextLineIdx),
            newLine = nextLineIdx,
            newCol = 0,
            struckChar = '\n',
            immediateSave = true
        )
    }

    fun onBackspacePressed() {
        val state = _uiState.value
        if (state.isPreviewMode) return

        val currentLines = state.lines.toMutableList()
        val lineIdx = state.cursorLine.coerceIn(0, currentLines.lastIndex)
        val currentLine = currentLines[lineIdx]
        val colIdx = state.cursorCol.coerceIn(0, currentLine.length)

        if (state.isHardcoreMode) {
            if (colIdx > 0) {
                soundEngine.playSpacebar()
                _uiState.update { it.copy(cursorCol = colIdx - 1) }
            }
            return
        }

        if (colIdx > 0) {
            val updatedLine = currentLine.removeRange(colIdx - 1, colIdx)
            currentLines[lineIdx] = updatedLine
            soundEngine.playSpacebar()
            updateLinesAndScheduleSave(
                lines = currentLines,
                overstrikes = state.overstrikesByLine,
                newLine = lineIdx,
                newCol = colIdx - 1,
                struckChar = ' '
            )
        } else if (lineIdx > 0) {
            val prevLine = currentLines[lineIdx - 1]
            val merged = (prevLine + currentLine).take(state.maxCharsPerLine)
            currentLines[lineIdx - 1] = merged
            currentLines.removeAt(lineIdx)
            soundEngine.playCarriageReturn()
            updateLinesAndScheduleSave(
                lines = currentLines,
                overstrikes = shiftOverstrikesOnDelete(state.overstrikesByLine, lineIdx),
                newLine = lineIdx - 1,
                newCol = prevLine.length.coerceAtMost(state.maxCharsPerLine),
                struckChar = ' '
            )
        }
    }

    fun onArrowNavigate(dLine: Int, dCol: Int) {
        val state = _uiState.value
        val nextLine = (state.cursorLine + dLine).coerceIn(0, state.lines.lastIndex)
        val lineLen = state.lines[nextLine].length
        val nextCol = if (dLine != 0) {
            state.cursorCol.coerceIn(0, lineLen)
        } else {
            (state.cursorCol + dCol).coerceIn(0, lineLen)
        }

        if (nextLine != state.cursorLine) {
            soundEngine.playPlatenTick()
            _uiState.update { it.copy(cursorLine = nextLine, cursorCol = nextCol) }
        } else if (nextCol != state.cursorCol) {
            soundEngine.playSpacebar()
            _uiState.update { it.copy(cursorLine = nextLine, cursorCol = nextCol) }
        }
    }

    fun moveCursorToLineStart() {
        val state = _uiState.value
        if (state.cursorCol != 0) {
            soundEngine.playCarriageReturn()
            _uiState.update { it.copy(cursorCol = 0) }
        }
    }

    fun moveCursorToLineEnd() {
        val state = _uiState.value
        val endCol = state.lines[state.cursorLine].length
        if (state.cursorCol != endCol) {
            soundEngine.playSpacebar()
            _uiState.update { it.copy(cursorCol = endCol) }
        }
    }

    fun scrollPaperLines(deltaLines: Int) {
        onArrowNavigate(dLine = deltaLines, dCol = 0)
    }

    private fun shiftOverstrikesOnInsert(
        map: Map<Int, Map<Int, List<Char>>>,
        insertedLineIdx: Int
    ): Map<Int, Map<Int, List<Char>>> {
        if (map.isEmpty()) return map
        val result = HashMap<Int, Map<Int, List<Char>>>(map.size)
        for ((line, cols) in map) {
            if (line >= insertedLineIdx) {
                result[line + 1] = cols
            } else {
                result[line] = cols
            }
        }
        return result
    }

    private fun shiftOverstrikesOnDelete(
        map: Map<Int, Map<Int, List<Char>>>,
        deletedLineIdx: Int
    ): Map<Int, Map<Int, List<Char>>> {
        if (map.isEmpty()) return map
        val result = HashMap<Int, Map<Int, List<Char>>>(map.size)
        for ((line, cols) in map) {
            when {
                line < deletedLineIdx -> result[line] = cols
                line > deletedLineIdx -> result[line - 1] = cols
            }
        }
        return result
    }

    private fun updateLinesAndScheduleSave(
        lines: List<String>,
        overstrikes: Map<Int, Map<Int, List<Char>>>,
        newLine: Int,
        newCol: Int,
        struckChar: Char,
        immediateSave: Boolean = false
    ) {
        val words = countWordsFast(lines)
        var chars = 0
        for (i in lines.indices) {
            chars += lines[i].length
        }
        val livePages = ((lines.size - 1).coerceAtLeast(0) / _uiState.value.linesPerPage) + 1

        _uiState.update { state ->
            val updatedActive = state.activeDocument?.copy(
                pageCount = livePages,
                wordCount = words
            )
            state.copy(
                activeDocument = updatedActive,
                documents = if (updatedActive != null) {
                    state.documents.map { if (it.uri == updatedActive.uri) updatedActive else it }
                } else {
                    state.documents
                },
                lines = lines,
                overstrikesByLine = overstrikes,
                cursorLine = newLine,
                cursorCol = newCol,
                saveStatus = SaveStatus.UNSAVED,
                strikeTriggerId = if (struckChar != '\n') state.strikeTriggerId + 1 else state.strikeTriggerId,
                ribbonSpoolAngle = (state.ribbonSpoolAngle + 7.5f) % 360f,
                lastStruckChar = struckChar,
                wordCount = words,
                charCount = chars
            )
        }

        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            if (!immediateSave) delay(750L)
            saveCurrentImmediately()
        }
    }

    private suspend fun saveCurrentImmediately() {
        val state = _uiState.value
        val folderUri = state.folderUri ?: return
        var doc = state.activeDocument

        if (doc == null) {
            val hasTypedContent = state.lines.any { it.isNotBlank() }
            if (!hasTypedContent) return
            val firstLineHeading = state.lines.firstOrNull { it.isNotBlank() }
                ?.trimStart('#', ' ')
                ?.take(24)
                ?.ifBlank { "Note 1" } ?: "Note 1"
            doc = repository.createNewDocument(folderUri, firstLineHeading) ?: return
            val updatedDocs = repository.listMarkdownDocuments(folderUri)
            repository.saveLastDocUri(doc.uri)
            _uiState.update {
                it.copy(
                    documents = updatedDocs,
                    activeDocument = doc
                )
            }
        }

        val content = _uiState.value.lines.joinToString("\n")
        _uiState.update { it.copy(saveStatus = SaveStatus.SAVING) }
        val ok = repository.writeDocumentContent(doc.uri, content)
        if (ok) {
            _uiState.update { it.copy(saveStatus = SaveStatus.SAVED) }
            BookshelfAppWidgetProvider.notifyWidgetsChanged(getApplication())
        }
    }

    private fun parseTextToTypewriterLines(raw: String, maxCols: Int): List<String> {
        if (raw.isEmpty()) return listOf("")
        val result = mutableListOf<String>()
        raw.lines().forEach { paragraph ->
            if (paragraph.length <= maxCols) {
                result.add(paragraph)
            } else {
                var remaining = paragraph
                while (remaining.length > maxCols) {
                    val splitAt = remaining.lastIndexOf(' ', maxCols).takeIf { it > maxCols / 2 } ?: maxCols
                    result.add(remaining.substring(0, splitAt))
                    remaining = remaining.substring(splitAt).trimStart()
                }
                result.add(remaining)
            }
        }
        return if (result.isEmpty()) listOf("") else result
    }

    private fun countWordsFast(lines: List<String>): Int {
        var count = 0
        for (i in lines.indices) {
            val s = lines[i]
            var inWord = false
            for (j in 0 until s.length) {
                if (s[j].isWhitespace()) {
                    inWord = false
                } else if (!inWord) {
                    inWord = true
                    count++
                }
            }
        }
        return count
    }
}
