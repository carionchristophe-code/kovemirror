package com.kove.mirror

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils

object PoiMarkerHelper {

    /**
     * Creates a composite bitmap containing the POI icon/pin and an always-visible
     * dark label box displaying the POI title and short description directly on the map.
     */
    fun createCompositePoiBitmap(
        context: Context,
        title: String,
        description: String,
        iconBmp: Bitmap?,
        scaleFactor: Float = 1.0f
    ): Bitmap {
        val density = context.resources.displayMetrics.density * scaleFactor.coerceIn(0.8f, 2.5f)
        val iconSize = (28 * density).toInt()

        // Strip HTML tags for clean map label display
        val cleanDesc = description.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
        val displayTitle = title.ifEmpty { "POI" }
        val displayDesc = if (cleanDesc.length > 40) cleanDesc.take(40) + "…" else cleanDesc

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 11f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val descPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 9.5f * density
        }

        val titleWidth = titlePaint.measureText(displayTitle)
        val descWidth = if (displayDesc.isNotEmpty()) descPaint.measureText(displayDesc) else 0f
        val maxTextWidth = Math.max(titleWidth, descWidth).coerceAtMost(200f * density)

        val padding = (5 * density).toInt()
        val boxWidth = (maxTextWidth + padding * 2).toInt()
        val boxHeight = (if (displayDesc.isNotEmpty()) 28 * density else 16 * density).toInt()

        val totalWidth = (iconSize + 4 * density + boxWidth).toInt()
        val totalHeight = Math.max(iconSize, boxHeight) + (4 * density).toInt()

        val bitmap = Bitmap.createBitmap(
            totalWidth.coerceAtLeast(1),
            totalHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)

        // Draw POI Icon (or fallback pin)
        val iconX = 0f
        val iconY = (totalHeight - iconSize) / 2f

        if (iconBmp != null) {
            val scaled = Bitmap.createScaledBitmap(iconBmp, iconSize, iconSize, true)
            canvas.drawBitmap(scaled, iconX, iconY, null)
        } else {
            val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#2563EB")
                style = Paint.Style.FILL
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 1.8f * density
            }
            val emojiPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 13f * density
                textAlign = Paint.Align.CENTER
            }
            val symbol = when {
                displayTitle.contains("gas", ignoreCase = true) || displayTitle.contains("benzin", ignoreCase = true) || displayTitle.contains("petrol", ignoreCase = true) -> "⛽"
                displayTitle.contains("camp", ignoreCase = true) || displayTitle.contains("kamping", ignoreCase = true) || displayTitle.contains("otel", ignoreCase = true) -> "🏕️"
                displayTitle.contains("park", ignoreCase = true) -> "🅿️"
                displayTitle.contains("flag", ignoreCase = true) || displayTitle.contains("bayrak", ignoreCase = true) -> "🚩"
                displayTitle.contains("star", ignoreCase = true) || displayTitle.contains("yıldız", ignoreCase = true) -> "⭐"
                else -> "📍"
            }
            val cx = iconX + iconSize / 2f
            val cy = iconY + iconSize / 2f
            canvas.drawCircle(cx, cy, iconSize / 2f - 2f, pinPaint)
            canvas.drawCircle(cx, cy, iconSize / 2f - 2f, borderPaint)
            canvas.drawText(symbol, cx, cy + 4.5f * density, emojiPaint)
        }

        // Draw Label Box
        val boxX = iconSize + 4f * density
        val boxY = (totalHeight - boxHeight) / 2f
        val rect = RectF(boxX, boxY, boxX + boxWidth, boxY + boxHeight)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E60F172A") // Sleek dark slate
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8") // Cyan border
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
        }

        canvas.drawRoundRect(rect, 5f * density, 5f * density, bgPaint)
        canvas.drawRoundRect(rect, 5f * density, 5f * density, strokePaint)

        // Draw Title & Description Text
        val titleX = boxX + padding
        val titleY = boxY + 11f * density
        val truncatedTitle = TextUtils.ellipsize(displayTitle, titlePaint, maxTextWidth, TextUtils.TruncateAt.END).toString()
        canvas.drawText(truncatedTitle, titleX, titleY, titlePaint)

        if (displayDesc.isNotEmpty()) {
            val descY = boxY + 22f * density
            val truncatedDesc = TextUtils.ellipsize(displayDesc, descPaint, maxTextWidth, TextUtils.TruncateAt.END).toString()
            canvas.drawText(truncatedDesc, titleX, descY, descPaint)
        }

        return bitmap
    }
}
