package com.soundcore.app.utils

import android.util.Base64
import java.util.Random

object PoTokenGenerator {
    private const val TOKEN_VERSION: Byte = 0x22
    private const val MAGIC_HEADER: Byte = 0x0A
    private const val INNER_TAG: Byte = 0x38
    private const val TIMESTAMP_TAG: Byte = 0x02

    fun generateContentToken(identifier: String, videoId: String): String {
        val timestamp = System.currentTimeMillis()
        val identifierBytes = identifier.toByteArray(Charsets.UTF_8)
        val stateBytes = videoId.toByteArray(Charsets.UTF_8)
        val keyBytes = ByteArray(16).also { Random().nextBytes(it) }

        val encryptedId = xorEncrypt(identifierBytes, keyBytes)
        val timestampBytes = encodeLong(timestamp)
        
        val innerPayload = buildByteArray {
            append(INNER_TAG)
            appendVarInt(stateBytes.size)
            append(stateBytes)
            append(TIMESTAMP_TAG)
            appendVarInt(timestampBytes.size)
            append(timestampBytes)
        }

        val tokenPayload = buildByteArray {
            append(MAGIC_HEADER)
            appendVarInt(keyBytes.size)
            append(keyBytes)
            append(TOKEN_VERSION)
            appendVarInt(encryptedId.size)
            append(encryptedId)
            append(innerPayload)
        }

        return Base64.encodeToString(tokenPayload, Base64.URL_SAFE or Base64.NO_WRAP).replace("=", "")
    }

    private fun xorEncrypt(data: ByteArray, key: ByteArray) = 
        ByteArray(data.size) { i -> (data[i].toInt() xor key[i % key.size].toInt()).toByte() }

    private fun encodeLong(value: Long): ByteArray {
        val buf = ByteArray(8)
        var v = value
        for (i in 0 until 8) { buf[i] = (v and 0xFF).toByte(); v = v shr 8 }
        var len = 8
        while (len > 1 && buf[len - 1] == 0.toByte()) len--
        return buf.copyOf(len)
    }

    private inline fun buildByteArray(block: ByteArrayBuilder.() -> Unit) = ByteArrayBuilder().apply(block).toByteArray()

    private class ByteArrayBuilder {
        private val list = mutableListOf<Byte>()
        fun append(b: Byte) { list.add(b) }
        fun append(bytes: ByteArray) { list.addAll(bytes.toList()) }
        fun appendVarInt(value: Int) {
            var v = value
            while (v >= 0x80) { list.add((v or 0x80).toByte()); v = v shr 7 }
            list.add(v.toByte())
        }
        fun toByteArray() = list.toByteArray()
    }
}
