/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachReversed
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.db.entities.Album
import com.metrolist.music.playback.ExoDownloadService
import com.metrolist.music.playback.queues.LocalAlbumRadio
import com.metrolist.music.ui.component.ClickableArtistText
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.component.YouTubeGridItem
import com.metrolist.music.ui.menu.AlbumMenu
import com.metrolist.music.ui.menu.SelectionSongMenu
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.ui.menu.YouTubeAlbumMenu
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.ui.utils.resize
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.AlbumViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlbumScreen(
    navController: NavController,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val listenTogetherManager = LocalListenTogetherManager.current
    val isListenTogetherGuest = listenTogetherManager?.let { it.isInRoom && !it.isHost } ?: false

    val scope = rememberCoroutineScope()

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()



    val playlistId by viewModel.playlistId.collectAsStateWithLifecycle()
    val albumWithSongs by viewModel.albumWithSongs.collectAsStateWithLifecycle()
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val hideVideoSongs by rememberPreference(key = HideVideoSongsKey, defaultValue = false)

    val filteredSongs =
        remember(albumWithSongs, hideExplicit, hideVideoSongs) {
            var songs = albumWithSongs?.songs ?: emptyList()
            if (hideExplicit) songs = songs.filter { !it.song.explicit }
            if (hideVideoSongs) songs = songs.filter { !it.song.isVideo }
            songs
        }

    var headerTint by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(albumWithSongs) {
        val currentAlbum = albumWithSongs ?: return@LaunchedEffect
        runCatching {
            val request = ImageRequest.Builder(context)
                .data(currentAlbum.album.thumbnailUrl)
                .allowHardware(false)
                .build()
            val bitmap = context.imageLoader.execute(request).image?.toBitmap() ?: return@runCatching null
            val palette = Palette.from(bitmap).generate()
            Color(palette.getMutedColor(palette.getDominantColor(0xFF3A3A3A.toInt())))
        }.getOrNull()?.let { color -> headerTint = color }
    }

    val moreByArtist by viewModel.moreByArtist.collectAsStateWithLifecycle()
    val moreByArtistName by viewModel.moreByArtistName.collectAsStateWithLifecycle()
    val moreByArtistLoaded by viewModel.moreByArtistLoaded.collectAsStateWithLifecycle()

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

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection =
        rememberSaveable(
            saver = listSaver<MutableList<String>, String>(
                save = { it.toList() },
                restore = { it.toMutableStateList() },
            ),
        ) { mutableStateListOf() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }
    if (inSelectMode) BackHandler(onBack = onExitSelectionMode)

    LaunchedEffect(filteredSongs) {
        selection.fastForEachReversed { songId ->
            if (filteredSongs.find { it.id == songId } == null) selection.remove(songId)
        }
    }

    val downloadUtil = LocalDownloadUtil.current
    var downloadState by remember { mutableIntStateOf(Download.STATE_STOPPED) }

    LaunchedEffect(albumWithSongs) {
        val songs = albumWithSongs?.songs?.map { it.id }
        if (songs.isNullOrEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it]?.state == Download.STATE_COMPLETED }) Download.STATE_COMPLETED
                else if (songs.all {
                        downloads[it]?.state == Download.STATE_QUEUED ||
                                downloads[it]?.state == Download.STATE_DOWNLOADING ||
                                downloads[it]?.state == Download.STATE_COMPLETED
                    }) Download.STATE_DOWNLOADING
                else Download.STATE_STOPPED
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(top = headerTopPadding, bottom = listBottomPadding),
        ) {
            val albumWithSongs = albumWithSongs
            if (albumWithSongs != null && albumWithSongs.songs.isNotEmpty() && moreByArtistLoaded) {
                item(key = "album_header") {
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
                                model = albumWithSongs.album.thumbnailUrl?.resize(1080, 1080),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = albumWithSongs.album.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Start,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().alpha(scrollFade).padding(horizontal = 24.dp),
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        ClickableArtistText(
                            artists = albumWithSongs.artists,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Start,
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = Int.MAX_VALUE,
                            modifier = Modifier.fillMaxWidth().alpha(scrollFade).padding(horizontal = 24.dp),
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        val releaseType = when {
                            albumWithSongs.songs.size <= 3 -> "Single"
                            albumWithSongs.songs.size <= 6 -> "EP"
                            else -> "Album"
                        }
                        Text(
                            text = buildString {
                                append(releaseType)
                                albumWithSongs.album.year?.let { append(" • ").append(it) }
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Start,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth().alpha(scrollFade).padding(horizontal = 24.dp),
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = albumWithSongs.album.thumbnailUrl?.resize(300, 300),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                            )

                            Surface(
                                onClick = { database.query { update(albumWithSongs.album.toggleLike()) } },
                                color = Color.Transparent,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        painter = painterResource(
                                            if (albumWithSongs.album.bookmarkedAt != null) R.drawable.library_add_check else R.drawable.library_add,
                                        ),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                            }

                            Surface(
                                onClick = {
                                    if (downloadState == Download.STATE_COMPLETED || downloadState == Download.STATE_DOWNLOADING) {
                                        albumWithSongs.songs.forEach { song ->
                                            DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, song.id, false)
                                        }
                                    } else {
                                        albumWithSongs.songs.forEach { song ->
                                            val downloadRequest = DownloadRequest
                                                .Builder(song.id, song.id.toUri())
                                                .setCustomCacheKey(song.id)
                                                .setData(song.song.title.toByteArray())
                                                .build()
                                            DownloadService.sendAddDownload(context, ExoDownloadService::class.java, downloadRequest, false)
                                        }
                                    }
                                },
                                color = Color.Transparent,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    when (downloadState) {
                                        Download.STATE_COMPLETED -> Icon(
                                            painter = painterResource(R.drawable.offline),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier.size(26.dp),
                                        )
                                        Download.STATE_DOWNLOADING, Download.STATE_QUEUED -> CircularProgressIndicator(
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(26.dp),
                                        )
                                        else -> Icon(
                                            painter = painterResource(R.drawable.download),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                            modifier = Modifier.size(26.dp),
                                        )
                                    }
                                }
                            }

                            Surface(
                                onClick = {
                                    menuState.show {
                                        AlbumMenu(originalAlbum = Album(albumWithSongs.album, albumWithSongs.artists), onDismiss = menuState::dismiss)
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

                            Surface(
                                onClick = {
                                    if (!isListenTogetherGuest) {
                                        playerConnection.service.getAutomix(playlistId)
                                        playerConnection.playQueue(LocalAlbumRadio(albumWithSongs))
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

                            Surface(
                                onClick = {
                                    if (!isListenTogetherGuest) {
                                        if (mediaMetadata?.album?.id == albumWithSongs.album.id) {
                                            playerConnection.togglePlayPause()
                                        } else {
                                            playerConnection.service.getAutomix(playlistId)
                                            playerConnection.playQueue(LocalAlbumRadio(albumWithSongs))
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
                                            if (mediaMetadata?.album?.id == albumWithSongs.album.id && isPlaying) R.drawable.pause else R.drawable.play,
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

                if (filteredSongs.isNotEmpty()) {
                    itemsIndexed(
                        items = filteredSongs,
                        key = { index, song -> "${song.id}_$index" },
                    ) { index, song ->
                        val onCheckedChange: (Boolean) -> Unit = { checked ->
                            if (checked) selection.add(song.id) else selection.remove(song.id)
                        }

                        SongListItem(
                            song = song,
                            subtitleOverride = song.artists.joinToString { it.name },
                            showThumbnail = false,
                            isActive = song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            showInLibraryIcon = true,
                            trailingContent = {
                                if (inSelectMode) {
                                    Checkbox(
                                        checked = song.id in selection,
                                        onCheckedChange = onCheckedChange,
                                    )
                                } else {
                                    IconButton(onClick = {
                                        menuState.show { SongMenu(originalSong = song, onDismiss = menuState::dismiss) }
                                    }, onLongClick = {}) {
                                        Icon(painter = painterResource(R.drawable.more_vert), contentDescription = null)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .combinedClickable(
                                    onClick = {
                                        if (inSelectMode) {
                                            onCheckedChange(song.id !in selection)
                                        } else if (!isListenTogetherGuest) {
                                            if (song.id == mediaMetadata?.id) {
                                                playerConnection.togglePlayPause()
                                            } else {
                                                playerConnection.service.getAutomix(playlistId)
                                                playerConnection.playQueue(LocalAlbumRadio(albumWithSongs, startIndex = index))
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (!inSelectMode) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            inSelectMode = true
                                            onCheckedChange(true)
                                        }
                                    },
                                ),
                        )
                    }
                }

                if (moreByArtist.isNotEmpty()) {
                    item(key = "more_by_artist_title") {
                        NavigationTitle(title = "More by $moreByArtistName", modifier = Modifier.animateItem())
                    }
                    item(key = "more_by_artist_list") {
                        LazyRow(
                            contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues(),
                        ) {
                            items(items = moreByArtist, key = { "more_by_${it.id}" }) { item ->
                                YouTubeGridItem(
                                    item = item,
                                    isActive = mediaMetadata?.album?.id == item.id,
                                    isPlaying = isPlaying,
                                    coroutineScope = scope,
                                    modifier = Modifier
                                        .combinedClickable(
                                            onClick = { navController.navigate("album/${item.id}") },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show { YouTubeAlbumMenu(albumItem = item, onDismiss = menuState::dismiss) }
                                            },
                                        ).animateItem(),
                                )
                            }
                        }
                    }
                }
            } else {
                item(key = "loading") {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        ContainedLoadingIndicator()
                    }
                }
            }
        }

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
                    Text(pluralStringResource(R.plurals.n_selected, selection.size, selection.size))
                } else {
                    Box(modifier = Modifier.fillMaxWidth().alpha(topBarFade), contentAlignment = Alignment.Center) {
                        Text(
                            text = albumWithSongs?.album?.title.orEmpty(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.basicMarquee(),
                        )
                    }
                }
            },
            navigationIcon = {
                if (inSelectMode) {
                    IconButton(onClick = onExitSelectionMode, onLongClick = {}) {
                        Icon(painter = painterResource(R.drawable.close), contentDescription = null)
                    }
                } else {
                    IconButton(
                        onClick = { navController.navigateUp() },
                        onLongClick = { navController.backToMain() },
                    ) {
                        Icon(painter = painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                }
            },
            actions = {
                if (inSelectMode) {
                    Checkbox(
                        checked = selection.size == filteredSongs.size && selection.isNotEmpty(),
                        onCheckedChange = {
                            if (selection.size == filteredSongs.size) selection.clear()
                            else { selection.clear(); selection.addAll(filteredSongs.map { it.id }) }
                        },
                    )
                    IconButton(
                        enabled = selection.isNotEmpty(),
                        onClick = {
                            menuState.show {
                                SelectionSongMenu(
                                    songSelection = selection.mapNotNull { songId -> filteredSongs.find { it.id == songId } },
                                    onDismiss = menuState::dismiss,
                                    clearAction = onExitSelectionMode,
                                )
                            }
                        },
                        onLongClick = {},
                    ) {
                        Icon(painter = painterResource(R.drawable.more_vert), contentDescription = null)
                    }
                } else {
                    albumWithSongs?.let { album ->
                        val liked = album.album.bookmarkedAt != null
                        Box(modifier = Modifier.alpha(topBarFade)) {
                            IconButton(
                                enabled = topBarFade > 0.5f,
                                onClick = { database.query { update(album.album.toggleLike()) } },
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
            }
        )
    }
}