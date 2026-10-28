package com.prtxkmrsng.typewriter.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.prtxkmrsng.typewriter.data.MarkdownDocument
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

data class BookshelfCapacity(
    val shelfCount: Int,
    val booksPerShelf: Int,
    val totalCapacity: Int
) {
    companion object {
        fun compute(widthDp: Float, heightDp: Float): BookshelfCapacity {
            val usableWidthDp = (widthDp - 24f).coerceAtLeast(100f)
            val usableHeightDp = (heightDp - 36f).coerceAtLeast(70f)

            val shelves = floor(usableHeightDp / 88f).toInt().coerceIn(1, 6)
            val booksPerRow = floor(usableWidthDp / 28f).toInt().coerceIn(3, 28)

            return BookshelfCapacity(
                shelfCount = shelves,
                booksPerShelf = booksPerRow,
                totalCapacity = shelves * booksPerRow
            )
        }
    }
}

private val BOOK_COVER_PALETTE = listOf(
    Color(0xFF7A1F1D),
    Color(0xFF1E4332),
    Color(0xFF1F334D),
    Color(0xFF6B4423),
    Color(0xFF4A2545),
    Color(0xFF8C5A2B),
    Color(0xFF2B3A3F),
    Color(0xFF5C2C24)
)

@Composable
fun BookshelfTopDrawer(
    isOpen: Boolean,
    theme: TypewriterThemeMode = TypewriterThemeMode.LIGHT,
    documents: List<MarkdownDocument>,
    activeDocumentUri: android.net.Uri?,
    onToggleOpen: () -> Unit,
    onSelectBook: (MarkdownDocument) -> Unit,
    onAddBook: () -> Unit,
    onPageFlipSound: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = isOpen,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
        ) {
            BookshelfCarouselCabinet(
                theme = theme,
                documents = documents,
                activeDocumentUri = activeDocumentUri,
                onSelectBook = onSelectBook,
                onAddBook = onAddBook,
                onClose = onToggleOpen,
                onPageFlipSound = onPageFlipSound
            )
        }

        // Top-Middle Theme-Synchronized Pull-Tab Handle
        Surface(
            modifier = Modifier
                .shadow(8.dp, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                .clickable { onToggleOpen() }
                .pointerInput(isOpen) {
                    var dragTotal = 0f
                    detectVerticalDragGestures { _, dragAmount ->
                        dragTotal += dragAmount
                        if (!isOpen && dragTotal > 24f) {
                            dragTotal = 0f
                            onToggleOpen()
                        } else if (isOpen && dragTotal < -24f) {
                            dragTotal = 0f
                            onToggleOpen()
                        }
                    }
                }
                .border(
                    width = 1.dp,
                    color = theme.shelfTrimAccent,
                    shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                ),
            color = theme.shelfOuterFrame,
            contentColor = theme.shelfHeaderText
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoStories,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = theme.shelfTrimAccent
                )
                Text(
                    text = if (isOpen) "CLOSE BOOKSHELF" else "BOOKSHELF (${documents.size})",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    color = theme.shelfHeaderText
                )
                Icon(
                    imageVector = if (isOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = theme.shelfTrimAccent
                )
            }
        }
    }
}

@Composable
private fun BookshelfCarouselCabinet(
    theme: TypewriterThemeMode,
    documents: List<MarkdownDocument>,
    activeDocumentUri: android.net.Uri?,
    onSelectBook: (MarkdownDocument) -> Unit,
    onAddBook: () -> Unit,
    onClose: () -> Unit,
    onPageFlipSound: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    // Real-time candle flame flicker state when in Twilight Torch mode
    var candleFlicker1 by remember { mutableFloatStateOf(1f) }
    var candleFlicker2 by remember { mutableFloatStateOf(1f) }
    var candleFlicker3 by remember { mutableFloatStateOf(1f) }
    var flameSwayPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(theme.isTwilight) {
        if (!theme.isTwilight) return@LaunchedEffect
        while (true) {
            withFrameMillis { frameMs ->
                val t = frameMs / 1000.0
                candleFlicker1 = (0.90f + sin(t * 5.3) * 0.06f + sin(t * 13.1) * 0.04f).toFloat().coerceIn(0.72f, 1.0f)
                candleFlicker2 = (0.92f + sin(t * 4.1 + 1.4) * 0.05f + sin(t * 11.7) * 0.03f).toFloat().coerceIn(0.75f, 1.0f)
                candleFlicker3 = (0.88f + sin(t * 6.2 + 2.8) * 0.07f + (Random.nextFloat() - 0.5f) * 0.03f).toFloat().coerceIn(0.70f, 1.0f)
                flameSwayPx = (sin(t * 3.4) * 2.5).toFloat()
            }
        }
    }

    Surface(
        modifier = Modifier
            .widthIn(max = 780.dp)
            .fillMaxWidth(0.88f)
            .height(310.dp)
            .shadow(24.dp, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .border(
                width = 2.dp,
                color = theme.shelfTrimAccent,
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
            ),
        color = theme.shelfOuterFrame,
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Theme-Synchronized Cabinet Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(theme.shelfHeaderBg, theme.shelfOuterFrame)
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (theme.isTwilight) {
                            "🕯 CANDLELIT LIBRARY • SWIPE TO FLIP VOLUMES"
                        } else {
                            "MANUSCRIPT CAROUSEL (${theme.displayName.uppercase()}) • SWIPE TO FLIP"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        color = theme.shelfHeaderText
                    )
                    Text(
                        text = if (documents.isEmpty()) {
                            "0 VOLUMES BOUND — ADD A NOTE TO PLACE IT ON THE SHELF"
                        } else {
                            "${documents.size} ${if (documents.size == 1) "VOLUME" else "VOLUMES"} • TAP ANY BOOK TO LOAD ONTO PLATEN"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = theme.shelfTrimAccent
                    )
                }

                Button(
                    onClick = onAddBook,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.accentRibbonRed,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "BIND NEW BOOK",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp
                    )
                }
            }

            HorizontalDivider(color = theme.shelfTrimAccent.copy(alpha = 0.6f), thickness = 1.dp)

            // 2. Main Carousel Stage + Theme-Synchronized Shelf Plank
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val plankHeight = 18.dp.toPx()
                    val plankTop = size.height - plankHeight

                    // Recessed bookcase back panel
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                theme.shelfOuterFrame,
                                theme.shelfBackPanel
                            ),
                            center = Offset(size.width / 2f, size.height * 0.45f),
                            radius = size.width * 0.6f
                        ),
                        size = size
                    )

                    // Shelf Plank
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                theme.shelfPlankTop,
                                theme.shelfPlankBottom
                            ),
                            startY = plankTop,
                            endY = size.height
                        ),
                        topLeft = Offset(0f, plankTop),
                        size = Size(size.width, plankHeight)
                    )
                    // Trim strip along the shelf plank
                    drawLine(
                        color = theme.shelfTrimAccent.copy(alpha = 0.75f),
                        start = Offset(0f, plankTop + 2f),
                        end = Offset(size.width, plankTop + 2f),
                        strokeWidth = 2f
                    )

                    // In Twilight mode, draw ambient background candle glow behind the books
                    if (theme.isTwilight) {
                        val candlePositions = listOf(
                            Offset(size.width * 0.14f, plankTop) to candleFlicker1,
                            Offset(size.width * 0.50f, plankTop) to candleFlicker2,
                            Offset(size.width * 0.86f, plankTop) to candleFlicker3
                        )
                        candlePositions.forEach { (pos, flicker) ->
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0x55FFB74D).copy(alpha = 0.34f * flicker),
                                        Color(0x28FF9800).copy(alpha = 0.16f * flicker),
                                        Color.Transparent
                                    ),
                                    center = Offset(pos.x, pos.y - 45.dp.toPx()),
                                    radius = 210.dp.toPx() * flicker
                                ),
                                radius = 210.dp.toPx() * flicker,
                                center = Offset(pos.x, pos.y - 45.dp.toPx())
                            )
                        }
                    }
                }

                if (documents.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "YOUR BOOKSHELF IS EMPTY",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = theme.shelfHeaderText
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Start typing on the platen or click 'BIND NEW BOOK' above.\nEach note becomes a hardcover volume that grows thicker every 5 pages.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            textAlign = TextAlign.Center,
                            color = theme.shelfTrimAccent
                        )
                    }
                } else {
                    val initialIndex = remember(documents, activeDocumentUri) {
                        documents.indexOfFirst { it.uri == activeDocumentUri }.coerceAtLeast(0)
                    }
                    val pagerState = rememberPagerState(
                        initialPage = initialIndex,
                        pageCount = { documents.size }
                    )

                    LaunchedEffect(pagerState.currentPage) {
                        onPageFlipSound()
                    }

                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val cardWidth = 220.dp
                        val horizontalPad = ((maxWidth - cardWidth) / 2).coerceAtLeast(32.dp)

                        HorizontalPager(
                            state = pagerState,
                            contentPadding = PaddingValues(horizontal = horizontalPad),
                            pageSpacing = 18.dp,
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 16.dp)
                        ) { pageIdx ->
                            val doc = documents[pageIdx]
                            val pageOffset = (
                                (pagerState.currentPage - pageIdx) + pagerState.currentPageOffsetFraction
                            )
                            val absOffset = abs(pageOffset).coerceIn(0f, 2f)

                            CarouselBookCard(
                                document = doc,
                                isTwilight = theme.isTwilight,
                                candleFlicker = candleFlicker2,
                                isActiveOnPlaten = doc.uri == activeDocumentUri,
                                isCenteredInCarousel = (pageIdx == pagerState.currentPage),
                                pageOffset = pageOffset,
                                absOffset = absOffset,
                                onClick = {
                                    if (pageIdx != pagerState.currentPage) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pageIdx)
                                        }
                                    }
                                    onSelectBook(doc)
                                    onClose()
                                }
                            )
                        }

                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val maxDots = documents.size.coerceAtMost(12)
                            for (i in 0 until maxDots) {
                                val selected = (i == pagerState.currentPage % maxDots)
                                Box(
                                    modifier = Modifier
                                        .size(if (selected) 7.dp else 5.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) theme.shelfHeaderText
                                            else theme.shelfTrimAccent.copy(alpha = 0.4f)
                                        )
                                )
                            }
                        }
                    }
                }

                // 3. Foreground Flickering Wax Candles & Shadow Vignette in Twilight Torch Mode
                if (theme.isTwilight) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val plankTop = size.height - 18.dp.toPx()

                        // Deep shadow vignette shrouding the upper/outer corners of the shelf
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0x99020204),
                                    Color(0xE6020204)
                                ),
                                center = Offset(size.width * 0.5f, plankTop - 60.dp.toPx()),
                                radius = size.width * 0.58f
                            ),
                            size = size
                        )

                        // Little melted wax candles sitting on the shelf plank
                        drawShelfWaxCandle(
                            baseCenter = Offset(size.width * 0.12f, plankTop),
                            pillarHeight = 28.dp.toPx(),
                            flicker = candleFlicker1,
                            swayPx = flameSwayPx
                        )
                        drawShelfWaxCandle(
                            baseCenter = Offset(size.width * 0.22f, plankTop),
                            pillarHeight = 19.dp.toPx(),
                            flicker = candleFlicker2,
                            swayPx = -flameSwayPx * 0.7f
                        )
                        drawShelfWaxCandle(
                            baseCenter = Offset(size.width * 0.80f, plankTop),
                            pillarHeight = 22.dp.toPx(),
                            flicker = candleFlicker3,
                            swayPx = flameSwayPx * 0.8f
                        )
                        drawShelfWaxCandle(
                            baseCenter = Offset(size.width * 0.89f, plankTop),
                            pillarHeight = 32.dp.toPx(),
                            flicker = candleFlicker1,
                            swayPx = -flameSwayPx
                        )
                    }
                }
            }
        }
    }
}

/**
 * Draws a melted wax candle in a brass saucer on the shelf plank with a flickering flame and warm halo.
 */
private fun DrawScope.drawShelfWaxCandle(
    baseCenter: Offset,
    pillarHeight: Float,
    flicker: Float,
    swayPx: Float
) {
    val saucerW = 18.dp.toPx()
    val saucerH = 4.dp.toPx()
    val pillarW = 10.dp.toPx()
    val candleTopY = baseCenter.y - saucerH - pillarHeight
    val flameCenter = Offset(baseCenter.x + swayPx, candleTopY - 8.dp.toPx())

    // 1. Warm candlelight halo cast onto nearby books
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0x66FFD57E).copy(alpha = 0.42f * flicker),
                Color(0x33FFA726).copy(alpha = 0.18f * flicker),
                Color.Transparent
            ),
            center = flameCenter,
            radius = 95.dp.toPx() * flicker
        ),
        radius = 95.dp.toPx() * flicker,
        center = flameCenter,
        blendMode = BlendMode.Plus
    )

    // 2. Brass candle saucer
    drawRoundRect(
        color = Color(0xFFA68246),
        topLeft = Offset(baseCenter.x - saucerW / 2f, baseCenter.y - saucerH),
        size = Size(saucerW, saucerH),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
    )

    // 3. Ivory wax pillar body
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFC8B696),
                Color(0xFFF2E6CE),
                Color(0xFFB5A282)
            ),
            startX = baseCenter.x - pillarW / 2f,
            endX = baseCenter.x + pillarW / 2f
        ),
        topLeft = Offset(baseCenter.x - pillarW / 2f, candleTopY),
        size = Size(pillarW, pillarHeight),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
    )

    // 4. Charred wick
    drawLine(
        color = Color(0xFF1F1710),
        start = Offset(baseCenter.x, candleTopY),
        end = Offset(baseCenter.x + swayPx * 0.3f, candleTopY - 4.dp.toPx()),
        strokeWidth = 1.5.dp.toPx()
    )

    // 5. Outer amber flame + inner white-gold core
    drawOval(
        color = Color(0xFFFFB340).copy(alpha = 0.92f * flicker),
        topLeft = Offset(flameCenter.x - 4.dp.toPx(), flameCenter.y - 6.dp.toPx()),
        size = Size(8.dp.toPx(), 12.dp.toPx())
    )
    drawOval(
        color = Color(0xFFFFF6D6),
        topLeft = Offset(flameCenter.x - 2.dp.toPx(), flameCenter.y - 3.dp.toPx()),
        size = Size(4.dp.toPx(), 7.dp.toPx())
    )
}

@Composable
private fun CarouselBookCard(
    document: MarkdownDocument,
    isTwilight: Boolean,
    candleFlicker: Float,
    isActiveOnPlaten: Boolean,
    isCenteredInCarousel: Boolean,
    pageOffset: Float,
    absOffset: Float,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    val cleanTitle = remember(document.name) {
        document.name.removeSuffix(".md").removeSuffix(".MD").ifBlank { "Untitled" }
    }
    val rawCoverColor = remember(cleanTitle) {
        val hash = abs(cleanTitle.hashCode())
        BOOK_COVER_PALETTE[hash % BOOK_COVER_PALETTE.size]
    }

    // In Twilight mode, side books fall into deeper shadow while the center book catches warm candlelight
    val twilightLightLevel = if (isTwilight) {
        (lerp(0.95f, 0.38f, (absOffset / 1.3f).coerceIn(0f, 1f)) * candleFlicker).coerceIn(0.32f, 1f)
    } else 1f

    val coverColor = Color(
        red = rawCoverColor.red * twilightLightLevel,
        green = rawCoverColor.green * twilightLightLevel,
        blue = rawCoverColor.blue * twilightLightLevel,
        alpha = 1f
    )

    val spineWidthDp = remember(document.pageCount) {
        (26f * document.thicknessMultiplier).coerceIn(26f, 68f).dp
    }
    val pageStackDepthDp = remember(document.pageCount) {
        (6f * document.thicknessMultiplier).coerceIn(6f, 18f).dp
    }

    val fivePageTier = (document.pageCount / 5)
    val volumeTierLabel = when {
        fivePageTier == 0 -> "SLIM MANUSCRIPT"
        fivePageTier == 1 -> "BOUND VOLUME (5+ PGS)"
        fivePageTier == 2 -> "THICK VOLUME (10+ PGS)"
        fivePageTier == 3 -> "HEAVY TOME (15+ PGS)"
        else -> "ARCHIVAL OMNIBUS (${fivePageTier * 5}+ PGS)"
    }

    val spineTitlePaint = remember(density) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFFF5E6C4).toArgb()
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.MONOSPACE,
                android.graphics.Typeface.BOLD
            )
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    Box(
        modifier = Modifier
            .width(220.dp)
            .height(215.dp)
            .graphicsLayer {
                val scale = lerp(1f, 0.84f, (absOffset / 1.5f).coerceIn(0f, 1f))
                scaleX = scale
                scaleY = scale
                rotationY = (pageOffset * -24f).coerceIn(-38f, 38f)
                translationY = lerp(0f, 14.dp.toPx(), (absOffset / 1.5f).coerceIn(0f, 1f))
                alpha = lerp(1f, 0.55f, (absOffset / 1.8f).coerceIn(0f, 1f))
                cameraDistance = 14f * density.density
            }
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .shadow(if (isCenteredInCarousel) 16.dp else 6.dp, RoundedCornerShape(6.dp))
        ) {
            // 1. 3D Left Book Spine
            Canvas(
                modifier = Modifier
                    .width(spineWidthDp)
                    .fillMaxHeight()
            ) {
                val spineDark = Color(
                    red = coverColor.red * 0.58f,
                    green = coverColor.green * 0.58f,
                    blue = coverColor.blue * 0.58f,
                    alpha = 1f
                )
                val spineHighlight = Color(
                    red = (coverColor.red * 1.18f).coerceAtMost(1f),
                    green = (coverColor.green * 1.18f).coerceAtMost(1f),
                    blue = (coverColor.blue * 1.18f).coerceAtMost(1f),
                    alpha = 1f
                )

                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(spineDark, spineHighlight, coverColor, spineDark)
                    ),
                    size = size,
                    cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                )

                val goldColor = Color(0xFFD4AF37).copy(alpha = 0.8f * twilightLightLevel)
                val ribCount = (1 + (document.pageCount / 5)).coerceIn(1, 4)
                for (r in 0 until ribCount) {
                    val topY = 12.dp.toPx() + (r * 5.dp.toPx())
                    val botY = size.height - 12.dp.toPx() - (r * 5.dp.toPx())
                    drawLine(
                        color = goldColor,
                        start = Offset(3f, topY),
                        end = Offset(size.width - 3f, topY),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = goldColor,
                        start = Offset(3f, botY),
                        end = Offset(size.width - 3f, botY),
                        strokeWidth = 2f
                    )
                }

                spineTitlePaint.textSize = (size.width * 0.36f).coerceIn(10.dp.toPx(), 14.dp.toPx())
                spineTitlePaint.alpha = (255 * twilightLightLevel).toInt().coerceIn(90, 255)
                val maxLenPx = (size.height - 48.dp.toPx()).coerceAtLeast(30f)
                var shortSpineTitle = cleanTitle.uppercase()
                while (shortSpineTitle.length > 3 && spineTitlePaint.measureText(shortSpineTitle) > maxLenPx) {
                    shortSpineTitle = shortSpineTitle.dropLast(2) + "…"
                }

                val cx = size.width / 2f
                val cy = size.height / 2f
                drawContext.canvas.nativeCanvas.apply {
                    save()
                    rotate(-90f, cx, cy)
                    val fm = spineTitlePaint.fontMetrics
                    val baseline = -(fm.ascent + fm.descent) / 2f
                    drawText(shortSpineTitle, cx, cy + baseline, spineTitlePaint)
                    restore()
                }
            }

            // 2. Front Hardcover Board
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                coverColor,
                                Color(
                                    red = coverColor.red * 0.78f,
                                    green = coverColor.green * 0.78f,
                                    blue = coverColor.blue * 0.78f,
                                    alpha = 1f
                                )
                            )
                        )
                    )
                    .border(
                        width = if (isActiveOnPlaten) 2.dp else 1.dp,
                        color = if (isActiveOnPlaten) Color(0xFFFFD57E) else Color(0xFFD4AF37).copy(alpha = 0.45f * twilightLightLevel)
                    )
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isActiveOnPlaten) "★ ON PLATEN" else "VOL. #${document.pageCount}P",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp,
                            color = if (isActiveOnPlaten) Color(0xFFFFE082) else Color(0xFFD9C398).copy(alpha = twilightLightLevel)
                        )
                    }

                    Surface(
                        color = Color(0xFFF6EFE0).copy(alpha = twilightLightLevel.coerceAtLeast(0.65f)),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFB5945B), RoundedCornerShape(4.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = cleanTitle,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                lineHeight = 16.sp,
                                color = Color(0xFF1F1E1B),
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${document.pageCount} ${if (document.pageCount == 1) "PAGE" else "PAGES"} • ${document.wordCount} WORDS",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = Color(0xFF8C2D24)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = volumeTierLabel,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp,
                            color = Color(0xFFE6C891).copy(alpha = twilightLightLevel)
                        )
                        Text(
                            text = "TAP TO OPEN NOTE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            color = Color(0xFFF5E6C4).copy(alpha = 0.75f * twilightLightLevel)
                        )
                    }
                }
            }

            // 3. 3D Stacked Cream Paper Edge on the Right
            Canvas(
                modifier = Modifier
                    .width(pageStackDepthDp)
                    .fillMaxHeight()
                    .padding(vertical = 3.dp)
            ) {
                drawRect(
                    color = Color(0xFFEAE0C8).copy(alpha = twilightLightLevel),
                    size = size
                )
                val visibleRulings = (2 + (document.pageCount / 5) * 2).coerceIn(2, 10)
                val stepX = size.width / (visibleRulings + 1)
                for (i in 1..visibleRulings) {
                    drawLine(
                        color = Color(0xFF8C7B65).copy(alpha = 0.55f * twilightLightLevel),
                        start = Offset(i * stepX, 0f),
                        end = Offset(i * stepX, size.height),
                        strokeWidth = 1f
                    )
                }
            }
        }
    }
}
