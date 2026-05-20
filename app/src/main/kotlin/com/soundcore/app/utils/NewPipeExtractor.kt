package com.soundcore.app.utils

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class SoundCoreDownloader : Downloader() {
    private val client = OkHttpClient.Builder().build()

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
        
        reqBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

        val response = client.newCall(reqBuilder.build()).execute()
        return Response(
            response.code, 
            response.message, 
            response.headers.toMultimap(), 
            response.body.string(), 
            response.request.url.toString()
        )
    }
}

object NewPipeExtractor {
    init {
        NewPipe.init(SoundCoreDownloader())
    }

    fun desofuscarEnlaceWeb(videoId: String, signatureCipher: String): String {
        return try {
            val params = signatureCipher.split("&").associate {
                val split = it.split("=")
                split[0] to java.net.URLDecoder.decode(split[1], "UTF-8")
            }

            val rawUrl = params["url"] ?: return ""
            val obfuscatedSignature = params["s"] ?: return rawUrl
            val signatureParam = params["sp"] ?: "sig"

            val cleanSignature = YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, obfuscatedSignature)
            val finalUrl = "$rawUrl&$signatureParam=$cleanSignature"

            YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, finalUrl)
        } catch (e: Exception) {
            ""
        }
    }
}
