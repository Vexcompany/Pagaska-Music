package com.metrolist.music.utils

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.metrolist.innertube.YouTube
import com.metrolist.paxsenix.Paxsenix
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import java.net.URI
import java.text.Normalizer
import kotlin.coroutines.resume

private const val MAX_IMPORT_PAGES = 10
private const val MAX_IMPORT_ENTRIES = 500
private const val IMPORT_TIMEOUT_MS = 60_000L // Keep provider fetches bounded.

enum class ExternalPlaylistSource { SPOTIFY, APPLE_MUSIC }

data class ExternalPlaylistTrack(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,
)

data class ExternalPlaylist(
    val source: ExternalPlaylistSource,
    val title: String,
    val artworkUrl: String?,
    val tracks: List<ExternalPlaylistTrack>,
)

data class ExternalPlaylistMatch(
    val sourceTrack: ExternalPlaylistTrack,
    val song: SongItem?,
    val confidence: Float,
)

internal data class ExternalPlaylistLink(
    val source: ExternalPlaylistSource,
    val id: String,
    val storefront: String = "us",
    val spotifyType: String = "playlist",
)

object PlaylistLinkParser {
    private val spotifyId = Regex("^[A-Za-z0-9]{22}$")
    private val appleId = Regex("^pl\\.[A-Za-z0-9.-]+$")

    internal fun parse(raw: String): ExternalPlaylistLink? {
        val value = raw.trim()
        if (value.startsWith("spotify:")) {
            val parts = value.split(":")
            if (parts.size == 3 && parts[1] in setOf("playlist", "album") && spotifyId.matches(parts[2])) {
                return ExternalPlaylistLink(ExternalPlaylistSource.SPOTIFY, parts[2], spotifyType = parts[1])
            }
        }

        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.userInfo != null || uri.port !in setOf(-1, 443)) return null
        val host = uri.host?.lowercase() ?: return null
        val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)

        if (host == "open.spotify.com" || host == "www.open.spotify.com" || host == "play.spotify.com") {
            val typeIndex = segments.indexOfFirst { it == "playlist" || it == "album" }
            if (typeIndex >= 0) {
                val id = segments.getOrNull(typeIndex + 1)?.substringBefore('?') ?: return null
                if (spotifyId.matches(id)) {
                    return ExternalPlaylistLink(ExternalPlaylistSource.SPOTIFY, id, spotifyType = segments[typeIndex])
                }
            }
        }

        if (host == "music.apple.com" || host == "www.music.apple.com") {
            val id = segments.firstOrNull { appleId.matches(it) } ?: return null
            val storefront = segments.firstOrNull()?.takeIf { it.matches(Regex("^[a-z]{2}$")) } ?: "us"
            return ExternalPlaylistLink(ExternalPlaylistSource.APPLE_MUSIC, id, storefront)
        }

        return null
    }

    fun isSpotifyShortLink(raw: String): Boolean =
        runCatching {
            URI(raw.trim()).let { uri ->
                uri.scheme == "https" &&
                    uri.host?.lowercase() == "spotify.link" &&
                    uri.userInfo == null &&
                    uri.port in setOf(-1, 443)
            }
        }.getOrDefault(false)
}

object PlaylistLinkFetcher {
    suspend fun fetch(context: Context, rawUrl: String): ExternalPlaylist = withTimeout(IMPORT_TIMEOUT_MS) {
        val resolvedUrl = if (PlaylistLinkParser.isSpotifyShortLink(rawUrl)) {
            withContext(Dispatchers.IO) { resolveSpotifyShortLink(rawUrl.trim()) }
        } else {
            rawUrl.trim()
        }

        val link = PlaylistLinkParser.parse(resolvedUrl)
            ?: throw IllegalArgumentException("Link tidak dikenali. Gunakan playlist Spotify atau Apple Music publik.")

        when (link.source) {
            ExternalPlaylistSource.SPOTIFY -> fetchSpotify(context, link)
            ExternalPlaylistSource.APPLE_MUSIC -> fetchAppleMusic(context, link)
        }
    }

    private fun resolveSpotifyShortLink(rawUrl: String): String {
        var current = runCatching { URI(rawUrl) }.getOrNull()
            ?: throw IllegalArgumentException("Tautan Spotify tidak valid.")
        repeat(5) {
            if (!isTrustedSpotifyRedirectUrl(current)) {
                throw IllegalArgumentException("Pengalihan tautan Spotify tidak aman.")
            }
            val response = Jsoup.connect(current.toString())
                .userAgent(USER_AGENT)
                .timeout(10_000)
                .followRedirects(false)
                .ignoreHttpErrors(true)
                .execute()
            val status = response.statusCode()
            if (status in 300..399) {
                val location = response.header("location")
                    ?: throw IllegalStateException("Spotify tidak memberikan alamat pengalihan.")
                current = runCatching { current.resolve(location) }.getOrNull()
                    ?: throw IllegalArgumentException("Alamat pengalihan Spotify tidak valid.")
            } else {
                if (status !in 200..299) {
                    throw IllegalStateException("Spotify gagal membuka tautan (HTTP $status).")
                }
                return current.toString()
            }
        }
        throw IllegalStateException("Terlalu banyak pengalihan tautan Spotify.")
    }

    private suspend fun fetchSpotify(context: Context, link: ExternalPlaylistLink): ExternalPlaylist {
        val payload = readSpotifyEmbed(context, link)
        val json = JSONObject(payload)
        val tracksJson = json.optJSONArray("tracks") ?: JSONArray()
        val tracks = buildList {
            for (index in 0 until minOf(tracksJson.length(), MAX_IMPORT_ENTRIES)) {
                val item = tracksJson.optJSONObject(index) ?: continue
                val title = item.optString("title").trim()
                val artist = item.optString("artist").trim()
                if (title.isNotBlank() && artist.isNotBlank()) {
                    add(
                        ExternalPlaylistTrack(
                            title = title,
                            artist = artist,
                            album = item.optString("album").takeIf(String::isNotBlank),
                            durationMs = item.optLong("durationMs").takeIf { it > 0 },
                        ),
                    )
                }
            }
        }
        require(tracks.isNotEmpty()) { "Spotify tidak mengembalikan daftar lagu. Pastikan playlist bersifat publik." }
        return ExternalPlaylist(
            source = ExternalPlaylistSource.SPOTIFY,
            title = json.optString("title").ifBlank { "Spotify Playlist" },
            artworkUrl = json.optString("artwork").takeIf(String::isNotBlank),
            tracks = tracks,
        )
    }

    private suspend fun readSpotifyEmbed(context: Context, link: ExternalPlaylistLink): String =
        withContext(Dispatchers.Main.immediate) {
            var webView: WebView? = null
            try {
                withTimeout(IMPORT_TIMEOUT_MS) {
                    suspendCancellableCoroutine { continuation ->
                        val view = WebView(context)
                        webView = view
                        view.settings.javaScriptEnabled = true
                        view.settings.domStorageEnabled = true
                        view.settings.loadsImagesAutomatically = false
                        view.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                view.evaluateJavascript(SPOTIFY_SCRAPE_SCRIPT, null)
                            }
                        }
                        val bridge = object {
                            @JavascriptInterface
                            fun onComplete(value: String) {
                                if (continuation.isActive) continuation.resume(value)
                            }

                            @JavascriptInterface
                            fun onFailure(message: String) {
                                if (continuation.isActive) continuation.resume("""{"error":""" + JSONObject.quote(message) + "}")
                            }
                        }
                        view.addJavascriptInterface(bridge, "PagaskaBridge")
                        continuation.invokeOnCancellation {
                            view.post { view.stopLoading() }
                        }
                        view.loadUrl("https://open.spotify.com/embed/" + link.spotifyType + "/" + link.id)
                    }
                }
            } finally {
                webView?.let { view ->
                    view.stopLoading()
                    view.removeJavascriptInterface("PagaskaBridge")
                    view.destroy()
                }
            }
        }.let { payload ->
            val json = JSONObject(payload)
            if (json.has("error")) throw IllegalStateException(json.optString("error"))
            json.toString()
        }

    private suspend fun fetchAppleMusic(context: Context, link: ExternalPlaylistLink): ExternalPlaylist = withContext(Dispatchers.IO) {
        var token = Paxsenix.getAppleMusicDeveloperToken(context)

        suspend fun requestJson(url: String): JSONObject {
            fun request(bearer: String): JSONObject =
                JSONObject(
                    Jsoup.connect(url)
                        .ignoreContentType(true)
                        .userAgent(USER_AGENT)
                        .header("Authorization", "Bearer " + bearer)
                        .header("Origin", "https://music.apple.com")
                        .header("Referer", "https://music.apple.com/")
                        .followRedirects(false)
                        .timeout(20_000)
                        .execute()
                        .body(),
                )

            return try {
                request(token)
            } catch (exception: HttpStatusException) {
                if (exception.statusCode != 401) throw exception
                token = Paxsenix.getAppleMusicDeveloperToken(context, forceRefresh = true)
                request(token)
            }
        }

        val base = "https://amp-api.music.apple.com/v1/catalog/" + link.storefront
        val metadata = requestJson(base + "/playlists/" + link.id)
            .optJSONArray("data")?.optJSONObject(0)?.optJSONObject("attributes") ?: JSONObject()
        val title = metadata.optString("name").ifBlank { "Apple Music Playlist" }
        val artwork = metadata.optJSONObject("artwork")?.optString("url")
            ?.replace("{w}", "600")?.replace("{h}", "600")

        var nextUrl: String? = base + "/playlists/" + link.id + "/tracks?limit=100"
        val tracks = mutableListOf<ExternalPlaylistTrack>()
        var pageCount = 0
        while (nextUrl != null && pageCount < MAX_IMPORT_PAGES && tracks.size < MAX_IMPORT_ENTRIES) {
            val response = requestJson(nextUrl)
            val data = response.optJSONArray("data") ?: JSONArray()
            for (index in 0 until data.length()) {
                if (tracks.size >= MAX_IMPORT_ENTRIES) break
                val attributes = data.optJSONObject(index)?.optJSONObject("attributes") ?: continue
                val trackTitle = attributes.optString("name").trim()
                val artist = attributes.optString("artistName").trim()
                if (trackTitle.isNotBlank() && artist.isNotBlank()) {
                    tracks += ExternalPlaylistTrack(
                        title = trackTitle,
                        artist = artist,
                        album = attributes.optString("albumName").takeIf(String::isNotBlank),
                        durationMs = attributes.optLong("durationInMillis").takeIf { it > 0 },
                    )
                }
            }
            val next = response.optString("next").takeIf(String::isNotBlank)
            nextUrl = next?.let {
                resolveAppleMusicNextUrl(nextUrl!!, it, link.storefront, link.id)
                    ?: throw IllegalStateException("Apple Music returned an invalid pagination URL.")
            }
            pageCount++
        }

        require(tracks.isNotEmpty()) { "Playlist Apple Music kosong atau tidak dapat diakses secara publik." }
        ExternalPlaylist(ExternalPlaylistSource.APPLE_MUSIC, title, artwork, tracks)
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    private val SPOTIFY_SCRAPE_SCRIPT = """
        (async function () {
          try {
            const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
            const found = new Map();
            let scroller = null;
            const firstRow = document.querySelector('[data-testid="tracklist-row"]');
            for (let node = firstRow; node; node = node.parentElement) {
              const style = getComputedStyle(node);
              if ((style.overflowY === 'auto' || style.overflowY === 'scroll') &&
                  node.scrollHeight > node.clientHeight + 80) { scroller = node; break; }
            }
            scroller = scroller || document.scrollingElement || document.documentElement;
            let stable = 0;
            for (let page = 0; page < 10 && found.size < 500; page++) {
              const rows = [...document.querySelectorAll('[data-testid="tracklist-row"]')];
              const rowHeight = rows[0]?.getBoundingClientRect().height || 56;
              const base = Math.round(scroller.scrollTop / rowHeight);
              rows.forEach((row, index) => {
                const cells = [...row.querySelectorAll('[role="gridcell"]')];
                const titleLink = row.querySelector('a[data-testid="internal-track-link"]');
                const title = (titleLink?.innerText || cells[0]?.innerText || '').trim();
                const artist = (cells[1]?.innerText || '').trim();
                const album = (cells[2]?.innerText || '').trim();
                const duration = (cells[cells.length - 1]?.innerText || '').trim();
                if (!title || !artist) return;
                const position = Number(row.getAttribute('aria-rowindex')) || base + index;
                const key = String(position);
                const match = duration.match(/^(\d+):(\d{1,2})$/);
                found.set(key, {
                  title, artist, album,
                  durationMs: match ? (Number(match[1]) * 60 + Number(match[2])) * 1000 : 0
                });
              });
              const before = found.size;
              scroller.scrollTop = Math.min(scroller.scrollTop + Math.max(400, scroller.clientHeight * 0.85), scroller.scrollHeight);
              await wait(350);
              const afterRows = document.querySelectorAll('[data-testid="tracklist-row"]').length;
              if (found.size === before && afterRows === rows.length) stable++; else stable = 0;
              if (stable >= 4 || scroller.scrollTop + scroller.clientHeight >= scroller.scrollHeight - 8) break;
            }
            const title = (document.querySelector('h1')?.innerText || document.querySelector('meta[property="og:title"]')?.content || document.title || 'Spotify Playlist').trim();
            const artwork = document.querySelector('meta[property="og:image"]')?.content || '';
            const tracks = [...found.entries()].sort((a,b) => Number(a[0]) - Number(b[0])).map(entry => entry[1]);
            if (!tracks.length) throw new Error('Spotify embed tidak menampilkan tracklist publik.');
            window.PagaskaBridge.onComplete(JSON.stringify({title, artwork, tracks}));
          } catch (error) {
            window.PagaskaBridge.onFailure(String(error));
          }
        })();
    """.trimIndent()
}

internal fun isTrustedSpotifyRedirectUrl(uri: URI): Boolean =
    uri.scheme == "https" &&
        uri.host?.lowercase() in setOf("spotify.link", "open.spotify.com", "www.open.spotify.com") &&
        uri.userInfo == null &&
        uri.port in setOf(-1, 443)

internal fun resolveAppleMusicNextUrl(
    currentUrl: String,
    next: String,
    storefront: String,
    playlistId: String,
): String? {
    val resolved = runCatching { URI(currentUrl).resolve(next.trim()) }.getOrNull() ?: return null
    val expectedPath = "/v1/catalog/$storefront/playlists/$playlistId/tracks"
    if (resolved.scheme != "https" ||
        resolved.host?.lowercase() != "amp-api.music.apple.com" ||
        resolved.userInfo != null ||
        resolved.port !in setOf(-1, 443) ||
        resolved.path != expectedPath
    ) {
        return null
    }
    return resolved.toASCIIString()
}

object PlaylistTrackMatcher {
    suspend fun match(
        tracks: List<ExternalPlaylistTrack>,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): List<ExternalPlaylistMatch> {
        val limitedTracks = tracks.take(MAX_IMPORT_ENTRIES)
        val semaphore = Semaphore(permits = 3)
        val results = mutableListOf<ExternalPlaylistMatch>()
        var completed = 0
        for (batch in limitedTracks.chunked(24)) {
            val batchResults = coroutineScope {
                batch.map { track ->
                    async {
                        semaphore.withPermit {
                            val query = listOf(track.title, track.artist).joinToString(" ")
                            val candidates = try {
                                YouTube.searchSummary(query).getOrNull()
                                    ?.summaries.orEmpty()
                                    .flatMap { it.items }
                                    .filterIsInstance<SongItem>()
                                    .distinctBy { it.id }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                emptyList()
                            }
                            val best = candidates
                                .map { song -> song to score(track, song) }
                                .maxByOrNull { it.second }
                            val confidence = best?.second ?: 0.0
                            ExternalPlaylistMatch(
                                sourceTrack = track,
                                song = best?.first?.takeIf { confidence >= 0.70 },
                                confidence = confidence.toFloat(),
                            )
                        }
                    }
                }.awaitAll()
            }
            results += batchResults
            completed += batch.size
            onProgress(completed, limitedTracks.size)
            delay(80)
        }
        return results
    }

    private fun score(track: ExternalPlaylistTrack, song: SongItem): Double {
        val titleScore = tokenSimilarity(track.title, song.title)
        val artistScore = song.artists.maxOfOrNull { tokenSimilarity(track.artist, it.name) } ?: 0.0
        val songDurationSeconds = song.duration
        val durationScore = if (track.durationMs != null && songDurationSeconds != null) {
            val difference = kotlin.math.abs(track.durationMs - songDurationSeconds * 1000L)
            when {
                difference <= 3_000 -> 0.12
                difference <= 8_000 -> 0.07
                difference <= 15_000 -> 0.02
                else -> -0.08
            }
        } else 0.0
        return (titleScore * 0.68 + artistScore * 0.32 + durationScore).coerceIn(0.0, 1.0)
    }

    private fun tokenSimilarity(first: String, second: String): Double {
        fun tokens(value: String): Set<String> =
            Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .replace(Regex("\\([^)]*(feat|ft\\.?)[^)]*\\)", RegexOption.IGNORE_CASE), "")
                .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
                .split(Regex("\\s+"))
                .filter { it.length > 1 }
                .toSet()

        val a = tokens(first)
        val b = tokens(second)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return a.intersect(b).size.toDouble() / a.union(b).size.toDouble()
    }
}
