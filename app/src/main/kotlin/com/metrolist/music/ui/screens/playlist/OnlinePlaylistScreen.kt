/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEachReversed
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.LocalSyncUtils
import com.metrolist.music.R
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.YouTubePlaylistQueue
import com.metrolist.music.ui.component.ExpandableText
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.ui.menu.YouTubePlaylistMenu
import com.metrolist.music.ui.menu.YouTubeSelectionSongMenu
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.ui.utils.resize
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.OnlinePlaylistViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnlinePlaylistScreen(
    navController: NavController,
    viewModel: OnlinePlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val listenTogetherManager = LocalListenTogetherManager.current
    val isListenTogetherGuest = listenTogetherManager?.let { it.isInRoom && !it.isHost } ?: false
    val coroutineScope = rememberCoroutineScope()
    val syncUtils = LocalSyncUtils.current

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val songs by viewModel.playlistSongs.collectAsStateWithLifecycle()
    val dbPlaylist by viewModel.dbPlaylist.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isPodcastPlaylist = viewModel.isPodcastPlaylist

    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

    // Palette tint extraction from playlist cover
    var headerTint by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(playlist?.thumbnail) {
        val thumb = playlist?.thumbnail ?: return@LaunchedEffect
        runCatching {
            val request = ImageRequest.Builder(context)
                .data(thumb)
                .allowHardware(false)
                .build()
            val bitmap = context.imageLoader.execute(request).image?.toBitmap() ?: return@runCatching null
            val palette = Palette.from(bitmap).generate()
            Color(palette.getMutedColor(palette.getDominantColor(0xFF3A3A3A.toInt())))
        }.getOrNull()?.let { color -> headerTint = color }
    }

    // Scroll-driven fades (same math as album screen)
    val lazyListState = rememberLazyListState()
    val headerFadePx = with(LocalDensity.current) { 420.dp.toPx() }
    val topBarFadePx = with(LocalDensity.current) { 170.dp.toPx() }
    val topBarStartPx = with(LocalDensity.current) { 180.dp.toPx() }
    val gradientEndPx = with(LocalDensity.current) { 380.dp.toPx() }
    val headerFallbackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val headerTopPadding = (LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateTopPadding() - 52.dp).coerceAtLeast(0.dp)
    val headerTopExtendPx = with(LocalDensity.current) { headerTopPadding.toPx() }
    val listBottomPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    val rawScrollOffset by remember {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex == 0) {
                lazyListState.firstVisibleItemScrollOffset.toFloat()
            } else {
                headerFadePx + topBarFadePx
            }
        }
    }
    val scrollFade by remember {
        derivedStateOf { 1f - (rawScrollOffset / headerFadePx).coerceIn(0f, 1f) }
    }
    val topBarFade by remember {
        derivedStateOf { ((rawScrollOffset - topBarStartPx) / topBarFadePx).coerceIn(0f, 1f) }
    }

    // Search state (preserved)
    val snackbarHostState = remember { SnackbarHostState() }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) { if (isSearching) focusRequester.requestFocus() }

    // Filtered songs
    val filteredSongs =
        remember(songs, query) {
            if (query.text.isEmpty()) {
                songs.mapIndexed { i, s -> i to s }
            } else {
                songs.mapIndexed { i, s -> i to s }.filter {
                    it.second.title.contains(query.text, true) ||
                            it.second.artists.fastAny { a -> a.name.contains(query.text, true) }
                }
            }
        }

    // Selection state (preserved with anchor logic)
    var inSelectMode by remember { mutableStateOf(false) }
    val selection = remember { mutableStateListOf<String>() }
    var selectionAnchorSongId by remember { mutableStateOf<String?>(null) }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
        selectionAnchorSongId = null
    }

    LaunchedEffect(filteredSongs) {
        selection.fastForEachReversed { songId ->
            if (filteredSongs.find { it.second.id == songId } == null) {
                selection.remove(songId)
            }
        }
        if (selectionAnchorSongId != null && filteredSongs.none { it.second.id == selectionAnchorSongId }) {
            selectionAnchorSongId = filteredSongs.firstOrNull { it.second.id in selection }?.second?.id
        }
    }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    // Like helper (matches the original logic)
    fun togglePlaylistLike(playlistItem: PlaylistItem, songItems: List<SongItem>, existingDb: Playlist?) {
        if (existingDb != null) {
            database.transaction {
                val currentPlaylist = existingDb.playlist
                update(currentPlaylist, playlistItem)
                update(currentPlaylist.toggleLike())
            }
        } else {
            coroutineScope.launch(Dispatchers.IO) {
                val playlistEntity = PlaylistEntity(
                    name = playlistItem.title,
                    browseId = playlistItem.id,
                    thumbnailUrl = playlistItem.thumbnail,
                    isEditable = playlistItem.isEditable,
                    remoteSongCount = playlistItem.songCountText?.let {
                        Regex("""\d+""").find(it)?.value?.toIntOrNull()
                    },
                    playEndpointParams = playlistItem.playEndpoint?.params,
                    shuffleEndpointParams = playlistItem.shuffleEndpoint?.params,
                    radioEndpointParams = playlistItem.radioEndpoint?.params,
                ).toggleLike()
                val songMetadata = songItems.map { it.toMediaMetadata() }
                database.withTransaction {
                    insert(playlistEntity)
                    songMetadata.onEach { insert(it) }
                    val songIds = songMetadata.map { it.id to it.setVideoId }
                    val createdPlaylist = database.playlistBlocking(playlistEntity.id)
                        ?: throw IllegalStateException("Failed to create playlist")
                    database.addSongsToPlaylist(createdPlaylist, songIds)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(
                top = headerTopPadding,
                bottom = listBottomPadding,
            ),
        ) {
            if (playlist == null || songs.isEmpty()) {
                if (isLoading) {
                    item(key = "loading_placeholder") {
                        Box(
                            modifier = Modifier
                                .fillParentMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            ContainedLoadingIndicator()
                        }
                    }
                } else if (error != null) {
                    item(key = "error_placeholder") {
                        Column(
                            modifier = Modifier
                                .fillParentMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = error ?: stringResource(R.string.error_unknown),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            androidx.compose.material3.TextButton(onClick = { viewModel.retry() }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                } else if (!isLoading && songs.isEmpty()) {
                    item(key = "empty_placeholder") {
                        Box(
                            modifier = Modifier
                                .fillParentMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.playlist_is_empty),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            } else {
                playlist?.let { playlistItem ->
                    if (!isSearching) {
                        // ===== HEADER =====
                        item(key = "playlist_header") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .drawBehind {
                                        drawRect(
                                            brush = Brush.verticalGradient(
                                                colorStops = arrayOf(
                                                    0f to (headerTint ?: headerFallbackColor).copy(alpha = scrollFade),
                                                    1f to Color.Transparent,
                                                ),
                                                startY = -headerTopExtendPx,
                                                endY = gradientEndPx,
                                            ),
                                            topLeft = Offset(0f, -headerTopExtendPx),
                                            size = Size(size.width, size.height + headerTopExtendPx),
                                        )
                                    }
                                    .padding(top = 8.dp, bottom = 29.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // Cover art
                                Surface(
                                    modifier = Modifier
                                        .size(240.dp)
                                        .alpha(scrollFade)
                                        .shadow(
                                            elevation = 24.dp,
                                            shape = RoundedCornerShape(3.dp),
                                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                        ),
                                    shape = RoundedCornerShape(3.dp),
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(playlistItem.thumbnail?.resize(1080, 1080))
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // Playlist title — left aligned
                                Text(
                                    text = playlistItem.title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Start,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .alpha(scrollFade)
                                        .padding(horizontal = 24.dp),
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Creator row — avatar + name, left aligned
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .alpha(scrollFade)
                                        .padding(horizontal = 24.dp)
                                        .combinedClickable(
                                            onClick = {
                                                val author = playlistItem.author
                                                if (author?.id != null) {
                                                    navController.navigate("artist/${author.id}")
                                                }
                                            },
                                        ),
                                ) {
                                    if (playlistItem.authorAvatarUrl != null) {
                                        AsyncImage(
                                            model = playlistItem.authorAvatarUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape),
                                        )
                                    }
                                    Text(
                                        text = playlistItem.author?.name ?: "",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Type label — same style as album "Album • year" row
                                Text(
                                    text = if (isPodcastPlaylist) "Podcast" else "Playlist",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Normal,
                                    textAlign = TextAlign.Start,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .alpha(scrollFade)
                                        .padding(horizontal = 24.dp),
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                // Action row — Spotify style
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // Bordered thumb
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(playlistItem.thumbnail?.resize(300, 300))
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                                    )

                                    // Library / like
                                    val liked = dbPlaylist?.playlist?.bookmarkedAt != null
                                    Surface(
                                        onClick = { togglePlaylistLike(playlistItem, songs, dbPlaylist) },
                                        color = Color.Transparent,
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                painter = painterResource(
                                                    if (liked) R.drawable.library_add_check else R.drawable.library_add,
                                                ),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onBackground,
                                                modifier = Modifier.size(26.dp),
                                            )
                                        }
                                    }

                                    // More menu
                                    Surface(
                                        onClick = {
                                            menuState.show {
                                                YouTubePlaylistMenu(
                                                    playlist = playlistItem,
                                                    songs = songs,
                                                    coroutineScope = coroutineScope,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                        color = Color.Transparent,
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                painter = painterResource(R.drawable.more_vert),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onBackground,
                                                modifier = Modifier.size(26.dp),
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.weight(1f))

                                    // Shuffle
                                    Surface(
                                        onClick = {
                                            if (!isListenTogetherGuest && songs.isNotEmpty()) {
                                                playerConnection.playQueue(
                                                    YouTubePlaylistQueue(
                                                        playlistId = playlistItem.id,
                                                        playlistTitle = playlistItem.title,
                                                        initialSongs = songs.shuffled(),
                                                        initialContinuation = viewModel.continuation,
                                                    ),
                                                )
                                                playerConnection.player.shuffleModeEnabled = true
                                            }
                                        },
                                        color = Color.Transparent,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                painter = painterResource(R.drawable.shuffle),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onBackground,
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                    }

                                    // Play / pause
                                    Surface(
                                        onClick = {
                                            if (!isListenTogetherGuest && songs.isNotEmpty()) {
                                                if (mediaMetadata?.id in songs.map { it.id }) {
                                                    playerConnection.togglePlayPause()
                                                } else {
                                                    playerConnection.playQueue(
                                                        YouTubePlaylistQueue(
                                                            playlistId = playlistItem.id,
                                                            playlistTitle = playlistItem.title,
                                                            initialSongs = songs,
                                                            initialContinuation = viewModel.continuation,
                                                        ),
                                                    )
                                                }
                                            }
                                        },
                                        color = Color.Transparent,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                painter = painterResource(
                                                    if (mediaMetadata?.id in songs.map { it.id } && isPlaying) R.drawable.pause else R.drawable.play,
                                                ),
                                                contentDescription = stringResource(R.string.play),
                                                tint = MaterialTheme.colorScheme.onBackground,
                                                modifier = Modifier.size(30.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ===== TRACK LIST (thumbnails kept → crop/black-bar feature applies) =====
                    itemsIndexed(filteredSongs) { index, (_, songItem) ->
                        val onCheckedChange: (Boolean) -> Unit = {
                            if (it) {
                                selection.add(songItem.id)
                            } else {
                                selection.remove(songItem.id)
                            }
                        }

                        YouTubeListItem(
                            item = songItem,
                            isActive = mediaMetadata?.id == songItem.id,
                            isPlaying = isPlaying,
                            isSelected = inSelectMode && songItem.id in selection,
                            modifier = Modifier
                                .combinedClickable(
                                    enabled = !hideExplicit || !songItem.explicit,
                                    onClick = {
                                        if (inSelectMode) {
                                            onCheckedChange(songItem.id !in selection)
                                        } else if (songItem.id == mediaMetadata?.id) {
                                            playerConnection.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                YouTubePlaylistQueue(
                                                    playlistId = playlistItem.id,
                                                    playlistTitle = playlistItem.title,
                                                    initialSongs = filteredSongs.map { it.second },
                                                    initialContinuation = viewModel.continuation,
                                                    startIndex = index,
                                                ),
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        if (!inSelectMode) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            inSelectMode = true
                                            onCheckedChange(true)
                                            selectionAnchorSongId = songItem.id
                                        } else {
                                            val anchorIndex = selectionAnchorSongId?.let { anchorId ->
                                                filteredSongs.indexOfFirst { it.second.id == anchorId }
                                            } ?: -1
                                            if (anchorIndex == -1) {
                                                onCheckedChange(true)
                                                selectionAnchorSongId = songItem.id
                                            } else {
                                                val range = if (anchorIndex <= index) anchorIndex..index else index..anchorIndex
                                                for (rangeIndex in range) {
                                                    val rangeSongId = filteredSongs[rangeIndex].second.id
                                                    if (rangeSongId !in selection) selection.add(rangeSongId)
                                                }
                                            }
                                        }
                                    },
                                ).animateItem(),
                            trailingContent = {
                                if (inSelectMode) {
                                    Checkbox(
                                        checked = songItem.id in selection,
                                        onCheckedChange = onCheckedChange,
                                    )
                                } else {
                                    IconButton(
                                        onClick = {
                                            menuState.show { YouTubeSongMenu(songItem, menuState::dismiss) }
                                        },
                                        onLongClick = {},
                                    ) {
                                        Icon(painterResource(R.drawable.more_vert), null)
                                    }
                                }
                            },
                        )
                    }

                    if (isLoadingMore) {
                        item(key = "loading_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                ContainedLoadingIndicator()
                            }
                        }
                    }
                }
            }
        }

        // ===== TOP BAR =====
        TopAppBar(
            modifier = Modifier
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            (headerTint ?: MaterialTheme.colorScheme.surfaceContainerHighest).copy(alpha = topBarFade),
                            MaterialTheme.colorScheme.background.copy(alpha = topBarFade),
                        ),
                    ),
                ),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            title = {
                if (inSelectMode) {
                    Text(
                        text = if (isPodcastPlaylist) {
                            pluralStringResource(R.plurals.n_episode, selection.size, selection.size)
                        } else {
                            pluralStringResource(R.plurals.n_song, selection.size, selection.size)
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                } else if (isSearching) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(topBarFade),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (topBarFade > 0f) {
                            Text(
                                text = playlist?.title.orEmpty(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.basicMarquee(),
                            )
                        }
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (isSearching) {
                            isSearching = false
                            query = TextFieldValue()
                        } else if (inSelectMode) {
                            onExitSelectionMode()
                        } else {
                            navController.navigateUp()
                        }
                    },
                    onLongClick = {
                        if (!isSearching && !inSelectMode) {
                            navController.backToMain()
                        }
                    },
                ) {
                    Icon(
                        painter = painterResource(
                            if (inSelectMode) R.drawable.close else R.drawable.arrow_back,
                        ),
                        contentDescription = null,
                    )
                }
            },
            actions = {
                if (inSelectMode) {
                    Checkbox(
                        checked = selection.size == filteredSongs.size && selection.isNotEmpty(),
                        onCheckedChange = {
                            if (selection.size == filteredSongs.size) selection.clear()
                            else { selection.clear(); selection.addAll(filteredSongs.map { it.second.id }) }
                        },
                    )
                    IconButton(
                        enabled = selection.isNotEmpty(),
                        onClick = {
                            menuState.show {
                                YouTubeSelectionSongMenu(
                                    songSelection = filteredSongs
                                        .filter { it.second.id in selection }
                                        .map { it.second },
                                    onDismiss = menuState::dismiss,
                                    clearAction = onExitSelectionMode,
                                )
                            }
                        },
                        onLongClick = {},
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null,
                        )
                    }
                } else if (isSearching) {
                    // no actions during search
                } else {
                    // Library/like button in top bar
                    playlist?.let { p ->
                        val liked = dbPlaylist?.playlist?.bookmarkedAt != null
                        Box(modifier = Modifier.alpha(topBarFade)) {
                            IconButton(
                                enabled = topBarFade > 0.5f,
                                onClick = { togglePlaylistLike(p, songs, dbPlaylist) },
                                onLongClick = {},
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (liked) R.drawable.library_add_check else R.drawable.library_add,
                                    ),
                                    contentDescription = null,
                                    tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}