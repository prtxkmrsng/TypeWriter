package com.prtxkmrsng.typewriter.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prtxkmrsng.typewriter.data.ExportedFileResult
import com.prtxkmrsng.typewriter.data.MarkdownDocument
import kotlinx.coroutines.delay
import kotlin.math.floor

private data class OverstrikeCacheKey(
    val lineIndex: Int,
    val strikeIndex: Int,
    val colIndex: Int,
    val char: Char,
    val inkColorBits: Long
)

private fun shareExportedManuscript(context: Context, exported: ExportedFileResult) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = exported.mimeType
        putExtra(Intent.EXTRA_STREAM, exported.uri)
        putExtra(Intent.EXTRA_SUBJECT, exported.fileName)
        putExtra(
            Intent.EXTRA_TEXT,
            "Typed on TypeWriter (@prtxkmrsng) — ${exported.fileName}"
        )
        clipData = ClipData.newRawUri(exported.fileName, exported.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(shareIntent, "Share ${exported.fileName}").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(chooser) }
}

@Composable
fun TypewriterRootScreen(viewModel: TypewriterViewModel) {
    val state by viewModel.uiState.collectAsState()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onFolderSelected(uri)
        }
    }

    if (state.folderUri == null) {
        OnboardingFolderScreen(
            onSelectFolder = { folderPickerLauncher.launch(null) }
        )
    } else {
        TypewriterWorkspace(
            state = state,
            viewModel = viewModel,
            onChangeFolder = { folderPickerLauncher.launch(null) }
        )
    }
}

@Composable
private fun OnboardingFolderScreen(onSelectFolder: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF161513)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 540.dp)
                .padding(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF6F1E3)),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = Color(0xFFB82921),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "TYPEWRITER",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    letterSpacing = 4.sp,
                    color = Color(0xFF1F1E1B)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "BY @PRTXKMRSNG",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    color = Color(0xFFB82921)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Every page you type is saved offline as a pure Markdown (.md) manuscript. Choose a dedicated folder on your tablet where your drafts will live and autosave.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = Color(0xFF3E3B35)
                )
                Spacer(modifier = Modifier.height(28.dp))
                Button(
                    onClick = onSelectFolder,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1F1E1B),
                        contentColor = Color(0xFFF6F1E3)
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "SELECT MANUSCRIPT FOLDER",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun TypewriterWorkspace(
    state: TypewriterUiState,
    viewModel: TypewriterViewModel,
    onChangeFolder: () -> Unit
) {
    val context = LocalContext.current
    val theme = state.themeMode
    val focusRequester = remember { FocusRequester() }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var isDrawerOpen by remember { mutableStateOf(false) }
    var showNewDocDialog by remember { mutableStateOf(false) }
    var docToRename by remember { mutableStateOf<MarkdownDocument?>(null) }
    var showDeveloperInfoDialog by remember { mutableStateOf(false) }

    var isActivelyTyping by remember { mutableStateOf(false) }
    LaunchedEffect(state.strikeTriggerId) {
        if (state.strikeTriggerId > 0L) {
            isActivelyTyping = true
            delay(2600L)
            isActivelyTyping = false
        }
    }
    val hudAlpha by animateFloatAsState(
        targetValue = if (isActivelyTyping && !isDrawerOpen && !state.isPreviewMode) 0.32f else 1f,
        animationSpec = tween(durationMillis = 450),
        label = "HudAlpha"
    )

    val textMeasurer = rememberTextMeasurer(cacheSize = 256)
    val density = LocalDensity.current

    // Subpixel-accurate character width measured across 100 monospace characters
    val charWidthPx = remember(density, density.density, density.fontScale) {
        val sampleCount = 100
        val layout = textMeasurer.measure(
            text = "M".repeat(sampleCount),
            style = TypewriterTypography.bodyMonospace,
            softWrap = false,
            maxLines = 1
        )
        (layout.size.width.toFloat() / sampleCount).coerceAtLeast(8f)
    }

    val lineHeightPx = with(density) { 32.dp.toPx() }
    val paperHorizontalPaddingPx = with(density) { 64.dp.toPx() }
    val textAreaWidthPx = state.maxCharsPerLine * charWidthPx
    val paperWidthPx = textAreaWidthPx + (paperHorizontalPaddingPx * 2f)

    val linesPerPage = state.linesPerPage
    val pageHeaderPx = lineHeightPx * 2.0f
    val pageFooterPx = lineHeightPx * 2.0f
    val pageContentHeightPx = linesPerPage * lineHeightPx
    val pageStridePx = pageHeaderPx + pageContentHeightPx + pageFooterPx

    fun lineYInPaperRoll(lineIndex: Int): Float {
        val pageIdx = lineIndex / linesPerPage
        val lineInPage = lineIndex % linesPerPage
        return (pageIdx * pageStridePx) + pageHeaderPx + (lineInPage * lineHeightPx)
    }

    val currentPage = (state.cursorLine / linesPerPage) + 1
    val totalPages = ((state.lines.size - 1).coerceAtLeast(0) / linesPerPage) + 1
    val lineInPage = (state.cursorLine % linesPerPage) + 1

    val typeheadCenter = remember(viewportSize) {
        Offset(
            x = viewportSize.width / 2f,
            y = viewportSize.height * 0.76f
        )
    }

    val activeInk = if (state.isRedRibbon) theme.accentRibbonRed else theme.inkColor
    val currentLineText = state.lines.getOrNull(state.cursorLine).orEmpty()

    // Exact pixel width of the characters preceding cursorCol on the active line,
    // measured with the exact same styled AnnotatedString used to draw the line.
    val activePrefixWidthPx = remember(
        state.cursorLine,
        state.cursorCol,
        currentLineText,
        activeInk,
        theme.accentRibbonRed,
        density.density,
        density.fontScale
    ) {
        val safeCol = state.cursorCol.coerceIn(0, currentLineText.length)
        if (safeCol == 0) {
            0f
        } else {
            val fullStyled = formatMonospaceMarkdownLine(
                line = currentLineText,
                lineIndex = state.cursorLine,
                baseInkColor = activeInk,
                accentRedColor = theme.accentRibbonRed
            )
            val prefixAnnotated = fullStyled.subSequence(0, safeCol)
            textMeasurer.measure(
                text = prefixAnnotated,
                style = TypewriterTypography.bodyMonospace,
                softWrap = false,
                maxLines = 1
            ).size.width.toFloat()
        }
    }

    val targetPaperLeftX = typeheadCenter.x - paperHorizontalPaddingPx - activePrefixWidthPx
    val targetPaperTopY = typeheadCenter.y - lineYInPaperRoll(state.cursorLine) - (lineHeightPx * 0.75f)

    // Instantaneous 25ms escapement step when typing characters; smooth slide on Carriage Return
    val animatedPaperX by animateFloatAsState(
        targetValue = targetPaperLeftX,
        animationSpec = if (state.cursorCol == 0) {
            tween(durationMillis = 160, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 25, easing = FastOutSlowInEasing)
        },
        label = "CarriageX"
    )

    val animatedPaperY by animateFloatAsState(
        targetValue = targetPaperTopY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "PlatenRollY"
    )

    LaunchedEffect(Unit, isDrawerOpen, showNewDocDialog, docToRename, showDeveloperInfoDialog, state.isPreviewMode) {
        if (!isDrawerOpen && !showNewDocDialog && docToRename == null && !showDeveloperInfoDialog && !state.isPreviewMode) {
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.deskBackground)
            .onSizeChanged { viewportSize = it }
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                if (event.key == Key.Escape && state.isPreviewMode) {
                    viewModel.togglePreviewMode()
                    return@onPreviewKeyEvent true
                }

                if (event.isCtrlPressed) {
                    when (event.key) {
                        Key.S -> {
                            viewModel.forceManualSave()
                            return@onPreviewKeyEvent true
                        }
                        Key.P -> {
                            viewModel.togglePreviewMode()
                            return@onPreviewKeyEvent true
                        }
                        Key.N -> {
                            showNewDocDialog = true
                            return@onPreviewKeyEvent true
                        }
                        Key.R -> {
                            viewModel.toggleRibbonColor()
                            return@onPreviewKeyEvent true
                        }
                        Key.H -> {
                            viewModel.toggleHardcoreMode()
                            return@onPreviewKeyEvent true
                        }
                        Key.T -> {
                            viewModel.setTheme(
                                if (theme.isTwilight) TypewriterThemeMode.LIGHT else TypewriterThemeMode.TWILIGHT
                            )
                            return@onPreviewKeyEvent true
                        }
                        else -> {}
                    }
                }

                when (event.key) {
                    Key.Enter, Key.NumPadEnter -> {
                        viewModel.onEnterPressed()
                        true
                    }
                    Key.Backspace, Key.Delete -> {
                        viewModel.onBackspacePressed()
                        true
                    }
                    Key.DirectionLeft -> {
                        viewModel.onArrowNavigate(0, -1)
                        true
                    }
                    Key.DirectionRight -> {
                        viewModel.onArrowNavigate(0, 1)
                        true
                    }
                    Key.DirectionUp -> {
                        viewModel.onArrowNavigate(-1, 0)
                        true
                    }
                    Key.DirectionDown -> {
                        viewModel.onArrowNavigate(1, 0)
                        true
                    }
                    Key.MoveHome -> {
                        viewModel.moveCursorToLineStart()
                        true
                    }
                    Key.MoveEnd -> {
                        viewModel.moveCursorToLineEnd()
                        true
                    }
                    Key.PageUp -> {
                        viewModel.scrollPaperLines(-15)
                        true
                    }
                    Key.PageDown -> {
                        viewModel.scrollPaperLines(15)
                        true
                    }
                    Key.Tab -> {
                        repeat(4) { viewModel.onCharacterTyped(' ') }
                        true
                    }
                    else -> {
                        val codePoint = event.utf16CodePoint
                        if (codePoint >= 32 && !Character.isISOControl(codePoint)) {
                            viewModel.onCharacterTyped(codePoint.toChar())
                            true
                        } else {
                            false
                        }
                    }
                }
            }
            .pointerInput(lineHeightPx) {
                var accumulatedDrag = 0f
                detectVerticalDragGestures { _, dragAmount ->
                    isActivelyTyping = false
                    accumulatedDrag += dragAmount
                    if (accumulatedDrag > lineHeightPx) {
                        viewModel.scrollPaperLines(-1)
                        accumulatedDrag = 0f
                    } else if (accumulatedDrag < -lineHeightPx) {
                        viewModel.scrollPaperLines(1)
                        accumulatedDrag = 0f
                    }
                }
            }
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) {
                isActivelyTyping = false
                focusRequester.requestFocus()
            }
    ) {
        // 1 & 2. UNIFIED PAPER ROLL + DIRECT CANVAS TEXT RENDERER (Zero Box/Constraint Drift)
        val perforationDash = remember {
            PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
        }
        val pageStampStyle = remember(theme.inkColor) {
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = theme.inkColor.copy(alpha = 0.42f)
            )
        }
        val pageHeaderGradientColors = remember {
            listOf(
                Color.Black.copy(alpha = 0.06f),
                Color.Transparent
            )
        }
        val overstrikeCache = remember {
            SimpleLruCache<OverstrikeCacheKey, AnnotatedString>(256)
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val pagesToDraw = (totalPages + 1).coerceAtLeast(2)
            val firstVisiblePage = floor((-animatedPaperY - 120f) / pageStridePx).toInt().coerceAtLeast(0)
            val lastVisiblePage = floor((size.height - animatedPaperY + 120f) / pageStridePx).toInt()
                .coerceAtMost(pagesToDraw - 1)

            // A. Draw Perforated Paper Sheets
            for (pageIdx in firstVisiblePage..lastVisiblePage) {
                val pageTopY = animatedPaperY + (pageIdx * pageStridePx)
                val pageBottomY = pageTopY + pageStridePx

                drawRect(
                    color = theme.paperShadow,
                    topLeft = Offset(animatedPaperX + 14f, pageTopY + 14f),
                    size = Size(paperWidthPx, pageStridePx)
                )

                drawRect(
                    color = theme.paperBackground,
                    topLeft = Offset(animatedPaperX, pageTopY),
                    size = Size(paperWidthPx, pageStridePx)
                )

                drawRect(
                    brush = Brush.verticalGradient(
                        colors = pageHeaderGradientColors,
                        startY = pageTopY,
                        endY = pageTopY + pageHeaderPx
                    ),
                    topLeft = Offset(animatedPaperX, pageTopY),
                    size = Size(paperWidthPx, pageHeaderPx)
                )

                val leftMarginX = animatedPaperX + paperHorizontalPaddingPx - 14f
                drawLine(
                    color = theme.marginLineColor,
                    start = Offset(leftMarginX, pageTopY),
                    end = Offset(leftMarginX, pageBottomY),
                    strokeWidth = 2f
                )

                val bellLineX = animatedPaperX + paperHorizontalPaddingPx + (state.bellWarningCol * charWidthPx)
                drawLine(
                    color = theme.marginLineColor.copy(alpha = 0.14f),
                    start = Offset(bellLineX, pageTopY),
                    end = Offset(bellLineX, pageBottomY),
                    strokeWidth = 1.5f
                )

                val stampLayout = textMeasurer.measure(
                    text = "PAGE ${(pageIdx + 1).toString().padStart(2, '0')} OF ${totalPages.toString().padStart(2, '0')}",
                    style = pageStampStyle
                )
                drawText(
                    textLayoutResult = stampLayout,
                    topLeft = Offset(
                        x = animatedPaperX + paperWidthPx - paperHorizontalPaddingPx - stampLayout.size.width,
                        y = pageTopY + (pageHeaderPx * 0.38f)
                    )
                )

                drawLine(
                    color = Color.Black.copy(alpha = 0.16f),
                    start = Offset(animatedPaperX, pageBottomY),
                    end = Offset(animatedPaperX + paperWidthPx, pageBottomY),
                    strokeWidth = 4f
                )
                drawLine(
                    color = theme.inkColor.copy(alpha = 0.45f),
                    start = Offset(animatedPaperX + 12f, pageBottomY),
                    end = Offset(animatedPaperX + paperWidthPx - 12f, pageBottomY),
                    strokeWidth = 2f,
                    pathEffect = perforationDash
                )
                drawCircle(
                    color = theme.deskBackground,
                    radius = 7f,
                    center = Offset(animatedPaperX, pageBottomY)
                )
                drawCircle(
                    color = theme.deskBackground,
                    radius = 7f,
                    center = Offset(animatedPaperX + paperWidthPx, pageBottomY)
                )
            }

            // B. Draw Visible Markdown Lines & Hardcore Overstrikes Directly on the Paper Canvas
            val textStartX = animatedPaperX + paperHorizontalPaddingPx
            val approxFirstPage = floor((-animatedPaperY - lineHeightPx * 3f) / pageStridePx).toInt().coerceAtLeast(0)
            val approxLastPage = floor((size.height - animatedPaperY + lineHeightPx * 3f) / pageStridePx).toInt().coerceAtLeast(0)
            val firstCandidateLine = (approxFirstPage * linesPerPage).coerceIn(0, state.lines.lastIndex)
            val lastCandidateLine = ((approxLastPage + 1) * linesPerPage - 1).coerceIn(0, state.lines.lastIndex)

            for (lineIdx in firstCandidateLine..lastCandidateLine) {
                val screenY = animatedPaperY + lineYInPaperRoll(lineIdx)
                if (screenY < -lineHeightPx * 2f || screenY > size.height + lineHeightPx * 2f) continue

                val lineText = state.lines[lineIdx]
                if (lineText.isNotEmpty()) {
                    val styledLine = formatMonospaceMarkdownLine(
                        line = lineText,
                        lineIndex = lineIdx,
                        baseInkColor = activeInk,
                        accentRedColor = theme.accentRibbonRed
                    )
                    val lineLayout = textMeasurer.measure(
                        text = styledLine,
                        style = TypewriterTypography.bodyMonospace,
                        softWrap = false,
                        maxLines = 1
                    )
                    drawText(
                        textLayoutResult = lineLayout,
                        topLeft = Offset(x = textStartX, y = screenY)
                    )
                }

                // Draw Hardcore Mode overstrike characters on this line
                val lineOverstrikes = state.overstrikesByLine[lineIdx]
                lineOverstrikes?.forEach { (colIdx, oldChars) ->
                    val cellX = textStartX + (colIdx * charWidthPx)
                    oldChars.forEachIndexed { strikeIdx, oldChar ->
                        val cacheKey = OverstrikeCacheKey(
                            lineIndex = lineIdx,
                            strikeIndex = strikeIdx,
                            colIndex = colIdx,
                            char = oldChar,
                            inkColorBits = theme.inkColor.value.toLong()
                        )
                        val overstrikeAnnotated = synchronized(overstrikeCache) {
                            overstrikeCache[cacheKey] ?: buildAnnotatedString {
                                withStyle(
                                    computeMechanicalCharStyle(
                                        lineIndex = lineIdx + strikeIdx + 1,
                                        colIndex = colIdx,
                                        char = oldChar,
                                        targetInkColor = theme.inkColor.copy(alpha = 0.78f)
                                    )
                                ) {
                                    append(oldChar)
                                }
                            }.also { overstrikeCache[cacheKey] = it }
                        }
                        val charLayout = textMeasurer.measure(
                            text = overstrikeAnnotated,
                            style = TypewriterTypography.bodyMonospace,
                            softWrap = false,
                            maxLines = 1
                        )
                        drawText(
                            textLayoutResult = charLayout,
                            topLeft = Offset(
                                x = cellX,
                                y = screenY + if (strikeIdx % 2 == 0) -1f else 1f
                            )
                        )
                    }
                }
            }
        }

        // 3. FIXED TYPEHEAD, RIBBON, ROTATING SPOOLS & MECHANICAL DECK
        TypewriterHardwareOverlay(
            typeheadCenter = typeheadCenter,
            charWidthPx = charWidthPx,
            lineHeightPx = lineHeightPx,
            theme = theme,
            isRedRibbon = state.isRedRibbon,
            isHardcoreMode = state.isHardcoreMode,
            strikeTriggerId = state.strikeTriggerId,
            ribbonSpoolAngle = state.ribbonSpoolAngle,
            currentPage = currentPage,
            totalPages = totalPages,
            lineInPage = lineInPage,
            linesPerPage = linesPerPage,
            cursorCol = state.cursorCol,
            charCount = state.charCount,
            onToggleRibbon = viewModel::toggleRibbonColor,
            onToggleHardcore = viewModel::toggleHardcoreMode,
            onCarriageReturn = viewModel::onEnterPressed
        )

        // 4. TWILIGHT MODE FLICKERING TORCH OVERLAY
        if (theme.isTwilight && !state.isPreviewMode) {
            TwilightTorchOverlay(
                typeheadCenter = typeheadCenter,
                strikeTriggerId = state.strikeTriggerId
            )
        }

        // 5. TOP MINIMALIST HUD BAR
        TopTypewriterHud(
            state = state,
            currentPage = currentPage,
            totalPages = totalPages,
            lineInPage = lineInPage,
            onToggleDrawer = {
                isActivelyTyping = false
                isDrawerOpen = !isDrawerOpen
            },
            onNewDocument = {
                isActivelyTyping = false
                showNewDocDialog = true
            },
            onToggleHardcore = viewModel::toggleHardcoreMode,
            onTogglePreview = viewModel::togglePreviewMode,
            onToggleMute = viewModel::toggleMute,
            onSelectTheme = viewModel::setTheme,
            onShowDeveloperInfo = {
                isActivelyTyping = false
                showDeveloperInfoDialog = true
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .alpha(hudAlpha)
        )

        // 6. VIRTUALIZED READ / PREVIEW MANUSCRIPT MODAL SHEET
        AnimatedVisibility(
            visible = state.isPreviewMode,
            modifier = Modifier.fillMaxSize()
        ) {
            ManuscriptPreviewModal(
                state = state,
                onExportPdf = { viewModel.exportManuscriptAsPdf() },
                onSharePdf = {
                    viewModel.exportManuscriptAsPdf { exported ->
                        shareExportedManuscript(context, exported)
                    }
                },
                onExportHtml = { viewModel.exportManuscriptAsHtml() },
                onShareHtml = {
                    viewModel.exportManuscriptAsHtml { exported ->
                        shareExportedManuscript(context, exported)
                    }
                },
                onShareMarkdown = {
                    viewModel.shareActiveMarkdown { exported ->
                        shareExportedManuscript(context, exported)
                    }
                },
                onShareLastExported = { exported ->
                    shareExportedManuscript(context, exported)
                },
                onClose = viewModel::togglePreviewMode
            )
        }

        // 7. SLIDE-OUT MANUSCRIPT DRAWER
        AnimatedVisibility(
            visible = isDrawerOpen,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            ManuscriptSidebar(
                state = state,
                onSelectDocument = {
                    viewModel.openDocument(it)
                    isDrawerOpen = false
                },
                onRenameDocument = { doc ->
                    docToRename = doc
                },
                onDeleteDocument = { doc ->
                    viewModel.deleteDocument(doc)
                },
                onNewDocument = {
                    isDrawerOpen = false
                    showNewDocDialog = true
                },
                onChangeFolder = onChangeFolder,
                onShowDeveloperInfo = {
                    isDrawerOpen = false
                    showDeveloperInfoDialog = true
                },
                onClose = { isDrawerOpen = false }
            )
        }
    }

    if (showNewDocDialog) {
        NewManuscriptDialog(
            onDismiss = { showNewDocDialog = false },
            onCreate = { title ->
                showNewDocDialog = false
                viewModel.createNewDocument(title)
            }
        )
    }

    docToRename?.let { targetDoc ->
        RenameManuscriptDialog(
            initialName = targetDoc.name.removeSuffix(".md"),
            onDismiss = { docToRename = null },
            onRename = { newTitle ->
                docToRename = null
                viewModel.renameDocument(targetDoc, newTitle)
            }
        )
    }

    if (showDeveloperInfoDialog) {
        DeveloperInfoDialog(
            onDismiss = { showDeveloperInfoDialog = false }
        )
    }
}

@Composable
private fun TopTypewriterHud(
    state: TypewriterUiState,
    currentPage: Int,
    totalPages: Int,
    lineInPage: Int,
    onToggleDrawer: () -> Unit,
    onNewDocument: () -> Unit,
    onToggleHardcore: () -> Unit,
    onTogglePreview: () -> Unit,
    onToggleMute: () -> Unit,
    onSelectTheme: (TypewriterThemeMode) -> Unit,
    onShowDeveloperInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = state.themeMode
    var showThemeMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        color = theme.hudBackground,
        contentColor = theme.hudText,
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleDrawer) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Manuscripts")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = state.activeDocument?.name ?: "Untitled.md",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = when (state.saveStatus) {
                            SaveStatus.SAVED -> "PAGE $currentPage/$totalPages (LINE $lineInPage/${state.linesPerPage}) • ${state.wordCount} WORDS • COL ${state.cursorCol}/${state.maxCharsPerLine}"
                            SaveStatus.SAVING -> "SAVING TO DISK..."
                            SaveStatus.UNSAVED -> "TYPING • PAGE $currentPage/$totalPages (LINE $lineInPage/${state.linesPerPage})"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = theme.hudText.copy(alpha = 0.75f)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = state.isHardcoreMode,
                    onClick = onToggleHardcore,
                    label = {
                        Text(
                            text = if (state.isHardcoreMode) "HARDCORE: ON" else "HARDCORE: OFF",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFB85D19),
                        selectedLabelColor = Color.White,
                        labelColor = theme.hudText
                    )
                )

                FilterChip(
                    selected = state.isPreviewMode,
                    onClick = onTogglePreview,
                    label = {
                        Text(
                            text = if (state.isPreviewMode) "CLOSE PREVIEW" else "PREVIEW & EXPORT",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = theme.accentRibbonRed,
                        selectedLabelColor = Color.White,
                        labelColor = theme.hudText
                    )
                )

                FilterChip(
                    selected = theme.isTwilight,
                    onClick = {
                        if (theme.isTwilight) {
                            onSelectTheme(TypewriterThemeMode.LIGHT)
                        } else {
                            onSelectTheme(TypewriterThemeMode.TWILIGHT)
                        }
                    },
                    label = {
                        Text(
                            text = "TWILIGHT",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.FlashlightOn,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF8C6D3B),
                        selectedLabelColor = Color(0xFFFFF3D1),
                        labelColor = theme.hudText
                    )
                )

                Box {
                    IconButton(onClick = { showThemeMenu = true }) {
                        Icon(Icons.Default.Palette, contentDescription = "Themes")
                    }
                    DropdownMenu(
                        expanded = showThemeMenu,
                        onDismissRequest = { showThemeMenu = false }
                    ) {
                        TypewriterThemeMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = mode.displayName,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (mode == theme) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    onSelectTheme(mode)
                                    showThemeMenu = false
                                }
                            )
                        }
                    }
                }

                IconButton(onClick = onToggleMute) {
                    Icon(
                        imageVector = if (state.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Toggle Typewriter Sound"
                    )
                }

                IconButton(onClick = onNewDocument) {
                    Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = "New Manuscript")
                }

                IconButton(onClick = onShowDeveloperInfo) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "About Developer @prtxkmrsng"
                    )
                }
            }
        }
    }
}

@Composable
private fun ManuscriptPreviewModal(
    state: TypewriterUiState,
    onExportPdf: () -> Unit,
    onSharePdf: () -> Unit,
    onExportHtml: () -> Unit,
    onShareHtml: () -> Unit,
    onShareMarkdown: () -> Unit,
    onShareLastExported: (ExportedFileResult) -> Unit,
    onClose: () -> Unit
) {
    val theme = state.themeMode
    val pages = remember(state.lines, state.linesPerPage) {
        state.lines.chunked(state.linesPerPage).ifEmpty { listOf(listOf("")) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .padding(top = 74.dp, bottom = 20.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 920.dp)
                .fillMaxHeight()
                .padding(horizontal = 24.dp),
            color = theme.paperBackground,
            contentColor = theme.inkColor,
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp, vertical = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "MANUSCRIPT PROOF • ${state.activeDocument?.name ?: "Untitled.md"}",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = theme.inkColor
                        )
                        Text(
                            text = "${pages.size} PAGES (${state.linesPerPage} LINES/PAGE) • ${state.wordCount} WORDS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = theme.inkColor.copy(alpha = 0.6f)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onExportPdf,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.accentRibbonRed,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("EXPORT PDF", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }

                        FilledTonalIconButton(
                            onClick = onSharePdf,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = theme.accentRibbonRed.copy(alpha = 0.16f),
                                contentColor = theme.accentRibbonRed
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share PDF via Android Share Sheet",
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        OutlinedButton(
                            onClick = onExportHtml,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("EXPORT HTML", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }

                        FilledTonalIconButton(
                            onClick = onShareHtml,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = theme.inkColor.copy(alpha = 0.10f),
                                contentColor = theme.inkColor
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share HTML via Android Share Sheet",
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        OutlinedButton(
                            onClick = onShareMarkdown,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.IosShare, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(".MD", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }

                        TextButton(onClick = onClose) {
                            Text(
                                text = "CLOSE [ESC]",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = theme.inkColor
                            )
                        }
                    }
                }

                state.exportStatusMessage?.let { statusMsg ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = theme.accentRibbonRed.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = statusMsg,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = theme.accentRibbonRed
                            )
                            state.lastExportedFile?.let { exported ->
                                TextButton(
                                    onClick = { onShareLastExported(exported) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        tint = theme.accentRibbonRed,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "SHARE ${exported.fileName.uppercase()}",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp,
                                        color = theme.accentRibbonRed
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 14.dp),
                    color = theme.inkColor.copy(alpha = 0.2f)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(pages) { pageIndex, pageLines ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "SHEET ${pageIndex + 1} OF ${pages.size}",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = theme.inkColor.copy(alpha = 0.45f)
                                )
                                Text(
                                    text = "LINES ${pageIndex * state.linesPerPage + 1}–${pageIndex * state.linesPerPage + pageLines.size}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = theme.inkColor.copy(alpha = 0.45f)
                                )
                            }

                            pageLines.forEachIndexed { lineInPageIdx, rawLine ->
                                val globalLineIdx = pageIndex * state.linesPerPage + lineInPageIdx
                                val trimmed = rawLine.trim()
                                when {
                                    trimmed == "---" || trimmed == "***" -> {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 10.dp),
                                            thickness = 1.5.dp,
                                            color = theme.accentRibbonRed.copy(alpha = 0.45f)
                                        )
                                    }
                                    trimmed.startsWith("### ") -> {
                                        Text(
                                            text = parseCleanMarkdownInline(
                                                raw = trimmed.removePrefix("### "),
                                                lineIndex = globalLineIdx,
                                                inkColor = theme.accentRibbonRed,
                                                accentRed = theme.accentRibbonRed
                                            ),
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                                        )
                                    }
                                    trimmed.startsWith("## ") -> {
                                        Text(
                                            text = parseCleanMarkdownInline(
                                                raw = trimmed.removePrefix("## "),
                                                lineIndex = globalLineIdx,
                                                inkColor = theme.accentRibbonRed,
                                                accentRed = theme.accentRibbonRed
                                            ),
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 23.sp,
                                            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                                        )
                                    }
                                    trimmed.startsWith("# ") -> {
                                        Text(
                                            text = parseCleanMarkdownInline(
                                                raw = trimmed.removePrefix("# "),
                                                lineIndex = globalLineIdx,
                                                inkColor = theme.accentRibbonRed,
                                                accentRed = theme.accentRibbonRed
                                            ),
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 26.sp,
                                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                        )
                                    }
                                    trimmed.startsWith("> ") -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(3.dp)
                                                    .height(26.dp)
                                                    .background(theme.accentRibbonRed)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = parseCleanMarkdownInline(
                                                    raw = trimmed.removePrefix("> "),
                                                    lineIndex = globalLineIdx,
                                                    inkColor = theme.inkColor,
                                                    accentRed = theme.accentRibbonRed
                                                ),
                                                fontFamily = FontFamily.Monospace,
                                                fontStyle = FontStyle.Italic,
                                                fontSize = 17.sp,
                                                lineHeight = 28.sp
                                            )
                                        }
                                    }
                                    else -> {
                                        Text(
                                            text = parseCleanMarkdownInline(
                                                raw = rawLine,
                                                lineIndex = globalLineIdx,
                                                inkColor = theme.inkColor,
                                                accentRed = theme.accentRibbonRed
                                            ),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 17.sp,
                                            lineHeight = 28.sp
                                        )
                                    }
                                }
                            }

                            if (pageIndex < pages.lastIndex) {
                                Spacer(modifier = Modifier.height(20.dp))
                                HorizontalDivider(
                                    color = theme.inkColor.copy(alpha = 0.28f),
                                    thickness = 1.dp
                                )
                                Text(
                                    text = "✂ — — — — — — — PAGE PERFORATION (55 LINES) — — — — — — — ✂",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = theme.inkColor.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .padding(vertical = 6.dp)
                                )
                                HorizontalDivider(
                                    color = theme.inkColor.copy(alpha = 0.28f),
                                    thickness = 1.dp
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseCleanMarkdownInline(
    raw: String,
    lineIndex: Int,
    inkColor: Color,
    accentRed: Color
) = buildAnnotatedString {
    var i = 0
    var visualCol = 0

    fun appendWithMechanicalPressure(
        segment: String,
        targetColor: Color,
        extraStyle: SpanStyle = SpanStyle()
    ) {
        segment.forEach { ch ->
            if (ch == ' ') {
                append(' ')
            } else {
                val pressureStyle = computeMechanicalCharStyle(
                    lineIndex = lineIndex,
                    colIndex = visualCol,
                    char = ch,
                    targetInkColor = targetColor
                ).merge(extraStyle)
                withStyle(pressureStyle) {
                    append(ch)
                }
            }
            visualCol++
        }
    }

    while (i < raw.length) {
        when {
            raw.startsWith("**", i) -> {
                val end = raw.indexOf("**", i + 2)
                if (end != -1) {
                    appendWithMechanicalPressure(
                        segment = raw.substring(i + 2, end),
                        targetColor = inkColor,
                        extraStyle = SpanStyle(fontWeight = FontWeight.Bold)
                    )
                    i = end + 2
                } else {
                    appendWithMechanicalPressure(raw[i].toString(), inkColor)
                    i++
                }
            }
            raw.startsWith("~~", i) -> {
                val end = raw.indexOf("~~", i + 2)
                if (end != -1) {
                    appendWithMechanicalPressure(
                        segment = raw.substring(i + 2, end),
                        targetColor = inkColor.copy(alpha = 0.6f),
                        extraStyle = SpanStyle(textDecoration = TextDecoration.LineThrough)
                    )
                    i = end + 2
                } else {
                    appendWithMechanicalPressure(raw[i].toString(), inkColor)
                    i++
                }
            }
            raw.startsWith("*", i) -> {
                val end = raw.indexOf("*", i + 1)
                if (end != -1) {
                    appendWithMechanicalPressure(
                        segment = raw.substring(i + 1, end),
                        targetColor = inkColor,
                        extraStyle = SpanStyle(fontStyle = FontStyle.Italic)
                    )
                    i = end + 1
                } else {
                    appendWithMechanicalPressure(raw[i].toString(), inkColor)
                    i++
                }
            }
            raw.startsWith("`", i) -> {
                val end = raw.indexOf("`", i + 1)
                if (end != -1) {
                    appendWithMechanicalPressure(
                        segment = raw.substring(i + 1, end),
                        targetColor = accentRed,
                        extraStyle = SpanStyle(
                            background = inkColor.copy(alpha = 0.12f)
                        )
                    )
                    i = end + 1
                } else {
                    appendWithMechanicalPressure(raw[i].toString(), inkColor)
                    i++
                }
            }
            else -> {
                appendWithMechanicalPressure(raw[i].toString(), inkColor)
                i++
            }
        }
    }
}

@Composable
private fun DeveloperInfoDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1713),
        tonalElevation = 16.dp,
        shape = RoundedCornerShape(10.dp),
        title = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, Color(0xFFB5945B), RoundedCornerShape(6.dp))
                    .background(Color(0xFF12100D), RoundedCornerShape(6.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "• PATENTED MECHANICAL REPLICA •",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                    color = Color(0xFFB5945B)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "TYPEWRITER",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    letterSpacing = 4.sp,
                    color = Color(0xFFF5EBD6)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "CRAFTED & ENGINEERED BY @prtxkmrsng",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFFE05249)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "An offline, tactile mechanical typewriter built for Android tablets and physical keyboards. Designed to bring the focused rhythm, acoustic feedback, and permanence of ink-on-paper to Markdown writing.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = Color(0xFFE6DEC8)
                )

                Surface(
                    color = Color(0xFF24201A),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "MACHINE SPECIFICATIONS & SHORTCUTS",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color(0xFFB5945B)
                        )
                        Text(
                            text = "• Designer & Developer: @prtxkmrsng",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFFF5EBD6)
                        )
                        Text(
                            text = "• Platen Carriage: 68 Cols (Bell at Col 60, 55 Lines/Page)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFFD5CCB8)
                        )
                        Text(
                            text = "• Shortcuts: Ctrl+S Save | Ctrl+P Preview | Ctrl+N New",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFFD5CCB8)
                        )
                        Text(
                            text = "• Toggles: Ctrl+R Ribbon | Ctrl+H Hardcore | Ctrl+T Torch",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFFD5CCB8)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            runCatching { uriHandler.openUri("https://github.com/prtxkmrsng") }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color(0xFFE6C891),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "@prtxkmrsng",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFFE6C891)
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            runCatching { uriHandler.openUri("https://github.com/prtxkmrsng/typewriter") }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = Color(0xFFE6C891),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SOURCE REPO",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFFE6C891)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB5945B),
                    contentColor = Color(0xFF12100D)
                )
            ) {
                Text(
                    text = "BACK TO TYPING",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
private fun ManuscriptSidebar(
    state: TypewriterUiState,
    onSelectDocument: (MarkdownDocument) -> Unit,
    onRenameDocument: (MarkdownDocument) -> Unit,
    onDeleteDocument: (MarkdownDocument) -> Unit,
    onNewDocument: () -> Unit,
    onChangeFolder: () -> Unit,
    onShowDeveloperInfo: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(340.dp),
        color = Color(0xFF181715),
        contentColor = Color(0xFFF2ECE1),
        tonalElevation = 16.dp
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MANUSCRIPTS (.MD)",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onNewDocument,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB82921),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("LOAD FRESH SHEET", fontFamily = FontFamily.Monospace)
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(state.documents, key = { it.uri.toString() }) { doc ->
                    val isSelected = doc.uri == state.activeDocument?.uri
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectDocument(doc) },
                        color = if (isSelected) Color(0xFF2E2C28) else Color.Transparent,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = if (isSelected) Color(0xFFE6C891) else Color(0xFF8E8A80)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = doc.name,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }

                            Row {
                                IconButton(
                                    onClick = { onRenameDocument(doc) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Rename",
                                        tint = Color(0xFF9E998E),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteDocument(doc) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete",
                                        tint = Color(0xFFD06259),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF2E2C28))
            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onChangeFolder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFFB0ABA0))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CHANGE STORAGE FOLDER",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFFB0ABA0)
                )
            }

            TextButton(
                onClick = onShowDeveloperInfo,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFE6C891))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "BUILT BY @prtxkmrsng",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color(0xFFE6C891)
                )
            }
        }
    }
}

@Composable
private fun NewManuscriptDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var title by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Insert Fresh Markdown Sheet",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Manuscript Title (e.g., Chapter 1)", fontFamily = FontFamily.Monospace) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onCreate(title.ifBlank { "Untitled" }) }) {
                Text("Insert Paper", fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = FontFamily.Monospace)
            }
        }
    )
}

@Composable
private fun RenameManuscriptDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var title by remember(initialName) { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Rename Manuscript",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("New Title", fontFamily = FontFamily.Monospace) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onRename(title.ifBlank { initialName }) }) {
                Text("Rename", fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = FontFamily.Monospace)
            }
        }
    )
}
