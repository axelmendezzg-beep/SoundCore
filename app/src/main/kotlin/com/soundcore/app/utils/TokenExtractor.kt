package com.soundcore.app.utils

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.*

@SuppressLint("SetJavaScriptEnabled")
class TokenExtractor(context: Context, private val onResult: (String) -> Unit) {
    private val webView = WebView(context)

    init {
        webView.settings.javaScriptEnabled = true
        webView.addJavascriptInterface(this, "SoundCoreBridge")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript("window.SoundCoreBridge.postData(window.ytInitialPlayerResponse?.responseContext?.visitorData || '')", null)
            }
        }
    }

    fun start() { webView.loadUrl("https://music.youtube.com") }

    @JavascriptInterface
    fun postData(vd: String) { if(vd.isNotEmpty()) onResult(vd) }
}
