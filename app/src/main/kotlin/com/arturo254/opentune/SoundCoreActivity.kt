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

    // El cable invisible que se amarra al reproductor nativo de la app
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            isMusicServiceBound = true
            if (service is MusicBinder) {
                // Clonamos el amarre exacto que hace Arturo con la base de datos y el ciclo de vida
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

        // Registramos el puente con el nuevo método
        webView.addJavascriptInterface(SoundCoreBridge(), "SoundCoreBridge")
        webView.loadUrl("file:///android_asset/index.html")

        // Arrancamos y vinculamos el servicio de música nativo en chinga
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

    // El puente que recibe los gritos de tu HTML
    inner class SoundCoreBridge {
        @JavascriptInterface
        fun playTrack(videoId: String) {
            runOnUiThread {
                if (playerConnection != null) {
                    Toast.makeText(this@SoundCoreActivity, "Inyectando a NewPipe: $videoId", Toast.LENGTH_SHORT).show()
                    // ¡LA LLAVE MAESTRA! Le metemos el ID a la cola nativa de Arturo
                    playerConnection?.playQueue(YouTubeQueue(videoId))
                } else {
                    Toast.makeText(this@SoundCoreActivity, "Error: Motor de audio desconectado", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
