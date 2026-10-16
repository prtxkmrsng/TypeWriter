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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Formats a single line of Markdown for the typewriter page while strictly preserving
 * 1:1 monospace character column positions (syntax characters are dimmed rather than removed).
 */
fun formatMonospaceMarkdownLine(
    line: String,
    baseInkColor: Color,
    accentRedColor: Color
): AnnotatedString = buildAnnotatedString {
    append(line)
    val trimmed = line.trimStart()

    // 1. Headings (#, ##, ###)
    if (trimmed.startsWith("#")) {
        addStyle(
            SpanStyle(
                color = accentRedColor,
                fontWeight = FontWeight.ExtraBold
            ),
            start = 0,
            end = line.length
        )
        return@buildAnnotatedString
    }

    // 2. Blockquotes (>)
    if (trimmed.startsWith(">")) {
        addStyle(
            SpanStyle(
                color = baseInkColor.copy(alpha = 0.78f),
                fontStyle = FontStyle.Italic
            ),
            start = 0,
            end = line.length
        )
    }

    val fadedSyntax = SpanStyle(color = baseInkColor.copy(alpha = 0.35f))

    // 3. Bold (**text**)
    Regex("\\*\\*(.+?)\\*\\*").findAll(line).forEach { match ->
        val range = match.range
        addStyle(fadedSyntax, range.first, range.first + 2)
        addStyle(
            SpanStyle(fontWeight = FontWeight.ExtraBold, color = baseInkColor),
            range.first + 2,
            range.last - 1
        )
        addStyle(fadedSyntax, range.last - 1, range.last + 1)
    }

    // 4. Italic (*text* or _text_)
    Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)|_([^_]+)_").findAll(line).forEach { match ->
        val range = match.range
        addStyle(fadedSyntax, range.first, range.first + 1)
        addStyle(
            SpanStyle(fontStyle = FontStyle.Italic, color = baseInkColor),
            range.first + 1,
            range.last
        )
        addStyle(fadedSyntax, range.last, range.last + 1)
    }

    // 5. Strikethrough (~~text~~)
    Regex("~~(.+?)~~").findAll(line).forEach { match ->
        val range = match.range
        addStyle(fadedSyntax, range.first, range.first + 2)
        addStyle(
            SpanStyle(textDecoration = TextDecoration.LineThrough, color = baseInkColor.copy(alpha = 0.65f)),
            range.first + 2,
            range.last - 1
        )
        addStyle(fadedSyntax, range.last - 1, range.last + 1)
    }

    // 6. Inline Code (`code`)
    Regex("`([^`]+)`").findAll(line).forEach { match ->
        val range = match.range
        addStyle(fadedSyntax, range.first, range.first + 1)
        addStyle(
            SpanStyle(
                background = baseInkColor.copy(alpha = 0.10f),
                color = accentRedColor,
                fontWeight = FontWeight.SemiBold
            ),
            range.first + 1,
            range.last
        )
        addStyle(fadedSyntax, range.last, range.last + 1)
    }
}

/**
 * Renders the Typewriter Ribbon, Card Guide, Platen Roller Bar, striking Typehead,
 * Rotating Left/Right Ink Spools, and Interactive Mechanical Deck below the ribbon.
 */
@Composable
fun TypewriterHardwareOverlay(
    typeheadCenter: Offset,
    theme: TypewriterThemeMode,
    isRedRibbon: Boolean,
    isHardcoreMode: Boolean,
    strikeTriggerId: Long,
    ribbonSpoolAngle: Float,
    cursorLine: Int,
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

            // 2. Lower Typewriter Body / Mechanical Deck Housing (starts right below the ribbon)
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

            // Brushed chrome bevel along the top edge of the lower deck
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

            // 4. Left & Right Rotating Ribbon Spools inside recessed deck wells
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

            // 5. Metallic Type Guide & Ribbon Vibrator Forks around the active character
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
            // Red/Chrome V-notch alignment guide directly under the active character
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

            // Radial fan of resting typebars in the segment comb
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

            // 8. If in Twilight Mode, draw the Brass Clip-On Lamp Fixture mounted at the typehead
            if (theme.isTwilight) {
                drawBrassTorchFixture(cx, cy + 38f)
            }
        }

        // 9. Interactive Mechanical Deck Controls & Odometer Plate along the bottom edge
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 12.dp)
        ) {
            // Left Mechanical Control: Carriage Return Lever + Hardcore Overstrike Switch
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

            // Center Vintage Brass Nameplate & Odometer
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .border(1.5.dp, Color(0xFF9C8253), RoundedCornerShape(6.dp)),
                color = Color(0xFF1A1712),
                shape = RoundedCornerShape(6.dp),
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "L:${(cursorLine + 1).toString().padStart(3, '0')}",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFFE6C891)
                    )
                    Text(
                        text = "• ROYAL STANDARD •",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp,
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

            // Right Mechanical Control: Bichrome Ribbon Color Selector Switch
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
    // Recessed shadow well in the typewriter deck
    drawCircle(
        color = Color(0xFF070809),
        radius = radius + 8f,
        center = center
    )
    // Wound ink ribbon bulk inside the spool
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
    // Outer metallic spool rim
    drawCircle(
        color = chromeColor.copy(alpha = 0.75f),
        radius = radius,
        center = center,
        style = Stroke(width = 4.5f)
    )

    // Rotating 3-spoke vintage metal reel
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

    // Center brass thumb-nut
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

/**
 * Twilight Mode Torchlight Overlay
 */
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
