package com.prtxkmrsng.typewriter

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.prtxkmrsng.typewriter.ui.computeMechanicalCharStyle
import com.prtxkmrsng.typewriter.ui.formatMonospaceMarkdownLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TypewriterFormattingTest {

    private val baseColor = Color(0xFF2C241E)
    private val redColor = Color(0xFF8B2500)

    @Test
    fun testEmptyLineFormatting() {
        val result = formatMonospaceMarkdownLine("", 0, baseColor, redColor)
        assertEquals("", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun testPlainTextFormatting() {
        val line = "The quick brown fox"
        val result = formatMonospaceMarkdownLine(line, 0, baseColor, redColor)
        assertEquals(line, result.text)
        // Check character styles were applied for non-space characters
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun testHeadingFormatting() {
        val heading = "# Chapter One"
        val result = formatMonospaceMarkdownLine(heading, 0, baseColor, redColor)
        assertEquals(heading, result.text)
        // Headings must have ExtraBold span style
        val hasExtraBold = result.spanStyles.any { it.item.fontWeight == FontWeight.ExtraBold }
        assertTrue("Heading should include FontWeight.ExtraBold", hasExtraBold)
    }

    @Test
    fun testQuoteFormatting() {
        val quote = "> To be or not to be"
        val result = formatMonospaceMarkdownLine(quote, 0, baseColor, redColor)
        assertEquals(quote, result.text)
        val hasItalic = result.spanStyles.any { it.item.fontStyle == FontStyle.Italic }
        assertTrue("Blockquote should include FontStyle.Italic", hasItalic)
    }

    @Test
    fun testHorizontalRuleFormatting() {
        val hr = "---"
        val result = formatMonospaceMarkdownLine(hr, 0, baseColor, redColor)
        assertEquals(hr, result.text)
        val hasExtraBold = result.spanStyles.any { it.item.fontWeight == FontWeight.ExtraBold }
        assertTrue("Horizontal rule should include FontWeight.ExtraBold", hasExtraBold)
    }

    @Test
    fun testListPrefixFormatting() {
        val item = "- First bullet"
        val result = formatMonospaceMarkdownLine(item, 0, baseColor, redColor)
        assertEquals(item, result.text)
        val hasRedAccent = result.spanStyles.any { it.item.color == redColor && it.item.fontWeight == FontWeight.ExtraBold }
        assertTrue("List prefix should be styled with accent color", hasRedAccent)
    }

    @Test
    fun testBoldFormatting() {
        val text = "hello **world** test"
        val result = formatMonospaceMarkdownLine(text, 0, baseColor, redColor)
        assertEquals(text, result.text)
        val hasBoldSpan = result.spanStyles.any { it.item.fontWeight == FontWeight.ExtraBold }
        assertTrue("Should include ExtraBold for **world**", hasBoldSpan)
    }

    @Test
    fun testItalicFormatting() {
        val text = "hello *world* test"
        val result = formatMonospaceMarkdownLine(text, 0, baseColor, redColor)
        assertEquals(text, result.text)
        val hasItalicSpan = result.spanStyles.any { it.item.fontStyle == FontStyle.Italic }
        assertTrue("Should include Italic for *world*", hasItalicSpan)
    }

    @Test
    fun testStrikethroughFormatting() {
        val text = "hello ~~deleted~~ test"
        val result = formatMonospaceMarkdownLine(text, 0, baseColor, redColor)
        assertEquals(text, result.text)
        val hasStrikeSpan = result.spanStyles.any { it.item.textDecoration == TextDecoration.LineThrough }
        assertTrue("Should include LineThrough for ~~deleted~~", hasStrikeSpan)
    }

    @Test
    fun testCodeFormatting() {
        val text = "val x = `42`"
        val result = formatMonospaceMarkdownLine(text, 0, baseColor, redColor)
        assertEquals(text, result.text)
        val hasCodeWeight = result.spanStyles.any { it.item.fontWeight == FontWeight.SemiBold }
        assertTrue("Should include SemiBold for inline code", hasCodeWeight)
    }

    @Test
    fun testFormatLineCacheHit() {
        val text = "Deterministic caching line test"
        val first = formatMonospaceMarkdownLine(text, 3, baseColor, redColor)
        val second = formatMonospaceMarkdownLine(text, 3, baseColor, redColor)
        assertSame("Cached formatted line should return the exact same instance", first, second)
    }

    @Test
    fun testComputeMechanicalCharStyleDeterminismAndBounds() {
        val style1 = computeMechanicalCharStyle(lineIndex = 5, colIndex = 12, char = 'A', targetInkColor = baseColor)
        val style2 = computeMechanicalCharStyle(lineIndex = 5, colIndex = 12, char = 'A', targetInkColor = baseColor)

        assertEquals("Same coordinates and char must produce identical style", style1, style2)

        // Test alpha bounds across various inputs
        for (line in 0..10) {
            for (col in 0..20) {
                val ch = ('A'.code + (col % 26)).toChar()
                val style = computeMechanicalCharStyle(line, col, ch, baseColor)
                val alpha = style.color.alpha
                assertTrue("Ink alpha $alpha must be >= 0.74f", alpha >= 0.74f)
                assertTrue("Ink alpha $alpha must be <= 1.0f", alpha <= 1.0f)
            }
        }
    }
}
