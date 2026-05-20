package com.soundcore.app.utils

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.*

@SuppressLint("SetJavaScriptEnabled")
class TokenExtractor(context: Context, private val onResult: (String, String) -> Unit) {
    private val webView = WebView(context)

    init {
        webView.settings.javaScriptEnabled = true
        webView.addJavascriptInterface(this, "SoundCoreBridge")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript("""
                    (function() {
                        setTimeout(() => {
                            const vd = window.ytInitialPlayerResponse?.responseContext?.visitorData || "unknown";
                            const po = window.ytcfg?.get("INNERTUBE_CONTEXT")?.client?.poToken || "";
                            window.SoundCoreBridge.postTokens(vd, po);
                        }, 2000);
                    })();
                """.trimIndent(), null)
            }
        }
    }

    fun start() { webView.loadUrl("https://music.youtube.com") }

    @JavascriptInterface
    fun postTokens(vd: String, po: String) { if(po.isNotEmpty()) onResult(vd, po) }
}
