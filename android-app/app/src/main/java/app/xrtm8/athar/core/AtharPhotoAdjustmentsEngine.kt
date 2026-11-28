package app.xrtm8.athar.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow

/**
 * PicsArt-style Photo Color Adjustments Engine:
 * - Highlights (أبرز الأحداث): recovers or boosts bright tones smoothly without clipping midtones.
 * - Shadows (الظلال): lifts or deepens shadow detail with smooth luminance falloff.
 * - Temperature (درجة الحرارة): warm amber <-> cool cyan-blue white-balance shift.
 * Processes in 64-row strips to prevent 4K heap OOM and preserves source alpha.
 */
object AtharPhotoAdjustmentsEngine {

    private const val STRIP_ROWS = 64

    fun isEnabled(highlights: Float, shadows: Float, temperature: Float): Boolean =
        abs(highlights) > 0.005f || abs(shadows) > 0.005f || abs(temperature) > 0.005f

    @JvmOverloads
    fun processBitmap(
        source: Bitmap,
        highlights: Float,
        shadows: Float,
        temperature: Float,
        inPlace: Boolean = false
    ): Bitmap {
        if (!isEnabled(highlights, shadows, temperature)) return source
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) return source

        val target = if (inPlace && source.isMutable) {
            source
        } else {
            source.copy(Bitmap.Config.ARGB_8888, true)
        }

        val stripRows = min(STRIP_ROWS, h)
        val stripBuf = IntArray(w * stripRows)

        var y = 0
        while (y < h) {
            val rows = min(stripRows, h - y)
            target.getPixels(stripBuf, 0, w, 0, y, w, rows)
            applyToBuffer(stripBuf, highlights, shadows, temperature)
            target.setPixels(stripBuf, 0, w, 0, y, w, rows)
            y += rows
        }
        return target
    }

    /**
     * Pure buffer adjustment pass (JVM unit-testable, preserves alpha).
     */
    fun applyToBuffer(
        pixels: IntArray,
        highlights: Float,
        shadows: Float,
        temperature: Float
    ) {
        if (!isEnabled(highlights, shadows, temperature)) return

        val hiAmount = highlights.coerceIn(-1f, 1f)
        val shAmount = shadows.coerceIn(-1f, 1f)
        val tempAmount = temperature.coerceIn(-1f, 1f)

        val hasHighlights = abs(hiAmount) > 0.005f
        val hasShadows = abs(shAmount) > 0.005f
        val hasTemp = abs(tempAmount) > 0.005f

        for (i in pixels.indices) {
            val c = pixels[i]
            val alphaMask = c and 0xFF000000.toInt()
            if (alphaMask == 0) continue

            var r = ((c shr 16) and 0xFF) / 255f
            var g = ((c shr 8) and 0xFF) / 255f
            var b = (c and 0xFF) / 255f

            if (hasHighlights || hasShadows) {
                val luma = (0.299f * r + 0.587f * g + 0.114f * b).coerceIn(0f, 1f)
                var delta = 0f
                if (hasHighlights && luma > 0.35f) {
                    val t = ((luma - 0.35f) / 0.65f).coerceIn(0f, 1f)
                    val weight = t * t * (3f - 2f * t)
                    delta += hiAmount * 0.38f * weight
                }
                if (hasShadows && luma < 0.65f) {
                    val t = ((0.65f - luma) / 0.65f).coerceIn(0f, 1f)
                    val weight = t * t * (3f - 2f * t)
                    delta += shAmount * 0.42f * weight
                }
                if (delta != 0f) {
                    r = (r + delta).coerceIn(0f, 1f)
                    g = (g + delta).coerceIn(0f, 1f)
                    b = (b + delta).coerceIn(0f, 1f)
                }
            }

            if (hasTemp) {
                if (tempAmount > 0f) {
                    // Warm: boost red, slight green warmth, reduce blue
                    r = (r + tempAmount * 0.16f * (1f - r * 0.35f)).coerceIn(0f, 1f)
                    g = (g + tempAmount * 0.05f * (1f - g * 0.35f)).coerceIn(0f, 1f)
                    b = (b - tempAmount * 0.18f * b.pow(0.85f)).coerceIn(0f, 1f)
                } else {
                    // Cool: reduce red, slight cyan-green, boost blue
                    val cool = -tempAmount
                    r = (r - cool * 0.16f * r.pow(0.85f)).coerceIn(0f, 1f)
                    g = (g + cool * 0.03f * (1f - g * 0.35f)).coerceIn(0f, 1f)
                    b = (b + cool * 0.18f * (1f - b * 0.35f)).coerceIn(0f, 1f)
                }
            }

            val ri = (r * 255f + 0.5f).toInt().coerceIn(0, 255)
            val gi = (g * 255f + 0.5f).toInt().coerceIn(0, 255)
            val bi = (b * 255f + 0.5f).toInt().coerceIn(0, 255)
            pixels[i] = alphaMask or (ri shl 16) or (gi shl 8) or bi
        }
    }
}
