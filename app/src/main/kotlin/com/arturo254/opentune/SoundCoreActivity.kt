package com.arturo254.opentune

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
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
import com.arturo254.opentune.db.MusicDatabase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SoundCoreActivity : ComponentActivity() {

    @Inject lateinit var database: MusicDatabase
    
    private lateinit var webView: WebView
    private var playerConnection: PlayerConnection? = null
    private var isMusicServiceBound = false
    private val activityScope = CoroutineScope(Dispatchers.Main)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            isMusicServiceBound = true
            if (service is MusicBinder) {
                playerConnection = PlayerConnection(this@SoundCoreActivity, service, database, CoroutineScope(Dispatchers.Main))
                runOnUiThread {
                    Toast.makeText(this@SoundCoreActivity, "¡Sistemas listos para la acción! 🏎️", Toast.LENGTH_SHORT).show()
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            isMusicServiceBound = false
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

    override fun onDestroy() {
        if (isMusicServiceBound) {
            unbindService(serviceConnection)
            isMusicServiceBound = false
        }
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
        fun searchTracks(query: String) {
            activityScope.launch(Dispatchers.IO) {
                // Usamos la búsqueda directa con el filtro de canciones (Song) de la librería de Arturo
                YouTube.search(query, YouTube.SearchFilter.Song).onSuccess { itemsPage ->
                    // Las listas de items en la app de Arturo usan .items
                    val items = itemsPage?.items ?: emptyList()
                    
                    val jsonBuilder = StringBuilder("[")
                    items.forEachIndexed { index, item ->
                        val title = item.title.replace("\"", "\\\"")
                        // Mapeamos los artistas de forma segura
                        val artist = item.artists.joinToString { it.name }.replace("\"", "\\\"")
                        val id = item.id
                        val thumbnail = item.thumbnail

                        jsonBuilder.append("{")
                            .append("\"id\":\"$id\",")
                            .append("\"title\":\"$title\",")
                            .append("\"artist\":\"$artist\",")
                            .append("\"thumbnail\":\"$thumbnail\"")
                            .append("}")
                        if (index < items.size - 1) jsonBuilder.append(",")
                    }
                    jsonBuilder.append("]")
                    val finalJson = jsonBuilder.toString()

                    runOnUiThread {
                        webView.loadUrl("javascript:onSearchTracksResult('$finalJson')")
                    }
                }.onFailure { error ->
                    runOnUiThread {
                        Toast.makeText(this@SoundCoreActivity, "Fallo en el radar: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
