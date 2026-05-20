package com.soundcore.app.parsers

import com.soundcore.app.client.SoundCoreClient
import org.json.JSONArray
import org.json.JSONObject

class SearchParser {
    private val client = SoundCoreClient()

    fun searchTracks(query: String): String {
        val payload = JSONObject()
        payload.put("query", query)
        // 🎯 El parámetro mágico para que YouTube Music devuelva estrictamente CANCIONES y no videos largos de 3 horas
        payload.put("params", "EgWKAQIIAWoKEAQQCRAFEAoQCg==")

        val jsonString = client.post("search", payload) ?: return "[]"

        val resultArray = JSONArray()

        try {
            val root = JSONObject(jsonString)
            val contents = root.optJSONObject("contents") ?: return "[]"
            val tabbedResults = contents.optJSONObject("tabbedSearchResultsRenderer") ?: return "[]"
            val tabs = tabbedResults.optJSONArray("tabs") ?: return "[]"
            val firstTab = tabs.optJSONObject(0) ?: return "[]"
            val tabRenderer = firstTab.optJSONObject("tabRenderer") ?: return "[]"
            val contentObj = tabRenderer.optJSONObject("content") ?: return "[]"
            val sectionList = contentObj.optJSONObject("sectionListRenderer") ?: return "[]"
            val sectionContents = sectionList.optJSONArray("contents") ?: return "[]"
            
            // Buscamos el estante correcto de música (musicShelfRenderer)
            var musicShelf: JSONObject? = null
            for (i in 0 until sectionContents.length()) {
                val section = sectionContents.optJSONObject(i)
                if (section != null && section.has("musicShelfRenderer")) {
                    musicShelf = section.getJSONObject("musicShelfRenderer")
                    break
                }
            }

            if (musicShelf == null) return "[]"
            val items = musicShelf.optJSONArray("contents") ?: return "[]"

            // Recorremos las rolas encontradas
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                
                // 1. Extraer ID de la Canción
                val playlistModules = item.optJSONObject("playlistItemData")
                val id = playlistModules?.optString("videoId") ?: continue

                val flexColumns = item.optJSONArray("flexColumns") ?: continue
                
                // 2. Extraer Título de la Rola
                val firstColumn = flexColumns.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                val title = firstColumn?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Unknown"

                // 3. Extraer Artista
                val secondColumn = flexColumns.optJSONObject(1)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                val artistRuns = secondColumn?.optJSONObject("text")?.optJSONArray("runs")
                val artist = artistRuns?.optJSONObject(0)?.optString("text") ?: "Unknown Artist"
                
                // 4. Extraer el browseId del Artista (Para entrar a su perfil después)
                val artistBrowseId = artistRuns?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")?.optString("browseId") ?: ""

                // 5. Extraer Portada en Máxima Calidad posible
                val thumbnails = item.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                val thumbnail = thumbnails?.optJSONObject(thumbnails.length() - 1)?.optString("url") ?: ""

                // Armamos un JSON individual optimizado para tu HTML
                val trackJson = JSONObject()
                trackJson.put("id", id)
                trackJson.put("title", title)
                trackJson.put("artist", artist)
                trackJson.put("artistBrowseId", artistBrowseId)
                trackJson.put("thumbnail", thumbnail)

                resultArray.put(trackJson)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Devolvemos el string limpio listo para inyectarse al JavaScript
        return resultArray.toString()
    }
}
