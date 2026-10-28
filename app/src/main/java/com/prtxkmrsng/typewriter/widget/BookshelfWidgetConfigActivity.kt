package com.prtxkmrsng.typewriter.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prtxkmrsng.typewriter.data.DocumentRepository

/**
 * Configuration Activity for selecting the "Shelf Number" (Shelf 1, Shelf 2, Shelf 3...)
 * of a Home Screen Bookshelf Widget so multiple widgets form contiguous shelves.
 */
class BookshelfWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Default to CANCELED in case the user backs out before confirming
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val repo = DocumentRepository(this)
        val theme = repo.getSavedThemeMode()

        // Suggest the next contiguous shelf number based on already-placed widgets
        val manager = AppWidgetManager.getInstance(this)
        val existingIds = manager.getAppWidgetIds(
            ComponentName(this, BookshelfAppWidgetProvider::class.java)
        )
        val savedNum = repo.getWidgetShelfNumber(appWidgetId)
        val defaultShelfNum = if (savedNum > 0) {
            savedNum
        } else {
            val highestUsed = existingIds
                .filter { it != appWidgetId }
                .maxOfOrNull { id -> repo.getWidgetShelfNumber(id).coerceAtLeast(1) } ?: 0
            (highestUsed + 1).coerceAtLeast(1)
        }

        setContent {
            var selectedShelf by remember { mutableIntStateOf(defaultShelfNum) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .border(2.dp, theme.shelfTrimAccent, RoundedCornerShape(12.dp)),
                    color = theme.shelfOuterFrame,
                    contentColor = theme.shelfHeaderText,
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 16.dp
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "CONFIGURE CONTIGUOUS BOOKSHELF",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = theme.shelfHeaderText
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Select the Shelf Number for this Home Screen widget. Shelves chain together contiguously: if Shelf 1 fits books 1–52, Shelf 2 automatically displays books 53 onwards.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = theme.shelfHeaderText.copy(alpha = 0.8f)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Grid of Shelf Numbers 1..8
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (row in 0 until 2) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    for (col in 1..4) {
                                        val shelfNum = row * 4 + col
                                        val isSelected = (selectedShelf == shelfNum)
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { selectedShelf = shelfNum }
                                                .border(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) theme.accentRibbonRed else theme.shelfTrimAccent.copy(alpha = 0.45f),
                                                    shape = RoundedCornerShape(8.dp)
                                                ),
                                            color = if (isSelected) theme.shelfPlankTop else theme.shelfBackPanel,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(vertical = 12.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = "SHELF",
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 9.sp,
                                                    color = theme.shelfHeaderText.copy(alpha = 0.7f)
                                                )
                                                Text(
                                                    text = "#$shelfNum",
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 18.sp,
                                                    color = theme.shelfHeaderText
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { finish() }) {
                                Text(
                                    text = "CANCEL",
                                    fontFamily = FontFamily.Monospace,
                                    color = theme.shelfHeaderText.copy(alpha = 0.75f)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    repo.saveWidgetShelfNumber(appWidgetId, selectedShelf)
                                    BookshelfAppWidgetProvider.notifyWidgetsChanged(this@BookshelfWidgetConfigActivity)

                                    val resultValue = Intent().apply {
                                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                    }
                                    setResult(Activity.RESULT_OK, resultValue)
                                    finish()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = theme.accentRibbonRed,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    text = "SAVE SHELF #$selectedShelf",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
