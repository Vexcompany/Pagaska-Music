package com.metrolist.music.utils

/**
 * Platform-neutral parsers for playlist interchange formats.
 * Parsing deliberately does not resolve or persist tracks: callers can review/match
 * entries first and preserve [PlaylistImportEntry.sourceIndex] when saving.
 */
data class PlaylistImportEntry(
    val sourceIndex: Int,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val durationSeconds: Long? = null,
    val sourceUrl: String? = null,
    val sourceId: String? = null,
)

data class ParsedCsv(
    val headers: List<String>,
    val rows: List<List<String>>,
)

object PlaylistImportParser {
    /** RFC-4180-style CSV parser supporting escaped quotes, commas and multiline cells. */
    fun parseCsv(input: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0

        while (i < input.length) {
            val c = input[i]
            when {
                quoted && c == '"' && i + 1 < input.length && input[i + 1] == '"' -> {
                    cell.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> {
                    row += cell.toString()
                    cell.setLength(0)
                }
                !quoted && (c == '\n' || c == '\r') -> {
                    row += cell.toString()
                    cell.setLength(0)
                    if (row.any { it.isNotEmpty() }) rows += row.toList()
                    row.clear()
                    if (c == '\r' && i + 1 < input.length && input[i + 1] == '\n') i++
                }
                else -> cell.append(c)
            }
            i++
        }
        row += cell.toString()
        if (row.any { it.isNotEmpty() }) rows += row.toList()
        return rows
    }

    fun csvToEntries(
        input: String,
        hasHeader: Boolean,
        titleColumn: Int,
        artistColumn: Int = -1,
        albumColumn: Int = -1,
        urlColumn: Int = -1,
    ): List<PlaylistImportEntry> {
        require(titleColumn >= 0) { "Title column must be selected" }
        val rows = parseCsv(input)
        val dataRows = if (hasHeader) rows.drop(1) else rows
        return dataRows.mapIndexedNotNull { index, row ->
            val title = row.getOrNull(titleColumn)?.trim().orEmpty()
            if (title.isBlank()) return@mapIndexedNotNull null
            val url = row.getOrNull(urlColumn)?.trim()?.takeIf(String::isNotEmpty)
            PlaylistImportEntry(
                sourceIndex = index,
                title = title,
                artist = row.getOrNull(artistColumn)?.trim().orEmpty(),
                album = row.getOrNull(albumColumn)?.trim().orEmpty(),
                sourceUrl = url,
                sourceId = extractYouTubeId(url),
            )
        }
    }

    /** Parses #EXTINF M3U entries and Pagaska's #YTM:<videoId> export markers. */
    fun parseM3u(input: String): List<PlaylistImportEntry> {
        val result = mutableListOf<PlaylistImportEntry>()
        var pendingTitle = ""
        var pendingDuration: Long? = null
        var pendingUrl: String? = null

        fun addEntry() {
            val titleLine = pendingTitle
            val url = pendingUrl
            val id = extractYouTubeId(url) ?: url?.takeIf { it.startsWith("#YTM:") }?.substringAfter("#YTM:")
            val parsed = splitArtistTitle(titleLine)
            if (titleLine.isNotBlank() || !url.isNullOrBlank()) {
                result += PlaylistImportEntry(
                    sourceIndex = result.size,
                    title = parsed.second.ifBlank { id ?: url.orEmpty() },
                    artist = parsed.first,
                    durationSeconds = pendingDuration,
                    sourceUrl = url?.takeUnless { it.startsWith("#YTM:") },
                    sourceId = id,
                )
            }
            pendingTitle = ""
            pendingDuration = null
            pendingUrl = null
        }

        input.lineSequence().map(String::trim).filter(String::isNotEmpty).forEach { line ->
            when {
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val payload = line.substringAfter(':')
                    val durationText = payload.substringBefore(',')
                    pendingDuration = durationText.toLongOrNull()?.takeIf { it >= 0 }
                    pendingTitle = payload.substringAfter(',', "").trim()
                }
                line.startsWith("#YTM:", ignoreCase = true) -> {
                    pendingUrl = line
                    addEntry()
                }
                line.startsWith("#") -> Unit
                else -> {
                    pendingUrl = line
                    addEntry()
                }
            }
        }
        if (pendingTitle.isNotBlank() || pendingUrl != null) addEntry()
        return result
    }

    private fun splitArtistTitle(value: String): Pair<String, String> {
        val separator = value.indexOf(" - ")
        return if (separator > 0) value.substring(0, separator).trim() to value.substring(separator + 3).trim()
        else "" to value.trim()
    }

    private fun extractYouTubeId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        if (url.startsWith("#YTM:", ignoreCase = true)) return url.substringAfter(':').takeIf(String::isNotBlank)
        val patterns = listOf(
            Regex("""(?:youtube\.com/watch\?[^#]*v=|youtu\.be/)([A-Za-z0-9_-]{11})""", RegexOption.IGNORE_CASE),
            Regex("""youtube\.com/shorts/([A-Za-z0-9_-]{11})""", RegexOption.IGNORE_CASE),
        )
        return patterns.firstNotNullOfOrNull { it.find(url)?.groupValues?.getOrNull(1) }
    }
}
