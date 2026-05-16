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

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            isMusicServiceBound = true
            if (service is MusicBinder) {
                playerConnection = PlayerConnection(this@SoundCoreActivity, service, database, CoroutineScope(Dispatchers.Main))
                runOnUiThread {
                    Toast.makeText(this@SoundCoreActivity, "¡Motor de Audio Vinculado! 🏎️", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this@SoundCoreActivity, "Inyectando a NewPipe: $videoId", Toast.LENGTH_SHORT).show()
                    
                    // Convertimos el String a un WatchEndpoint real con el ID del video y playlist nula
                    val endpoint = WatchEndpoint(videoId = videoId, playlistId = null)
                    
                    // Ahora sí, le pasamos el objeto exacto que el motor quiere masticar
                    playerConnection?.playQueue(YouTubeQueue(endpoint))
                } else {
                    Toast.makeText(this@SoundCoreActivity, "Error: Motor de audio desconectado", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
