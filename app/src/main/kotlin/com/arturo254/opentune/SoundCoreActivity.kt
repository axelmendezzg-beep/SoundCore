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

    // Variable para rastrear el ID multimedia actual y evitar bucles innecesarios en el WebView
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

                // Sincroniza el estado de reproducción (Play/Pause)
                activityScope.launch {
                    conn.isPlaying.collectLatest { playing ->
                        webView.loadUrl("javascript:setPlaybackState($playing)")
                    }
                }

                // 🔥 CORRECCIÓN CLAVE: Rastreador cíclico seguro acoplado al bucle de progreso
                // En lugar de recolectar un flujo inexistente en conn, leemos directamente del player nativo
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
                    // 1. Sincronizar Progreso de la barra de reproducción
                    if (p.isPlaying) {
                        val current = p.currentPosition / 1000
                        val duration = p.duration / 1000
                        if (duration > 0) {
                            webView.loadUrl("javascript:updatePlaybackProgress($current, $duration)")
                        }
                    }

                    // 2. 🔥 Sincronizar Metadatos cuando cambia la pista (Next/Prev) desde el sistema/notificación
                    val currentMediaItem = p.currentMediaItem
                    if (currentMediaItem != null) {
                        val mediaId = currentMediaItem.mediaId
                        if (mediaId != lastMediaId) {
                            lastMediaId = mediaId

                            val metadata = currentMediaItem.mediaMetadata
                            val title = metadata.title?.toString()?.replace("\"", "\\\"") ?: "Desconocido"
                            val artist = metadata.artist?.toString()?.replace("\"", "\\\"") ?: "Artista Desconocido"
                            val thumbnail = metadata.artworkUri?.toString() ?: ""
                            
                            // Pasamos el ID que usa Arturo para identificar el recurso o el artista
                            val artistBrowseId = mediaId ?: ""

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
                rawId.split(",").firstOrNull { it.trim().isNotEmpty() }?.trim() ?: rawId
            } else {
                rawId
            }

            activityScope.launch(Dispatchers.IO) {
                YouTube.artist(cleanArtistId).onSuccess { artistPage ->
                    val nombreReal = artistPage.artist.title.replace("\"", "\\\"")
                    val fotoReal = artistPage.artist.thumbnail ?: ""
                    
                    val songItems = artistPage.sections.flatMap { it.items }.filterIsInstance<SongItem>()
                    val albumItems = artistPage.sections.flatMap { it.items }.filterIsInstance<AlbumItem>()
                    
                    val tracksJsonBuilder = StringBuilder("[")
                    songItems.forEachIndexed { index, song ->
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
                        if (index < songItems.size - 1) tracksJsonBuilder.append(",")
                    }
                    tracksJsonBuilder.append("]")

                    val albumsJsonBuilder = StringBuilder("[")
                    albumItems.forEachIndexed { index, album ->
                        val aTitle = album.title.replace("\"", "\\\"")
                        albumsJsonBuilder.append("{")
                            .append("\"title\":\"$aTitle\",")
                            .append("\"year\":\"${album.year ?: ""}\",")
                            .append("\"thumbnail\":\"${album.thumbnail ?: ""}\"")
                            .append("}")
                        if (index < albumItems.size - 1) albumsJsonBuilder.append(",")
                    }
                    albumsJsonBuilder.append("]")

                    val finalJson = "{\"name\":\"$nombreReal\",\"thumbnail\":\"$fotoReal\",\"tracks\":$tracksJsonBuilder,\"albums\":$albumsJsonBuilder}"
                    val base64Json = Base64.encodeToString(finalJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                    
                    runOnUiThread {
                        webView.loadUrl("javascript:onArtistDetailsResultEncoded('$base64Json')")
                    }
                }.onFailure { error ->
                    runOnUiThread {
                        val errorMsg = error.message ?: "Error de red"
                        val safeError = errorMsg.replace("\"", "\\\"").replace("'", "\\'")
                        webView.loadUrl("javascript:onArtistDetailsError('$safeError')")
                    }
                }
            }
        }
    }
}
