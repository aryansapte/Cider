/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class AlbumViewModel
@Inject
constructor(
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val albumId = savedStateHandle.get<String>("albumId")!!
    val playlistId = MutableStateFlow("")
    val albumWithSongs =
        database
            .albumWithSongs(albumId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    var otherVersions = MutableStateFlow<List<AlbumItem>>(emptyList())
    val moreByArtist = MutableStateFlow<List<AlbumItem>>(emptyList())
    val moreByArtistName = MutableStateFlow("")
    val moreByArtistLoaded = MutableStateFlow(false)

    companion object {
        private val moreByCache = mutableMapOf<String, List<AlbumItem>>()
        private val moreByNameCache = mutableMapOf<String, String>()
    }

    init {
        viewModelScope.launch {
            val album = database.album(albumId).first()
            YouTube
                .album(albumId)
                .onSuccess {
                    playlistId.value = it.album.playlistId
                    otherVersions.value = it.otherVersions
                    database.transaction {
                        if (album == null) {
                            insert(it)
                        } else {
                            update(album.album, it, album.artists)
                        }
                    }
                }
                .onFailure {
                    reportException(it)
                    if (it.message?.contains("NOT_FOUND") == true) {
                        database.query {
                            album?.album?.let(::delete)
                        }
                    }
                }
        }

        // "More by artist": try each artist in order until one has items to show.
        viewModelScope.launch {
            val album = withTimeoutOrNull(6000) {
                albumWithSongs.first { it != null && it.artists.isNotEmpty() }
            }
            if (album != null) {
                for (artist in album.artists) {
                    val cached = moreByCache[artist.id]
                    val items: List<AlbumItem> =
                        if (cached != null) {
                            cached
                        } else {
                            val page = runCatching {
                                withTimeoutOrNull(6000) { YouTube.artist(artist.id) }?.getOrNull()
                            }.getOrNull()
                            val list =
                                page
                                    ?.sections
                                    ?.flatMap { it.items }
                                    ?.filterIsInstance<AlbumItem>()
                                    ?.filter { it.id != albumId }
                                    ?.distinctBy { it.id }
                                    ?.take(8)
                                    ?: emptyList()
                            moreByCache[artist.id] = list
                            moreByNameCache[artist.id] = artist.name
                            list
                        }
                    if (items.isNotEmpty()) {
                        moreByArtistName.value = moreByNameCache[artist.id] ?: artist.name
                        moreByArtist.value = items
                        break
                    }
                }
            }
            moreByArtistLoaded.value = true
        }

        // Hard safety: the page NEVER waits longer than 1.5s.
        viewModelScope.launch {
            delay(1500)
            moreByArtistLoaded.value = true
        }
    }
}