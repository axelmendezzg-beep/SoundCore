package com.soundcore.app.utils

import android.util.Log
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@OptIn(ExperimentalEncodingApi::class)
object SoundCoreExtractor {
    private val httpClient = OkHttpClient()
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    // --- MÓDULO ROBADO Y ADAPTADO DE POTOKENGENERATOR.KT ---
    private const val TOKEN_VERSION: Byte = 0x22
    private const val MAGIC_HEADER: Byte = 0x0A
    private const val INNER_TAG: Byte = 0x38
    private const val TIMESTAMP_TAG: Byte = 0x02

    private fun generateContentToken(videoId: String): String {
        val timestamp = System.currentTimeMillis()
        val identifierBytes = "ANDROID_MUSIC".toByteArray(Charsets.UTF_8)
        val stateBytes = videoId.toByteArray(Charsets.UTF_8)
        val keyBytes = ByteArray(16).also { Random.nextBytes(it) }
        
        val encryptedId = ByteArray(identifierBytes.size) { i ->
            (identifierBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }

        val buf = ByteArray(8)
        var v = timestamp
        for (i in 0 until 8) {
            buf[i] = (v and 0xFF).toByte()
            v = v shr 8
        }
        var len = 8
        while (len > 1 && buf[len - 1] == 0.toByte()) len--
        val timestampBytes = buf.copyOf(len)

        val innerPayload = ByteArrayBuilder().apply {
            append(INNER_TAG)
            appendVarInt(stateBytes.size)
            append(stateBytes)
            append(TIMESTAMP_TAG)
            appendVarInt(timestampBytes.size)
            append(timestampBytes)
        }.toByteArray()

        val tokenPayload = ByteArrayBuilder().apply {
            append(MAGIC_HEADER)
            appendVarInt(keyBytes.size)
            append(keyBytes)
            append(TOKEN_VERSION)
            appendVarInt(encryptedId.size)
            append(encryptedId)
            append(innerPayload)
        }.toByteArray()

        return Base64.UrlSafe.encode(tokenPayload).trimEnd('=')
    }

    private class ByteArrayBuilder {
        private val list = mutableListOf<Byte>()
        fun append(b: Byte) { list.add(b) }
        fun append(bytes: ByteArray) { for (b in bytes) list.add(b) }
        fun appendVarInt(value: Int) {
            var v = value
            while (v >= 0x80) {
                list.add((v or 0x80).toByte())
                v = v shr 7
            }
            list.add(v.toByte())
        }
        fun toByteArray(): ByteArray = list.toByteArray()
    }

    // --- MÓDULO ROBADO Y ADAPTADO DE INNERTUBE.KT + YOUTUBE.KT ---
    fun extraerStreamNativo(videoId: String): String? {
        try {
            // Generamos el poToken dinámico idéntico a ArchiveTune
            val tokenAutenticado = generateContentToken(videoId)
            
            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID_MUSIC")
                        put("clientVersion", "7.27.52")
                        put("osName", "Android")
                        put("osVersion", "15")
                        put("deviceMake", "Google")
                        put("deviceModel", "Pixel 9 Pro")
                        put("androidSdkVersion", "35")
                        put("gl", "MX")
                        put("hl", "es-419")
                    })
                })
                put("playbackContext", JSONObject().apply {
                    put("contentPlaybackContext", JSONObject().apply {
                        put("signatureTimestamp", 20580)
                    })
                })
                // Inyectamos las dimensiones de integridad validadas con el Token
                put("serviceIntegrityDimensions", JSONObject().apply {
                    put("poToken", tokenAutenticado)
                })
            }

            val request = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/player?key=AIzaSyAO_JVHe4FpCg5N2X")
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .header("Content-Type", "application/json")
                .header("User-Agent", "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 15; en_US; Pixel 9 Pro; Build/AP4A.250205.002; Cronet/132.0.6834.79) gzip")
                .header("X-Goog-Api-Format-Version", "1")
                .header("X-YouTube-Client-Name", "21")
                .header("X-YouTube-Client-Version", "7.27.52")
                .build()

            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                if (body.contains("streamingData")) {
                    val json = JSONObject(body)
                    val adaptiveFormats = json.getJSONObject("streamingData").getJSONArray("adaptiveFormats")
                    
                    for (i in 0 until adaptiveFormats.length()) {
                        val format = adaptiveFormats.getJSONObject(i)
                        if (format.getString("mimeType").contains("audio")) {
                            if (format.has("url")) {
                                return format.getString("url")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SoundCoreExtractor", "Fallo de extracción: ${e.message}")
        }
        return null
    }
}
