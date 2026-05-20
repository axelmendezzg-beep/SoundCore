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

// Hilos de Corrutinas
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

        // Inicializamos el motor multimedia nativo de Google
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
                Toast.makeText(this@MainActivity, "Extrayendo stream nativo... 🎧", Toast.LENGTH_SHORT).show()

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
                        val fakeCpn = UUID.randomUUID().toString().replace("-", "").take(16)

                        // 🎯 TRUCO MAESTRO ARCHIVETUNE + ANDROID INMUNE:
                        // Usamos el cliente nativo de Android incorporado en TV/VR que no exige poToken/Attestation obligatorio
                        val payload = JSONObject().apply {
                            put("videoId", id)
                            put("context", JSONObject().apply {
                                put("client", JSONObject().apply {
                                    put("clientName", "ANDROID_VR")
                                    put("clientVersion", "1.52.41")
                                    put("hl", "es")
                                    put("gl", "MX")
                                })
                                put("thirdParty", JSONObject().apply {
                                    put("embedUrl", "https://www.youtube.com/watch?v=$id")
                                })
                            })
                            put("playbackContext", JSONObject().apply {
                                put("contentPlaybackContext", JSONObject().apply {
                                    put("signatureTimestamp", 19500)
                                    put("cpn", fakeCpn)
                                })
                            })
                        }

                        // Petición directa al Core Endpoint de InnerTube
                        val request = Request.Builder()
                            .url("https://www.youtube.com/youtubei/v1/player?key=AIzaSyAO_JVHe4FpCg5N2X")
                            .post(payload.toString().toRequestBody(JSON_MEDIA))
                            .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile; rv:124.0) Gecko/124.0 Firefox/124.0")
                            .header("Content-Type", "application/json")
                            .build()

                        httpClient.newCall(request).execute().use { response ->
                            val resBody = response.body?.string() ?: ""
                            val jsonResponse = JSONObject(resBody)

                            if (!jsonResponse.has("streamingData")) {
                                throw Exception("Bloqueo de firma detectado")
                            }

                            val streamingData = jsonResponse.getJSONObject("streamingData")
                            
                            // Intentamos raspar primero los adaptiveFormats o los formats directos
                            val formats = if (streamingData.has("adaptiveFormats")) {
                                streamingData.getJSONArray("adaptiveFormats")
                            } else {
                                streamingData.getJSONArray("formats")
                            }

                            var finalAudioUrl: String? = null

                            // Recorremos los formatos buscando el stream de audio nativo (.mp4/.m4a) que tenga URL directa
                            for (i in 0 until formats.length()) {
                                val format = formats.getJSONObject(i)
                                val mimeType = format.optString("mimeType", "")
                                
                                if ((mimeType.contains("audio/") || mimeType.contains("video/mp4")) && format.has("url")) {
                                    finalAudioUrl = format.getString("url")
                                    // Preferimos los de audio puro si están disponibles
                                    if (mimeType.contains("audio/")) break
                                }
                            }

                            if (finalAudioUrl != null) {
                                withContext(Dispatchers.Main) {
                                    // Inyectamos el flujo multimedia real directo a las bocinas del dispositivo
                                    val mediaItem = MediaItem.fromUri(finalAudioUrl)
                                    exoPlayer?.setMediaItem(mediaItem)
                                    exoPlayer?.prepare()
                                    exoPlayer?.play()
                                    Toast.makeText(this@MainActivity, "Sonando: $title 😈🔥", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                throw Exception("Stream protegido por firma pesada")
                            }
                        }
                    } catch (e: Exception) {
                        // 🚀 SISTEMA DE RESPALDO CON SERVIDOR SECUNDARIO DE EMERGENCIAS (ENDPOINT REFORMADO)
                        try {
                            val backupUrl = "https://inv.tux.digital/api/v1/streams/$id"
                            val req = Request.Builder().url(backupUrl).header("User-Agent", "Mozilla/5.0").build()
                            
                            httpClient.newCall(req).execute().use { res ->
                                val body = JSONObject(res.body?.string() ?: "")
                                val adaptive = body.getJSONArray("adaptiveFormats")
                                var fallbackUrl: String? = null
                                
                                for (i in 0 until adaptive.length()) {
                                    val format = adaptive.getJSONObject(i)
                                    if (format.optString("type", "").contains("audio")) {
                                        fallbackUrl = format.getString("url")
                                        break
                                    }
                                }
                                
                                if (fallbackUrl != null) {
                                    withContext(Dispatchers.Main) {
                                        exoPlayer?.setMediaItem(MediaItem.fromUri(fallbackUrl))
                                        exoPlayer?.prepare()
                                        exoPlayer?.play()
                                        Toast.makeText(this@MainActivity, "Sonando (Híbrido Activo) ⚡", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    throw Exception()
                                }
                            }
                        } catch (err: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "Error de cifrado. Intenta con otra pista.", Toast.LENGTH_LONG).show()
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
