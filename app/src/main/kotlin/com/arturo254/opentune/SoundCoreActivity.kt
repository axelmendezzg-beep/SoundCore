package com.arturo254.opentune

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.arturo254.opentune.playback.MusicService
import com.arturo254.opentune.playback.MusicService.MusicBinder
import com.arturo254.opentune.playback.PlayerConnection
import com.arturo254.opentune.playback.queues.YouTubeQueue
import com.arturo254.opentune.innertube.models.WatchEndpoint
import com.arturo254.opentune.innertube.YouTube
import com.arturo254.opentune.innertube.models.SongItem
import com.arturo254.opentune.innertube.models.AlbumItem
import com.arturo254.opentune.innertube.models.ArtistItem
import com.arturo254.opentune.db.MusicDatabase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

@AndroidEntryPoint
class SoundCoreActivity : ComponentActivity() {

    @Inject lateinit var database: MusicDatabase
    
    private lateinit var webView: WebView
    private var playerConnection: PlayerConnection? = null
    private var isMusicServiceBound = false
    private val activityScope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    private var lastMediaId: String? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            isMusicServiceBound = true
            if (service is MusicBinder) {
                val conn = PlayerConnection(this@SoundCoreActivity, service, database, CoroutineScope(Dispatchers.Main))
                playerConnection = conn
                
                runOnUiThread {
                    Toast.makeText(this@SoundCoreActivity, "¡Sistemas nativos acoplados! 🏎️", Toast.LENGTH_SHORT).show()
                }

                activityScope.launch {
                    conn.isPlaying.collectLatest { playing ->
                        webView.loadUrl("javascript:setPlaybackState($playing)")
                    }
                }

                startPlaybackTrackers()
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            isMusicServiceBound = false
            progressJob?.cancel()
            playerConnection?.dispose()
            playerConnection = null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()

        webView.addJavascriptInterface(SoundCoreBridge(), "SoundCoreBridge")
        webView.loadUrl("file:///android_asset/index.html")

        bindService(Intent(this, MusicService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun startPlaybackTrackers() {
        progressJob?.cancel()
        progressJob = activityScope.launch(Dispatchers.Main) {
            while (isActive) {
                playerConnection?.player?.let { p ->
                    if (p.isPlaying) {
                        val current = p.currentPosition / 1000
                        val duration = p.duration / 1000
                        if (duration > 0) {
                            webView.loadUrl("javascript:updatePlaybackProgress($current, $duration)")
                        }
                    }

                    val currentMediaItem = p.currentMediaItem
                    if (currentMediaItem != null) {
                        val mediaId = currentMediaItem.mediaId
                        if (mediaId != lastMediaId) {
                            lastMediaId = mediaId

                            val metadata = currentMediaItem.mediaMetadata
                            val title = metadata.title?.toString()?.replace("\"", "\\\"") ?: "Desconocido"
                            val artist = metadata.artist?.toString()?.replace("\"", "\\\"") ?: "Artista Desconocido"
                            val thumbnail = metadata.artworkUri?.toString() ?: ""
                            
                            val artistBrowseId = if (mediaId != null && (mediaId.startsWith("UC") || mediaId.startsWith("FM"))) mediaId else artist

                            val json = """
                                {
                                    "title": "$title",
                                    "artist": "$artist",
                                    "thumbnail": "$thumbnail",
                                    "artistBrowseId": "$artistBrowseId"
                                }
                            """.trimIndent()

                            val base64Json = Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                            webView.loadUrl("javascript:onTrackChangedFromNative('$base64Json')")
                        }
                    }
                }
                delay(1000)
            }
        }
    }

    override fun onDestroy() {
        if (isMusicServiceBound) {
            unbindService(serviceConnection)
            isMusicServiceBound = false
        }
        progressJob?.cancel()
        activityScope.cancel()
        playerConnection?.dispose()
        super.onDestroy()
    }

    inner class SoundCoreBridge {
        
        @JavascriptInterface
        fun playTrack(videoId: String) {
            runOnUiThread {
                if (playerConnection != null) {
                    val endpoint = WatchEndpoint(videoId = videoId, playlistId = null)
                    playerConnection?.playQueue(YouTubeQueue(endpoint))
                } else {
                    Toast.makeText(this@SoundCoreActivity, "Error: Motor de audio desconectado", Toast.LENGTH_LONG).show()
                }
            }
        }

        @JavascriptInterface
        fun togglePlayPause() {
            runOnUiThread {
                playerConnection?.player?.let { p ->
                    if (p.isPlaying) p.pause() else p.play()
                }
            }
        }

        @JavascriptInterface
        fun skipToNext() {
            runOnUiThread {
                playerConnection?.player?.seekToNext()
            }
        }

        @JavascriptInterface
        fun skipToPrevious() {
            runOnUiThread {
                playerConnection?.player?.seekToPrevious()
            }
        }

        @JavascriptInterface
        fun seekToPosition(seconds: Int) {
            runOnUiThread {
                playerConnection?.player?.seekTo((seconds * 1000).toLong())
            }
        }

        @JavascriptInterface
        fun searchTracks(query: String) {
            activityScope.launch(Dispatchers.IO) {
                YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).onSuccess { searchResult ->
                    val songs = searchResult.items.filterIsInstance<SongItem>()
                    
                    val jsonBuilder = StringBuilder("[")
                    songs.forEachIndexed { index, song ->
                        val title = song.title.replace("\"", "\\\"")
                        val artist = song.artists.joinToString { it.name }.replace("\"", "\\\"")
                        val allArtistIds = song.artists.map { it.id ?: "" }.joinToString(",")
                        val id = song.id
                        val thumbnail = song.thumbnail ?: ""

                        jsonBuilder.append("{")
                            .append("\"id\":\"$id\",")
                            .append("\"title\":\"$title\",")
                            .append("\"artist\":\"$artist\",")
                            .append("\"artistBrowseId\":\"$allArtistIds\",")
                            .append("\"thumbnail\":\"$thumbnail\"")
                            .append("}")
                        if (index < songs.size - 1) jsonBuilder.append(",")
                    }
                    jsonBuilder.append("]")
                    
                    val base64Json = Base64.encodeToString(jsonBuilder.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                    runOnUiThread {
                        webView.loadUrl("javascript:onSearchTracksResultEncoded('$base64Json')")
                    }
                }.onFailure { error ->
                    runOnUiThread {
                        Toast.makeText(this@SoundCoreActivity, "Fallo en el radar: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        @JavascriptInterface
        fun loadArtistDetails(browseId: String) {
            val rawId = browseId.trim()
            if (rawId.isEmpty()) return
            
            val cleanArtistId = if (rawId.contains(",")) {
                rawId.split(",").map { it.trim() }.firstOrNull { it.startsWith("UC") || it.startsWith("FM") } ?: ""
            } else {
                rawId
            }

            activityScope.launch(Dispatchers.IO) {
                if (cleanArtistId.isEmpty() || (!cleanArtistId.startsWith("UC") && !cleanArtistId.startsWith("FM"))) {
                    executeFallbackSearch(rawId)
                    return@launch
                }

                YouTube.artist(cleanArtistId).onSuccess { artistPage ->
                    try {
                        val artistItem = artistPage.artist
                        val title = artistItem.title
                        val thumb = artistItem.thumbnail ?: ""
                        val sections = artistPage.sections
                        
                        // Capturar canciones populares limpias
                        val songsSection = sections.firstOrNull { section ->
                            val lowerTitle = section.title.lowercase()
                            (lowerTitle.contains("canción") || lowerTitle.contains("canciones") || 
                             lowerTitle.contains("song") || lowerTitle.contains("top") || lowerTitle.contains("hits")) &&
                            !lowerTitle.contains("relacionado") && !lowerTitle.contains("similar") && !lowerTitle.contains("radio")
                        }

                        // Capturar sección de Álbumes
                        val albumsSection = sections.firstOrNull { section ->
                            val lowerTitle = section.title.lowercase()
                            (lowerTitle.contains("álbum") || lowerTitle.contains("album")) && !lowerTitle.contains("single") && !lowerTitle.contains("sencillo")
                        }

                        // Capturar sección de Sencillos/Singles
                        val singlesSection = sections.firstOrNull { section ->
                            val lowerTitle = section.title.lowercase()
                            lowerTitle.contains("single") || lowerTitle.contains("sencillo")
                        }

                        val rawSongs = songsSection?.items?.filterIsInstance<SongItem>() ?: emptyList()
                        val officialAlbums = albumsSection?.items?.filterIsInstance<AlbumItem>() ?: emptyList()
                        val officialSingles = singlesSection?.items?.filterIsInstance<AlbumItem>() ?: emptyList()

                        // Filtrado estricto anti-mezclas de otros artistas
                        var officialSongs = rawSongs.filter { song ->
                            song.artists.any { it.name.lowercase().contains(title.lowercase()) || title.lowercase().contains(it.name.lowercase()) }
                        }
                        
                        // Salvavidas para artistas pequeños (Miranda León/Omar): si "Canciones" viene vacío por la API, usamos sus sencillos
                        if (officialSongs.isEmpty()) {
                            if (officialSingles.isNotEmpty()) {
                                officialSongs = officialSingles.map { 
                                    SongItem(id = it.id, title = it.title, artists = listOf(ArtistItem(id = cleanArtistId, title = title)), thumbnail = it.thumbnail, duration = 0)
                                }
                            } else {
                                officialSongs = rawSongs
                            }
                        }

                        if (officialSongs.isNotEmpty() || officialAlbums.isNotEmpty() || officialSingles.isNotEmpty()) {
                            sendManualPayload(title, thumb, officialSongs, officialAlbums, officialSingles)
                        } else {
                            executeFallbackSearch(title)
                        }

                    } catch (e: Exception) {
                        executeFallbackSearch(cleanArtistId)
                    }
                }.onFailure { error ->
                    executeFallbackSearch(rawId)
                }
            }
        }

        private suspend fun executeFallbackSearch(corruptId: String) {
            val queryTarget = if (corruptId.trim().isNotEmpty() && corruptId.length != 11) {
                corruptId
            } else {
                withContext(Dispatchers.Main) {
                    playerConnection?.player?.currentMediaItem?.mediaMetadata?.artist?.toString() ?: ""
                }
            }

            if (queryTarget.isEmpty()) {
                triggerHtmlError("No se especificó un artista legítimo para buscar")
                return
            }

            YouTube.search(queryTarget, YouTube.SearchFilter.FILTER_ARTIST).onSuccess { searchResult ->
                val legitArtist = searchResult.items.filterIsInstance<ArtistItem>().firstOrNull()
                val trackName = legitArtist?.title ?: queryTarget
                val trackPhoto = legitArtist?.thumbnail ?: ""
                
                YouTube.artist(legitArtist?.id ?: "").onSuccess { fallbackPage ->
                    val sections = fallbackPage.sections
                    
                    val songsSection = sections.firstOrNull { s -> 
                        val l = s.title.lowercase()
                        (l.contains("can") || l.contains("song") || l.contains("top")) && !l.contains("radio") && !l.contains("simil")
                    }
                    val albumsSection = sections.firstOrNull { s -> (s.title.lowercase().contains("alb") || s.title.lowercase().contains("disc")) && !s.title.lowercase().contains("sing") && !s.title.lowercase().contains("senc") }
                    val singlesSection = sections.firstOrNull { s -> s.title.lowercase().contains("sing") || s.title.lowercase().contains("senc") }
                    
                    val rawSongs = songsSection?.items?.filterIsInstance<SongItem>() ?: emptyList()
                    var fSongs = rawSongs.filter { song ->
                        song.artists.any { it.name.lowercase().contains(trackName.lowercase()) || trackName.lowercase().contains(it.name.lowercase()) }
                    }
                    if (fSongs.isEmpty()) fSongs = rawSongs
                    
                    val fAlbums = albumsSection?.items?.filterIsInstance<AlbumItem>() ?: emptyList()
                    val fSingles = singlesSection?.items?.filterIsInstance<AlbumItem>() ?: emptyList()
                    
                    if (fSongs.isNotEmpty() || fAlbums.isNotEmpty() || fSingles.isNotEmpty()) {
                        sendManualPayload(trackName, trackPhoto, fSongs, fAlbums, fSingles)
                    } else {
                        executeSongSearchFallback(queryTarget, trackName, trackPhoto)
                    }
                }.onFailure {
                    executeSongSearchFallback(queryTarget, trackName, trackPhoto)
                }
            }.onFailure { error ->
                triggerHtmlError(error.message)
            }
        }

        private suspend fun executeSongSearchFallback(query: String, trackName: String, trackPhoto: String) {
            YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).onSuccess { songResult ->
                val rawSongs = songResult.items.filterIsInstance<SongItem>()
                sendManualPayload(trackName, trackPhoto, rawSongs, emptyList(), emptyList())
            }.onFailure { err ->
                triggerHtmlError(err.message)
            }
        }

        private suspend fun sendManualPayload(name: String, thumbnail: String, songs: List<SongItem>, albums: List<AlbumItem>, singles: List<AlbumItem>) {
            val tracksJsonBuilder = StringBuilder("[")
            songs.take(20).forEachIndexed { index, song ->
                val tTitle = song.title.replace("\"", "\\\"")
                val tArtist = song.artists.joinToString { it.name }.replace("\"", "\\\"")
                val tArtistIds = song.artists.map { it.id ?: "" }.joinToString(",")
                tracksJsonBuilder.append("{")
                    .append("\"id\":\"${song.id}\",")
                    .append("\"title\":\"$tTitle\",")
                    .append("\"artist\":\"$tArtist\",")
                    .append("\"artistBrowseId\":\"$tArtistIds\",")
                    .append("\"thumbnail\":\"${song.thumbnail ?: ""}\"")
                    .append("}")
                if (index < minOf(songs.size, 20) - 1) tracksJsonBuilder.append(",")
            }
            tracksJsonBuilder.append("]")

            val albumsJsonBuilder = StringBuilder("[")
            val cleanAlbums = albums.distinctBy { it.title }
            cleanAlbums.forEachIndexed { index, album ->
                val aTitle = album.title.replace("\"", "\\\"")
                albumsJsonBuilder.append("{")
                    .append("\"title\":\"$aTitle\",")
                    .append("\"year\":\"${album.year ?: ""}\",")
                    .append("\"thumbnail\":\"${album.thumbnail ?: ""}\"")
                    .append("}")
                if (index < cleanAlbums.size - 1) albumsJsonBuilder.append(",")
            }
            albumsJsonBuilder.append("]")

            val singlesJsonBuilder = StringBuilder("[")
            val cleanSingles = singles.distinctBy { it.title }
            cleanSingles.forEachIndexed { index, single ->
                val sTitle = single.title.replace("\"", "\\\"")
                singlesJsonBuilder.append("{")
                    .append("\"title\":\"$sTitle\",")
                    .append("\"year\":\"${single.year ?: ""}\",")
                    .append("\"thumbnail\":\"${single.thumbnail ?: ""}\"")
                    .append("}")
                if (index < cleanSingles.size - 1) singlesJsonBuilder.append(",")
            }
            singlesJsonBuilder.append("]")

            val finalJson = "{\"name\":\"${name.replace("\"", "\\\"")}\",\"thumbnail\":\"$thumbnail\",\"tracks\":$tracksJsonBuilder,\"albums\":$albumsJsonBuilder,\"singles\":$singlesJsonBuilder}"
            val base64Json = Base64.encodeToString(finalJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            
            withContext(Dispatchers.Main) {
                webView.loadUrl("javascript:onArtistDetailsResultEncoded('$base64Json')")
            }
        }

        private suspend fun triggerHtmlError(message: String?) {
            withContext(Dispatchers.Main) {
                val errorMsg = message ?: "Error de sincronización con InnerTube"
                val safeError = errorMsg.replace("\"", "\\\"").replace("'", "\\'")
                webView.loadUrl("javascript:onArtistDetailsError('$safeError')")
 }
        }
    }
}
