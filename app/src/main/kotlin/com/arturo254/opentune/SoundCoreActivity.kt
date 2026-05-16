package com.arturo254.opentune

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SoundCoreActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState: Bundle?)

        // 1. Inicializamos el WebView (El cascarón para tu HTML)
        webView = WebView(this)
        setContentView(webView)

        // 2. Configuramos para que soporte JavaScript a tope
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()

        // 3. ¡EL PUENTE INVISIBLE! Conectamos Kotlin con JavaScript
        webView.addJavascriptInterface(SoundCoreBridge(), "SoundCoreBridge")

        // 4. Cargamos tu diseño guardado en assets
        webView.loadUrl("file:///android_asset/index.html")
    }

    // Esta clase interna es la que escucha los clics de tu HTML
    inner class SoundCoreBridge {
        @JavascriptInterface
        fun playTrack(videoId: String) {
            // Este grito viene desde el HTML
            runOnUiThread {
                Toast.makeText(this@SoundCoreActivity, "Conectado al motor! ID: $videoId", Toast.LENGTH_LONG).show()
            }
        }
    }
}
