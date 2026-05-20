package com.soundcore.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.Base64
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.soundcore.app.client.SoundCoreBridge
import com.soundcore.app.parsers.SearchParser
import com.soundcore.app.utils.SoundCoreExtractor
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var exoPlayer: ExoPlayer? = null

    private fun logToConsole(msg: String) {
        runOnUiThread { 
            val escaped = msg.replace("'", "\\'").replace("\n", " ")
            webView.evaluateJavascript("console.log('Android: $escaped')", null) 
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)
        
        exoPlayer = ExoPlayer.Builder(this).build()

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()

        val bridge = SoundCoreBridge(
            onSearchTrack = { _, _ -> },
            onPlayTrack = { id, title, artist, thumbnail ->
                procesarFlujoDeAudio(id, title)
            }
        )
        webView.addJavascriptInterface(bridge, "SoundCoreNative")

        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun search(query: String, callbackId: String) {
                logToConsole("Buscando pistas para: $query")
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val jsonResult = SearchParser().searchTracks(query)
                        val base64Result = Base64.encodeToString(jsonResult.toByteArray(), Base64.NO_WRAP)
                        withContext(Dispatchers.Main) {
                            webView.evaluateJavascript("javascript:SoundCoreResponse.handle('$callbackId', '$base64Result')", null)
                        }
                    } catch (e: Exception) {
                        logToConsole("Error en motor de búsqueda: ${e.message}")
                    }
                }
            }
        }, "SoundCoreSearchBridge")

        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun procesarFlujoDeAudio(id: String, title: String) {
        logToConsole("Bypasseando integridad de Google para: $title")
        CoroutineScope(Dispatchers.IO).launch {
            val urlDirecta = SoundCoreExtractor.extraerStreamNativo(id)
            
            withContext(Dispatchers.Main) {
                if (urlDirecta != null) {
                    logToConsole("¡Bypass completado con éxito! Cargando ExoPlayer...")
                    exoPlayer?.stop()
                    exoPlayer?.clearMediaItems()
                    
                    val mediaItem = MediaItem.fromUri(urlDirecta)
                    exoPlayer?.setMediaItem(mediaItem)
                    exoPlayer?.playWhenReady = true
                    exoPlayer?.prepare()
                    
                    Toast.makeText(this@MainActivity, "Sonando: $title", Toast.LENGTH_SHORT).show()
                } else {
                    logToConsole("Error crítico: El bypass fue rechazado por el servidor de YouTube.")
                    Toast.makeText(this@MainActivity, "Error de decodificación", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
    }
}
