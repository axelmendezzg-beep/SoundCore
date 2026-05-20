import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

fun main() {
    val client = OkHttpClient()
    val request = Request.Builder()
        .url("https://www.youtube.com/youtubei/v1/player?key=...") // Tu URL
        .header("X-YouTube-Client-Name", "ANDROID_MUSIC")
        .build()

    client.newCall(request).execute().use { response ->
        println("Status Code: ${response.code}")
        println("Body: ${response.body?.string()}")
    }
}

