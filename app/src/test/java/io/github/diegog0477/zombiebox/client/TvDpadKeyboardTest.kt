package io.github.diegog0477.zombiebox.client

import io.github.diegog0477.zombiebox.client.core.ui.TvKeyboardActions
import io.github.diegog0477.zombiebox.client.core.ui.TvKeyboardMode
import org.junit.Assert.*
import org.junit.Test

class TvDpadKeyboardTest {

    @Test
    fun insertAppendsAndUpdatesCursor() {
        val (text1, cursor1) = TvKeyboardActions.insert("", "hello", 0, 0, 100)
        assertEquals("hello", text1)
        assertEquals(5, cursor1)

        val (text2, cursor2) = TvKeyboardActions.insert(text1, " world", cursor1, cursor1, 100)
        assertEquals("hello world", text2)
        assertEquals(11, cursor2)
    }

    @Test
    fun insertRespectsMaximumQueryLength() {
        val initial = "a".repeat(95)
        val (text1, cursor1) = TvKeyboardActions.insert(initial, "12345", 95, 95, 100)
        assertEquals(100, text1.length)
        assertEquals(100, cursor1)

        // Further insertion at capacity is ignored
        val (text2, cursor2) = TvKeyboardActions.insert(text1, "extra", 100, 100, 100)
        assertEquals(100, text2.length)
        assertEquals(text1, text2)
        assertEquals(100, cursor2)

        // Partial insertion when capacity allows fewer characters than addition
        val (text3, cursor3) = TvKeyboardActions.insert("a".repeat(98), "xyz", 98, 98, 100)
        assertEquals(100, text3.length)
        assertEquals("a".repeat(98) + "xy", text3)
        assertEquals(100, cursor3)
    }

    @Test
    fun insertReplacesSelectedRange() {
        val initial = "search for apples"
        // Select "apples" (start 11, end 17) and replace with "oranges"
        val (text, cursor) = TvKeyboardActions.insert(initial, "oranges", 11, 17, 100)
        assertEquals("search for oranges", text)
        assertEquals(18, cursor)
    }

    @Test
    fun deleteRemovesPrecedingCharacterWhenNoSelection() {
        val (text1, cursor1) = TvKeyboardActions.delete("hello", 5, 5)
        assertEquals("hell", text1)
        assertEquals(4, cursor1)

        val (text2, cursor2) = TvKeyboardActions.delete("hello", 2, 2)
        assertEquals("hllo", text2)
        assertEquals(1, cursor2)
    }

    @Test
    fun deleteRemovesExactSelectionRange() {
        val (text, cursor) = TvKeyboardActions.delete("quick brown fox", 6, 11) // "brown"
        assertEquals("quick  fox", text)
        assertEquals(6, cursor)
    }

    @Test
    fun deleteOnEmptyOrZeroCursorIsSafe() {
        val (text1, cursor1) = TvKeyboardActions.delete("", 0, 0)
        assertEquals("", text1)
        assertEquals(0, cursor1)

        val (text2, cursor2) = TvKeyboardActions.delete("abc", 0, 0)
        assertEquals("abc", text2)
        assertEquals(0, cursor2)
    }

    @Test
    fun clearResetsTextAndCursor() {
        val (text, cursor) = TvKeyboardActions.clear()
        assertEquals("", text)
        assertEquals(0, cursor)
    }

    @Test
    fun keyboardModesProvideExpectedRowsAnd10KeyColumns() {
        for (mode in TvKeyboardMode.values()) {
            val rows = TvKeyboardActions.rowsFor(mode)
            assertEquals(4, rows.size)
            for (row in rows) {
                assertEquals(
                    "Each row in mode $mode must have exactly 10 keys for deterministic D-pad alignment",
                    10,
                    row.size,
                )
            }
            assertEquals(TvKeyboardActions.NUMBERS_ROW, rows[0])
        }

        // Verify lowercase has 'q' and 'a' in expected positions
        val lowerRows = TvKeyboardActions.rowsFor(TvKeyboardMode.LOWERCASE)
        assertEquals("q", lowerRows[1][0])
        assertEquals("p", lowerRows[1][9])
        assertEquals("a", lowerRows[2][0])
        assertEquals("z", lowerRows[3][0])

        // Verify uppercase has 'Q' and 'A'
        val upperRows = TvKeyboardActions.rowsFor(TvKeyboardMode.UPPERCASE)
        assertEquals("Q", upperRows[1][0])
        assertEquals("P", upperRows[1][9])
        assertEquals("A", upperRows[2][0])
        assertEquals("Z", upperRows[3][0])

        // Verify symbols row 1 has punctuation
        val symbolRows = TvKeyboardActions.rowsFor(TvKeyboardMode.SYMBOLS)
        assertEquals("!", symbolRows[1][0])
        assertEquals(")", symbolRows[1][9])
    }

    @Test
    fun deterministicDpadTraversalMappings() {
        // Mode tab to number key column mapping
        assertEquals(1, TvKeyboardActions.tabToKeyCol(0)) // abc -> 2
        assertEquals(4, TvKeyboardActions.tabToKeyCol(1)) // ABC -> 5
        assertEquals(8, TvKeyboardActions.tabToKeyCol(2)) // ?123 -> 9

        // Key column to mode tab mapping
        assertEquals(0, TvKeyboardActions.keyColToTab(0))
        assertEquals(0, TvKeyboardActions.keyColToTab(2))
        assertEquals(1, TvKeyboardActions.keyColToTab(3))
        assertEquals(1, TvKeyboardActions.keyColToTab(6))
        assertEquals(2, TvKeyboardActions.keyColToTab(7))
        assertEquals(2, TvKeyboardActions.keyColToTab(9))

        // Action row mappings
        // Key col 0..2 -> Space (0)
        assertEquals(0, TvKeyboardActions.keyColToActionIndex(0))
        assertEquals(0, TvKeyboardActions.keyColToActionIndex(2))
        // Key col 3..4 -> Backspace (1)
        assertEquals(1, TvKeyboardActions.keyColToActionIndex(3))
        assertEquals(1, TvKeyboardActions.keyColToActionIndex(4))
        // Key col 5..6 -> Clear (2)
        assertEquals(2, TvKeyboardActions.keyColToActionIndex(5))
        assertEquals(2, TvKeyboardActions.keyColToActionIndex(6))
        // Key col 7..9 -> Done (3)
        assertEquals(3, TvKeyboardActions.keyColToActionIndex(7))
        assertEquals(3, TvKeyboardActions.keyColToActionIndex(9))

        // Action index back to key col
        assertEquals(1, TvKeyboardActions.actionIndexToKeyCol(0))
        assertEquals(4, TvKeyboardActions.actionIndexToKeyCol(1))
        assertEquals(6, TvKeyboardActions.actionIndexToKeyCol(2))
        assertEquals(8, TvKeyboardActions.actionIndexToKeyCol(3))
    }
}
