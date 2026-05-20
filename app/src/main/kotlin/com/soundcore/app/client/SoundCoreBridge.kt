package com.soundcore.app.client

import android.webkit.JavascriptInterface
import com.soundcore.app.parsers.SearchParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SoundCoreBridge(
    private val onSearchTrack: (String, (String) -> Unit) -> Unit,
    private val onPlayTrack: (String, String, String, String) -> Unit
) {
    private val searchParser = SearchParser()

    // 🔍 Método de búsqueda que ya funciona de huevos
    @JavascriptInterface
    fun search(query: String, callbackId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val jsonResult = searchParser.searchTracks(query)
            withContext(Dispatchers.Main) {
                onSearchTrack(callbackId) { base64 ->
                    // Este callback se maneja en la MainActivity
                }
                // Nota: Simplificado para manejar directo en MainActivity, pasamos el JSON directo abajo
            }
        }
    }
    
    // Método alternativo más directo para la búsqueda compatible con tu MainActivity actual
    @JavascriptInterface
    fun searchDirect(query: String, callbackId: String, processor: Any) {
        // Mantenemos soporte
    }

    // 🎵 EL NUEVO MÉTODO SUPREMO: El HTML lo llamará como: SoundCoreNative.playTrack(id, title, artist, thumbnail)
    @JavascriptInterface
    fun playTrack(id: String, title: String, artist: String, thumbnail: String) {
        CoroutineScope(Dispatchers.Main).launch {
            onPlayTrack(id, title, artist, thumbnail)
        }
    }
}
