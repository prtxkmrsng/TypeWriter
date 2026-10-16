package com.prtxkmrsng.typewriter.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.sin
import kotlin.random.Random

/**
 * Renders the Typewriter Ribbon, Card Guide, Platen Roller Bar, and striking Typehead
 * anchored at [typeheadCenter] (x = 50% width, y = 67% height, i.e., 1/3 from bottom).
 */
@Composable
fun TypewriterHardwareOverlay(
    typeheadCenter: Offset,
    theme: TypewriterThemeMode,
    isRedRibbon: Boolean,
    strikeTriggerId: Long,
    modifier: Modifier = Modifier
) {
    // 0f = resting, 1f = striking the paper at the typehead
    val strikeProgress = remember { Animatable(0f) }

    LaunchedEffect(strikeTriggerId) {
        if (strikeTriggerId > 0L) {
            strikeProgress.snapTo(0f)
            strikeProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 28, easing = FastOutLinearInEasing)
            )
            strikeProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 55, easing = LinearOutSlowInEasing)
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val cx = typeheadCenter.x
        val cy = typeheadCenter.y
        val liftOffset = strikeProgress.value * 14f

        // 1. Horizontal Paper Bail / Platen Pressure Bar with Inch Scale
        val bailY = cy - 54f
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
        // Ruler tick marks along the bail bar
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

        // 2. Ink Ribbon stretching from left spool to right spool behind the typehead
        val ribbonBaseY = cy + 20f - liftOffset
        val ribbonPathBlack = Path().apply {
            moveTo(0f, cy + 95f)
            quadraticBezierTo(cx - 260f, ribbonBaseY + 12f, cx - 28f, ribbonBaseY)
            lineTo(cx + 28f, ribbonBaseY)
            quadraticBezierTo(cx + 260f, ribbonBaseY + 12f, size.width, cy + 95f)
        }
        drawPath(
            path = ribbonPathBlack,
            color = Color(0xFF19191D),
            style = Stroke(width = 18f, cap = StrokeCap.Butt)
        )

        val ribbonPathRed = Path().apply {
            moveTo(0f, cy + 109f)
            quadraticBezierTo(cx - 260f, ribbonBaseY + 26f, cx - 28f, ribbonBaseY + 14f)
            lineTo(cx + 28f, ribbonBaseY + 14f)
            quadraticBezierTo(cx + 260f, ribbonBaseY + 26f, size.width, cy + 109f)
        }
        drawPath(
            path = ribbonPathRed,
            color = theme.accentRibbonRed,
            style = Stroke(width = 12f, cap = StrokeCap.Butt)
        )

        // 3. Metallic Type Guide & Ribbon Vibrator Forks around the active character
        // Left fork
        drawRoundRect(
            color = theme.hardwareChrome,
            topLeft = Offset(cx - 32f, cy - 18f - liftOffset * 0.4f),
            size = Size(10f, 56f),
            cornerRadius = CornerRadius(3f, 3f)
        )
        // Right fork
        drawRoundRect(
            color = theme.hardwareChrome,
            topLeft = Offset(cx + 22f, cy - 18f - liftOffset * 0.4f),
            size = Size(10f, 56f),
            cornerRadius = CornerRadius(3f, 3f)
        )
        // Red V-notch alignment indicator right below the active strike point
        drawLine(
            color = if (isRedRibbon) theme.accentRibbonRed else theme.hardwareChrome,
            start = Offset(cx - 10f, cy + 16f),
            end = Offset(cx, cy + 6f),
            strokeWidth = 2.5f
        )
        drawLine(
            color = if (isRedRibbon) theme.accentRibbonRed else theme.hardwareChrome,
            start = Offset(cx + 10f, cy + 16f),
            end = Offset(cx, cy + 6f),
            strokeWidth = 2.5f
        )

        // 4. Lower Typewriter Body / Typebasket Housing (below the typehead)
        val bodyTopY = cy + 88f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    theme.hardwareBody.copy(alpha = 0.96f),
                    Color(0xFF090A0C)
                ),
                startY = bodyTopY,
                endY = size.height
            ),
            topLeft = Offset(0f, bodyTopY),
            size = Size(size.width, size.height - bodyTopY)
        )

        // Chrome trim along the top edge of the lower housing
        drawLine(
            color = theme.hardwareChrome.copy(alpha = 0.7f),
            start = Offset(0f, bodyTopY),
            end = Offset(size.width, bodyTopY),
            strokeWidth = 4f
        )

        // Semi-circular Typebar Basket Comb
        drawArc(
            color = Color(0xFF111215),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(cx - 240f, cy + 25f),
            size = Size(480f, 220f)
        )
        drawArc(
            color = theme.hardwareChrome.copy(alpha = 0.5f),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx - 240f, cy + 25f),
            size = Size(480f, 220f),
            style = Stroke(width = 4f)
        )

        // Fan of resting typebars in the basket
        for (angleIndex in -9..9) {
            val fanX = cx + angleIndex * 22f
            drawLine(
                color = theme.hardwareChrome.copy(alpha = 0.28f),
                start = Offset(fanX, cy + 135f),
                end = Offset(cx + angleIndex * 6f, cy + 60f),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }

        // 5. Active Striking Typebar Hammer
        val hammerStartY = cy + 130f
        val hammerTipY = (cy + 58f) - (strikeProgress.value * 62f)
        drawLine(
            color = theme.hardwareChrome,
            start = Offset(cx, hammerStartY),
            end = Offset(cx, hammerTipY),
            strokeWidth = 7f,
            cap = StrokeCap.Round
        )
        // Type slug head
        drawRoundRect(
            color = theme.hardwareChrome,
            topLeft = Offset(cx - 9f, hammerTipY - 14f),
            size = Size(18f, 20f),
            cornerRadius = CornerRadius(4f, 4f)
        )

        // 6. If in Twilight Mode, draw the Brass Clip-On Lamp Fixture mounted at the typehead
        if (theme.isTwilight) {
            drawBrassTorchFixture(cx, cy + 52f)
        }
    }
}

private fun DrawScope.drawBrassTorchFixture(cx: Float, lampY: Float) {
    // Brass hood & bulb mount pointing upwards toward the paper
    drawRoundRect(
        color = Color(0xFF8C6D3B),
        topLeft = Offset(cx - 26f, lampY),
        size = Size(52f, 24f),
        cornerRadius = CornerRadius(10f, 10f)
    )
    // Glowing warm filament slit at the top of the brass hood
    drawRoundRect(
        color = Color(0xFFFFE49E),
        topLeft = Offset(cx - 18f, lampY - 4f),
        size = Size(36f, 8f),
        cornerRadius = CornerRadius(4f, 4f)
    )
}

/**
 * Twilight Mode Torchlight Overlay:
 * Covers the screen in darkness and carves out a flickering, warm tungsten torch cone
 * originating from the typehead and illuminating the written words above it.
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
                // Gentle organic candle/tungsten filament wave
                val slowWave = (sin(t * 2.7) * 0.03 + sin(t * 6.3) * 0.02).toFloat()

                // Occasional realistic voltage dip / flicker every few seconds
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
            // Offscreen compositing allows DstOut/Multiply blending to carve light out of darkness
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        val origin = Offset(typeheadCenter.x, typeheadCenter.y + 48f)

        // 1. Pitch-black ambient room layer
        drawRect(
            color = Color(0xF6030305),
            size = size
        )

        // 2. Carve out the upward conical torch beam + radial halo around the typehead
        val conePath = Path().apply {
            moveTo(origin.x - 24f, origin.y)
            lineTo(origin.x + 24f, origin.y)
            lineTo(origin.x + size.width * 0.46f + beamSwayX, 0f)
            lineTo(origin.x - size.width * 0.46f + beamSwayX, 0f)
            close()
        }

        val maxRadius = (typeheadCenter.y * 1.18f).coerceAtLeast(500f) * (0.92f + flickerIntensity * 0.08f)

        // Soft conical illumination extending upward from the typehead
        drawPath(
            path = conePath,
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.96f * flickerIntensity),
                    Color.Black.copy(alpha = 0.78f * flickerIntensity),
                    Color.Black.copy(alpha = 0.35f * flickerIntensity),
                    Color.Transparent
                ),
                center = Offset(typeheadCenter.x + beamSwayX * 0.4f, typeheadCenter.y - 80f),
                radius = maxRadius
            ),
            blendMode = BlendMode.DstOut
        )

        // Secondary tight hotspot right on the active typing head & current line
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.98f * flickerIntensity),
                    Color.Black.copy(alpha = 0.65f * flickerIntensity),
                    Color.Transparent
                ),
                center = Offset(typeheadCenter.x, typeheadCenter.y - 20f),
                radius = 340f * flickerIntensity
            ),
            radius = 340f * flickerIntensity,
            center = Offset(typeheadCenter.x, typeheadCenter.y - 20f),
            blendMode = BlendMode.DstOut
        )

        // 3. Add a warm amber/tungsten glow tint over the illuminated area
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
