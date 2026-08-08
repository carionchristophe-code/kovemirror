package com.kove.mirror

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object SpeedLimitHelper {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastLat = 0.0
    private var lastLon = 0.0
    private var lastFetchTime = 0L
    private var cachedSpeedLimit: Int? = null

    fun getSpeedLimit(lat: Double, lon: Double, onResult: (Int?) -> Unit) {
        val now = System.currentTimeMillis()
        // Throttling: if position hasn't moved > 100m and fetched < 10 seconds ago, return cached limit
        if (cachedSpeedLimit != null && (now - lastFetchTime < 10000L)) {
            val dist = FloatArray(1)
            android.location.Location.distanceBetween(lastLat, lastLon, lat, lon, dist)
            if (dist[0] < 100f) {
                onResult(cachedSpeedLimit)
                return
            }
        }

        executor.execute {
            var speedLimit: Int? = null
            var conn: HttpURLConnection? = null
            try {
                val urlStr = "https://overpass-api.de/api/interpreter?data=[out:json];way(around:30,$lat,$lon)[maxspeed];out%20tags;"
                val url = URL(urlStr)
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "KoveMirror/2.0")
                conn.connectTimeout = 4000
                conn.readTimeout = 4000

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val elements = json.optJSONArray("elements")
                    if (elements != null && elements.length() > 0) {
                        for (i in 0 until elements.length()) {
                            val tags = elements.getJSONObject(i).optJSONObject("tags")
                            val maxspeedRaw = tags?.optString("maxspeed", "") ?: ""
                            val parsed = parseMaxSpeed(maxspeedRaw)
                            if (parsed != null) {
                                speedLimit = parsed
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                DebugLogger.error("❌ Overpass maxspeed error: ${e.message}")
            } finally {
                conn?.disconnect()
            }

            lastLat = lat
            lastLon = lon
            lastFetchTime = now
            cachedSpeedLimit = speedLimit

            mainHandler.post {
                onResult(speedLimit)
            }
        }
    }

    private fun parseMaxSpeed(raw: String): Int? {
        if (raw.isEmpty()) return null
        val num = raw.filter { it.isDigit() }.toIntOrNull() ?: return null
        return if (num in 10..160) num else null
    }
}
