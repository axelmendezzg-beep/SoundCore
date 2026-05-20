package com.soundcore.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.soundcore.app.client.SoundCoreBridge
import android.util.Base64
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var exoPlayer: ExoPlayer? = null

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        webView = WebView(this)
        setContentView(webView)

        // Inicializamos el motor de audio de Google
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

        // Configuramos el puente con sus dos acciones: buscar y reproducir
        val searchParser = com.soundcore.app.parsers.SearchParser()
        
        val bridge = SoundCoreBridge(
            onSearchTrack = { _, _ -> }, // No usado directamente en esta firma simplificada
            onPlayTrack = { id, title, artist, thumbnail ->
                // 🎯 REPRODUCCIÓN EN ACCIÓN:
                // El truco maestro: Le pegamos al servidor de piped/googlevideo o stream directo usando el videoId
                // Para la prueba rápida de audio, cargamos el truco de stream directo open-source:
                val streamUrl = "https://pipedapi.kavin.rocks/v2/streams/$id" 
                
                Toast.makeText(this, "Reproduciendo: $title", Toast.LENGTH_SHORT).show()
                
                // Le inyectamos la pista a ExoPlayer
                // Nota: En el siguiente paso usaremos un parseador de audio real, ahorita jalamos el link directo para probar transiciones
                // Para asegurar que suene, usaremos una url de test de audio directo o el puente completo de stream de yt en el prox paso
            }
        )

        // Sobreescribimos el puente de forma limpia para que use la lógica exacta que ya tenías de búsqueda
        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun search(query: String, callbackId: String) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    val jsonResult = searchParser.searchTracks(query)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        val base64Result = Base64.encodeToString(jsonResult.toByteArray(), Base64.NO_WRAP)
                        webView.evaluateJavascript("javascript:SoundCoreResponse.handle('$callbackId', '$base64Result')", null)
                    }
                }
            }

            @android.webkit.JavascriptInterface
            fun playTrack(id: String, title: String, artist: String, thumbnail: String) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    // Aquí mandamos el stream. Para la v2 limpia usaremos el extractor de firmas de audio.
                    Toast.makeText(this@MainActivity, "Cargando audio: $title 😈", Toast.LENGTH_LONG).show()
                    
                    // Inyectamos el stream directo simulado usando el servidor de audio de yt stream
                    val mediaItem = MediaItem.fromUri("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3") // Audio de prueba para verificar bocinas
                    exoPlayer?.setMediaItem(mediaItem)
                    exoPlayer?.prepare()
                    exoPlayer?.play()
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
