package com.prtxkmrsng.typewriter

import com.prtxkmrsng.typewriter.ui.TypewriterViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypewriterMechanicsTest {

    @Test
    fun testCountWordsFastEmpty() {
        assertEquals(0, TypewriterViewModel.countWordsFast(emptyList()))
        assertEquals(0, TypewriterViewModel.countWordsFast(listOf("")))
        assertEquals(0, TypewriterViewModel.countWordsFast(listOf("   ", "\t  ", " ")))
    }

    @Test
    fun testCountWordsFastSingleAndMultipleWords() {
        assertEquals(1, TypewriterViewModel.countWordsFast(listOf("word")))
        assertEquals(5, TypewriterViewModel.countWordsFast(listOf("The quick brown fox jumps")))
        assertEquals(5, TypewriterViewModel.countWordsFast(listOf("   The   quick \t brown   fox   jumps   ")))
    }

    @Test
    fun testCountWordsFastMultiLine() {
        val lines = listOf(
            "First line of text",
            "Second line",
            "",
            "   ",
            "Third line with more words."
        )
        // 4 + 2 + 0 + 0 + 5 = 11 words
        assertEquals(11, TypewriterViewModel.countWordsFast(lines))
    }

    @Test
    fun testParseTextToTypewriterLinesEmpty() {
        val parsed = TypewriterViewModel.parseTextToTypewriterLines("", maxCols = 68)
        assertEquals(listOf(""), parsed)
    }

    @Test
    fun testParseTextToTypewriterLinesShortLine() {
        val text = "Short line"
        val parsed = TypewriterViewModel.parseTextToTypewriterLines(text, maxCols = 68)
        assertEquals(listOf("Short line"), parsed)
    }

    @Test
    fun testParseTextToTypewriterLinesExactMaxCols() {
        val text = "A".repeat(68)
        val parsed = TypewriterViewModel.parseTextToTypewriterLines(text, maxCols = 68)
        assertEquals(listOf(text), parsed)
    }

    @Test
    fun testParseTextToTypewriterLinesWordBoundaryWrap() {
        val text = "The quick brown fox jumps over the lazy dog and runs across the green grassy meadows."
        val parsed = TypewriterViewModel.parseTextToTypewriterLines(text, maxCols = 40)
        assertTrue(parsed.size > 1)
        for (line in parsed) {
            assertTrue("Line length ${line.length} exceeds maxCols 40: '$line'", line.length <= 40)
        }
        // Ensure no empty trailing line
        assertTrue(parsed.last().isNotEmpty())
    }

    @Test
    fun testParseTextToTypewriterLinesUnbrokenLongWord() {
        val longWord = "A".repeat(150)
        val parsed = TypewriterViewModel.parseTextToTypewriterLines(longWord, maxCols = 50)
        assertEquals(3, parsed.size)
        assertEquals(50, parsed[0].length)
        assertEquals(50, parsed[1].length)
        assertEquals(50, parsed[2].length)
    }

    @Test
    fun testShiftOverstrikesOnInsert() {
        val overstrikes = mapOf(
            0 to mapOf(5 to listOf('X')),
            2 to mapOf(10 to listOf('O'))
        )
        // Insert line at index 1 -> line 0 stays at 0, line 2 becomes line 3
        val shifted = TypewriterViewModel.shiftOverstrikesOnInsert(overstrikes, insertedLineIdx = 1)
        assertEquals(mapOf(5 to listOf('X')), shifted[0])
        assertEquals(null, shifted[1])
        assertEquals(null, shifted[2])
        assertEquals(mapOf(10 to listOf('O')), shifted[3])
    }

    @Test
    fun testShiftOverstrikesOnDelete() {
        val overstrikes = mapOf(
            0 to mapOf(5 to listOf('X')),
            1 to mapOf(3 to listOf('Y')),
            2 to mapOf(10 to listOf('O'))
        )
        // Delete line 1 -> line 0 stays, line 1 is removed, line 2 becomes line 1
        val shifted = TypewriterViewModel.shiftOverstrikesOnDelete(overstrikes, deletedLineIdx = 1)
        assertEquals(mapOf(5 to listOf('X')), shifted[0])
        assertEquals(mapOf(10 to listOf('O')), shifted[1])
        assertEquals(null, shifted[2])
    }

    @Test
    fun testShiftOverstrikesOnWrap() {
        // Line 1 has overstrike at col 10 (kept) and col 25 (wrapped with splitCol 20)
        val overstrikes = mapOf(
            1 to mapOf(10 to listOf('A'), 25 to listOf('B')),
            2 to mapOf(5 to listOf('C'))
        )
        val shifted = TypewriterViewModel.shiftOverstrikesOnWrap(overstrikes, wrappedLineIdx = 1, splitCol = 20)
        // Line 1 should have col 10 -> 'A'
        assertEquals(mapOf(10 to listOf('A')), shifted[1])
        // Line 2 (the carry line) should have col (25 - 20 = 5) -> 'B'
        assertEquals(mapOf(5 to listOf('B')), shifted[2])
        // Previous line 2 should now be at line 3 -> col 5 -> 'C'
        assertEquals(mapOf(5 to listOf('C')), shifted[3])
    }

    @Test
    fun testShiftOverstrikesOnMerge() {
        // Line 0 length was 15, merging line 1 into line 0
        val overstrikes = mapOf(
            0 to mapOf(5 to listOf('A')),
            1 to mapOf(3 to listOf('B')),
            2 to mapOf(8 to listOf('C'))
        )
        val shifted = TypewriterViewModel.shiftOverstrikesOnMerge(overstrikes, mergedLineIdx = 1, prevLineLen = 15)
        // Line 0 should now have both its original at 5 and merged at 3 + 15 = 18
        assertEquals(mapOf(5 to listOf('A'), 18 to listOf('B')), shifted[0])
        // Line 1 should be gone, previous line 2 should shift to line 1
        assertEquals(mapOf(8 to listOf('C')), shifted[1])
        assertEquals(null, shifted[2])
    }

    @Test
    fun testShiftOverstrikesOnCharInsert() {
        val overstrikes = mapOf(
            0 to mapOf(2 to listOf('A'), 7 to listOf('B')),
            1 to mapOf(4 to listOf('C'))
        )
        // Insert character at line 0, col 5
        // Col 2 < 5 stays at 2. Col 7 >= 5 shifts to 8. Line 1 stays unchanged.
        val shifted = TypewriterViewModel.shiftOverstrikesOnCharInsert(overstrikes, lineIdx = 0, colIdx = 5)
        assertEquals(mapOf(2 to listOf('A'), 8 to listOf('B')), shifted[0])
        assertEquals(mapOf(4 to listOf('C')), shifted[1])

        // Insert at col 0 -> all cols on line 0 shift by 1
        val shiftedAtZero = TypewriterViewModel.shiftOverstrikesOnCharInsert(overstrikes, lineIdx = 0, colIdx = 0)
        assertEquals(mapOf(3 to listOf('A'), 8 to listOf('B')), shiftedAtZero[0])
    }

    @Test
    fun testShiftOverstrikesOnCharDelete() {
        val overstrikes = mapOf(
            0 to mapOf(2 to listOf('A'), 5 to listOf('D'), 8 to listOf('B')),
            1 to mapOf(4 to listOf('C'))
        )
        // Delete character at line 0, col 5 (the overstruck character itself)
        val shiftedDeletedChar = TypewriterViewModel.shiftOverstrikesOnCharDelete(overstrikes, lineIdx = 0, deletedColIdx = 5)
        // Col 2 stays, col 5 is dropped, col 8 shifts to 7
        assertEquals(mapOf(2 to listOf('A'), 7 to listOf('B')), shiftedDeletedChar[0])
        assertEquals(mapOf(4 to listOf('C')), shiftedDeletedChar[1])

        // Delete at col 0 -> col 2 shifts to 1, col 5 shifts to 4, col 8 shifts to 7
        val shiftedAtZero = TypewriterViewModel.shiftOverstrikesOnCharDelete(overstrikes, lineIdx = 0, deletedColIdx = 0)
        assertEquals(mapOf(1 to listOf('A'), 4 to listOf('D'), 7 to listOf('B')), shiftedAtZero[0])
    }

    @Test
    fun testShiftOverstrikesOnLineSplit() {
        val overstrikes = mapOf(
            0 to mapOf(3 to listOf('A'), 12 to listOf('B')),
            1 to mapOf(5 to listOf('C'))
        )
        // Split line 0 at col 8 (e.g. Enter pressed at col 8)
        // Col 3 < 8 stays on line 0. Col 12 >= 8 moves to line 1 with col (12 - 8 = 4).
        // Original line 1 shifts to line 2.
        val shifted = TypewriterViewModel.shiftOverstrikesOnLineSplit(overstrikes, splitLineIdx = 0, splitCol = 8)
        assertEquals(mapOf(3 to listOf('A')), shifted[0])
        assertEquals(mapOf(4 to listOf('B')), shifted[1])
        assertEquals(mapOf(5 to listOf('C')), shifted[2])
    }

    @Test
    fun testShiftOverstrikesOnWrapSpaceDiscard() {
        // Line 0 has overstrike at col 5, on the space at col 10, and on a word at col 15
        val overstrikes = mapOf(
            0 to mapOf(5 to listOf('A'), 10 to listOf('S'), 15 to listOf('B'))
        )
        // Wrapping with isSpaceWrap = true, splitCol = 11 (discardCol = 10)
        val shifted = TypewriterViewModel.shiftOverstrikesOnWrap(
            overstrikes,
            wrappedLineIdx = 0,
            splitCol = 11,
            isSpaceWrap = true
        )
        // Kept line has col 5 -> 'A'. Overstrike at col 10 (the discarded space) is dropped.
        assertEquals(mapOf(5 to listOf('A')), shifted[0])
        // Carry line has col (15 - 11 = 4) -> 'B'
        assertEquals(mapOf(4 to listOf('B')), shifted[1])
    }

    @Test
    fun testParseTextToTypewriterLinesUnicodeAndCJK() {
        val mixedText = "Typewriter 機械式タイプライター supports full-width unicode characters and emoji ✨ gracefully."
        val parsed = TypewriterViewModel.parseTextToTypewriterLines(mixedText, maxCols = 30)
        assertTrue(parsed.size > 1)
        for (line in parsed) {
            assertTrue("Line '$line' length ${line.length} exceeds 30", line.length <= 30)
        }
    }
}
