package com.soundcore.app.client

import android.webkit.JavascriptInterface
import com.soundcore.app.parsers.SearchParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SoundCoreBridge(private val onResult: (String, String) -> Unit) {
    private val searchParser = SearchParser()

    // Este método lo vas a poder ejecutar en tu JS como: SoundCoreNative.search("Kanye")
    @JavascriptInterface
    fun search(query: String, callbackId: String) {
        // Ejecutamos en un hilo de fondo (IO) para que la interfaz web no se congele mientras descarga de internet
        CoroutineScope(Dispatchers.IO).launch {
            val jsonResult = searchParser.searchTracks(query)
            
            // Regresamos al hilo principal para responderle al WebView
            withContext(Dispatchers.Main) {
                onResult(callbackId, jsonResult)
            }
        }
    }
}
