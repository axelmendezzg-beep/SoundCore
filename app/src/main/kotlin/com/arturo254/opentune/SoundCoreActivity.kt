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

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            isMusicServiceBound = true
            if (service is MusicBinder) {
                val conn = PlayerConnection(this@SoundCoreActivity, service, database, CoroutineScope(Dispatchers.Main))
                playerConnection = conn
                
                runOnUiThread {
                    Toast.makeText(this@SoundCoreActivity, "¡Sistemas nativos acoplados! 🏎️", Toast.LENGTH_SHORT).show()
                }

                // 📡 Hilo 1: Escucha si pausaste o le diste play desde fuera de la app o barra de notificaciones
                activityScope.launch {
                    conn.isPlaying.collectLatest { playing ->
                        webView.loadUrl("javascript:setPlaybackState($playing)")
                    }
                }

                // 🕒 Hilo 2: Activa el reloj que manda los segundos reales a tu barra de progreso
                startProgressTracker()
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

    // Monitorea el segundo exacto del reproductor de Arturo cada 1000 milisegundos
    private fun startProgressTracker() {
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

        // --- 🎛️ NUEVOS PUENTES: CONTROLES REALES DE AUDIO ---
        
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
                        val id = song.id
                        val thumbnail = song.thumbnail ?: ""

                        jsonBuilder.append("{")
                            .append("\"id\":\"$id\",")
                            .append("\"title\":\"$title\",")
                            .append("\"artist\":\"$artist\",")
                            .append("\"thumbnail\":\"$thumbnail\"")
                            .append("}")
                        if (index < songs.size - 1) jsonBuilder.append(",")
                    }
                    jsonBuilder.append("]")
                    val finalJson = jsonBuilder.toString()
                    
                    val base64Json = Base64.encodeToString(finalJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

                    runOnUiThread {
                        webView.loadUrl("javascript:onSearchTracksResultEncoded('$base64Json')")
                    }
                }.onFailure { error ->
                    runOnUiThread {
                        Toast.makeText(this@SoundCoreActivity, "Fallo en el radar nativo: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
