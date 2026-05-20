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

// 🔥 IMPORTS CRÍTICOS PARA CORRUTINAS 🔥
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        val searchParser = com.soundcore.app.parsers.SearchParser()

        // 🚀 PUENTE NATIVO REFORMADO Y SEGURO
        webView.addJavascriptInterface(object {
            
            @android.webkit.JavascriptInterface
            fun search(query: String, callbackId: String) {
                // Abrimos el contenedor global (MainScope o IO según toque) usando el Scope explícito
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
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(this@MainActivity, "Cargando audio: $title 😈", Toast.LENGTH_LONG).show()
                    
                    // 🎵 Stream de prueba directo a las bocinas del celular
                    val mediaItem = MediaItem.fromUri("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3")
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
