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
import com.soundcore.app.utils.PoTokenGenerator
import com.soundcore.app.parsers.SearchParser
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
        
        exoPlayer = ExoPlayer.Builder(this).build()

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()

        val bridge = SoundCoreBridge(
            onSearchTrack = { _, _ -> },
            onPlayTrack = { id, title, artist, thumbnail ->
                ejecutarStream(id, title)
            }
        )

        webView.addJavascriptInterface(bridge, "SoundCoreNative")

        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun search(query: String, callbackId: String) {
                logToConsole("Buscando en YouTube Music: $query")
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val jsonResult = SearchParser().searchTracks(query)
                        val base64Result = Base64.encodeToString(jsonResult.toByteArray(), Base64.NO_WRAP)
                        withContext(Dispatchers.Main) {
                            webView.evaluateJavascript("javascript:SoundCoreResponse.handle('$callbackId', '$base64Result')", null)
                        }
                    } catch (e: Exception) {
                        logToConsole("Error en búsqueda: ${e.message}")
                    }
                }
            }
        }, "SoundCoreSearchBridge")

        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun ejecutarStream(id: String, title: String) {
        logToConsole("Iniciando descifrado completo para: $title")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Generar PoToken local con el algoritmo clonado
                val token = PoTokenGenerator.generateContentToken("ANDROID_MUSIC", id)
                logToConsole("PoToken generado localmente.")

                // 2. Construir el JSON clonando la estructura exacta de PlayerBody.kt
                val payload = JSONObject().apply {
                    put("videoId", id)
                    
                    // Contexto de Cliente Estructurado
                    put("context", JSONObject().apply {
                        put("client", JSONObject().apply {
                            put("clientName", "ANDROID_MUSIC")
                            put("clientVersion", "7.07.03")
                            put("gl", "MX")
                            put("hl", "es-419")
                        })
                    })
                    
                    // PlaybackContext (Firma de timestamp esencial que nos faltaba)
                    put("playbackContext", JSONObject().apply {
                        put("contentPlaybackContext", JSONObject().apply {
                            put("signatureTimestamp", 20190)
                        })
                    })
                    
                    // Dimensiones de integridad con el PoToken amarrado
                    put("serviceIntegrityDimensions", JSONObject().apply {
                        put("poToken", token)
                    })
                }

                // 3. Petición HTTP con los headers clonados de InnerTube.kt
                val request = Request.Builder()
                    .url("https://music.youtube.com/youtubei/v1/player?key=AIzaSyAO_JVHe4FpCg5N2X")
                    .post(payload.toString().toRequestBody(JSON_MEDIA))
                    .header("Content-Type", "application/json")
                    .header("X-Goog-Api-Format-Version", "1")
                    .header("X-YouTube-Client-Name", "6") // 6 es la ID interna para ANDROID_MUSIC
                    .header("X-YouTube-Client-Version", "7.07.03")
                    .header("X-Origin", "https://music.youtube.com")
                    .header("Referer", "https://music.youtube.com")
                    .header("User-Agent", "com.google.android.apps.youtube.music/7.07.03 (Linux; U; Android 14; es_MX; Redmi Note 14) Build/UKQ1.230917.001")
                    .build()

                httpClient.newCall(request).execute().use { res ->
                    val body = res.body?.string() ?: ""
                    if (body.contains("streamingData")) {
                        val json = JSONObject(body)
                        val streamingData = json.getJSONObject("streamingData")
                        val adaptiveFormats = streamingData.getJSONArray("adaptiveFormats")
                        
                        var audioUrl = ""
                        for (i in 0 until adaptiveFormats.length()) {
                            val format = adaptiveFormats.getJSONObject(i)
                            if (format.getString("mimeType").contains("audio")) {
                                audioUrl = format.getString("url")
                                break
                            }
                        }

                        if (audioUrl.isNotEmpty()) {
                            logToConsole("¡ÉXITO SUPREMO! Enlace extraído.")
                            withContext(Dispatchers.Main) {
                                exoPlayer?.setMediaItem(MediaItem.fromUri(audioUrl))
                                exoPlayer?.prepare()
                                exoPlayer?.play()
                                Toast.makeText(this@MainActivity, "Sonando: $title", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            logToConsole("Error: Formatos mutados sin URL de audio.")
                        }
                    } else {
                        logToConsole("Error del Servidor: Mapeo rechazado. Revisa la firma.")
                    }
                }
            } catch (e: Exception) {
                logToConsole("Fallo en Hilo de Red: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
    }
}
