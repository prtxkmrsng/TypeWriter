package com.prtxkmrsng.typewriter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp

@Composable
fun TypewriterScreen(
    viewModel: TypewriterViewModel,
    uiState: TypewriterUiState
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TypewriterTopBar(
                isMuted = uiState.isMuted,
                onToggleMute = { viewModel.toggleMute() },
                onClear = { viewModel.clearDocument() }
            )
        },
        bottomBar = {
            TypewriterStatusBar(
                wordCount = uiState.wordCount,
                charCount = uiState.charCount
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(VintagePaper)
                .padding(16.dp)
        ) {
            BasicTextField(
                value = uiState.textFieldValue,
                onValueChange = { viewModel.onTextChanged(it) },
                textStyle = TypewriterTypography.bodyLarge.copy(color = VintageInk),
                cursorBrush = SolidColor(VintageInk),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
            )
        }
    }
}
