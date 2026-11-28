package app.xrtm8.athar.ui.components

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import app.xrtm8.athar.model.TextAlignOption
import app.xrtm8.athar.model.TextLayer
import app.xrtm8.athar.typography.TypographyLayoutResult

/**
 * Thread-safe frosted glass plaque renderer behind Arabic typography.
 * Scratch objects and gradient shaders are cached per-thread so UI drawing and background
 * exports never contend or allocate repeatedly on steady frames.
 */
object TextGlassBackdropRenderer {

    private class GlassScratch {
        val rectF = RectF()
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(100, 0, 0, 0) }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val sheenStops = floatArrayOf(0.0f, 0.45f, 1.0f)
        val sheenColors = IntArray(3)
        var cachedFilterDensity = 0f
        var cachedBlurFilter: BlurMaskFilter? = null
        var lastLeft = Float.NaN
        var lastTop = Float.NaN
        var lastRight = Float.NaN
        var lastBottom = Float.NaN
        var lastAlpha = -1
        var cachedSheen: LinearGradient? = null

        fun getShadowBlurFilter(density: Float): BlurMaskFilter {
            val existing = cachedBlurFilter
            if (existing != null && cachedFilterDensity == density) return existing
            val filter = BlurMaskFilter((16f * density).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
            cachedFilterDensity = density
            cachedBlurFilter = filter
            return filter
        }

        fun getSheenShader(bLeft: Float, bTop: Float, bRight: Float, bBottom: Float, alphaInt: Int): LinearGradient {
            val existing = cachedSheen
            if (existing != null && lastLeft == bLeft && lastTop == bTop && lastRight == bRight && lastBottom == bBottom && lastAlpha == alphaInt) {
                return existing
            }
            sheenColors[0] = Color.argb((alphaInt * 0.40f).toInt(), 255, 255, 255)
            sheenColors[1] = Color.argb(0, 255, 255, 255)
            sheenColors[2] = Color.argb((alphaInt * 0.15f).toInt(), 255, 255, 255)
            val grad = LinearGradient(bLeft, bTop, bRight, bBottom, sheenColors, sheenStops, Shader.TileMode.CLAMP)
            lastLeft = bLeft; lastTop = bTop; lastRight = bRight; lastBottom = bBottom; lastAlpha = alphaInt
            cachedSheen = grad
            return grad
        }
    }

    private val threadScratch = ThreadLocal.withInitial { GlassScratch() }

    fun drawGlassBackdrop(
        canvas: Canvas,
        layer: TextLayer,
        layout: TypographyLayoutResult,
        textSizePx: Float,
        density: Float
    ) {
        drawGlassBackdrop(
            canvas = canvas,
            enableGlassBackdrop = layer.enableGlassBackdrop,
            glassBackdropPadding = layer.glassBackdropPadding,
            glassBackdropPaddingV = layer.glassBackdropPaddingV,
            glassBackdropCornerRadius = layer.glassBackdropCornerRadius,
            glassBackdropColorLong = layer.glassBackdropColorLong,
            glassBackdropBorderColorLong = layer.glassBackdropBorderColorLong,
            glassBackdropOpacity = layer.glassBackdropOpacity,
            textAlign = layer.textAlign,
            layout = layout,
            textSizePx = textSizePx,
            density = density,
            glassBackdropBorderWidth = layer.glassBackdropBorderWidth,
            glassBackdropBorderOpacity = layer.glassBackdropBorderOpacity
        )
    }

    fun drawGlassBackdrop(
        canvas: Canvas,
        enableGlassBackdrop: Boolean,
        glassBackdropPadding: Float,
        glassBackdropCornerRadius: Float,
        glassBackdropColorLong: Long,
        glassBackdropBorderColorLong: Long,
        glassBackdropOpacity: Float,
        @Suppress("UNUSED_PARAMETER") textAlign: TextAlignOption,
        layout: TypographyLayoutResult,
        textSizePx: Float,
        density: Float,
        glassBackdropPaddingV: Float? = null,
        glassBackdropBorderWidth: Float = 1.2f,
        glassBackdropBorderOpacity: Float = 1.0f
    ) {
        if (!enableGlassBackdrop || layout.maxLineWidth <= 0f) return

        val s = threadScratch.get()!!
        val pad = glassBackdropPadding * density
        val padV = (glassBackdropPaddingV ?: glassBackdropPadding) * density
        val bLeft = -layout.halfBlockW - pad
        val bRight = layout.halfBlockW + pad
        val bTop = layout.startY - textSizePx * 0.75f - padV
        val bBottom = layout.startY + layout.totalHeight + textSizePx * 0.25f + padV

        s.rectF.set(bLeft, bTop, bRight, bBottom)
        val cornerR = (glassBackdropCornerRadius * density).coerceAtLeast(4f)

        s.shadowPaint.maskFilter = s.getShadowBlurFilter(density)
        canvas.drawRoundRect(s.rectF, cornerR, cornerR, s.shadowPaint)

        val baseColor = glassBackdropColorLong.toInt()
        val alphaInt = (glassBackdropOpacity.coerceIn(0.1f, 0.98f) * 255).toInt()
        s.fillPaint.color = (baseColor and 0x00FFFFFF) or (alphaInt shl 24)
        canvas.drawRoundRect(s.rectF, cornerR, cornerR, s.fillPaint)

        s.sheenPaint.shader = s.getSheenShader(bLeft, bTop, bRight, bBottom, alphaInt)
        canvas.drawRoundRect(s.rectF, cornerR, cornerR, s.sheenPaint)

        if (glassBackdropBorderWidth > 0.05f && glassBackdropBorderOpacity > 0.01f) {
            val rawBorder = glassBackdropBorderColorLong.toInt()
            val rawAlpha = (rawBorder ushr 24) and 0xFF
            val effAlpha = ((if (rawAlpha == 0) 255 else rawAlpha) * glassBackdropBorderOpacity.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
            s.strokePaint.strokeWidth = glassBackdropBorderWidth.coerceIn(0f, 8f) * density
            s.strokePaint.color = (rawBorder and 0x00FFFFFF) or (effAlpha shl 24)
            canvas.drawRoundRect(s.rectF, cornerR, cornerR, s.strokePaint)
        }
    }
}
