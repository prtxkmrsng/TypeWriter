package com.prtxkmrsng.typewriter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.*
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.prtxkmrsng.typewriter.MainActivity
import com.prtxkmrsng.typewriter.R
import com.prtxkmrsng.typewriter.data.DocumentRepository
import com.prtxkmrsng.typewriter.data.MarkdownDocument
import com.prtxkmrsng.typewriter.ui.TypewriterThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor

/**
 * Resizable, Theme-Synchronized, Contiguous Android Home Screen Bookshelf AppWidget.
 * - Supports selecting "Shelf Number" (Shelf 1, Shelf 2, Shelf 3...) so multiple widgets
 *   chain together contiguously (e.g. Shelf 1 displays books 1..N, Shelf 2 displays N+1 onwards).
 * - Changes wood, plank, header, and trim colors to match the active TypewriterThemeMode.
 * - In Twilight Torch mode, shrouds the shelves in darkness and places flickering wax candles
 *   along the shelf planks that illuminate nearby book spines.
 */
class BookshelfAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        notifyWidgetsChanged(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val (w, h) = extractDimensionsDp(context, newOptions)
        DocumentRepository(context).saveWidgetDimensionsDp(appWidgetId, w, h)
        // Re-pack all contiguous shelves whenever any shelf is resized!
        notifyWidgetsChanged(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val repo = DocumentRepository(context)
        appWidgetIds.forEach { repo.deleteWidgetPrefs(it) }
        notifyWidgetsChanged(context)
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val SPINE_COLORS = intArrayOf(
            Color.rgb(122, 31, 29),
            Color.rgb(30, 67, 50),
            Color.rgb(31, 51, 77),
            Color.rgb(107, 68, 35),
            Color.rgb(74, 37, 69),
            Color.rgb(140, 90, 43),
            Color.rgb(43, 58, 63),
            Color.rgb(92, 44, 36)
        )

        private fun extractDimensionsDp(context: Context, options: Bundle?): Pair<Float, Float> {
            val isLandscape =
                context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val widthDp = if (isLandscape) {
                options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 280) ?: 280
            } else {
                options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 220) ?: 220
            }.coerceIn(140, 960).toFloat()

            val heightDp = if (isLandscape) {
                options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 140) ?: 140
            } else {
                options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180) ?: 180
            }.coerceIn(100, 820).toFloat()

            return widthDp to heightDp
        }

        fun notifyWidgetsChanged(context: Context) {
            val appContext = context.applicationContext
            scope.launch {
                val manager = AppWidgetManager.getInstance(appContext)
                val component = ComponentName(appContext, BookshelfAppWidgetProvider::class.java)
                val allIds = manager.getAppWidgetIds(component)
                if (allIds.isEmpty()) return@launch

                val repo = DocumentRepository(appContext)
                val theme = repo.getSavedThemeMode()
                val folderUri = repo.getSavedFolderUri()
                val documents = if (folderUri != null) {
                    repo.listMarkdownDocuments(folderUri)
                } else {
                    emptyList()
                }
                val activeUri = repo.getLastDocUri()

                // Ensure every placed widget has a valid Shelf Number (auto-assign 1, 2, 3... if unset)
                val usedNumbers = mutableSetOf<Int>()
                allIds.sorted().forEach { id ->
                    val saved = repo.getWidgetShelfNumber(id)
                    if (saved > 0) {
                        usedNumbers.add(saved)
                    }
                }
                allIds.sorted().forEach { id ->
                    if (repo.getWidgetShelfNumber(id) <= 0) {
                        var candidate = 1
                        while (usedNumbers.contains(candidate)) candidate++
                        repo.saveWidgetShelfNumber(id, candidate)
                        usedNumbers.add(candidate)
                    }
                    val opts = manager.getAppWidgetOptions(id)
                    val (w, h) = extractDimensionsDp(appContext, opts)
                    repo.saveWidgetDimensionsDp(id, w, h)
                }

                // Map shelfNumber -> dimensions (widthDp, heightDp) of that shelf widget
                val dimensionsByShelfNum = mutableMapOf<Int, Pair<Float, Float>>()
                allIds.forEach { id ->
                    val sNum = repo.getWidgetShelfNumber(id).coerceAtLeast(1)
                    val dims = repo.getWidgetDimensionsDp(id)
                        ?: extractDimensionsDp(appContext, manager.getAppWidgetOptions(id))
                    dimensionsByShelfNum[sNum] = dims
                }

                val maxShelfNumber = allIds.maxOfOrNull { repo.getWidgetShelfNumber(it).coerceAtLeast(1) } ?: 1

                // Pre-calculate contiguous book ranges [startDocIndex .. endDocIndexExclusive) for Shelf 1..maxShelfNumber
                val shelfStartIndexMap = mutableMapOf<Int, Int>()
                var runningDocIndex = 0
                for (shelfNum in 1..maxShelfNumber) {
                    shelfStartIndexMap[shelfNum] = runningDocIndex
                    val (wDp, hDp) = dimensionsByShelfNum[shelfNum] ?: (260f to 160f)
                    runningDocIndex = simulateBooksConsumedOnWidget(
                        documents = documents,
                        startDocIndex = runningDocIndex,
                        widthDp = wDp,
                        heightDp = hDp,
                        isTwilight = theme.isTwilight
                    )
                }

                // Render each widget with its assigned contiguous slice of books
                allIds.forEach { widgetId ->
                    val shelfNum = repo.getWidgetShelfNumber(widgetId).coerceAtLeast(1)
                    val (widthDp, heightDp) = dimensionsByShelfNum[shelfNum]
                        ?: extractDimensionsDp(appContext, manager.getAppWidgetOptions(widgetId))
                    val startBookIdx = shelfStartIndexMap[shelfNum] ?: 0

                    renderAndPushWidget(
                        context = appContext,
                        appWidgetManager = manager,
                        appWidgetId = widgetId,
                        shelfNumber = shelfNum,
                        widthDp = widthDp,
                        heightDp = heightDp,
                        documents = documents,
                        startDocIndex = startBookIdx,
                        activeUri = activeUri,
                        theme = theme
                    )
                }
            }
        }

        /**
         * Simulates packing books onto a widget of [widthDp] x [heightDp] starting at [startDocIndex],
         * returning the next unplaced book index for the next contiguous shelf widget.
         */
        private fun simulateBooksConsumedOnWidget(
            documents: List<MarkdownDocument>,
            startDocIndex: Int,
            widthDp: Float,
            heightDp: Float,
            isTwilight: Boolean
        ): Int {
            val usableWidthDp = (widthDp - 28f).coerceAtLeast(110f)
            val usableHeightDp = (heightDp - 40f).coerceAtLeast(70f)
            val shelfRows = floor(usableHeightDp / 92f).toInt().coerceIn(1, 6)

            var cursor = startDocIndex
            for (row in 0 until shelfRows) {
                var usedWidthDp = if (isTwilight) 20f else 0f // Reserve space for Twilight candle
                var booksInRow = 0
                while (cursor < documents.size) {
                    val doc = documents[cursor]
                    val bookWidthDp = (26f * doc.thicknessMultiplier).coerceIn(26f, 64f)
                    val candleExtraDp = if (isTwilight && booksInRow > 0 && booksInRow % 4 == 0) 18f else 0f
                    if (usedWidthDp + candleExtraDp + bookWidthDp + 2f > usableWidthDp && booksInRow > 0) {
                        break
                    }
                    usedWidthDp += candleExtraDp + bookWidthDp + 2f
                    booksInRow++
                    cursor++
                }
            }
            return cursor
        }

        private fun renderAndPushWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            shelfNumber: Int,
            widthDp: Float,
            heightDp: Float,
            documents: List<MarkdownDocument>,
            startDocIndex: Int,
            activeUri: android.net.Uri?,
            theme: TypewriterThemeMode
        ) {
            val usableWidthDp = (widthDp - 28f).coerceAtLeast(110f)
            val usableHeightDp = (heightDp - 40f).coerceAtLeast(70f)
            val shelfCount = floor(usableHeightDp / 92f).toInt().coerceIn(1, 6)
            val shelfBayHeightDp = (usableHeightDp / shelfCount.toFloat()) - 14f

            val rootViews = RemoteViews(context.packageName, R.layout.bookshelf_app_widget)

            // Apply active App Theme colors to the Bookshelf Widget frame, header & interior
            rootViews.setInt(R.id.widget_bookshelf_root, "setBackgroundColor", theme.shelfOuterFrame.toArgb())
            rootViews.setInt(R.id.widget_header_bar, "setBackgroundColor", theme.shelfHeaderBg.toArgb())
            rootViews.setInt(R.id.widget_shelves_container, "setBackgroundColor", theme.shelfBackPanel.toArgb())
            rootViews.setTextColor(R.id.widget_header_title, theme.shelfHeaderText.toArgb())
            rootViews.setTextColor(R.id.widget_header_stats, theme.shelfTrimAccent.toArgb())

            // Tapping the left title launches TypeWriter
            val appLaunchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val launchPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                appLaunchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            rootViews.setOnClickPendingIntent(R.id.widget_header_title, launchPendingIntent)

            // Tapping the right "SHELF #X ⚙" badge opens BookshelfWidgetConfigActivity to change Shelf Number
            val configIntent = Intent(context, BookshelfWidgetConfigActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val configPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 50000,
                configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            rootViews.setOnClickPendingIntent(R.id.widget_header_stats, configPendingIntent)

            rootViews.removeAllViews(R.id.widget_shelves_container)

            var docCursor = startDocIndex
            val firstBookOneBased = startDocIndex + 1

            for (shelfIdx in 0 until shelfCount) {
                val shelfRowViews = RemoteViews(context.packageName, R.layout.bookshelf_widget_shelf_row)
                shelfRowViews.removeAllViews(R.id.shelf_books_row)

                // Theme the wooden/metal shelf plank & trim line
                shelfRowViews.setInt(R.id.shelf_plank_body, "setBackgroundColor", theme.shelfPlankBottom.toArgb())
                shelfRowViews.setInt(R.id.shelf_plank_trim, "setBackgroundColor", theme.shelfTrimAccent.toArgb())
                shelfRowViews.setTextColor(R.id.shelf_empty_label, theme.shelfTrimAccent.toArgb())

                if (docCursor >= documents.size && shelfIdx == 0) {
                    shelfRowViews.setViewVisibility(R.id.shelf_books_row, View.GONE)
                    shelfRowViews.setViewVisibility(R.id.shelf_empty_label, View.VISIBLE)
                    val emptyMsg = if (documents.isEmpty()) {
                        "SHELF #$shelfNumber EMPTY — TAP TO BIND A NOTE"
                    } else {
                        "SHELF #$shelfNumber (AWAITING BOOK #${startDocIndex + 1}+)"
                    }
                    shelfRowViews.setTextViewText(R.id.shelf_empty_label, emptyMsg)
                    shelfRowViews.setOnClickPendingIntent(R.id.shelf_empty_label, configPendingIntent)
                } else {
                    shelfRowViews.setViewVisibility(R.id.shelf_books_row, View.VISIBLE)
                    shelfRowViews.setViewVisibility(R.id.shelf_empty_label, View.GONE)

                    var usedRowWidthDp = 0f
                    var booksInRow = 0

                    // In Twilight mode, place an initial flickering wax candle at the start of the shelf
                    if (theme.isTwilight && (docCursor < documents.size || shelfIdx == 0)) {
                        val candleViews = RemoteViews(context.packageName, R.layout.bookshelf_widget_book_item)
                        candleViews.setImageViewBitmap(
                            R.id.widget_book_spine_img,
                            renderTwilightCandleBitmap(context, shelfBayHeightDp, seed = shelfIdx * 7 + shelfNumber)
                        )
                        candleViews.setOnClickPendingIntent(R.id.widget_book_item_root, launchPendingIntent)
                        shelfRowViews.addView(R.id.shelf_books_row, candleViews)
                        usedRowWidthDp += 20f
                    }

                    while (docCursor < documents.size) {
                        val doc = documents[docCursor]
                        val bookWidthDp = (26f * doc.thicknessMultiplier).coerceIn(26f, 64f)
                        val needsCandleAfter = theme.isTwilight && booksInRow > 0 && booksInRow % 4 == 0
                        val candleWidthDp = if (needsCandleAfter) 18f else 0f

                        if (usedRowWidthDp + candleWidthDp + bookWidthDp + 2f > usableWidthDp && booksInRow > 0) {
                            break
                        }

                        if (needsCandleAfter) {
                            val candleViews = RemoteViews(context.packageName, R.layout.bookshelf_widget_book_item)
                            candleViews.setImageViewBitmap(
                                R.id.widget_book_spine_img,
                                renderTwilightCandleBitmap(context, shelfBayHeightDp, seed = docCursor)
                            )
                            candleViews.setOnClickPendingIntent(R.id.widget_book_item_root, launchPendingIntent)
                            shelfRowViews.addView(R.id.shelf_books_row, candleViews)
                            usedRowWidthDp += candleWidthDp
                        }

                        // Calculate how close this book is to a candle in Twilight mode (0 = right next to candle, 1 = 1 book away, 2 = dim middle)
                        val candleDistance = if (theme.isTwilight) {
                            val mod = booksInRow % 4
                            kotlin.math.min(mod, 4 - mod)
                        } else 0

                        val bookViews = RemoteViews(context.packageName, R.layout.bookshelf_widget_book_item)
                        val bookBitmap = renderSingleBookSpineBitmap(
                            context = context,
                            doc = doc,
                            spineWidthDp = bookWidthDp,
                            bayHeightDp = shelfBayHeightDp,
                            isActive = (doc.uri == activeUri),
                            isTwilight = theme.isTwilight,
                            candleDistance = candleDistance
                        )
                        bookViews.setImageViewBitmap(R.id.widget_book_spine_img, bookBitmap)

                        val openBookIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(MainActivity.EXTRA_OPEN_DOCUMENT_URI, doc.uri.toString())
                        }
                        val bookPendingIntent = PendingIntent.getActivity(
                            context,
                            doc.uri.toString().hashCode(),
                            openBookIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        bookViews.setOnClickPendingIntent(R.id.widget_book_item_root, bookPendingIntent)

                        shelfRowViews.addView(R.id.shelf_books_row, bookViews)
                        usedRowWidthDp += bookWidthDp + 2f
                        booksInRow++
                        docCursor++
                    }
                }

                rootViews.addView(R.id.widget_shelves_container, shelfRowViews)
            }

            val lastBookOneBased = docCursor
            val rangeLabel = if (lastBookOneBased >= firstBookOneBased && documents.isNotEmpty()) {
                "BOOKS $firstBookOneBased–$lastBookOneBased"
            } else {
                "${documents.size} TOTAL"
            }
            rootViews.setTextViewText(
                R.id.widget_header_title,
                if (theme.isTwilight) "🕯 TWILIGHT LIBRARY" else "TYPEWRITER LIBRARY"
            )
            rootViews.setTextViewText(
                R.id.widget_header_stats,
                "SHELF #$shelfNumber ⚙ • $rangeLabel"
            )

            appWidgetManager.updateAppWidget(appWidgetId, rootViews)
        }

        /**
         * Renders a miniature flickering wax candle in a brass holder with a warm radial halo
         * for Twilight Torch mode on the Home Screen bookshelf widget.
         */
        private fun renderTwilightCandleBitmap(
            context: Context,
            bayHeightDp: Float,
            seed: Int
        ): Bitmap {
            val density = context.resources.displayMetrics.density.coerceIn(1.5f, 3.0f)
            val widthPx = (18f * density).toInt().coerceAtLeast(28)
            val heightPx = (bayHeightDp.coerceIn(56f, 160f) * density).toInt().coerceAtLeast(80)

            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            val cx = widthPx / 2f
            val candleHeight = (22f + (abs(seed) % 8)) * density
            val saucerHeight = 3.5f * density
            val candleBottom = heightPx - saucerHeight
            val candleTop = candleBottom - candleHeight
            val flameCenterY = candleTop - 7f * density

            // 1. Warm radial candlelight halo illuminating the dark shelf bay
            paint.shader = RadialGradient(
                cx,
                flameCenterY,
                widthPx * 2.1f,
                intArrayOf(
                    Color.argb(145, 255, 190, 85),
                    Color.argb(65, 255, 140, 40),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), paint)
            paint.shader = null

            // 2. Brass saucer base at the bottom of the candle
            paint.color = Color.rgb(166, 130, 70)
            canvas.drawRoundRect(
                RectF(2f, candleBottom, widthPx - 2f, heightPx.toFloat()),
                2f * density,
                2f * density,
                paint
            )

            // 3. Cream melted wax pillar body
            val pillarHalfW = 4.2f * density
            paint.color = Color.rgb(235, 220, 190)
            canvas.drawRoundRect(
                RectF(cx - pillarHalfW, candleTop, cx + pillarHalfW, candleBottom),
                2f * density,
                2f * density,
                paint
            )

            // 4. Wick
            paint.color = Color.rgb(30, 22, 16)
            paint.strokeWidth = 1.2f * density
            canvas.drawLine(cx, candleTop, cx, candleTop - 3f * density, paint)

            // 5. Outer golden flame & inner white-hot core
            paint.color = Color.rgb(255, 175, 50)
            canvas.drawOval(
                RectF(
                    cx - 3.2f * density,
                    flameCenterY - 5.5f * density,
                    cx + 3.2f * density,
                    candleTop - 1f * density
                ),
                paint
            )
            paint.color = Color.rgb(255, 245, 200)
            canvas.drawOval(
                RectF(
                    cx - 1.6f * density,
                    flameCenterY - 3f * density,
                    cx + 1.6f * density,
                    candleTop - 1.5f * density
                ),
                paint
            )

            return bitmap
        }

        private fun renderSingleBookSpineBitmap(
            context: Context,
            doc: MarkdownDocument,
            spineWidthDp: Float,
            bayHeightDp: Float,
            isActive: Boolean,
            isTwilight: Boolean,
            candleDistance: Int
        ): Bitmap {
            val density = context.resources.displayMetrics.density.coerceIn(1.5f, 3.0f)
            val cleanTitle = doc.name.removeSuffix(".md").removeSuffix(".MD").ifBlank { "Untitled" }
            val hash = abs(cleanTitle.hashCode())

            val widthPx = (spineWidthDp * density).toInt().coerceAtLeast(36)
            val fullBayHeightPx = (bayHeightDp.coerceIn(56f, 160f) * density).toInt().coerceAtLeast(80)
            val heightFactor = 0.86f + ((hash % 11) * 0.01f)
            val bookHeightPx = (fullBayHeightPx * heightFactor).toInt().coerceAtLeast(64)
            val topGapPx = (fullBayHeightPx - bookHeightPx).coerceAtLeast(0)

            val bitmap = Bitmap.createBitmap(widthPx, fullBayHeightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            val rawBaseColor = SPINE_COLORS[hash % SPINE_COLORS.size]
            // In Twilight mode, books right next to a candle are warmly lit; books further away are dimmer in shadow
            val lightFactor = if (isTwilight) {
                when (candleDistance) {
                    0 -> 0.92f
                    1 -> 0.62f
                    else -> 0.38f
                }
            } else 1.0f

            val baseColor = Color.rgb(
                (Color.red(rawBaseColor) * lightFactor).toInt().coerceIn(0, 255),
                (Color.green(rawBaseColor) * lightFactor).toInt().coerceIn(0, 255),
                (Color.blue(rawBaseColor) * lightFactor).toInt().coerceIn(0, 255)
            )

            val bookRect = RectF(1f, topGapPx.toFloat(), widthPx - 1f, fullBayHeightPx.toFloat())
            val radius = 3.5f * density

            val darkEdge = Color.rgb(
                (Color.red(baseColor) * 0.62f).toInt(),
                (Color.green(baseColor) * 0.62f).toInt(),
                (Color.blue(baseColor) * 0.62f).toInt()
            )
            val highlight = Color.rgb(
                (Color.red(baseColor) * 1.22f).toInt().coerceAtMost(255),
                (Color.green(baseColor) * 1.22f).toInt().coerceAtMost(255),
                (Color.blue(baseColor) * 1.22f).toInt().coerceAtMost(255)
            )
            paint.shader = LinearGradient(
                0f,
                0f,
                widthPx.toFloat(),
                0f,
                intArrayOf(darkEdge, highlight, baseColor, darkEdge),
                floatArrayOf(0f, 0.28f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(bookRect, radius, radius, paint)
            paint.shader = null

            // Warm candlelight reflection near the lower half of the book spine in Twilight mode
            if (isTwilight && candleDistance <= 1) {
                val glowAlpha = if (candleDistance == 0) 75 else 35
                paint.shader = RadialGradient(
                    widthPx / 2f,
                    fullBayHeightPx * 0.78f,
                    fullBayHeightPx * 0.65f,
                    intArrayOf(
                        Color.argb(glowAlpha, 255, 185, 75),
                        Color.TRANSPARENT
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(bookRect, radius, radius, paint)
                paint.shader = null
            }

            if (isActive) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f * density
                paint.color = Color.rgb(255, 213, 126)
                canvas.drawRoundRect(bookRect, radius, radius, paint)
                paint.style = Paint.Style.FILL
            }

            val ribCount = (1 + (doc.pageCount / 5)).coerceIn(1, 4)
            val ribAlpha = (195 * lightFactor).toInt().coerceIn(70, 210)
            paint.color = Color.argb(ribAlpha, 212, 175, 55)
            for (r in 0 until ribCount) {
                val topRibY = topGapPx + (7f + r * 4f) * density
                val botRibY = fullBayHeightPx - (7f + r * 4f) * density
                canvas.drawRect(3f, topRibY, widthPx - 3f, topRibY + 1.8f * density, paint)
                canvas.drawRect(3f, botRibY - 1.8f * density, widthPx - 3f, botRibY, paint)
            }

            val textAlpha = if (isTwilight) {
                when (candleDistance) {
                    0 -> 250
                    1 -> 195
                    else -> 135
                }
            } else 255

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(textAlpha, 245, 230, 196)
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                textSize = 11f * density
            }

            val reservedRibSpace = (18f + ribCount * 8f) * density
            val maxTextWidthPx = (bookHeightPx - reservedRibSpace).coerceAtLeast(24f)
            var displayTitle = cleanTitle
            while (displayTitle.length > 3 && textPaint.measureText(displayTitle) > maxTextWidthPx) {
                displayTitle = displayTitle.dropLast(2) + "…"
            }

            val cx = widthPx / 2f
            val cy = topGapPx + (bookHeightPx / 2f)
            canvas.save()
            canvas.rotate(-90f, cx, cy)
            val fm = textPaint.fontMetrics
            val baseline = -(fm.ascent + fm.descent) / 2f
            canvas.drawText(displayTitle, cx, cy + baseline, textPaint)
            canvas.restore()

            return bitmap
        }
    }
}
