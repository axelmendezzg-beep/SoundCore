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
                reproducirAudioNativo(id, title)
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

    private fun reproducirAudioNativo(id: String, title: String) {
        logToConsole("Iniciando tunelización nativa (ANDROID_MUSIC) para: $title")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // JSON emulando al cliente oficial de Android Music (Pixel 9 Pro / Android 15)
                val payload = JSONObject().apply {
                    put("videoId", id)
                    put("context", JSONObject().apply {
                        put("client", JSONObject().apply {
                            put("clientName", "ANDROID_MUSIC")
                            put("clientVersion", "7.27.52")
                            put("osName", "Android")
                            put("osVersion", "15")
                            put("deviceMake", "Google")
                            put("deviceModel", "Pixel 9 Pro")
                            put("androidSdkVersion", "35")
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
                    .header("User-Agent", "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 15; en_US; Pixel 9 Pro; Build/AP4A.250205.002; Cronet/132.0.6834.79) gzip")
                    .header("X-Goog-Api-Format-Version", "1")
                    .build()

                httpClient.newCall(request).execute().use { res ->
                    val body = res.body?.string() ?: ""
                    if (body.contains("streamingData")) {
                        val json = JSONObject(body)
                        val adaptiveFormats = json.getJSONObject("streamingData").getJSONArray("adaptiveFormats")
                        
                        var streamUrl = ""
                        for (i in 0 until adaptiveFormats.length()) {
                            val format = adaptiveFormats.getJSONObject(i)
                            // Buscamos el stream que sea puramente audio
                            if (format.getString("mimeType").contains("audio")) {
                                if (format.has("url")) {
                                    streamUrl = format.getString("url")
                                    break
                                }
                            }
                        }

                        if (streamUrl.isNotEmpty()) {
                            logToConsole("¡Enlace directo obtenido sin cifrar! Pasando a ExoPlayer...")
                            withContext(Dispatchers.Main) {
                                exoPlayer?.stop()
                                exoPlayer?.clearMediaItems()
                                
                                val mediaItem = MediaItem.fromUri(streamUrl)
                                exoPlayer?.setMediaItem(mediaItem)
                                exoPlayer?.playWhenReady = true
                                exoPlayer?.prepare()
                                
                                Toast.makeText(this@MainActivity, "Sonando: $title", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            logToConsole("Error crítico: YouTube no entregó una URL directa para este dispositivo.")
                        }
                    } else {
                        logToConsole("Error de Integridad: No se encontraron datos de streaming en la respuesta.")
                    }
                }
            } catch (e: Exception) {
                logToConsole("Fallo en la conexión nativa: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
    }
}
