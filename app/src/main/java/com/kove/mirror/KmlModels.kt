package com.kove.mirror

import android.graphics.Bitmap
import org.osmdroid.util.GeoPoint

data class KmlStyle(
    val id: String = "",
    val lineColor: Int? = null,
    val lineWidth: Float? = null,
    val iconHref: String? = null,
    val iconScale: Float = 1.0f,
    val iconColor: Int? = null,
    val polyColor: Int? = null,
    val fill: Boolean = true,
    val outline: Boolean = true
)

enum class KmlGeometryType {
    POINT,
    LINESTRING,
    POLYGON,
    MULTIGEOMETRY
}

data class KmlPlacemark(
    val name: String = "",
    val description: String = "",
    val styleUrl: String = "",
    var inlineStyle: KmlStyle? = null,
    val geometryType: KmlGeometryType = KmlGeometryType.LINESTRING,
    val points: List<GeoPoint> = emptyList(),
    val polygons: List<List<GeoPoint>> = emptyList(),
    var iconBitmap: Bitmap? = null
)

data class KmlDocument(
    val name: String = "",
    val placemarks: List<KmlPlacemark> = emptyList(),
    val styles: Map<String, KmlStyle> = emptyMap()
)

object KmlColorUtils {
    /**
     * Converts KML AABBGGRR hex string to Android ARGB Color Int.
     */
    fun parseKmlColor(hex: String?): Int? {
        if (hex.isNullOrBlank()) return null
        val clean = hex.trim().removePrefix("#")
        if (clean.length != 8) return null
        return try {
            val alpha = clean.substring(0, 2).toInt(16)
            val blue = clean.substring(2, 4).toInt(16)
            val green = clean.substring(4, 6).toInt(16)
            val red = clean.substring(6, 8).toInt(16)
            (alpha shl 24) or (red shl 16) or (green shl 8) or blue
        } catch (_: Exception) {
            null
        }
    }
}
