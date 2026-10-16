package com.prtxkmrsng.typewriter.ui

import android.app.Application
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import com.prtxkmrsng.typewriter.audio.TypewriterSoundEngine
import com.prtxkmrsng.typewriter.data.Document
import com.prtxkmrsng.typewriter.data.DocumentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TypewriterUiState(
    val textFieldValue: TextFieldValue = TextFieldValue(""),
    val wordCount: Int = 0,
    val charCount: Int = 0,
    val isMuted: Boolean = false
)

class TypewriterViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DocumentRepository(application)
    private val soundEngine = TypewriterSoundEngine(application)

    private val _uiState = MutableStateFlow(TypewriterUiState())
    val uiState: StateFlow<TypewriterUiState> = _uiState.asStateFlow()

    init {
        val doc = repository.loadDocument()
        val textValue = TextFieldValue(
            text = doc.content,
            selection = TextRange(doc.content.length)
        )
        _uiState.value = TypewriterUiState(
            textFieldValue = textValue,
            wordCount = calculateWordCount(doc.content),
            charCount = doc.content.length,
            isMuted = soundEngine.isMuted()
        )
    }

    fun onTextChanged(newValue: TextFieldValue) {
        val oldText = _uiState.value.textFieldValue.text
        val newText = newValue.text

        if (newText.length > oldText.length) {
            val addedChar = newText.lastOrNull()
            if (addedChar == '\n') {
                soundEngine.playReturn()
            } else {
                soundEngine.playKeystroke()
            }
        } else if (newText.length < oldText.length) {
            soundEngine.playBackspace()
        }

        _uiState.value = _uiState.value.copy(
            textFieldValue = newValue,
            wordCount = calculateWordCount(newText),
            charCount = newText.length
        )

        repository.saveDocument(Document(content = newText))
    }

    fun toggleMute() {
        val muted = soundEngine.toggleMute()
        _uiState.value = _uiState.value.copy(isMuted = muted)
    }

    fun clearDocument() {
        soundEngine.playReturn()
        _uiState.value = _uiState.value.copy(
            textFieldValue = TextFieldValue(""),
            wordCount = 0,
            charCount = 0
        )
        repository.clearDocument()
    }

    private fun calculateWordCount(text: String): Int {
        if (text.isBlank()) return 0
        return text.trim().split("\\s+".toRegex()).size
    }

    override fun onCleared() {
        super.onCleared()
        soundEngine.release()
    }
}
