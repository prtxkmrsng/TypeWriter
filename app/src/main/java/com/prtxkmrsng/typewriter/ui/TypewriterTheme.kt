package com.prtxkmrsng.typewriter.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class TypewriterThemeMode(
    val displayName: String,
    val deskBackground: Color,
    val paperBackground: Color,
    val paperShadow: Color,
    val inkColor: Color,
    val accentRibbonRed: Color,
    val hardwareBody: Color,
    val hardwareChrome: Color,
    val marginLineColor: Color,
    val hudBackground: Color,
    val hudText: Color,
    // Theme-specific Bookshelf colors
    val shelfOuterFrame: Color,
    val shelfBackPanel: Color,
    val shelfPlankTop: Color,
    val shelfPlankBottom: Color,
    val shelfTrimAccent: Color,
    val shelfHeaderBg: Color,
    val shelfHeaderText: Color,
    val isTwilight: Boolean = false
) {
    LIGHT(
        displayName = "Classic Ivory",
        deskBackground = Color(0xFFD6CFC2),
        paperBackground = Color(0xFFFDFBF5),
        paperShadow = Color(0x44000000),
        inkColor = Color(0xFF1F1E1B),
        accentRibbonRed = Color(0xFFB82921),
        hardwareBody = Color(0xFF232528),
        hardwareChrome = Color(0xFFC8CDD0),
        marginLineColor = Color(0x33B82921),
        hudBackground = Color(0xEB232528),
        hudText = Color(0xFFF5F2EB),
        shelfOuterFrame = Color(0xFF5A3D28),
        shelfBackPanel = Color(0xFF2D1E14),
        shelfPlankTop = Color(0xFF785338),
        shelfPlankBottom = Color(0xFF4A311F),
        shelfTrimAccent = Color(0xFFC8A971),
        shelfHeaderBg = Color(0xFF3D281A),
        shelfHeaderText = Color(0xFFFDFBF5)
    ),
    DARK(
        displayName = "Midnight Oil",
        deskBackground = Color(0xFF0E1013),
        paperBackground = Color(0xFF1A1D22),
        paperShadow = Color(0x88000000),
        inkColor = Color(0xFFE5E2DC),
        accentRibbonRed = Color(0xFFE05249),
        hardwareBody = Color(0xFF121417),
        hardwareChrome = Color(0xFF6E7681),
        marginLineColor = Color(0x33E05249),
        hudBackground = Color(0xEB181B20),
        hudText = Color(0xFFE5E2DC),
        shelfOuterFrame = Color(0xFF16191E),
        shelfBackPanel = Color(0xFF0B0D10),
        shelfPlankTop = Color(0xFF262B33),
        shelfPlankBottom = Color(0xFF13161A),
        shelfTrimAccent = Color(0xFF6E7681),
        shelfHeaderBg = Color(0xFF101216),
        shelfHeaderText = Color(0xFFE5E2DC)
    ),
    VINTAGE(
        displayName = "1940s Sepia",
        deskBackground = Color(0xFF3A2B20),
        paperBackground = Color(0xFFF2E5C9),
        paperShadow = Color(0x66000000),
        inkColor = Color(0xFF2B2118),
        accentRibbonRed = Color(0xFF9E2A22),
        hardwareBody = Color(0xFF241B14),
        hardwareChrome = Color(0xFFB89968),
        marginLineColor = Color(0x339E2A22),
        hudBackground = Color(0xEB241B14),
        hudText = Color(0xFFF2E5C9),
        shelfOuterFrame = Color(0xFF2E1C12),
        shelfBackPanel = Color(0xFF170E09),
        shelfPlankTop = Color(0xFF523320),
        shelfPlankBottom = Color(0xFF2B190F),
        shelfTrimAccent = Color(0xFFB89968),
        shelfHeaderBg = Color(0xFF1F130C),
        shelfHeaderText = Color(0xFFF2E5C9)
    ),
    OLIVETTI(
        displayName = "Olivetti Valentine",
        deskBackground = Color(0xFF22282A),
        paperBackground = Color(0xFFFAF8F2),
        paperShadow = Color(0x55000000),
        inkColor = Color(0xFF1A1A1A),
        accentRibbonRed = Color(0xFFD92B2B),
        hardwareBody = Color(0xFFC92828),
        hardwareChrome = Color(0xFFDCDCDC),
        marginLineColor = Color(0x28D92B2B),
        hudBackground = Color(0xEBC92828),
        hudText = Color(0xFFFFFFFF),
        shelfOuterFrame = Color(0xFF9E1F1F),
        shelfBackPanel = Color(0xFF1A1E20),
        shelfPlankTop = Color(0xFFC92828),
        shelfPlankBottom = Color(0xFF7A1515),
        shelfTrimAccent = Color(0xFFE6E6E6),
        shelfHeaderBg = Color(0xFF851818),
        shelfHeaderText = Color(0xFFFFFFFF)
    ),
    UNDERWOOD(
        displayName = "Underwood Envoy",
        deskBackground = Color(0xFF1E2621),
        paperBackground = Color(0xFFF8F5EC),
        paperShadow = Color(0x66000000),
        inkColor = Color(0xFF19211C),
        accentRibbonRed = Color(0xFFB52B24),
        hardwareBody = Color(0xFF1D3328),
        hardwareChrome = Color(0xFFC8B07A),
        marginLineColor = Color(0x30B52B24),
        hudBackground = Color(0xEB182B22),
        hudText = Color(0xFFF2EAD8),
        shelfOuterFrame = Color(0xFF182B22),
        shelfBackPanel = Color(0xFF0D1712),
        shelfPlankTop = Color(0xFF284738),
        shelfPlankBottom = Color(0xFF14241C),
        shelfTrimAccent = Color(0xFFC8B07A),
        shelfHeaderBg = Color(0xFF12211A),
        shelfHeaderText = Color(0xFFF2EAD8)
    ),
    TWILIGHT(
        displayName = "Twilight Torch",
        deskBackground = Color(0xFF050507),
        paperBackground = Color(0xFFF5EBD6),
        paperShadow = Color(0xCC000000),
        inkColor = Color(0xFF181410),
        accentRibbonRed = Color(0xFFA8241C),
        hardwareBody = Color(0xFF0D0D10),
        hardwareChrome = Color(0xFF8C734B),
        marginLineColor = Color(0x33A8241C),
        hudBackground = Color(0xD90D0D10),
        hudText = Color(0xFFE6C891),
        shelfOuterFrame = Color(0xFF0C0908),
        shelfBackPanel = Color(0xFF040305),
        shelfPlankTop = Color(0xFF1F1510),
        shelfPlankBottom = Color(0xFF0A0705),
        shelfTrimAccent = Color(0xFF8C6D3B),
        shelfHeaderBg = Color(0xFF090706),
        shelfHeaderText = Color(0xFFE6C891),
        isTwilight = true
    )
}

object TypewriterTypography {
    val bodyMonospace = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    )
}
