package com.prtxkmrsng.typewriter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.prtxkmrsng.typewriter.ui.TypewriterScreen
import com.prtxkmrsng.typewriter.ui.TypewriterTheme
import com.prtxkmrsng.typewriter.ui.TypewriterViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: TypewriterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TypewriterTheme {
                val uiState by viewModel.uiState.collectAsState()
                TypewriterScreen(
                    viewModel = viewModel,
                    uiState = uiState
                )
            }
        }
    }
}
