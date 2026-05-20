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

    // 📞 Sistema centralizado de Logs hacia la Consola del HTML
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

        // 🌉 INSTANCIA DEL PUENTE REAL: Conectamos los lambdas del Bridge a la lógica de la MainActivity
        val bridge = SoundCoreBridge(
            onSearchTrack = { callbackId, _ ->
                // Este bloque se ejecuta cuando SearchParser termina en el Bridge
                // Para simplificar y no romper tu interfaz, hacemos la búsqueda directa abajo
            },
            onPlayTrack = { id, title, artist, thumbnail ->
                // Recibe los 4 parámetros exactos que pide tu SoundCoreBridge
                ejecutarStream(id, title)
            }
        )

        // Registramos tu puente real con el nombre que espera tu HTML
        webView.addJavascriptInterface(bridge, "SoundCoreNative")

        // Interceptamos la búsqueda para resolverla aquí directo con tu SearchParser y mandarla al WebView
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

    // 🎵 El motor de reproducción blindado con el PoToken de ArchiveTune
    private fun ejecutarStream(id: String, title: String) {
        logToConsole("Iniciando descifrado para: $title")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val token = PoTokenGenerator.generateContentToken("ANDROID_MUSIC", id)
                logToConsole("PoToken generado con éxito")

                val payload = JSONObject().apply {
                    put("videoId", id)
                    put("context", JSONObject().apply {
                        put("client", JSONObject().put("clientName", "ANDROID_MUSIC").put("clientVersion", "7.07.03"))
                    })
                    put("serviceIntegrityDimensions", JSONObject().put("poToken", token))
                }

                val request = Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/player?key=AIzaSyAO_JVHe4FpCg5N2X")
                    .post(payload.toString().toRequestBody(JSON_MEDIA))
                    .header("X-YouTube-Client-Name", "ANDROID_MUSIC")
                    .header("X-YouTube-Client-Version", "7.07.03")
                    .build()

                httpClient.newCall(request).execute().use { res ->
                    val body = res.body?.string() ?: ""
                    if (body.contains("streamingData")) {
                        val json = JSONObject(body)
                        val adaptiveFormats = json.getJSONObject("streamingData").getJSONArray("adaptiveFormats")
                        
                        // Buscamos el primer link de audio estable
                        var audioUrl = ""
                        for (i in 0 until adaptiveFormats.length()) {
                            val format = adaptiveFormats.getJSONObject(i)
                            if (format.getString("mimeType").contains("audio")) {
                                audioUrl = format.getString("url")
                                break
                            }
                        }

                        if (audioUrl.isNotEmpty()) {
                            logToConsole("¡StreamingData obtenido! Pasando al ExoPlayer...")
                            withContext(Dispatchers.Main) {
                                exoPlayer?.setMediaItem(MediaItem.fromUri(audioUrl))
                                exoPlayer?.prepare()
                                exoPlayer?.play()
                                Toast.makeText(this@MainActivity, "Sonando: $title", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            logToConsole("Error: No se encontró URL de audio en los formatos")
                        }
                    } else {
                        logToConsole("YouTube denegó el audio. Revisar logs.")
                    }
                }
            } catch (e: Exception) {
                logToConsole("Fallo crítico en playTrack: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
    }
}
