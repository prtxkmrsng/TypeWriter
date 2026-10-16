package com.prtxkmrsng.typewriter.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

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
                Spacer(modifier = Modifier.height(12.dp))
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
    val theme = state.themeMode
    val focusRequester = remember { FocusRequester() }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var isDrawerOpen by remember { mutableStateOf(false) }
    var showNewDocDialog by remember { mutableStateOf(false) }

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val charSampleLayout = remember(density) {
        textMeasurer.measure(
            text = "M",
            style = TypewriterTypography.bodyMonospace
        )
    }
    val charWidthPx = charSampleLayout.size.width.toFloat().coerceAtLeast(12f)
    val lineHeightPx = with(density) { 32.dp.toPx() }
    val paperHorizontalPaddingPx = with(density) { 64.dp.toPx() }
    val textAreaWidthPx = state.maxCharsPerLine * charWidthPx
    val paperWidthPx = textAreaWidthPx + (paperHorizontalPaddingPx * 2f)

    // Page geometry for 55-line perforated fanfold sheets:
    // Each page has a 2-line header, 55 content lines, and a 2-line footer/perforation zone.
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

    // Fixed Typehead position: centered horizontally, 76% down from top
    val typeheadCenter = remember(viewportSize) {
        Offset(
            x = viewportSize.width / 2f,
            y = viewportSize.height * 0.76f
        )
    }

    val targetPaperLeftX = typeheadCenter.x - paperHorizontalPaddingPx - (state.cursorCol * charWidthPx)
    val targetPaperTopY = typeheadCenter.y - lineYInPaperRoll(state.cursorLine) - (lineHeightPx * 0.78f)

    val animatedPaperX by animateFloatAsState(
        targetValue = targetPaperLeftX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "CarriageX"
    )

    val animatedPaperY by animateFloatAsState(
        targetValue = targetPaperTopY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "PlatenRollY"
    )

    LaunchedEffect(Unit, isDrawerOpen, showNewDocDialog, state.isPreviewMode) {
        if (!isDrawerOpen && !showNewDocDialog && !state.isPreviewMode) {
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
            .pointerInput(Unit) {
                var accumulatedDrag = 0f
                detectVerticalDragGestures { _, dragAmount ->
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
                focusRequester.requestFocus()
            }
    ) {
        // 1. UNBOUNDED CONTINUOUS FANFOLD PAPER ROLL WITH 55-LINE PERFORATIONS
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

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Render visible pages plus one extra feed page below the current page
            val pagesToDraw = (totalPages + 1).coerceAtLeast(2)

            for (pageIdx in 0 until pagesToDraw) {
                val pageTopY = animatedPaperY + (pageIdx * pageStridePx)
                val pageBottomY = pageTopY + pageStridePx

                if (pageBottomY < -120f || pageTopY > size.height + 120f) continue

                // Drop shadow on desk
                drawRect(
                    color = theme.paperShadow,
                    topLeft = Offset(animatedPaperX + 14f, pageTopY + 14f),
                    size = Size(paperWidthPx, pageStridePx)
                )

                // Main page sheet
                drawRect(
                    color = theme.paperBackground,
                    topLeft = Offset(animatedPaperX, pageTopY),
                    size = Size(paperWidthPx, pageStridePx)
                )

                // Left red margin line
                val leftMarginX = animatedPaperX + paperHorizontalPaddingPx - 14f
                drawLine(
                    color = theme.marginLineColor,
                    start = Offset(leftMarginX, pageTopY),
                    end = Offset(leftMarginX, pageBottomY),
                    strokeWidth = 2f
                )

                // Right bell warning margin line
                val bellLineX = animatedPaperX + paperHorizontalPaddingPx + (state.bellWarningCol * charWidthPx)
                drawLine(
                    color = theme.marginLineColor.copy(alpha = 0.14f),
                    start = Offset(bellLineX, pageTopY),
                    end = Offset(bellLineX, pageBottomY),
                    strokeWidth = 1.5f
                )

                // Top-right Page Stamp ("PAGE 01 / 02  •  55 LINES")
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

                // Bottom Page-Break Perforation Seam (every 55 lines)
                // Recessed crease shadow
                drawLine(
                    color = Color.Black.copy(alpha = 0.16f),
                    start = Offset(animatedPaperX, pageBottomY),
                    end = Offset(animatedPaperX + paperWidthPx, pageBottomY),
                    strokeWidth = 4f
                )
                // Dashed tear-off perforation line
                drawLine(
                    color = theme.inkColor.copy(alpha = 0.45f),
                    start = Offset(animatedPaperX + 12f, pageBottomY),
                    end = Offset(animatedPaperX + paperWidthPx - 12f, pageBottomY),
                    strokeWidth = 2f,
                    pathEffect = perforationDash
                )
                // Left & Right Fanfold Tear Notches on the paper edge
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
        }

        // 2. VIRTUALIZED UNBOUNDED LINE RENDERER (Never clips at screen height or width!)
        val activeInk = if (state.isRedRibbon) theme.accentRibbonRed else theme.inkColor
        val textAreaWidthDp = with(density) { (textAreaWidthPx + charWidthPx * 4f).toDp() }
        val lineHeightDp = with(density) { lineHeightPx.toDp() }
        val textStartScreenX = (animatedPaperX + paperHorizontalPaddingPx).roundToInt()

        Box(modifier = Modifier.fillMaxSize()) {
            state.lines.forEachIndexed { lineIdx, lineText ->
                val screenY = animatedPaperY + lineYInPaperRoll(lineIdx)

                // Only compose lines currently within the vertical viewport
                if (screenY >= -lineHeightPx * 2f && screenY <= viewportSize.height + lineHeightPx * 2f) {
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = textStartScreenX,
                                    y = screenY.roundToInt()
                                )
                            }
                            .requiredWidth(textAreaWidthDp)
                            .height(lineHeightDp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        val styledLine = remember(lineText, activeInk, theme.accentRibbonRed) {
                            formatMonospaceMarkdownLine(
                                line = lineText,
                                baseInkColor = activeInk,
                                accentRedColor = theme.accentRibbonRed
                            )
                        }
                        Text(
                            text = styledLine,
                            style = TypewriterTypography.bodyMonospace,
                            maxLines = 1,
                            softWrap = false
                        )

                        // Render physical Hardcore Mode overstruck characters on this line
                        state.overstruckCells.forEach { (cell, oldChars) ->
                            if (cell.first == lineIdx) {
                                val colOffsetDp = with(density) { (cell.second * charWidthPx).toDp() }
                                oldChars.forEachIndexed { strikeIdx, oldChar ->
                                    Text(
                                        text = oldChar.toString(),
                                        style = TypewriterTypography.bodyMonospace.copy(
                                            color = theme.inkColor.copy(alpha = 0.72f)
                                        ),
                                        modifier = Modifier.offset(
                                            x = colOffsetDp,
                                            y = if (strikeIdx % 2 == 0) (-0.5).dp else 0.5.dp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. FIXED TYPEHEAD, RIBBON, ROTATING SPOOLS & MECHANICAL DECK (at 76% height)
        TypewriterHardwareOverlay(
            typeheadCenter = typeheadCenter,
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
            TwilightTorchOverlay(typeheadCenter = typeheadCenter)
        }

        // 5. TOP MINIMALIST HUD BAR WITH PAGE COUNTER
        TopTypewriterHud(
            state = state,
            currentPage = currentPage,
            totalPages = totalPages,
            lineInPage = lineInPage,
            onToggleDrawer = { isDrawerOpen = !isDrawerOpen },
            onNewDocument = { showNewDocDialog = true },
            onToggleHardcore = viewModel::toggleHardcoreMode,
            onTogglePreview = viewModel::togglePreviewMode,
            onToggleMute = viewModel::toggleMute,
            onSelectTheme = viewModel::setTheme,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // 6. READ / PREVIEW MANUSCRIPT MODAL SHEET WITH PDF & HTML EXPORT
        AnimatedVisibility(
            visible = state.isPreviewMode,
            modifier = Modifier.fillMaxSize()
        ) {
            ManuscriptPreviewModal(
                state = state,
                onExportPdf = viewModel::exportManuscriptAsPdf,
                onExportHtml = viewModel::exportManuscriptAsHtml,
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
                onNewDocument = {
                    isDrawerOpen = false
                    showNewDocDialog = true
                },
                onChangeFolder = onChangeFolder,
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
                    Icon(Icons.Default.MenuBook, contentDescription = "Manuscripts")
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
                            SaveStatus.SAVED -> "PAGE $currentPage/$totalPages (LINE$lineInPage/${state.linesPerPage}) •${state.wordCount} WORDS • COL ${state.cursorCol}/${state.maxCharsPerLine}"
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        imageVector = if (state.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = "Toggle Typewriter Sound"
                    )
                }

                IconButton(onClick = onNewDocument) {
                    Icon(Icons.Default.NoteAdd, contentDescription = "New Manuscript")
                }
            }
        }
    }
}

@Composable
private fun ManuscriptPreviewModal(
    state: TypewriterUiState,
    onExportPdf: () -> Unit,
    onExportHtml: () -> Unit,
    onClose: () -> Unit
) {
    val theme = state.themeMode
    val scrollState = rememberScrollState()
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
                .widthIn(max = 860.dp)
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
                    .padding(horizontal = 56.dp, vertical = 36.dp)
            ) {
                // Header Bar with Export PDF / Export HTML / Close buttons
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
                            text = "${pages.size} PAGES (${state.linesPerPage} LINES/PAGE) •${state.wordCount} WORDS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = theme.inkColor.copy(alpha = 0.6f)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onExportPdf,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.accentRibbonRed,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("EXPORT PDF", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onExportHtml,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("EXPORT HTML", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }

                        TextButton(onClick = onClose) {
                            Text(
                                text = "CLOSE [ESC]",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = theme.inkColor
                            )
                        }
                    }
                }

                // Export Status Banner
                state.exportStatusMessage?.let { statusMsg ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = theme.accentRibbonRed.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusMsg,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = theme.accentRibbonRed,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 14.dp),
                    color = theme.inkColor.copy(alpha = 0.2f)
                )

                // Paginated Manuscript Content (55 lines per perforated page)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    pages.forEachIndexed { pageIndex, pageLines ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "SHEET ${pageIndex + 1} OF${pages.size}",
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

                        pageLines.forEach { rawLine ->
                            val trimmed = rawLine.trim()
                            when {
                                trimmed.startsWith("### ") -> {
                                    Text(
                                        text = trimmed.removePrefix("### "),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = theme.accentRibbonRed,
                                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                                    )
                                }
                                trimmed.startsWith("## ") -> {
                                    Text(
                                        text = trimmed.removePrefix("## "),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 23.sp,
                                        color = theme.accentRibbonRed,
                                        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                                    )
                                }
                                trimmed.startsWith("# ") -> {
                                    Text(
                                        text = trimmed.removePrefix("# "),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 26.sp,
                                        color = theme.accentRibbonRed,
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
                                            text = parseCleanMarkdownInline(trimmed.removePrefix("> "), theme.inkColor, theme.accentRibbonRed),
                                            fontFamily = FontFamily.Monospace,
                                            fontStyle = FontStyle.Italic,
                                            fontSize = 17.sp,
                                            lineHeight = 28.sp
                                        )
                                    }
                                }
                                else -> {
                                    Text(
                                        text = parseCleanMarkdownInline(rawLine, theme.inkColor, theme.accentRibbonRed),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 17.sp,
                                        lineHeight = 28.sp
                                    )
                                }
                            }
                        }

                        // Perforation divider between 55-line pages
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

private fun parseCleanMarkdownInline(
    raw: String,
    inkColor: Color,
    accentRed: Color
) = buildAnnotatedString {
    var i = 0
    while (i < raw.length) {
        when {
            raw.startsWith("**", i) -> {
                val end = raw.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = inkColor)) {
                        append(raw.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(raw[i])
                    i++
                }
            }
            raw.startsWith("~~", i) -> {
                val end = raw.indexOf("~~", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = inkColor.copy(alpha = 0.6f))) {
                        append(raw.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(raw[i])
                    i++
                }
            }
            raw.startsWith("*", i) -> {
                val end = raw.indexOf("*", i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = inkColor)) {
                        append(raw.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(raw[i])
                    i++
                }
            }
            raw.startsWith("`", i) -> {
                val end = raw.indexOf("`", i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(background = inkColor.copy(alpha = 0.12f), color = accentRed, fontWeight = FontWeight.SemiBold)) {
                        append(raw.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(raw[i])
                    i++
                }
            }
            else -> {
                append(raw[i])
                i++
            }
        }
    }
}

@Composable
private fun ManuscriptSidebar(
    state: TypewriterUiState,
    onSelectDocument: (com.prtxkmrsng.typewriter.data.MarkdownDocument) -> Unit,
    onNewDocument: () -> Unit,
    onChangeFolder: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(320.dp),
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
                items(state.documents) { doc ->
                    val isSelected = doc.uri == state.activeDocument?.uri
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectDocument(doc) },
                        color = if (isSelected) Color(0xFF2E2C28) else Color.Transparent,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = if (isSelected) Color(0xFFE6C891) else Color(0xFF8E8A80)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = doc.name,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF2E2C28))
            Spacer(modifier = Modifier.height(12.dp))

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
