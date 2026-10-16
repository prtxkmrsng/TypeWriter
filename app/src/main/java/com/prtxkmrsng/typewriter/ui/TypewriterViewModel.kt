package com.prtxkmrsng.typewriter.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prtxkmrsng.typewriter.audio.TypewriterSoundEngine
import com.prtxkmrsng.typewriter.data.DocumentRepository
import com.prtxkmrsng.typewriter.data.MarkdownDocument
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SaveStatus { SAVED, SAVING, UNSAVED }

data class TypewriterUiState(
    val folderUri: Uri? = null,
    val documents: List<MarkdownDocument> = emptyList(),
    val activeDocument: MarkdownDocument? = null,
    val lines: List<String> = listOf(""),
    val cursorLine: Int = 0,
    val cursorCol: Int = 0,
    val maxCharsPerLine: Int = 68,
    val bellWarningCol: Int = 60,
    val themeMode: TypewriterThemeMode = TypewriterThemeMode.LIGHT,
    val isRedRibbon: Boolean = false,
    val isMuted: Boolean = false,
    val saveStatus: SaveStatus = SaveStatus.SAVED,
    val strikeTriggerId: Long = 0L,
    val lastStruckChar: Char = ' ',
    val wordCount: Int = 0,
    val charCount: Int = 0
)

class TypewriterViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DocumentRepository(application)
    val soundEngine = TypewriterSoundEngine()

    private val _uiState = MutableStateFlow(TypewriterUiState())
    val uiState: StateFlow<TypewriterUiState> = _uiState.asStateFlow()

    private var autosaveJob: Job? = null

    init {
        val savedTheme = runCatching {
            TypewriterThemeMode.valueOf(repository.getSavedThemeName())
        }.getOrDefault(TypewriterThemeMode.LIGHT)

        val savedFolder = repository.getSavedFolderUri()
        _uiState.update { it.copy(folderUri = savedFolder, themeMode = savedTheme) }

        if (savedFolder != null) {
            refreshDocumentsAndLoadInitial(savedFolder)
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
                val created = repository.createNewDocument(folderUri, "First Draft.md")
                val updatedDocs = repository.listMarkdownDocuments(folderUri)
                _uiState.update { it.copy(documents = updatedDocs) }
                created?.let { openDocument(it) }
            } else {
                _uiState.update { it.copy(documents = docs) }
                val lastUri = repository.getLastDocUri()
                val targetDoc = docs.find { it.uri == lastUri } ?: docs.first()
                openDocument(targetDoc)
            }
        }
    }

    fun createNewDocument(title: String) {
        val folderUri = _uiState.value.folderUri ?: return
        viewModelScope.launch {
            saveCurrentImmediately()
            val newDoc = repository.createNewDocument(folderUri, title) ?: return@launch
            val docs = repository.listMarkdownDocuments(folderUri)
            _uiState.update { it.copy(documents = docs) }
            openDocument(newDoc)
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

            _uiState.update {
                it.copy(
                    activeDocument = doc,
                    lines = parsedLines,
                    cursorLine = lastLineIndex,
                    cursorCol = lastColIndex,
                    saveStatus = SaveStatus.SAVED,
                    wordCount = countWords(parsedLines),
                    charCount = parsedLines.sumOf { line -> line.length }
                )
            }
            soundEngine.playCarriageReturn()
        }
    }

    fun setTheme(theme: TypewriterThemeMode) {
        repository.saveThemeName(theme.name)
        _uiState.update { it.copy(themeMode = theme) }
    }

    fun toggleRibbonColor() {
        soundEngine.playKeyStrike()
        _uiState.update { it.copy(isRedRibbon = !it.isRedRibbon) }
    }

    fun toggleMute() {
        val nextMute = !_uiState.value.isMuted
        soundEngine.isMuted = nextMute
        _uiState.update { it.copy(isMuted = nextMute) }
    }

    /**
     * Handles physical character input from the hardware keyboard.
     */
    fun onCharacterTyped(char: Char) {
        val state = _uiState.value
        val currentLines = state.lines.toMutableList()
        val lineIdx = state.cursorLine.coerceIn(0, currentLines.lastIndex)
        val currentLine = currentLines[lineIdx]
        val colIdx = state.cursorCol.coerceIn(0, currentLine.length)

        // Insert or overwrite at current carriage position
        val updatedLine = StringBuilder(currentLine).apply {
            insert(colIdx, char)
        }.toString()

        // Check if we hit or exceeded the right margin
        if (updatedLine.length > state.maxCharsPerLine) {
            // Word-wrap onto the next line automatically like a typist hitting the margin stop
            val lastSpace = updatedLine.lastIndexOf(' ', startIndex = state.maxCharsPerLine - 1)
            val (keptLine, carryOver) = if (lastSpace > state.maxCharsPerLine / 2) {
                updatedLine.substring(0, lastSpace) to updatedLine.substring(lastSpace + 1)
            } else {
                updatedLine.substring(0, state.maxCharsPerLine) to updatedLine.substring(state.maxCharsPerLine)
            }

            currentLines[lineIdx] = keptLine
            currentLines.add(lineIdx + 1, carryOver)

            soundEngine.playCarriageReturn()
            updateLinesAndScheduleSave(
                lines = currentLines,
                newLine = lineIdx + 1,
                newCol = carryOver.length,
                struckChar = char
            )
            return
        }

        currentLines[lineIdx] = updatedLine
        val nextCol = colIdx + 1

        // Play appropriate acoustic feedback
        if (nextCol == state.bellWarningCol) {
            soundEngine.playMarginBell()
        } else if (char == ' ') {
            soundEngine.playSpacebar()
        } else {
            soundEngine.playKeyStrike()
        }

        updateLinesAndScheduleSave(
            lines = currentLines,
            newLine = lineIdx,
            newCol = nextCol,
            struckChar = char
        )
    }

    /**
     * Handles Enter / Return key: rolls paper up 1 line and returns carriage to column 0.
     */
    fun onEnterPressed() {
        val state = _uiState.value
        val currentLines = state.lines.toMutableList()
        val lineIdx = state.cursorLine.coerceIn(0, currentLines.lastIndex)
        val currentLine = currentLines[lineIdx]
        val colIdx = state.cursorCol.coerceIn(0, currentLine.length)

        val beforeCursor = currentLine.substring(0, colIdx)
        val afterCursor = currentLine.substring(colIdx)

        currentLines[lineIdx] = beforeCursor
        currentLines.add(lineIdx + 1, afterCursor)

        soundEngine.playCarriageReturn()
        updateLinesAndScheduleSave(
            lines = currentLines,
            newLine = lineIdx + 1,
            newCol = 0,
            struckChar = '\n',
            immediateSave = true
        )
    }

    /**
     * Handles Backspace: shifts paper one column right and removes character.
     */
    fun onBackspacePressed() {
        val state = _uiState.value
        val currentLines = state.lines.toMutableList()
        val lineIdx = state.cursorLine.coerceIn(0, currentLines.lastIndex)
        val currentLine = currentLines[lineIdx]
        val colIdx = state.cursorCol.coerceIn(0, currentLine.length)

        if (colIdx > 0) {
            val updatedLine = currentLine.removeRange(colIdx - 1, colIdx)
            currentLines[lineIdx] = updatedLine
            soundEngine.playSpacebar()
            updateLinesAndScheduleSave(
                lines = currentLines,
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

        if (nextLine != state.cursorLine || nextCol != state.cursorCol) {
            soundEngine.playSpacebar()
            _uiState.update { it.copy(cursorLine = nextLine, cursorCol = nextCol) }
        }
    }

    fun scrollPaperLines(deltaLines: Int) {
        onArrowNavigate(dLine = deltaLines, dCol = 0)
    }

    private fun updateLinesAndScheduleSave(
        lines: List<String>,
        newLine: Int,
        newCol: Int,
        struckChar: Char,
        immediateSave: Boolean = false
    ) {
        val words = countWords(lines)
        val chars = lines.sumOf { it.length }

        _uiState.update {
            it.copy(
                lines = lines,
                cursorLine = newLine,
                cursorCol = newCol,
                saveStatus = SaveStatus.UNSAVED,
                strikeTriggerId = if (struckChar != '\n') it.strikeTriggerId + 1 else it.strikeTriggerId,
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
        val doc = _uiState.value.activeDocument ?: return
        val content = _uiState.value.lines.joinToString("\n")
        _uiState.update { it.copy(saveStatus = SaveStatus.SAVING) }
        val ok = repository.writeDocumentContent(doc.uri, content)
        if (ok) {
            _uiState.update { it.copy(saveStatus = SaveStatus.SAVED) }
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

    private fun countWords(lines: List<String>): Int {
        return lines.sumOf { line ->
            line.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
        }
    }
}
