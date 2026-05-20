package com.soundcore.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebSettings
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
import java.util.UUID

// Corrutinas
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var exoPlayer: ExoPlayer? = null
    private val httpClient = OkHttpClient()

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        webView = WebView(this)
        setContentView(webView)

        exoPlayer = ExoPlayer.Builder(this).build()

        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true
        
        webView.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                view.loadUrl(url)
                return true
            }
        }

        val searchParser = com.soundcore.app.parsers.SearchParser()

        webView.addJavascriptInterface(object {
            
            @android.webkit.JavascriptInterface
            fun search(query: String, callbackId: String) {
                CoroutineScope(Dispatchers.IO).launch {
                    val jsonResult = searchParser.searchTracks(query)
                    withContext(Dispatchers.Main) {
                        val base64Result = Base64.encodeToString(jsonResult.toByteArray(), Base64.NO_WRAP)
                        webView.evaluateJavascript("javascript:SoundCoreResponse.handle('$callbackId', '$base64Result')", null)
                    }
                }
            }

            @android.webkit.JavascriptInterface
            fun playTrack(id: String, title: String, artist: String, thumbnail: String) {
                Toast.makeText(this@MainActivity, "Inyectando flujo WEB_REMIX... ⚡", Toast.LENGTH_SHORT).show()

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
                        
                        // Generamos un CPN aleatorio de 16 caracteres estilo ArchiveTune
                        val fakeCpn = UUID.randomUUID().toString().replace("-", "").take(16)

                        val payload = JSONObject().apply {
                            put("videoId", id)
                            put("context", JSONObject().apply {
                                put("client", JSONObject().apply {
                                    put("clientName", "WEB_REMIX")
                                    put("clientVersion", "1.20260510.01.00")
                                    put("hl", "es")
                                    put("gl", "MX")
                                    put("timeZone", "America/Mexico_City")
                                })
                            })
                            put("playbackContext", JSONObject().apply {
                                put("contentPlaybackContext", JSONObject().apply {
                                    put("signatureTimestamp", 19500)
                                    put("cpn", fakeCpn)
                                })
                            })
                        }

                        // Agregamos la clave pública global que usa InnerTube
                        val request = Request.Builder()
                            .url("https://music.youtube.com/youtubei/v1/player?key=AIzaSyAO_JVHe4FpCg5N2X")
                            .post(payload.toString().toRequestBody(JSON_MEDIA))
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .header("Origin", "https://music.youtube.com")
                            .header("Referer", "https://music.youtube.com/")
                            .build()

                        httpClient.newCall(request).execute().use { response ->
                            val resBody = response.body?.string() ?: ""
                            val jsonResponse = JSONObject(resBody)
                            
                            // Si Google nos rebota por falta de poToken, tiramos excepcion para activar el Plan B
                            if (!jsonResponse.has("streamingData")) {
                                throw Exception("Requiere poToken Attestation")
                            }

                            val streamingData = jsonResponse.getJSONObject("streamingData")
                            val adaptiveFormats = streamingData.getJSONArray("adaptiveFormats")
                            var finalAudioUrl: String? = null
                            
                            for (i in 0 until adaptiveFormats.length()) {
                                val format = adaptiveFormats.getJSONObject(i)
                                val mimeType = format.getString("mimeType")
                                // Buscamos la pista de audio limpia y verificamos que la URL directa venga libre de firmas pesadas
                                if (mimeType.contains("audio/") && format.has("url")) {
                                    finalAudioUrl = format.getString("url")
                                    break
                                }
                            }

                            if (finalAudioUrl != null) {
                                withContext(Dispatchers.Main) {
                                    val mediaItem = MediaItem.fromUri(finalAudioUrl)
                                    exoPlayer?.setMediaItem(mediaItem)
                                    exoPlayer?.prepare()
                                    exoPlayer?.play()
                                    Toast.makeText(this@MainActivity, "Sonando nativo: $title 😈", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                throw Exception("Firma protegida")
                            }
                        }
                    } catch (e: Exception) {
                        // 🚀 PLAN B: Si WEB_REMIX nativo chilla por integridad/tokens,
                        // extraemos el stream directo usando un espejo open-source de invidious/piped estable
                        try {
                            val fallbackUrl = "https://pipedapi.kavin.rocks/streams/$id"
                            val req = Request.Builder()
                                .url(fallbackUrl)
                                .header("User-Agent", "Mozilla/5.0")
                                .build()
                                
                            httpClient.newCall(req).execute().use { res ->
                                val body = JSONObject(res.body?.string() ?: "")
                                val audioStreams = body.getJSONArray("audioStreams")
                                if (audioStreams.length() > 0) {
                                    // El primer stream de audio suele ser un Opus/M4A limpio directo de los servidores de Google sin restricciones
                                    val fallbackAudio = audioStreams.getJSONObject(0).getString("url")
                                    
                                    withContext(Dispatchers.Main) {
                                        val mediaItem = MediaItem.fromUri(fallbackAudio)
                                        exoPlayer?.setMediaItem(mediaItem)
                                        exoPlayer?.prepare()
                                        exoPlayer?.play()
                                        Toast.makeText(this@MainActivity, "Sonando (Híbrido): $title 🔥", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } catch (err: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "Error de red en este track", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }, "SoundCoreNative")

        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
