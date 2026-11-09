package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistImportParserTest {
    @Test
    fun `csv document separates trimmed headers from data rows`() {
        val document = PlaylistImportParser.parseCsvDocument(
            "\uFEFF Title , Artist \n Song , Performer \n",
        )
        assertEquals(listOf("Title", "Artist"), document.headers)
        assertEquals(listOf(listOf(" Song ", " Performer ")), document.rows)
    }

    @Test
    fun `csv converts entries with inferred column mapping`() {
        val mapping = PlaylistImportParser.inferCsvColumnMapping(
            listOf("Track Name", "Artist", "Album", "YouTube URL"),
        )
        val entries = PlaylistImportParser.csvToEntries(
            "Track Name,Artist,Album,YouTube URL\\nSong,A,Record,https://youtu.be/abcdefghijk",
            hasHeader = true,
            mapping = mapping,
        )
        assertEquals("Song", entries.single().title)
        assertEquals("A", entries.single().artist)
        assertEquals("Record", entries.single().album)
        assertEquals("abcdefghijk", entries.single().sourceId)
    }

    @Test
    fun `csv handles quoted commas escaped quotes and CRLF`() {
        val rows = PlaylistImportParser.parseCsv(
            "Title,Artist,Album\r\n\"Song, One\",\"A \"\"Quoted\"\" Artist\",Album\r\n",
        )
        assertEquals(listOf("Title", "Artist", "Album"), rows[0])
        assertEquals(listOf("Song, One", "A \"Quoted\" Artist", "Album"), rows[1])
    }

    @Test
    fun `csv strips utf8 bom before reading header`() {
        val entries = PlaylistImportParser.csvToEntries(
            "\uFEFFTitle,Artist\nSong,A",
            hasHeader = true,
            titleColumn = 0,
            artistColumn = 1,
        )
        assertEquals("Song", entries.single().title)
        assertEquals("A", entries.single().artist)
        assertEquals(0, entries.single().sourceIndex)
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
    fun `csv retains rows with blank titles for later review`() {
        val entries = PlaylistImportParser.csvToEntries(
            "Title,Artist\n,Unknown Artist\nNamed,Known Artist",
            hasHeader = true,
            titleColumn = 0,
            artistColumn = 1,
        )
        assertEquals(listOf("", "Named"), entries.map { it.title })
        assertEquals(listOf(0, 1), entries.map { it.sourceIndex })
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
    fun `m3u preserves metadata when an extinf has no url before next extinf`() {
        val entries = PlaylistImportParser.parseM3u(
            "#EXTINF:120,Artist - Missing URL\n#EXTINF:245,Artist - Next Song\nhttps://youtu.be/abcdefghijk",
        )
        assertEquals(2, entries.size)
        assertEquals("Missing URL", entries[0].title)
        assertEquals(120L, entries[0].durationSeconds)
        assertEquals("Next Song", entries[1].title)
        assertEquals("abcdefghijk", entries[1].sourceId)
    }

    @Test
    fun `m3u tolerates entries without extinf`() {
        val entries = PlaylistImportParser.parseM3u("https://youtu.be/abcdefghijk\n")
        assertEquals(1, entries.size)
        assertEquals("abcdefghijk", entries[0].sourceId)
    }
}
