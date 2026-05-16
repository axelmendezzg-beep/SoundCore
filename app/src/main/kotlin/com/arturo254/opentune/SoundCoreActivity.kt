package com.arturo254.opentune

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity

class SoundCoreActivity : ComponentActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Cascarón del WebView a pantalla completa
        webView = WebView(this)
        setContentView(webView)

        // 2. Soporte total para JavaScript y HTML5
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()

        // 3. El puente invisible
        webView.addJavascriptInterface(SoundCoreBridge(), "SoundCoreBridge")

        // 4. Tu interfaz Yeezy negra Amoled
        webView.loadUrl("file:///android_asset/index.html")
    }

    inner class SoundCoreBridge {
        @JavascriptInterface
        fun playTrack(videoId: String) {
            runOnUiThread {
                Toast.makeText(this@SoundCoreActivity, "¡Conectado al motor! ID: $videoId", Toast.LENGTH_LONG).show()
                // Aquí mandaremos la orden al servicio en el siguiente paso
            }
        }
    }
}
