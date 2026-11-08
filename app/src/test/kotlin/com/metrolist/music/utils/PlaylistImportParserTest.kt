package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistImportParserTest {
    @Test
    fun `csv handles quoted commas escaped quotes and CRLF`() {
        val rows = PlaylistImportParser.parseCsv(
            "Title,Artist,Album\r\n\"Song, One\",\"A \"\"Quoted\"\" Artist\",Album\r\n",
        )
        assertEquals(listOf("Title", "Artist", "Album"), rows[0])
        assertEquals(listOf("Song, One", "A \"Quoted\" Artist", "Album"), rows[1])
    }

    @Test
    fun `csv import keeps source order and extracts youtube id`() {
        val entries = PlaylistImportParser.csvToEntries(
            "Title,Artist,URL\nSecond,B,https://youtu.be/abcdefghijk\nFirst,A,https://youtube.com/watch?v=12345678901",
            hasHeader = true,
            titleColumn = 0,
            artistColumn = 1,
            urlColumn = 2,
        )
        assertEquals(listOf(0, 1), entries.map { it.sourceIndex })
        assertEquals(listOf("Second", "First"), entries.map { it.title })
        assertEquals(listOf("abcdefghijk", "12345678901"), entries.map { it.sourceId })
    }

    @Test
    fun `csv skips blank titles and permits no optional columns`() {
        val entries = PlaylistImportParser.csvToEntries(" A \n\nB", false, titleColumn = 0)
        assertEquals(listOf("A", "B"), entries.map { it.title })
        assertNull(entries.first().sourceId)
    }

    @Test
    fun `m3u handles extinf youtube urls and pagaska marker`() {
        val entries = PlaylistImportParser.parseM3u(
            "#EXTM3U\n#EXTINF:245,Artist - Song One\nhttps://youtube.com/watch?v=abcdefghijk\n#EXTINF:0,Artist Two - Song Two\n#YTM:12345678901\n",
        )
        assertEquals(2, entries.size)
        assertEquals(listOf(0, 1), entries.map { it.sourceIndex })
        assertEquals("Song One", entries[0].title)
        assertEquals("Artist", entries[0].artist)
        assertEquals(245L, entries[0].durationSeconds)
        assertEquals("abcdefghijk", entries[0].sourceId)
        assertEquals("Song Two", entries[1].title)
        assertEquals("12345678901", entries[1].sourceId)
        assertNull(entries[1].sourceUrl)
    }

    @Test
    fun `m3u tolerates entries without extinf`() {
        val entries = PlaylistImportParser.parseM3u("https://youtu.be/abcdefghijk\n")
        assertEquals(1, entries.size)
        assertEquals("abcdefghijk", entries[0].sourceId)
    }
}
