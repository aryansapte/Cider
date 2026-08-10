/**
 * Cider — custom Artist Radio screen
 * Based on Metrolist (GPL-3.0)
 */

package com.metrolist.music.ui.screens.artist

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.db.entities.PlaylistSongMap
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.extensions.toMediaItem
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArtistRadioScreen(
    navController: NavController,
    artistId: String,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val songs = remember { ArtistRadioCache.playlists[artistId] ?: emptyList() }
    val artistName = remember { ArtistRadioCache.names[artistId] ?: "Artist" }

    var isSaved by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            item(key = "header") {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "$artistName Radio",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${songs.size} songs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                if (songs.isNotEmpty()) {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = "$artistName Radio",
                                            items = songs.map { it.toMediaItem() }
                                        )
                                    )
                                }
                            },
                        ) {
                            Icon(painterResource(R.drawable.play), contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Play all")
                        }

                        Spacer(Modifier.width(16.dp))

                        IconButton(
                            onClick = {
                                if (isSaved) {
                                    Toast.makeText(context, "Already saved to library", Toast.LENGTH_SHORT).show()
                                } else {
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        val playlistEntity = PlaylistEntity(
                                            name = "$artistName Radio",
                                            bookmarkedAt = LocalDateTime.now(),
                                            isEditable = true,
                                        )
                                        database.query {
                                            insert(playlistEntity)
                                            songs.forEachIndexed { index, song ->
                                                insert(song.toSongEntity())
                                                insert(
                                                    PlaylistSongMap(
                                                        playlistId = playlistEntity.id,
                                                        songId = song.id,
                                                        position = index,
                                                    ),
                                                )
                                            }
                                        }
                                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            isSaved = true
                                            snackbarHostState.showSnackbar("Saved to library")
                                        }
                                    }
                                }
                            },
                        ) {
                            Icon(
                                painterResource(if (isSaved) R.drawable.check else R.drawable.add),
                                contentDescription = "Save to library",
                            )
                        }
                    }
                }
            }

            items(
                items = songs,
                key = { it.id },
            ) { song ->
                YouTubeListItem(
                    item = song,
                    isActive = mediaMetadata?.id == song.id,
                    isPlaying = isPlaying,
                    trailingContent = {
                        IconButton(
                            onClick = {
                                menuState.show {
                                    YouTubeSongMenu(song = song, onDismiss = menuState::dismiss)
                                }
                            },
                        ) {
                            Icon(painterResource(R.drawable.more_vert), contentDescription = null)
                        }
                    },
                    modifier =
                        Modifier
                            .combinedClickable(
                                onClick = {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = "$artistName Radio",
                                            items = songs.map { it.toMediaItem() },
                                            startIndex = songs.indexOf(song)
                                        )
                                    )
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        YouTubeSongMenu(song = song, onDismiss = menuState::dismiss)
                                    }
                                },
                            ).animateItem(),
                )
            }
        }

        TopAppBar(
            title = { Text("$artistName Radio") },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                }
            },
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// Converts a YouTube SongItem into a Room Database SongEntity
private fun SongItem.toSongEntity() =
    SongEntity(
        id = id,
        title = title,
        duration = duration ?: -1,
        thumbnailUrl = thumbnail,
        albumId = album?.id,
        albumName = album?.name,
    )