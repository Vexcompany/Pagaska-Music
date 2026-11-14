package com.metrolist.music.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.LocalDatabase
import com.metrolist.music.R
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.utils.ExternalPlaylistMatch
import com.metrolist.music.utils.PlaylistLinkFetcher
import com.metrolist.music.utils.PlaylistTrackMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

@Composable
fun ExternalPlaylistImportDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onImported: (String) -> Unit,
) {
    if (!isVisible) return

    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()

    var url by remember { mutableStateOf("") }
    var playlist by remember { mutableStateOf<com.metrolist.music.utils.ExternalPlaylist?>(null) }
    var matches by remember { mutableStateOf<List<ExternalPlaylistMatch>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0 to 0) }

    val matchedCount = matches.count { it.song != null }
    val canImport = playlist != null && matchedCount > 0 && !loading && !importing

    AlertDialog(
        onDismissRequest = { if (!loading && !importing) onDismiss() },
        title = { Text(stringResource(R.string.import_external_playlist)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.external_playlist_import_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        playlist = null
                        matches = emptyList()
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !loading && !importing,
                    label = { Text(stringResource(R.string.playlist_link)) },
                    placeholder = { Text("https://open.spotify.com/playlist/...") },
                )
                Button(
                    onClick = {
                        coroutineScope.launch {
                            loading = true
                            error = null
                            playlist = null
                            matches = emptyList()
                            progress = 0 to 0
                            try {
                                val fetched = PlaylistLinkFetcher.fetch(context, url)
                                playlist = fetched
                                matches = PlaylistTrackMatcher.match(fetched.tracks) { done, total ->
                                    progress = done to total
                                }
                            } catch (exception: Exception) {
                                error = exception.message ?: context.getString(R.string.external_playlist_import_failed)
                            } finally {
                                loading = false
                            }
                        }
                    },
                    enabled = url.isNotBlank() && !loading && !importing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.fetch_and_match))
                }

                if (loading) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator()
                        Text(
                            text = if (playlist == null) {
                                stringResource(R.string.fetching_playlist)
                            } else {
                                stringResource(R.string.matching_playlist_tracks, progress.first, progress.second)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                playlist?.let { fetched ->
                    Text(fetched.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.external_playlist_match_summary, matchedCount, fetched.tracks.size),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                        itemsIndexed(matches) { index, match ->
                            Column(modifier = Modifier.padding(vertical = 5.dp)) {
                                Text(
                                    text = (index + 1).toString() + ". " + match.sourceTrack.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = if (match.song != null) {
                                        match.song.artists.joinToString { it.name } + " · " + match.song.title
                                    } else {
                                        stringResource(R.string.external_track_not_matched)
                                    },
                                    color = if (match.song != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (importing) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.importing_playlist))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canImport,
                onClick = {
                    val source = playlist ?: return@TextButton
                    val importedSongs = matches.mapNotNull { it.song }.map { it.toMediaMetadata() }
                    if (importedSongs.isEmpty()) return@TextButton
                    coroutineScope.launch {
                        importing = true
                        error = null
                        try {
                            val entity = PlaylistEntity(
                                name = source.title,
                                thumbnailUrl = source.artworkUrl,
                                bookmarkedAt = LocalDateTime.now(),
                                isEditable = true,
                            )
                            withContext(Dispatchers.IO) {
                                database.withTransaction {
                                    insert(entity)
                                    importedSongs.forEach(::insert)
                                }
                                val localPlaylist = database.playlist(entity.id).firstOrNull()
                                    ?: throw IllegalStateException("The imported playlist could not be created.")
                                database.addSongsToPlaylist(
                                    localPlaylist,
                                    importedSongs.map { it.id to it.setVideoId },
                                )
                            }
                            onImported(entity.id)
                        } catch (exception: Exception) {
                            error = exception.message ?: context.getString(R.string.external_playlist_import_failed)
                            importing = false
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.import_matched_tracks, matchedCount))
            }
        },
        dismissButton = {
            TextButton(
                enabled = !loading && !importing,
                onClick = onDismiss,
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
