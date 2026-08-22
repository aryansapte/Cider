/**
 * Cider Music (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalSyncUtils
import com.metrolist.music.R
import com.metrolist.music.constants.AddToPlaylistSortDescendingKey
import com.metrolist.music.constants.AddToPlaylistSortTypeKey
import com.metrolist.music.constants.PlaylistSortType
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.extensions.move
import com.metrolist.music.ui.component.CreatePlaylistDialog
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.PlaylistsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.animation.slideInVertically
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.LocalPlayerConnection

/**
 * Cache for passing song IDs to the Add to Playlist screen
 */
object AddToPlaylistCache {
    var pendingSongIds: List<String>? = null
    var pendingSongTitle: String? = null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistScreen(
    navController: NavController,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val database = LocalDatabase.current
    val syncUtils = LocalSyncUtils.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }

    var headerTint by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(mediaMetadata?.thumbnailUrl) {
        val url = mediaMetadata?.thumbnailUrl ?: return@LaunchedEffect
        runCatching {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
            val bitmap = context.imageLoader.execute(request).image?.toBitmap() ?: return@runCatching null
            val palette = Palette.from(bitmap).generate()
            Color(palette.getMutedColor(palette.getDominantColor(0xFF3A3A3A.toInt())))
        }.getOrNull()?.let { color -> headerTint = color }
    }

    val playlists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    val (sortType, onSortTypeChange) = rememberEnumPreference(
        AddToPlaylistSortTypeKey,
        PlaylistSortType.NAME,
    )
    val (sortDescending) = rememberPreference(AddToPlaylistSortDescendingKey, false)

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    val selectedPlaylists = remember { mutableStateListOf<String>() }
    val preselectedPlaylists = remember { mutableStateListOf<String>() }

    val songIds = AddToPlaylistCache.pendingSongIds

    LaunchedEffect(songIds, playlists) {
        if (songIds.isNullOrEmpty() || playlists.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val containing = playlists
                .filter { database.playlistDuplicates(it.id, songIds).isNotEmpty() }
                .map { it.id }
            withContext(Dispatchers.Main) {
                preselectedPlaylists.clear()
                preselectedPlaylists.addAll(containing)
                selectedPlaylists.clear()
                selectedPlaylists.addAll(containing)
            }
        }
    }


    BackHandler { navController.navigateUp() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Add to playlist",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Transparent)
                            .clickable { showCreateDialog = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.add),
                            contentDescription = stringResource(R.string.create_playlist),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
                modifier = Modifier.background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            headerTint ?: MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                ),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(bottom = 80.dp),
            ) {
                // Search field
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp, bottom = 16.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Find playlist",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                            innerTextField()
                        }
                    },
                )

                // List with smooth crossfade on search / sort changes
                AnimatedContent(
                    targetState = searchQuery,
                    transitionSpec = {
                        (slideInVertically(tween(220)) { it / 5 } + fadeIn(tween(220))) togetherWith
                                fadeOut(tween(180))
                    },
                    label = "playlistSearch",
                    modifier = Modifier.fillMaxWidth(),
                ) { query ->
                    val list = remember(playlists, query, sortType, sortDescending) {
                        val filtered = if (query.isBlank()) {
                            playlists
                        } else {
                            playlists.filter { it.playlist.name.contains(query, ignoreCase = true) }
                        }
                        val sorted = when (sortType) {
                            PlaylistSortType.NAME -> filtered.sortedBy { it.playlist.name.lowercase() }
                            PlaylistSortType.CREATE_DATE -> filtered.sortedBy { it.playlist.createdAt }
                            PlaylistSortType.LAST_UPDATED -> filtered.sortedBy { it.playlist.lastUpdateTime }
                            PlaylistSortType.SONG_COUNT -> filtered.sortedBy { it.songCount }
                        }
                        if (sortDescending) sorted.reversed() else sorted
                    }
                    val containing = list.filter { it.id in preselectedPlaylists }
                    val others = list.filter { it.id !in preselectedPlaylists }

                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        if (containing.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Saved in",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "Clear all",
                                        color = Color(0xFF1DB954),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clickable {
                                            selectedPlaylists.clear()
                                        },
                                    )
                                }
                            }

                            items(containing) { playlist ->
                                PlaylistRow(
                                    playlist = playlist,
                                    isSelected = playlist.id in selectedPlaylists,
                                    onClick = {
                                        if (playlist.id in selectedPlaylists) {
                                            selectedPlaylists.remove(playlist.id)
                                        } else {
                                            selectedPlaylists.add(playlist.id)
                                        }
                                    },
                                )
                            }
                        }

                        if (others.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.drag_handle),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Text(
                                            text = "Most relevant",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }

                            items(others) { playlist ->
                                PlaylistRow(
                                    playlist = playlist,
                                    isSelected = playlist.id in selectedPlaylists,
                                    onClick = {
                                        if (playlist.id in selectedPlaylists) {
                                            selectedPlaylists.remove(playlist.id)
                                        } else {
                                            selectedPlaylists.add(playlist.id)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Floating Done button
            AnimatedVisibility(
                visible = selectedPlaylists.toSet() != preselectedPlaylists.toSet() || isProcessing,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            bottom = 16.dp + WindowInsets.navigationBars
                                .asPaddingValues()
                                .calculateBottomPadding(),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Button(
                        onClick = {
                            if (isProcessing) return@Button
                            isProcessing = true
                            coroutineScope.launch {
                                try {
                                    val ids = songIds ?: return@launch
                                    val toAdd = selectedPlaylists.filter { it !in preselectedPlaylists }
                                    val toRemove = preselectedPlaylists.filter { it !in selectedPlaylists }

                                    withContext(Dispatchers.IO) {
                                        toAdd.forEach { playlistId ->
                                            val playlist = playlists.find { it.id == playlistId } ?: return@forEach
                                            database.addSongsToPlaylist(playlist, ids.map { it to null }, prepend = true)
                                            playlist.playlist.browseId?.let { browseId ->
                                                ids.forEach { songId ->
                                                    syncUtils.addToPlaylist(browseId, playlistId, songId)
                                                }
                                            }
                                        }

                                        toRemove.forEach { playlistId ->
                                            val playlist = playlists.find { it.id == playlistId } ?: return@forEach
                                            val maps = database.playlistSongMaps(playlistId, 0).filter { it.songId in ids }
                                            database.withTransaction {
                                                maps.forEach { map ->
                                                    move(playlistId, map.position, Int.MAX_VALUE)
                                                    delete(map.copy(position = Int.MAX_VALUE))
                                                }
                                            }
                                            playlist.playlist.browseId?.let { browseId ->
                                                maps.forEach { map ->
                                                    syncUtils.scheduleRemoveFromPlaylist(browseId, map.songId, playlistId) { map.setVideoId }
                                                }
                                            }
                                        }
                                    }

                                    withContext(Dispatchers.Main) {
                                        AddToPlaylistCache.pendingSongIds = null
                                        AddToPlaylistCache.pendingSongTitle = null
                                        navController.navigateUp()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isProcessing = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.height(56.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1DB954),
                            contentColor = Color.Black,
                        ),
                        enabled = !isProcessing,
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                text = "Done",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                        }
                    }
                }
            }

            if (showCreateDialog) {
                CreatePlaylistDialog(
                    onDismiss = { showCreateDialog = false },
                    allowSyncing = true,
                )
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val thumbUrl = playlist.thumbnails.firstOrNull()
            if (thumbUrl != null) {
                AsyncImage(
                    model = thumbUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.queue_music),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = "${playlist.songCount} songs",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }

        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (isSelected) Color(0xFF1DB954) else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = CircleShape,
                )
                .background(
                    if (isSelected) Color(0xFF1DB954) else Color.Transparent,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Icon(
                    painter = painterResource(R.drawable.radio_button_unchecked),
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}