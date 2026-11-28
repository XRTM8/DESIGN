package app.xrtm8.athar.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.os.Build
import app.xrtm8.athar.model.CinematicGlowState
import app.xrtm8.athar.model.GlowStyle
import kotlin.math.max
import kotlin.math.min

/**
 * Optical Glow, Anamorphic Motion Streaks, and Dual-Stage Halation Engine:
 * - Alpha-weighted high-pass luminance isolation (`a / 255f`) so transparent collage gaps stay clean.
 * - Multi-pass separable Gaussian approximation (3 box passes) eliminating boxy halos.
 * - Dual-stage Kodak-style Halation (tight crimson core + wide amber scatter).
 * - Resolution-normalized radii relative to [referenceWidthPx] for 480px/1080px/4K parity.
 * - Exposes [extractHighlightOverlay] for post-frame SCREEN compositing in unified pipelines.
 */
object AtharOpticalGlowEngine {

    @JvmOverloads
    fun processBitmap(
        source: Bitmap,
        state: CinematicGlowState,
        referenceWidthPx: Int = source.width
    ): Bitmap {
        if (!state.isEnabled) return source
        val overlay = extractHighlightOverlay(source, state, referenceWidthPx) ?: return source

        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            alpha = ((state.glow / 100f) * 255f).toInt().coerceIn(0, 255)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                blendMode = android.graphics.BlendMode.SCREEN
            } else {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            }
        }
        canvas.drawBitmap(overlay, null, Rect(0, 0, source.width, source.height), paint)
        overlay.recycle()
        return result
    }

    /**
     * Generates a downscaled (`maxDim = 640px`) highlight-only overlay bitmap for SCREEN compositing.
     * Returns null when [state] is disabled or source dimensions are invalid.
     */
    @JvmOverloads
    fun extractHighlightOverlay(
        source: Bitmap,
        state: CinematicGlowState,
        referenceWidthPx: Int = source.width
    ): Bitmap? {
        if (!state.isEnabled) return null
        val sw = source.width
        val sh = source.height
        if (sw <= 0 || sh <= 0) return null

        val maxDim = 640f
        val scale = min(1f, maxDim / max(sw, sh).toFloat())
        val pw = (sw * scale).toInt().coerceAtLeast(16)
        val ph = (sh * scale).toInt().coerceAtLeast(16)

        val smallSrc = Bitmap.createScaledBitmap(source, pw, ph, true)
        val pixels = IntArray(pw * ph)
        smallSrc.getPixels(pixels, 0, pw, 0, 0, pw, ph)
        smallSrc.recycle()

        val threshold = (1.0f - (state.range / 100f) * 0.72f).coerceIn(0.15f, 0.95f)
        val knee = 0.12f
        val warmFactor = state.warmth / 100f

        val rWarm = 1.0f + (0.28f * warmFactor)
        val gWarm = (1.0f - (0.10f * warmFactor)).coerceAtLeast(0.4f)
        val bWarm = (1.0f - (0.75f * warmFactor)).coerceAtLeast(0.08f)

        val refScale = (pw.toFloat() / (referenceWidthPx.coerceAtLeast(1) * scale)).coerceIn(0.5f, 2.0f)

        val outPixels = when (state.style) {
            GlowStyle.MOTION -> {
                val hi = isolateHighlights(pixels, threshold, knee, rWarm, gWarm, bWarm, squaredFalloff = false)
                val radius = ((state.spread / 100f) * (pw * 0.18f) * refScale).toInt().coerceIn(4, pw / 3)
                convolveHorizontalStreakBuffer(hi, pw, ph, radius, directional = true)
            }
            GlowStyle.STREAK -> {
                val hi = isolateHighlights(pixels, threshold, knee, rWarm, gWarm, bWarm, squaredFalloff = false)
                val radius = ((state.spread / 100f) * (pw * 0.35f) * refScale).toInt().coerceIn(6, pw / 2)
                convolveHorizontalStreakBuffer(hi, pw, ph, radius, directional = false)
            }
            GlowStyle.HALATION -> {
                // Dual-stage Kodak halation: tight crimson core + wider amber scatter
                val coreHi = isolateHighlights(
                    pixels,
                    threshold = max(0.50f, threshold + 0.08f),
                    knee = knee,
                    rMult = rWarm * 1.30f,
                    gMult = gWarm * 0.24f,
                    bMult = bWarm * 0.06f,
                    squaredFalloff = true
                )
                val scatterHi = isolateHighlights(
                    pixels,
                    threshold = threshold,
                    knee = knee,
                    rMult = rWarm * 1.15f,
                    gMult = gWarm * 0.62f,
                    bMult = bWarm * 0.14f,
                    squaredFalloff = true
                )
                val baseRadius = ((state.spread / 100f) * (pw * 0.065f) * refScale).coerceIn(3f, 34f)
                val coreBlurred = convolveGaussianBuffer(coreHi, pw, ph, (baseRadius * 0.45f).toInt().coerceAtLeast(2), passes = 3)
                val scatterBlurred = convolveGaussianBuffer(scatterHi, pw, ph, (baseRadius * 1.25f).toInt().coerceAtLeast(3), passes = 3)
                addArgbBuffers(coreBlurred, scatterBlurred)
            }
            GlowStyle.BLOOM, GlowStyle.DIFFUSION -> {
                val hi = isolateHighlights(
                    pixels,
                    threshold = threshold,
                    knee = knee,
                    rMult = rWarm,
                    gMult = gWarm,
                    bMult = bWarm,
                    squaredFalloff = (state.style == GlowStyle.BLOOM)
                )
                val radius = ((state.spread / 100f) * (pw * 0.065f) * refScale).toInt().coerceIn(2, 36)
                convolveGaussianBuffer(hi, pw, ph, radius, passes = 3)
            }
        }

        val bmp = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
        bmp.setPixels(outPixels, 0, pw, 0, 0, pw, ph)
        return bmp
    }

    internal fun isolateHighlights(
        pixels: IntArray,
        threshold: Float,
        knee: Float,
        rMult: Float,
        gMult: Float,
        bMult: Float,
        squaredFalloff: Boolean
    ): IntArray {
        val highlightPixels = IntArray(pixels.size)
        val lowBound = threshold - knee
        val span = (knee * 2f).coerceAtLeast(0.01f)

        for (i in pixels.indices) {
            val c = pixels[i]
            val a = (c ushr 24) and 0xFF
            if (a == 0) continue

            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val luma = (0.299f * r + 0.587f * g + 0.114f * b) / 255f

            if (luma > lowBound) {
                var weight = ((luma - lowBound) / span).coerceIn(0f, 1f)
                if (squaredFalloff) {
                    val excess = ((luma - threshold) / (1f - threshold).coerceAtLeast(0.05f)).coerceIn(0f, 1f)
                    weight *= (0.35f + 0.65f * excess * excess)
                }
                weight *= (a / 255f)

                // Bitmap.setPixels premultiplies RGB by alpha (na / 255f), so keep RGB unpremultiplied here
                // to avoid double-attenuating highlights (weight^2) during SCREEN compositing.
                val nr = (r * rMult).toInt().coerceIn(0, 255)
                val ng = (g * gMult).toInt().coerceIn(0, 255)
                val nb = (b * bMult).toInt().coerceIn(0, 255)
                val na = (255f * weight).toInt().coerceIn(0, 255)
                highlightPixels[i] = (na shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }
        return highlightPixels
    }

    internal fun addArgbBuffers(primary: IntArray, secondary: IntArray): IntArray {
        for (i in primary.indices) {
            val p = primary[i]
            val s = secondary[i]
            val a = min(255, ((p ushr 24) and 0xFF) + ((s ushr 24) and 0xFF))
            val r = min(255, ((p shr 16) and 0xFF) + ((s shr 16) and 0xFF))
            val g = min(255, ((p shr 8) and 0xFF) + ((s shr 8) and 0xFF))
            val b = min(255, (p and 0xFF) + (s and 0xFF))
            primary[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return primary
    }

    private fun convolveHorizontalStreakBuffer(
        pixels: IntArray,
        w: Int,
        h: Int,
        radius: Int,
        directional: Boolean
    ): IntArray {
        val outPixels = IntArray(w * h)
        val rL = if (directional) radius / 3 else radius
        val rR = if (directional) radius * 2 else radius
        val window = (rL + rR + 1).coerceAtLeast(1)

        for (y in 0 until h) {
            val rowOffset = y * w
            var sumR = 0; var sumG = 0; var sumB = 0; var sumA = 0

            for (i in -rL..rR) {
                val px = min(w - 1, max(0, i))
                val c = pixels[rowOffset + px]
                sumA += (c ushr 24) and 0xFF
                sumR += (c shr 16) and 0xFF
                sumG += (c shr 8) and 0xFF
                sumB += c and 0xFF
            }

            for (x in 0 until w) {
                outPixels[rowOffset + x] = ((sumA / window) shl 24) or
                    ((sumR / window) shl 16) or
                    ((sumG / window) shl 8) or
                    (sumB / window)

                val xRemove = min(w - 1, max(0, x - rL))
                val xAdd = min(w - 1, max(0, x + rR + 1))
                val cRemove = pixels[rowOffset + xRemove]
                val cAdd = pixels[rowOffset + xAdd]

                sumA += ((cAdd ushr 24) and 0xFF) - ((cRemove ushr 24) and 0xFF)
                sumR += ((cAdd shr 16) and 0xFF) - ((cRemove shr 16) and 0xFF)
                sumG += ((cAdd shr 8) and 0xFF) - ((cRemove shr 8) and 0xFF)
                sumB += (cAdd and 0xFF) - (cRemove and 0xFF)
            }
        }
        return outPixels
    }

    /**
     * Multi-pass separable box blur approximating a true Gaussian kernel cleanly without blocky artifacts.
     */
    internal fun convolveGaussianBuffer(
        pixels: IntArray,
        w: Int,
        h: Int,
        radius: Int,
        passes: Int = 3
    ): IntArray {
        if (radius <= 0 || w <= 0 || h <= 0) return pixels
        val perPassRadius = max(1, (radius / max(1, passes - 1)).coerceIn(1, 30))
        val intermediate = IntArray(w * h)
        var current = pixels.copyOf()

        repeat(passes.coerceIn(1, 3)) {
            boxPassHorizontal(current, intermediate, w, h, perPassRadius)
            boxPassVertical(intermediate, current, w, h, perPassRadius)
        }
        return current
    }

    private fun boxPassHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
        val window = radius * 2 + 1
        for (y in 0 until h) {
            val row = y * w
            var sumR = 0; var sumG = 0; var sumB = 0; var sumA = 0
            for (i in -radius..radius) {
                val c = src[row + min(w - 1, max(0, i))]
                sumA += (c ushr 24) and 0xFF
                sumR += (c shr 16) and 0xFF
                sumG += (c shr 8) and 0xFF
                sumB += c and 0xFF
            }
            for (x in 0 until w) {
                dst[row + x] = ((sumA / window) shl 24) or
                    ((sumR / window) shl 16) or
                    ((sumG / window) shl 8) or
                    (sumB / window)
                val cRem = src[row + min(w - 1, max(0, x - radius))]
                val cAdd = src[row + min(w - 1, max(0, x + radius + 1))]
                sumA += ((cAdd ushr 24) and 0xFF) - ((cRem ushr 24) and 0xFF)
                sumR += ((cAdd shr 16) and 0xFF) - ((cRem shr 16) and 0xFF)
                sumG += ((cAdd shr 8) and 0xFF) - ((cRem shr 8) and 0xFF)
                sumB += (cAdd and 0xFF) - (cRem and 0xFF)
            }
        }
    }

    private fun boxPassVertical(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
        val window = radius * 2 + 1
        for (x in 0 until w) {
            var sumR = 0; var sumG = 0; var sumB = 0; var sumA = 0
            for (i in -radius..radius) {
                val c = src[min(h - 1, max(0, i)) * w + x]
                sumA += (c ushr 24) and 0xFF
                sumR += (c shr 16) and 0xFF
                sumG += (c shr 8) and 0xFF
                sumB += c and 0xFF
            }
            for (y in 0 until h) {
                dst[y * w + x] = ((sumA / window) shl 24) or
                    ((sumR / window) shl 16) or
                    ((sumG / window) shl 8) or
                    (sumB / window)
                val cRem = src[min(h - 1, max(0, y - radius)) * w + x]
                val cAdd = src[min(h - 1, max(0, y + radius + 1)) * w + x]
                sumA += ((cAdd ushr 24) and 0xFF) - ((cRem ushr 24) and 0xFF)
                sumR += ((cAdd shr 16) and 0xFF) - ((cRem shr 16) and 0xFF)
                sumG += ((cAdd shr 8) and 0xFF) - ((cRem shr 8) and 0xFF)
                sumB += (cAdd and 0xFF) - (cRem and 0xFF)
            }
        }
    }
}
