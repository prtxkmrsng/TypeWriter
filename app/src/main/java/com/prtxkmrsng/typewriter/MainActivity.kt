package com.prtxkmrsng.typewriter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.prtxkmrsng.typewriter.ui.TypewriterRootScreen
import com.prtxkmrsng.typewriter.ui.TypewriterViewModel

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_DOCUMENT_URI = "extra_open_document_uri"
    }

    private val viewModel: TypewriterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        handleBookWidgetIntent(intent)

        setContent {
            TypewriterRootScreen(viewModel = viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleBookWidgetIntent(intent)
    }

    private fun handleBookWidgetIntent(intent: Intent?) {
        val docUriString = intent?.getStringExtra(EXTRA_OPEN_DOCUMENT_URI)
        if (!docUriString.isNullOrBlank()) {
            viewModel.openDocumentByUriString(docUriString)
            intent.removeExtra(EXTRA_OPEN_DOCUMENT_URI)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.flushPendingSave()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
