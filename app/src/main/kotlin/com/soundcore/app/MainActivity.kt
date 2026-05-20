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
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

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
                logToConsole("Invocando buscador de InnerTube para: $query")
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
                                        put("artist", "YouTube Artist") 
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
                            logToConsole("InnerTube no regresó resultados.")
                        }
                    } catch (e: Exception) {
                        logToConsole("Error en búsqueda: ${e.message}")
                    }
                }
            }
        }, "SoundCoreSearchBridge")

        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun solicitarStreamingNativo(videoId: String, title: String) {
        logToConsole("Iniciando bypass de reproducción para: $title")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Le pedimos al motor que extraiga los streams reales del video usando su lógica interna
                val streamResult = YouTube.streams(videoId)
                
                if (streamResult.isSuccess) {
                    val adaptiveStreams = streamResult.getOrNull()
                    // Buscamos el primer stream de audio disponible que nos regrese ArchiveTune
                    val audioStream = adaptiveStreams?.firstOrNull { it.isAudio }
                    
                    if (audioStream != null) {
                        // Le inyectamos el poToken directamente a la URL de streaming real obtenida
                        val urlConBypass = YouTube.appendGvsPoToken(audioStream.url)

                        withContext(Dispatchers.Main) {
                            logToConsole("¡URL Real Obtenida con Bypass! Cargando en ExoPlayer...")
                            exoPlayer?.stop()
                            exoPlayer?.clearMediaItems()
                            
                            val mediaItem = MediaItem.fromUri(urlConBypass)
                            exoPlayer?.setMediaItem(mediaItem)
                            exoPlayer?.playWhenReady = true
                            exoPlayer?.prepare()
                            
                            Toast.makeText(this@MainActivity, "Sonando: $title", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            logToConsole("No se encontraron pistas de audio válidas para este video.")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        logToConsole("El motor InnerTube rechazó la extracción del stream: ${streamResult.exceptionOrNull()?.message}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    logToConsole("Fallo en la inyección del reproductor: ${e.message}")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
    }
}
