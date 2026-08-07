package com.kove.mirror

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

data class GeocodeSearchResult(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

object GeocoderHelper {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun searchAddress(query: String, onResult: (List<GeocodeSearchResult>) -> Unit) {
        executor.execute {
            val results = mutableListOf<GeocodeSearchResult>()
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val urlStr = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&addressdetails=1&limit=8"
                val url = URL(urlStr)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "KoveMirror/2.0 (Motorcycle Navigation App)")
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                if (conn.responseCode == 200) {
                    val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(jsonStr)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val displayName = obj.optString("display_name", "")
                        val lat = obj.optString("lat", "0").toDoubleOrNull() ?: 0.0
                        val lon = obj.optString("lon", "0").toDoubleOrNull() ?: 0.0

                        // Extract main title vs subtitle address
                        val parts = displayName.split(",")
                        val title = parts.firstOrNull()?.trim() ?: displayName
                        val subtitle = if (parts.size > 1) parts.drop(1).joinToString(",").trim() else ""

                        if (lat != 0.0 || lon != 0.0) {
                            results.add(
                                GeocodeSearchResult(
                                    name = title,
                                    address = subtitle,
                                    latitude = lat,
                                    longitude = lon
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                DebugLogger.error("❌ Geocoding search error: ${e.message}")
            }

            mainHandler.post {
                onResult(results)
            }
        }
    }

    fun reverseGeocode(lat: Double, lon: Double, onResult: (String) -> Unit) {
        executor.execute {
            var address = ""
            try {
                val urlStr = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json"
                val url = URL(urlStr)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "KoveMirror/2.0 (Motorcycle Navigation App)")
                    connectTimeout = 4000
                    readTimeout = 4000
                }

                if (conn.responseCode == 200) {
                    val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val obj = JSONObject(jsonStr)
                    address = obj.optString("display_name", "")
                }
            } catch (e: Exception) {
                DebugLogger.error("❌ Reverse geocoding error: ${e.message}")
            }

            mainHandler.post {
                onResult(address)
            }
        }
    }
}
