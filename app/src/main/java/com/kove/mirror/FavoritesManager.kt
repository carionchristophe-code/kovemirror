package com.kove.mirror

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class FavoriteLocation(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val address: String = "",
    val latitude: Double,
    val longitude: Double
)

object FavoritesManager {
    private const val PREFS_NAME = "kove_map_favorites"
    private const val KEY_FAVORITES_JSON = "favorites_json"

    fun getFavorites(context: Context): MutableList<FavoriteLocation> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_FAVORITES_JSON, null) ?: return mutableListOf()
        val list = mutableListOf<FavoriteLocation>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    FavoriteLocation(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "Favorite"),
                        address = obj.optString("address", ""),
                        latitude = obj.getDouble("latitude"),
                        longitude = obj.getDouble("longitude")
                    )
                )
            }
        } catch (e: Exception) {
            DebugLogger.error("❌ Error parsing favorites JSON: ${e.message}")
        }
        return list
    }

    fun saveFavorite(context: Context, favorite: FavoriteLocation) {
        val current = getFavorites(context)
        current.removeAll { it.id == favorite.id }
        current.add(0, favorite) // add to top
        saveAll(context, current)
    }

    fun deleteFavorite(context: Context, id: String) {
        val current = getFavorites(context)
        current.removeAll { it.id == id }
        saveAll(context, current)
    }

    private fun saveAll(context: Context, list: List<FavoriteLocation>) {
        try {
            val jsonArray = JSONArray()
            for (fav in list) {
                val obj = JSONObject().apply {
                    put("id", fav.id)
                    put("name", fav.name)
                    put("address", fav.address)
                    put("latitude", fav.latitude)
                    put("longitude", fav.longitude)
                }
                jsonArray.put(obj)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_FAVORITES_JSON, jsonArray.toString())
                .apply()
        } catch (e: Exception) {
            DebugLogger.error("❌ Error saving favorites JSON: ${e.message}")
        }
    }
}
