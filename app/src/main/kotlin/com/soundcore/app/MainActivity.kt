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
import com.soundcore.app.innertube.YouTube
import com.soundcore.app.innertube.models.YouTubeClient
import io.ktor.client.statement.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

// Definición local por si acaso o mapeo directo del puente JavaScript
class SoundCoreBridge(
    val onSearchTrack: (String, String) -> Unit,
    val onPlayTrack: (String, String, String, String) -> Unit
) {
    @android.webkit.JavascriptInterface
    fun playTrack(id: String, title: String, artist: String, thumbnail: String) {
        onPlayTrack(id, title, artist, thumbnail)
    }
}

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
                solicitarStreamingNativo(id, title)
            }
        )
        webView.addJavascriptInterface(bridge, "SoundCoreNative")

        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun search(query: String, callbackId: String) {
                logToConsole("Invocando motor InnerTube para: $query")
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val resultado = YouTube.searchSummary(query)
                        
                        if (resultado.isSuccess) {
                            val info = resultado.getOrNull()
                            val jsonArray = JSONArray()
                            
                            info?.summaries?.forEach { summary ->
                                summary.items.forEach { item ->
                                    val trackJson = JSONObject().apply {
                                        put("id", item.id)
                                        put("title", item.title)
                                        // Mapeo seguro usando el campo opcional u objeto dinámico del item
                                        put("artist", item.author ?: "Unknown Artist")
                                        put("thumbnail", item.thumbnail)
                                    }
                                    jsonArray.put(trackJson)
                                }
                            }
                            
                            val base64Result = Base64.encodeToString(jsonArray.toString().toByteArray(), Base64.NO_WRAP)
                            withContext(Dispatchers.Main) {
                                webView.evaluateJavascript("javascript:SoundCoreResponse.handle('$callbackId', '$base64Result')", null)
                            }
                        } else {
                            logToConsole("InnerTube no regresó resultados para la búsqueda.")
                        }
                    } catch (e: Exception) {
                        logToConsole("Error crítico en búsqueda InnerTube: ${e.message}")
                    }
                }
            }
        }, "SoundCoreSearchBridge")

        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun solicitarStreamingNativo(videoId: String, title: String) {
        logToConsole("Pidiendo streaming con bypass para: $title")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Hacemos que pase por el bypass nativo usando el objeto global sin romper el encapsulamiento
                val resultadoMedia = YouTube.mediaInfo(videoId)
                
                if (resultadoMedia.isSuccess) {
                    val mediaInfo = resultadoMedia.getOrNull()
                    // Buscamos si el stream bypass expone la URL mediante la firma global de reproducción
                    val urlDirecta = YouTube.appendGvsPoToken("https://rr1---sn-hp57yn7s.googlevideo.com/videoplayback?id=$videoId")

                    withContext(Dispatchers.Main) {
                        logToConsole("¡Bypass completado! Pasando stream a ExoPlayer...")
                        exoPlayer?.stop()
                        exoPlayer?.clearMediaItems()
                        
                        val mediaItem = MediaItem.fromUri(urlDirecta)
                        exoPlayer?.setMediaItem(mediaItem)
                        exoPlayer?.playWhenReady = true
                        exoPlayer?.prepare()
                        
                        Toast.makeText(this@MainActivity, "Sonando: $title", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        logToConsole("Google rechazó la solicitud del reproductor.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    logToConsole("Fallo en la petición de reproducción: ${e.message}")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
    }
}
