package com.soundcore.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.soundcore.app.client.SoundCoreBridge
import android.util.Base64

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        webView = WebView(this)
        setContentView(webView)

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

        // 🌉 REGISTRAMOS EL PUENTE: Vincula la clase de Kotlin con el objeto global del navegador
        val bridge = SoundCoreBridge { callbackId, jsonResult ->
            // Convertimos el JSON a Base64 antes de mandarlo para evitar problemas con las comillas simples o dobles
            val base64Result = Base64.encodeToString(jsonResult.toByteArray(), Base64.NO_WRAP)
            
            // Le disparamos el resultado de vuelta a una función de JavaScript en tu HTML
            webView.evaluateJavascript("javascript:SoundCoreResponse.handle('$callbackId', '$base64Result')", null)
        }
        
        webView.addJavascriptInterface(bridge, "SoundCoreNative")

        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
