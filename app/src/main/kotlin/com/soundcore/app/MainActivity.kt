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
import com.soundcore.app.utils.PoTokenGenerator

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
                Toast.makeText(this@MainActivity, "Inyectando firmas de integridad...", Toast.LENGTH_SHORT).show()
                val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val fakeCpn = UUID.randomUUID().toString().replace("-", "").take(16)
                        val simulatedPoToken = PoTokenGenerator.generateContentToken("SoundCorePlayer", id)

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
                                    put("signatureTimestamp", 19600)
                                    put("cpn", fakeCpn)
                                })
                            })
                            put("serviceIntegrityDimensions", JSONObject().apply {
                                put("poToken", simulatedPoToken)
                            })
                        }

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
                                throw Exception("Restricción en reproductor nativo")
                            }

                            val streamingData = jsonResponse.getJSONObject("streamingData")
                            val adaptiveFormats = streamingData.getJSONArray("adaptiveFormats")
                            var finalAudioUrl: String? = null

                            for (i in 0 until adaptiveFormats.length()) {
                                val format = adaptiveFormats.getJSONObject(i)
                                val mimeType = format.optString("mimeType", "")
                                if (mimeType.contains("audio/")) {
                                    val rawUrl = format.getString("url")
                                    finalAudioUrl = if (rawUrl.contains("?")) "$rawUrl&pot=$simulatedPoToken" else "$rawUrl?pot=$simulatedPoToken"
                                    break
                                }
                            }

                            if (finalAudioUrl != null) {
                                withContext(Dispatchers.Main) {
                                    exoPlayer?.setMediaItem(MediaItem.fromUri(finalAudioUrl))
                                    exoPlayer?.prepare()
                                    exoPlayer?.play()
                                    Toast.makeText(this@MainActivity, "Sonando nativo: $title 😈🔥", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                throw Exception("Firma bloqueada")
                            }
                        }
                    } catch (e: Exception) {
                        try {
                            val proxyUrl = "https://api.cobalt.tools/api/json"
                            val cobaltPayload = JSONObject().apply {
                                put("url", "https://www.youtube.com/watch?v=$id")
                                put("downloadMode", "audio")
                                put("audioFormat", "mp3")
                            }
                            val req = Request.Builder()
                                .url(proxyUrl)
                                .post(cobaltPayload.toString().toRequestBody(JSON_MEDIA))
                                .header("Accept", "application/json")
                                .header("Content-Type", "application/json")
                                .build()
                            httpClient.newCall(req).execute().use { res ->
                                val body = JSONObject(res.body?.string() ?: "")
                                if (body.getString("status") == "stream" || body.getString("status") == "redirect") {
                                    val streamUrl = body.getString("url")
                                    withContext(Dispatchers.Main) {
                                        exoPlayer?.setMediaItem(MediaItem.fromUri(streamUrl))
                                        exoPlayer?.prepare()
                                        exoPlayer?.play()
                                        Toast.makeText(this@MainActivity, "Sonando (Cobalt Engine) 🚀", Toast.LENGTH_SHORT).show()
                                    }
                                } else { throw Exception() }
                            }
                        } catch (err: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "Error de red en este track", Toast.LENGTH_LONG).show()
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
