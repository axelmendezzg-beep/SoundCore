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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import com.soundcore.app.client.SoundCoreBridge
import com.soundcore.app.parsers.SearchParser
import com.soundcore.app.utils.NewPipeExtractor
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var exoPlayer: ExoPlayer? = null
    private val httpClient = OkHttpClient()
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

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
        
        // Inicializar ExoPlayer nativo
        exoPlayer = ExoPlayer.Builder(this).build()

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()

        // Puente nativo para capturar clics de reproducción en la interfaz web
        val bridge = SoundCoreBridge(
            onSearchTrack = { _, _ -> },
            onPlayTrack = { id, title, artist, thumbnail ->
                ejecutarStreamConNewPipe(id, title)
            }
        )
        webView.addJavascriptInterface(bridge, "SoundCoreNative")

        // Puente nativo para las búsquedas rápidas
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

    private fun ejecutarStreamConNewPipe(id: String, title: String) {
        logToConsole("Iniciando tunelización y desofuscación para: $title")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Generamos la estructura limpia de WEB_REMIX que descubrimos en el laboratorio
                val payload = JSONObject().apply {
                    put("videoId", id)
                    put("context", JSONObject().apply {
                        put("client", JSONObject().apply {
                            put("clientName", "WEB_REMIX")
                            put("clientVersion", "1.20260515.01.00")
                            put("gl", "MX")
                            put("hl", "es-419")
                        })
                    })
                    put("playbackContext", JSONObject().apply {
                        put("contentPlaybackContext", JSONObject().apply {
                            put("signatureTimestamp", 20580)
                        })
                    })
                }

                val request = Request.Builder()
                    .url("https://music.youtube.com/youtubei/v1/player?key=AIzaSyAO_JVHe4FpCg5N2X")
                    .post(payload.toString().toRequestBody(JSON_MEDIA))
                    .header("Content-Type", "application/json")
                    .header("X-Goog-Api-Format-Version", "1")
                    .header("X-YouTube-Client-Name", "67")
                    .header("X-YouTube-Client-Version", "1.20260515.01.00")
                    .header("X-Origin", "https://music.youtube.com")
                    .header("Referer", "https://music.youtube.com")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .build()

                httpClient.newCall(request).execute().use { res ->
                    val body = res.body?.string() ?: ""
                    if (body.contains("streamingData")) {
                        val json = JSONObject(body)
                        val adaptiveFormats = json.getJSONObject("streamingData").getJSONArray("adaptiveFormats")
                        
                        var audioUrl = ""
                        for (i in 0 until adaptiveFormats.length()) {
                            val format = adaptiveFormats.getJSONObject(i)
                            if (format.getString("mimeType").contains("audio")) {
                                // 🛠️ LA SOLUCIÓN DEFINITIVA PARA EL ERROR 403:
                                if (format.has("url")) {
                                    audioUrl = format.getString("url")
                                } else if (format.has("signatureCipher")) {
                                    val cipher = format.getString("signatureCipher")
                                    // Mandamos el cipher encriptado a NewPipe para romper el candado matemático
                                    audioUrl = NewPipeExtractor.desofuscarEnlaceWeb(id, cipher)
                                }
                                break
                            }
                        }

                        if (audioUrl.isNotEmpty()) {
                            logToConsole("¡Enlace liberado por NewPipe! Cargando ExoPlayer...")
                            withContext(Dispatchers.Main) {
                                exoPlayer?.setMediaItem(MediaItem.fromUri(audioUrl))
                                exoPlayer?.prepare()
                                exoPlayer?.play()
                                Toast.makeText(this@MainActivity, "Sonando: $title", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            logToConsole("Error crítico: El desofuscador devolvió un enlace vacío.")
                        }
                    } else {
                        logToConsole("Error de Integridad: YouTube rechazó los formatos base.")
                    }
                }
            } catch (e: Exception) {
                logToConsole("Fallo de red en canal seguro: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
    }
}
