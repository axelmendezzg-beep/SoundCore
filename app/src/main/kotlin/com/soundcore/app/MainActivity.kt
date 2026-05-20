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
import java.net.URLDecoder

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
                ejecutarStreamWeb(id, title)
            }
        )

        webView.addJavascriptInterface(bridge, "SoundCoreNative")

        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun search(query: String, callbackId: String) {
                logToConsole("Buscando en YouTube Music (Web): $query")
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

    private fun ejecutarStreamWeb(id: String, title: String) {
        logToConsole("Iniciando tunelización WEB_REMIX para: $title")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Payload idéntico al de nuestro script exitoso de Node.js
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
                    .header("X-YouTube-Client-Name", "67") // 67 = WEB_REMIX
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
                                // Soporte para url directa o cipher empaquetado
                                if (format.has("url")) {
                                    audioUrl = format.getString("url")
                                } else if (format.has("signatureCipher")) {
                                    val cipher = format.getString("signatureCipher")
                                    audioUrl = extraerUrlDeCipher(cipher)
                                }
                                break
                            }
                        }

                        if (audioUrl.isNotEmpty()) {
                            logToConsole("¡Enlace obtenido con éxito! Transmitiendo al ExoPlayer...")
                            withContext(Dispatchers.Main) {
                                exoPlayer?.setMediaItem(MediaItem.fromUri(audioUrl))
                                exoPlayer?.prepare()
                                exoPlayer?.play()
                                Toast.makeText(this@MainActivity, "Sonando: $title", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            logToConsole("Error: No se pudo extraer la URL del stream de audio.")
                        }
                    } else {
                        logToConsole("Error: YouTube rechazó los formatos web.")
                    }
                }
            } catch (e: Exception) {
                logToConsole("Fallo en red: ${e.message}")
            }
        }
    }

    // Extractor rápido de url cuando viene ofuscada en el cipher web
    private fun extraerUrlDeCipher(cipher: String): String {
        val params = cipher.split("&")
        var url = ""
        for (p in params) {
            if (p.startsWith("url=")) {
                url = URLDecoder.decode(p.substring(4), "UTF-8")
                break
            }
        }
        return url
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
    }
}
