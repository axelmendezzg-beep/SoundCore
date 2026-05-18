package com.arturo254.opentune.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class ThumbnailRenderer(
    val musicThumbnailRenderer: MusicThumbnailRenderer? = null,
    val croppedSquareThumbnailRenderer: MusicThumbnailRenderer? = null,
) {
    @Serializable
    data class MusicThumbnailRenderer(
        val thumbnail: Thumbnails,
        val thumbnailCrop: String? = null,
        val thumbnailScale: String? = null,
        val trackingParams: String? = null,
    ) {
        fun getThumbnailUrl(): String? {
            val urlOriginal = thumbnail.thumbnails.lastOrNull()?.url ?: return null
            
            // 🔥 PARCHE GLOBAL SOUNDCORE: Destruye cualquier restricción de tamaño de YouTube Music
            return when {
                urlOriginal.contains("=w") || urlOriginal.contains("-h") -> {
                    urlOriginal.replace(Regex("=w\\d+-h\\d+.*"), "=w512-h512-c")
                }
                urlOriginal.contains("=s") || urlOriginal.contains("-c") -> {
                    urlOriginal.replace(Regex("=s\\d+.*"), "=s960")
                }
                else -> urlOriginal
            }
        }
    }
}
