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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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

    // Measure exact monospace character width & line height
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
    val paperTopPaddingPx = with(density) { 72.dp.toPx() }
    val paperWidthPx = (state.maxCharsPerLine * charWidthPx) + (paperHorizontalPaddingPx * 2f)

    // Typehead is fixed in the horizontal center and 1/3rd from the bottom (y = 2/3 of screen height)
    val typeheadCenter = remember(viewportSize) {
        Offset(
            x = viewportSize.width / 2f,
            y = viewportSize.height * (2f / 3f)
        )
    }

    // Calculate paper carriage translation so (cursorCol, cursorLine) sits right at typeheadCenter
    val targetPaperLeftX = typeheadCenter.x - paperHorizontalPaddingPx - (state.cursorCol * charWidthPx)
    val targetPaperTopY = typeheadCenter.y - paperTopPaddingPx - (state.cursorLine * lineHeightPx) - (lineHeightPx * 0.78f)

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

    LaunchedEffect(Unit, isDrawerOpen, showNewDocDialog) {
        if (!isDrawerOpen && !showNewDocDialog) {
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
            // Allow manual platen rolling by dragging vertically on the tablet screen
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
        // 1. THE MOVING PAPER SHEET ON THE CARRIAGE
        val paperMinHeightPx = ((state.lines.size + 12) * lineHeightPx) + paperTopPaddingPx * 2f
        val paperWidthDp = with(density) { paperWidthPx.toDp() }
        val paperHeightDp = with(density) { paperMinHeightPx.toDp() }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = animatedPaperX.roundToInt(),
                        y = animatedPaperY.roundToInt()
                    )
                }
                .size(width = paperWidthDp, height = paperHeightDp)
        ) {
            // Paper drop shadow + ruled margin lines + perforated page edges
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Drop shadow on desk
                drawRect(
                    color = theme.paperShadow,
                    topLeft = Offset(14f, 18f),
                    size = size
                )
                // Main paper sheet
                drawRect(
                    color = theme.paperBackground,
                    size = size
                )
                // Left red margin line
                drawLine(
                    color = theme.marginLineColor,
                    start = Offset(paperHorizontalPaddingPx - 14f, 0f),
                    end = Offset(paperHorizontalPaddingPx - 14f, size.height),
                    strokeWidth = 2f
                )
                // Right warning margin line (where the bell dings)
                val bellLineX = paperHorizontalPaddingPx + (state.bellWarningCol * charWidthPx)
                drawLine(
                    color = theme.marginLineColor.copy(alpha = 0.14f),
                    start = Offset(bellLineX, 0f),
                    end = Offset(bellLineX, size.height),
                    strokeWidth = 1.5f
                )
            }

            // Typed Markdown lines rendered on the moving sheet
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = with(density) { paperHorizontalPaddingPx.toDp() },
                        top = with(density) { paperTopPaddingPx.toDp() },
                        end = with(density) { paperHorizontalPaddingPx.toDp() }
                    )
            ) {
                val activeInk = if (state.isRedRibbon) theme.accentRibbonRed else theme.inkColor
                state.lines.forEachIndexed { index, lineText ->
                    val isHeading = lineText.trimStart().startsWith("#")
                    Text(
                        text = lineText,
                        style = TypewriterTypography.bodyMonospace.copy(
                            color = if (isHeading) theme.accentRibbonRed else activeInk,
                            fontWeight = if (isHeading) FontWeight.Bold else FontWeight.Medium
                        ),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.height(with(density) { lineHeightPx.toDp() })
                    )
                }
            }
        }

        // 2. FIXED TYPEHEAD, RIBBON & LOWER TYPEBASKET (1/3rd from bottom)
        TypewriterHardwareOverlay(
            typeheadCenter = typeheadCenter,
            theme = theme,
            isRedRibbon = state.isRedRibbon,
            strikeTriggerId = state.strikeTriggerId
        )

        // 3. TWILIGHT MODE FLICKERING TORCH OVERLAY
        if (theme.isTwilight) {
            TwilightTorchOverlay(typeheadCenter = typeheadCenter)
        }

        // 4. TOP MINIMALIST HUD BAR
        TopTypewriterHud(
            state = state,
            onToggleDrawer = { isDrawerOpen = !isDrawerOpen },
            onNewDocument = { showNewDocDialog = true },
            onToggleRibbon = viewModel::toggleRibbonColor,
            onToggleMute = viewModel::toggleMute,
            onSelectTheme = viewModel::setTheme,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // 5. SLIDE-OUT MANUSCRIPT DRAWER
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
    onToggleDrawer: () -> Unit,
    onNewDocument: () -> Unit,
    onToggleRibbon: () -> Unit,
    onToggleMute: () -> Unit,
    onSelectTheme: (TypewriterThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = state.themeMode
    var showThemeMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        color = theme.hudBackground,
        contentColor = theme.hudText,
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Drawer + Document Name + Autosave badge
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
                            SaveStatus.SAVED -> "AUTOSAVED • ${state.wordCount} WORDS • COL ${state.cursorCol}/${state.maxCharsPerLine}"
                            SaveStatus.SAVING -> "SAVING TO DISK..."
                            SaveStatus.UNSAVED -> "TYPING..."
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = theme.hudText.copy(alpha = 0.72f)
                    )
                }
            }

            // Right: Ribbon Selector (Black/Red), Sound Toggle, Theme & Twilight Selector, New File
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Black / Red Ribbon Selector Lever
                OutlinedButton(
                    onClick = onToggleRibbon,
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (state.isRedRibbon) theme.accentRibbonRed else Color.White)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (state.isRedRibbon) "RED INK" else "BLACK INK",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = theme.hudText
                    )
                }

                // Quick Twilight Torch toggle button
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
                            text = "TWILIGHT TORCH",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.FlashlightOn,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF8C6D3B),
                        selectedLabelColor = Color(0xFFFFF3D1),
                        labelColor = theme.hudText
                    )
                )

                // Theme Picker Dropdown
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

                // Mute / Unmute Mechanical Audio
                IconButton(onClick = onToggleMute) {
                    Icon(
                        imageVector = if (state.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = "Toggle Typewriter Sound"
                    )
                }

                // New Markdown Sheet
                IconButton(onClick = onNewDocument) {
                    Icon(Icons.Default.NoteAdd, contentDescription = "New Manuscript")
                }
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
