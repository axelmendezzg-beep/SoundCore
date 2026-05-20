package com.soundcore.app.client

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class SoundCoreClient {
    // Configuramos un cliente rápido y con buenos timeouts por si el internet de Telmex/Izzi se pone lento
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val BASE_URL = "https://music.youtube.com/youtubei/v1"
    private val API_KEY = "Ykc3bXg3bXg" // Llave maestra pública de InnerTube

    // Genera el bloque "context" obligatorio que pide Google para no clonar basura
    private fun createBaseContext(): JSONObject {
        val context = JSONObject()
        val clientObj = JSONObject()
        
        // 🎭 El disfraz exacto de ArchiveTune 2026 para saltar geobloqueos
        clientObj.put("clientName", "WEB_REMIX")
        clientObj.put("clientVersion", "1.20260114.01.00")
        clientObj.put("gl", Locale.getDefault().country.ifEmpty { "MX" })
        clientObj.put("hl", Locale.getDefault().language.ifEmpty { "es" })
        
        context.put("client", clientObj)
        return context
    }

    // El método supremo para disparar POSTs a browse, search o next
    fun post(endpoint: String, payload: JSONObject): String? {
        val url = "$BASE_URL/$endpoint?key=$API_KEY"
        
        // Fusionamos el contexto base con la carga útil que venga del parser
        val finalBody = JSONObject()
        finalBody.put("context", createBaseContext())
        
        // Metemos los datos extra (query, browseId, etc.)
        payload.keys().forEach { key ->
            finalBody.put(key, payload.get(key))
        }

        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .post(finalBody.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
