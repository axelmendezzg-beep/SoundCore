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
import com.soundcore.app.innertube.YouTube
import com.soundcore.app.innertube.models.YouTubeClient
import io.ktor.client.call.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

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

        // Puente para cuando el usuario le da click a reproducir en el HTML
        val bridge = SoundCoreBridge(
            onSearchTrack = { _, _ -> },
            onPlayTrack = { id, title, artist, thumbnail ->
                solicitarStreamingNativo(id, title)
            }
        )
        webView.addJavascriptInterface(bridge, "SoundCoreNative")

        // Puente para la barra de búsqueda del HTML conectado al motor clonado
        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun search(query: String, callbackId: String) {
                logToConsole("Invocando motor InnerTube para: $query")
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Llamamos al buscador oficial del motor clonado
                        val resultado = YouTube.searchSummary(query)
                        
                        if (resultado.isSuccess) {
                            val info = resultado.getOrNull()
                            val jsonArray = JSONArray()
                            
                            // Parseamos los resultados devueltos por InnerTube a tu formato JSON estándar
                            info?.summaries?.forEach { summary ->
                                summary.items.forEach { item ->
                                    val trackJson = JSONObject().apply {
                                        put("id", item.id)
                                        put("title", item.title)
                                        put("artist", item.artists.joinToString(", ") { it.name })
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
        logToConsole("Pidiendo pistas de streaming autenticadas para: $title")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Aquí llamamos al endpoint "player" de InnerTube que autogenera el poToken de manera interna
                val responseResult = YouTube.innerTube.player(
                    client = YouTubeClient.ANDROID_MUSIC,
                    videoId = videoId,
                    playlistId = null,
                    signatureTimestamp = 20580,
                    poToken = null // Lo dejamos null para que use su generador interno por defecto
                )

                // Leemos la respuesta cruda del servidor usando el deserializador de Ktor
                val bodyText = responseResult.bodyAsText()
                val json = JSONObject(bodyText)
                var urlDirecta: String? = null

                if (json.has("streamingData")) {
                    val adaptiveFormats = json.getJSONObject("streamingData").getJSONArray("adaptiveFormats")
                    for (i in 0 until adaptiveFormats.length()) {
                        val format = adaptiveFormats.getJSONObject(i)
                        if (format.getString("mimeType").contains("audio")) {
                            if (format.has("url")) {
                                urlDirecta = format.getString("url")
                                break
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    if (urlDirecta != null) {
                        logToConsole("¡Enlace de audio obtenido! Rompiendo seguridad de Google. Reproduciendo...")
                        exoPlayer?.stop()
                        exoPlayer?.clearMediaItems()
                        
                        val mediaItem = MediaItem.fromUri(urlDirecta)
                        exoPlayer?.setMediaItem(mediaItem)
                        exoPlayer?.playWhenReady = true
                        exoPlayer?.prepare()
                        
                        Toast.makeText(this@MainActivity, "Sonando: $title", Toast.LENGTH_SHORT).show()
                    } else {
                        logToConsole("Google rechazó el token clonado. Revisar cookies o firmas.")
                        Toast.makeText(this@MainActivity, "Error de streaming", Toast.LENGTH_SHORT).show()
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
