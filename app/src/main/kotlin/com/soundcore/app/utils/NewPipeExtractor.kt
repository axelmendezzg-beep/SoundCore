package com.soundcore.app.utils

import android.util.Log
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

class SoundCoreDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .build()

    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val bodyData = request.dataToSend()

        val reqBuilder = okhttp3.Request.Builder()
            .method(method, bodyData?.toRequestBody())
            .url(url)

        headers.forEach { (name, values) ->
            values.forEach { value -> reqBuilder.addHeader(name, value) }
        }
        
        // Clonamos el User-Agent exacto que usa la app en las peticiones exitosas
        reqBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")

        val response = client.newCall(reqBuilder.build()).execute()
        val bodyString = response.body?.string() ?: ""
        
        return Response(
            response.code, 
            response.message, 
            response.headers.toMultimap(), 
            bodyString, 
            response.request.url.toString()
        )
    }
}

object NewPipeExtractor {
    private var isInitialized = false

    fun asegurarInicializacion() {
        if (!isInitialized) {
            try {
                NewPipe.init(SoundCoreDownloader())
                isInitialized = true
            } catch (e: Exception) {
                Log.e("SoundCoreNewPipe", "Fallo al inicializar motor: ${e.message}")
            }
        }
    }

    fun desofuscarEnlaceWeb(videoId: String, signatureCipher: String): String {
        asegurarInicializacion()
        return try {
            // Desarmamos el Cipher
            val params = signatureCipher.split("&").associate {
                val split = it.split("=")
                if (split.size == 2) {
                    split[0] to java.net.URLDecoder.decode(split[1], "UTF-8")
                } else {
                    split[0] to ""
                }
            }

            val rawUrl = params["url"] ?: return ""
            val obfuscatedSignature = params["s"] ?: return rawUrl
            val signatureParam = params["sp"] ?: "sig"

            // Intentamos romper la firma con el JS de NewPipe
            val cleanSignature = YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, obfuscatedSignature)
            val finalUrl = "$rawUrl&$signatureParam=$cleanSignature"

            // Rompemos el throttling de velocidad (el parámetro 'n')
            val unthrottledUrl = YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, finalUrl)
            
            // Retornamos el enlace listo
            unthrottledUrl
        } catch (e: Exception) {
            // Si hay error, imprimimos el mensaje real para no quedar a ciegas
            "ERROR_EXTRACTOR: ${e.message}"
        }
    }
}
