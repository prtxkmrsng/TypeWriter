package com.prtxkmrsng.typewriter.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Computes a deterministic, organic mechanical typewriter imprint SpanStyle for a single character
 * using only the built-in Monospace font (preserving exact 1:1 horizontal column width).
 *
 * - Varies ribbon ink transfer opacity (74%..100%)
 * - Adds a subtle ink-spread shadow on high-pressure key strikes
 * - Applies a microscopic vertical typebar tolerance (BaselineShift)
 */
fun computeMechanicalCharStyle(
    lineIndex: Int,
    colIndex: Int,
    char: Char,
    targetInkColor: Color
): SpanStyle {
    // Mix coordinates + char code so each typed letter has a unique, stable mechanical strike feel
    var h = (lineIndex * 3137) xor (colIndex * 997) xor (char.code * 131)
    h = h xor (h ushr 16)
    h *= 0x45d9f3b
    h = h xor (h ushr 16)
    val positiveHash = abs(h)

    // Normalized strike pressure in 0.0f..1.0f
    val pressure = (positiveHash % 1000) / 1000f

    // Ink transfer opacity: lighter strikes look slightly worn; heavy strikes are deep & saturated
    val inkAlpha = when {
        pressure > 0.72f -> 1.0f
        pressure > 0.25f -> 0.86f + (pressure * 0.14f)
        else -> 0.73f + (pressure * 0.45f)
    }.coerceIn(0.72f, 1.0f)

    // Microscopic typebar vertical alignment tolerance (-0.020f .. +0.020f)
    val microShift = (((positiveHash / 1000) % 9) - 4) * 0.005f

    // Heavy strikes press more ink from the ribbon into the paper fibers (subtle ink bleed shadow)
    val inkBleedShadow = when {
        pressure > 0.72f -> Shadow(
            color = targetInkColor.copy(alpha = 0.40f),
            offset = Offset(0.25f, 0.25f),
            blurRadius = 1.15f
        )
        pressure > 0.42f -> Shadow(
            color = targetInkColor.copy(alpha = 0.18f),
            offset = Offset(0.15f, 0.15f),
            blurRadius = 0.55f
        )
        else -> null
    }

    return SpanStyle(
        color = targetInkColor.copy(alpha = inkAlpha),
        baselineShift = BaselineShift(microShift),
        shadow = inkBleedShadow
    )
}

/**
 * Formats a line of Markdown on the typewriter sheet with both inline Markdown styling
 * AND subtle per-character mechanical ink-pressure variation.
 */
fun formatMonospaceMarkdownLine(
    line: String,
    lineIndex: Int = 0,
    baseInkColor: Color,
    accentRedColor: Color
): AnnotatedString = buildAnnotatedString {
    append(line)
    if (line.isEmpty()) return@buildAnnotatedString

    val trimmed = line.trimStart()
    val isHeading = trimmed.startsWith("#")
    val isQuote = trimmed.startsWith(">")

    // 1. Apply per-character mechanical ink-pressure & micro-baseline variation first
    val lineBaseColor = when {
        isHeading -> accentRedColor
        isQuote -> baseInkColor.copy(alpha = 0.82f)
        else -> baseInkColor
    }

    for (colIdx in line.indices) {
        val ch = line[colIdx]
        if (ch != ' ') {
            addStyle(
                style = computeMechanicalCharStyle(
                    lineIndex = lineIndex,
                    colIndex = colIdx,
                    char = ch,
                    targetInkColor = lineBaseColor
                ),
                start = colIdx,
                end = colIdx + 1
            )
        }
    }

    // 2. Headings (#, ##, ###)
    if (isHeading) {
        addStyle(
            SpanStyle(fontWeight = FontWeight.ExtraBold),
            start = 0,
            end = line.length
        )
        return@buildAnnotatedString
    }

    // 3. Blockquotes (>)
    if (isQuote) {
        addStyle(
            SpanStyle(fontStyle = FontStyle.Italic),
            start = 0,
            end = line.length
        )
    }

    val fadedSyntax = SpanStyle(color = baseInkColor.copy(alpha = 0.34f))

    // 4. Bold (**text**)
    Regex("\\*\\*(.+?)\\*\\*").findAll(line).forEach { match ->
        val range = match.range
        addStyle(fadedSyntax, range.first, range.first + 2)
        addStyle(
            SpanStyle(fontWeight = FontWeight.ExtraBold),
            range.first + 2,
            range.last - 1
        )
        addStyle(fadedSyntax, range.last - 1, range.last + 1)
    }

    // 5. Italic (*text* or _text_)
    Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)|_([^_]+)_").findAll(line).forEach { match ->
        val range = match.range
        addStyle(fadedSyntax, range.first, range.first + 1)
        addStyle(
            SpanStyle(fontStyle = FontStyle.Italic),
            range.first + 1,
            range.last
        )
        addStyle(fadedSyntax, range.last, range.last + 1)
    }

    // 6. Strikethrough (~~text~~)
    Regex("~~(.+?)~~").findAll(line).forEach { match ->
        val range = match.range
        addStyle(fadedSyntax, range.first, range.first + 2)
        addStyle(
            SpanStyle(
                textDecoration = TextDecoration.LineThrough,
                color = baseInkColor.copy(alpha = 0.62f)
            ),
            range.first + 2,
            range.last - 1
        )
        addStyle(fadedSyntax, range.last - 1, range.last + 1)
    }

    // 7. Inline Code (`code`)
    Regex("`([^`]+)`").findAll(line).forEach { match ->
        val range = match.range
        addStyle(fadedSyntax, range.first, range.first + 1)
        for (idx in (range.first + 1) until range.last) {
            val ch = line[idx]
            addStyle(
                computeMechanicalCharStyle(lineIndex, idx, ch, accentRedColor).copy(
                    background = baseInkColor.copy(alpha = 0.10f),
                    fontWeight = FontWeight.SemiBold
                ),
                idx,
                idx + 1
            )
        }
        addStyle(fadedSyntax, range.last, range.last + 1)
    }
}

@Composable
fun TypewriterHardwareOverlay(
    typeheadCenter: Offset,
    theme: TypewriterThemeMode,
    isRedRibbon: Boolean,
    isHardcoreMode: Boolean,
    strikeTriggerId: Long,
    ribbonSpoolAngle: Float,
    currentPage: Int,
    totalPages: Int,
    lineInPage: Int,
    linesPerPage: Int,
    cursorCol: Int,
    charCount: Int,
    onToggleRibbon: () -> Unit,
    onToggleHardcore: () -> Unit,
    onCarriageReturn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strikeProgress = remember { Animatable(0f) }

    LaunchedEffect(strikeTriggerId) {
        if (strikeTriggerId > 0L) {
            strikeProgress.snapTo(0f)
            strikeProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 26, easing = FastOutLinearInEasing)
            )
            strikeProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 52, easing = LinearOutSlowInEasing)
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = typeheadCenter.x
            val cy = typeheadCenter.y
            val liftOffset = strikeProgress.value * 14f

            // 1. Horizontal Paper Bail / Platen Pressure Bar with Inch Scale
            val bailY = cy - 52f
            drawRect(
                color = theme.hardwareChrome.copy(alpha = 0.85f),
                topLeft = Offset(0f, bailY),
                size = Size(size.width, 8f)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.35f),
                topLeft = Offset(0f, bailY + 8f),
                size = Size(size.width, 4f)
            )
            val tickStep = 24f
            var tickX = cx % tickStep
            while (tickX < size.width) {
                val isMajor = (((tickX - cx) / tickStep).toInt() % 5 == 0)
                drawLine(
                    color = theme.hardwareBody,
                    start = Offset(tickX, bailY),
                    end = Offset(tickX, bailY + if (isMajor) 7f else 4f),
                    strokeWidth = if (isMajor) 2f else 1f
                )
                tickX += tickStep
            }

            // 2. Lower Typewriter Body / Mechanical Deck Housing
            val bodyTopY = cy + 62f
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        theme.hardwareBody,
                        Color(0xFF0B0C0E)
                    ),
                    startY = bodyTopY,
                    endY = size.height
                ),
                topLeft = Offset(0f, bodyTopY),
                size = Size(size.width, size.height - bodyTopY)
            )

            drawLine(
                color = theme.hardwareChrome.copy(alpha = 0.78f),
                start = Offset(0f, bodyTopY),
                end = Offset(size.width, bodyTopY),
                strokeWidth = 4f
            )

            val leftSpoolCenter = Offset(size.width * 0.16f, bodyTopY + (size.height - bodyTopY) * 0.48f)
            val rightSpoolCenter = Offset(size.width * 0.84f, bodyTopY + (size.height - bodyTopY) * 0.48f)
            val spoolRadius = ((size.height - bodyTopY) * 0.36f).coerceIn(44f, 78f)

            // 3. Bichrome Ink Ribbon feeding from Left Spool -> Typehead Vibrator -> Right Spool
            val ribbonBaseY = cy + 16f - liftOffset
            val ribbonPathBlack = Path().apply {
                moveTo(leftSpoolCenter.x, leftSpoolCenter.y - spoolRadius * 0.6f)
                quadraticBezierTo(cx - 240f, ribbonBaseY + 10f, cx - 28f, ribbonBaseY)
                lineTo(cx + 28f, ribbonBaseY)
                quadraticBezierTo(cx + 240f, ribbonBaseY + 10f, rightSpoolCenter.x, rightSpoolCenter.y - spoolRadius * 0.6f)
            }
            drawPath(
                path = ribbonPathBlack,
                color = Color(0xFF19191D),
                style = Stroke(width = 16f, cap = StrokeCap.Round)
            )

            val ribbonPathRed = Path().apply {
                moveTo(leftSpoolCenter.x, leftSpoolCenter.y - spoolRadius * 0.6f + 12f)
                quadraticBezierTo(cx - 240f, ribbonBaseY + 22f, cx - 28f, ribbonBaseY + 12f)
                lineTo(cx + 28f, ribbonBaseY + 12f)
                quadraticBezierTo(cx + 240f, ribbonBaseY + 22f, rightSpoolCenter.x, rightSpoolCenter.y - spoolRadius * 0.6f + 12f)
            }
            drawPath(
                path = ribbonPathRed,
                color = theme.accentRibbonRed,
                style = Stroke(width = 11f, cap = StrokeCap.Round)
            )

            // 4. Left & Right Rotating Ribbon Spools
            drawRibbonSpool(
                center = leftSpoolCenter,
                radius = spoolRadius,
                angle = ribbonSpoolAngle,
                chromeColor = theme.hardwareChrome,
                accentRed = theme.accentRibbonRed
            )
            drawRibbonSpool(
                center = rightSpoolCenter,
                radius = spoolRadius,
                angle = -ribbonSpoolAngle,
                chromeColor = theme.hardwareChrome,
                accentRed = theme.accentRibbonRed
            )

            // 5. Metallic Type Guide & Ribbon Vibrator Forks
            drawRoundRect(
                color = theme.hardwareChrome,
                topLeft = Offset(cx - 32f, cy - 18f - liftOffset * 0.4f),
                size = Size(10f, 54f),
                cornerRadius = CornerRadius(3f, 3f)
            )
            drawRoundRect(
                color = theme.hardwareChrome,
                topLeft = Offset(cx + 22f, cy - 18f - liftOffset * 0.4f),
                size = Size(10f, 54f),
                cornerRadius = CornerRadius(3f, 3f)
            )
            drawLine(
                color = if (isRedRibbon) theme.accentRibbonRed else theme.hardwareChrome,
                start = Offset(cx - 10f, cy + 14f),
                end = Offset(cx, cy + 4f),
                strokeWidth = 2.5f
            )
            drawLine(
                color = if (isRedRibbon) theme.accentRibbonRed else theme.hardwareChrome,
                start = Offset(cx + 10f, cy + 14f),
                end = Offset(cx, cy + 4f),
                strokeWidth = 2.5f
            )

            // 6. Center Typebasket & Radial Typebars
            val basketWidth = (size.width * 0.36f).coerceIn(320f, 540f)
            val basketHeight = (size.height - cy).coerceAtLeast(160f)
            drawArc(
                color = Color(0xFF08090B),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(cx - basketWidth / 2f, cy + 22f),
                size = Size(basketWidth, basketHeight * 1.35f)
            )
            drawArc(
                color = theme.hardwareChrome.copy(alpha = 0.55f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - basketWidth / 2f, cy + 22f),
                size = Size(basketWidth, basketHeight * 1.35f),
                style = Stroke(width = 4f)
            )

            for (angleIndex in -11..11) {
                val spreadX = cx + angleIndex * (basketWidth / 26f)
                drawLine(
                    color = theme.hardwareChrome.copy(alpha = 0.32f),
                    start = Offset(spreadX, size.height),
                    end = Offset(cx + angleIndex * 5.5f, cy + 48f),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }

            // 7. Active Striking Typebar Hammer
            val hammerStartY = size.height - 20f
            val hammerTipY = (cy + 46f) - (strikeProgress.value * 52f)
            drawLine(
                color = theme.hardwareChrome,
                start = Offset(cx, hammerStartY),
                end = Offset(cx, hammerTipY),
                strokeWidth = 7f,
                cap = StrokeCap.Round
            )
            drawRoundRect(
                color = theme.hardwareChrome,
                topLeft = Offset(cx - 9f, hammerTipY - 14f),
                size = Size(18f, 20f),
                cornerRadius = CornerRadius(4f, 4f)
            )

            if (theme.isTwilight) {
                drawBrassTorchFixture(cx, cy + 38f)
            }
        }

        // 8. Interactive Mechanical Deck Controls & Page/Line Odometer Plate
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.align(Alignment.BottomStart),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DeckMechanicalSwitch(
                    label = "CARRIAGE RETURN",
                    subLabel = "LINE FEED ↵",
                    isActive = false,
                    activeColor = theme.accentRibbonRed,
                    chromeColor = theme.hardwareChrome,
                    onClick = onCarriageReturn
                )
                DeckMechanicalSwitch(
                    label = "HARDCORE MODE",
                    subLabel = if (isHardcoreMode) "OVERSTRIKE ON" else "MODERN EDIT",
                    isActive = isHardcoreMode,
                    activeColor = Color(0xFFD9822B),
                    chromeColor = theme.hardwareChrome,
                    onClick = onToggleHardcore
                )
            }

            // Center Vintage Brass Nameplate with Page Counter & Line Odometer
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .border(1.5.dp, Color(0xFF9C8253), RoundedCornerShape(6.dp)),
                color = Color(0xFF1A1712),
                shape = RoundedCornerShape(6.dp),
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "PAGE ${currentPage.toString().padStart(2, '0')}/${totalPages.toString().padStart(2, '0')}",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFFE6C891)
                    )
                    Text(
                        text = "• L:${lineInPage.toString().padStart(2, '0')}/$linesPerPage •",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        color = Color(0xFFC5A367)
                    )
                    Text(
                        text = "C:${cursorCol.toString().padStart(2, '0')} | ${charCount.toString().padStart(5, '0')} KEYS",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFFE6C891)
                    )
                }
            }

            Row(
                modifier = Modifier.align(Alignment.BottomEnd),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DeckMechanicalSwitch(
                    label = "RIBBON SELECTOR",
                    subLabel = if (isRedRibbon) "RED LOWER INK" else "BLACK UPPER INK",
                    isActive = isRedRibbon,
                    activeColor = theme.accentRibbonRed,
                    chromeColor = theme.hardwareChrome,
                    onClick = onToggleRibbon
                )
            }
        }
    }
}

@Composable
private fun DeckMechanicalSwitch(
    label: String,
    subLabel: String,
    isActive: Boolean,
    activeColor: Color,
    chromeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .border(1.dp, chromeColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp)),
        color = Color(0xFF141619).copy(alpha = 0.9f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (isActive) activeColor else chromeColor.copy(alpha = 0.4f))
                    .border(1.dp, chromeColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = chromeColor
                )
                Text(
                    text = subLabel,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = if (isActive) activeColor else chromeColor.copy(alpha = 0.65f)
                )
            }
        }
    }
}

private fun DrawScope.drawRibbonSpool(
    center: Offset,
    radius: Float,
    angle: Float,
    chromeColor: Color,
    accentRed: Color
) {
    drawCircle(
        color = Color(0xFF070809),
        radius = radius + 8f,
        center = center
    )
    drawCircle(
        color = Color(0xFF1D1E22),
        radius = radius * 0.82f,
        center = center
    )
    drawCircle(
        color = accentRed.copy(alpha = 0.55f),
        radius = radius * 0.82f,
        center = center,
        style = Stroke(width = 4f)
    )
    drawCircle(
        color = chromeColor.copy(alpha = 0.75f),
        radius = radius,
        center = center,
        style = Stroke(width = 4.5f)
    )

    rotate(degrees = angle, pivot = center) {
        for (i in 0 until 3) {
            val rad = Math.toRadians((i * 120.0))
            val endX = center.x + (radius * 0.95f * cos(rad)).toFloat()
            val endY = center.y + (radius * 0.95f * sin(rad)).toFloat()
            drawLine(
                color = chromeColor.copy(alpha = 0.8f),
                start = center,
                end = Offset(endX, endY),
                strokeWidth = 7f,
                cap = StrokeCap.Round
            )
        }
    }

    drawCircle(
        color = Color(0xFFB5945B),
        radius = radius * 0.22f,
        center = center
    )
    drawCircle(
        color = Color(0xFF231F18),
        radius = radius * 0.08f,
        center = center
    )
}

private fun DrawScope.drawBrassTorchFixture(cx: Float, lampY: Float) {
    drawRoundRect(
        color = Color(0xFF8C6D3B),
        topLeft = Offset(cx - 26f, lampY),
        size = Size(52f, 24f),
        cornerRadius = CornerRadius(10f, 10f)
    )
    drawRoundRect(
        color = Color(0xFFFFE49E),
        topLeft = Offset(cx - 18f, lampY - 4f),
        size = Size(36f, 8f),
        cornerRadius = CornerRadius(4f, 4f)
    )
}

@Composable
fun TwilightTorchOverlay(
    typeheadCenter: Offset,
    modifier: Modifier = Modifier
) {
    var flickerIntensity by remember { mutableFloatStateOf(1f) }
    var beamSwayX by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var nextGlitchTime = 0L
        while (true) {
            withFrameMillis { frameMs ->
                val t = frameMs / 1000.0
                val slowWave = (sin(t * 2.7) * 0.03 + sin(t * 6.3) * 0.02).toFloat()

                val glitchDip = if (frameMs > nextGlitchTime) {
                    if (Random.nextFloat() < 0.035f) {
                        nextGlitchTime = frameMs + Random.nextLong(1400L, 4500L)
                        -Random.nextFloat() * 0.28f
                    } else 0f
                } else 0f

                flickerIntensity = (0.94f + slowWave + glitchDip).coerceIn(0.62f, 1.0f)
                beamSwayX = (sin(t * 1.9) * 8.0).toFloat()
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        val origin = Offset(typeheadCenter.x, typeheadCenter.y + 38f)

        drawRect(
            color = Color(0xF6030305),
            size = size
        )

        val conePath = Path().apply {
            moveTo(origin.x - 24f, origin.y)
            lineTo(origin.x + 24f, origin.y)
            lineTo(origin.x + size.width * 0.48f + beamSwayX, 0f)
            lineTo(origin.x - size.width * 0.48f + beamSwayX, 0f)
            close()
        }

        val maxRadius = (typeheadCenter.y * 1.15f).coerceAtLeast(520f) * (0.92f + flickerIntensity * 0.08f)

        drawPath(
            path = conePath,
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.96f * flickerIntensity),
                    Color.Black.copy(alpha = 0.78f * flickerIntensity),
                    Color.Black.copy(alpha = 0.35f * flickerIntensity),
                    Color.Transparent
                ),
                center = Offset(typeheadCenter.x + beamSwayX * 0.4f, typeheadCenter.y - 90f),
                radius = maxRadius
            ),
            blendMode = BlendMode.DstOut
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.98f * flickerIntensity),
                    Color.Black.copy(alpha = 0.65f * flickerIntensity),
                    Color.Transparent
                ),
                center = Offset(typeheadCenter.x, typeheadCenter.y - 20f),
                radius = 350f * flickerIntensity
            ),
            radius = 350f * flickerIntensity,
            center = Offset(typeheadCenter.x, typeheadCenter.y - 20f),
            blendMode = BlendMode.DstOut
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x38FFB74D).copy(alpha = 0.22f * flickerIntensity),
                    Color(0x1FFFA726).copy(alpha = 0.12f * flickerIntensity),
                    Color.Transparent
                ),
                center = Offset(typeheadCenter.x, typeheadCenter.y - 90f),
                radius = maxRadius * 0.85f
            ),
            radius = maxRadius * 0.85f,
            center = Offset(typeheadCenter.x, typeheadCenter.y - 90f),
            blendMode = BlendMode.Plus
        )
    }
}
