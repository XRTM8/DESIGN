package app.xrtm8.athar.core

import android.graphics.*
import android.util.LruCache
import app.xrtm8.athar.model.AtharMaskCatalog
import app.xrtm8.athar.model.MaskBlendMode
import app.xrtm8.athar.model.ProceduralMaskType
import kotlin.math.*

/**
 * Athar Mask Studio 3.0 — Photorealistic Procedural Sunlight, Gobo & Optical Mask Engine.
 *
 * Key architecture:
 * - Perspective-projected sunlight & shadow gobos (Matrix.setPolyToPoly) with warm penumbra
 *   edge diffraction and cool/neutral ambient room shadows (never flat solid black crush).
 * - Dimension-keyed LRU offscreen buffer (<= 720px) + AtharImageProcessor.fastStackBlur
 *   driven by [softness], ensuring identical API 26-34 hardware preview and 4K software export.
 * - Preserves caller [Paint.alpha], blend mode, and color filter across every mask routine.
 */
object AtharProceduralMaskRenderer {

    private const val PREVIEW_BUFFER_MAX_DIM = 480
    private const val EXPORT_BUFFER_MAX_DIM = 720

    private class ProceduralScratch {
        val path = Path()
        val srcRect = Rect()
        val dstRect = RectF()
        val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    }

    private val goboCache by lazy(LazyThreadSafetyMode.PUBLICATION) { object : LruCache<String, Bitmap>(4) {} }
    private val threadScratch = ThreadLocal.withInitial { ProceduralScratch() }

    /**
     * Backward-compatible 5-argument entry point using default softness/warmth.
     */
    fun drawProceduralMask(
        canvas: Canvas,
        type: ProceduralMaskType,
        width: Float,
        height: Float,
        paint: Paint
    ) {
        drawProceduralMask(canvas, type, width, height, paint, softness = 0.25f, warmth = 0.35f)
    }

    /**
     * Draws [type] onto [canvas] at [width] x [height] preserving [paint]'s alpha,
     * blendMode/xfermode, and colorFilter.
     */
    fun drawProceduralMask(
        canvas: Canvas,
        type: ProceduralMaskType,
        width: Float,
        height: Float,
        paint: Paint,
        softness: Float,
        warmth: Float
    ) {
        if (width <= 0f || height <= 0f) return

        when (type) {
            ProceduralMaskType.FRENCH_WINDOW,
            ProceduralMaskType.WINDOW_BLINDS,
            ProceduralMaskType.GEOMETRIC_ARCH,
            ProceduralMaskType.ARCHED_MASHRABIYA,
            ProceduralMaskType.GOLDEN_HOUR_BEAMS,
            ProceduralMaskType.OPTICAL_SUNBURST,
            ProceduralMaskType.ANAMORPHIC_STREAK,
            ProceduralMaskType.BOTANICAL_FERN_SHADOW,
            ProceduralMaskType.LOUVER_GRID_SHADOW,
            ProceduralMaskType.PRISM_DIAGONAL_FLARE,
            ProceduralMaskType.SOFT_BOKEH_LIGHTS,
            ProceduralMaskType.VINTAGE_FILM_BURN -> {
                val s = threadScratch.get()!!
                val buffered = getOrBuildBufferedMask(type, width, height, softness, warmth)
                s.srcRect.set(0, 0, buffered.width, buffered.height)
                s.dstRect.set(0f, 0f, width, height)
                canvas.drawBitmap(buffered, s.srcRect, s.dstRect, paint)
            }
            ProceduralMaskType.FILM_BORDER_35MM -> drawFilmBorder35mm(canvas, width, height, paint)
            ProceduralMaskType.STUDIO_PASSEPARTOUT -> drawStudioPassepartout(canvas, width, height, paint)
            ProceduralMaskType.VECTOR_HALFTONE -> drawVectorHalftone(canvas, width, height, paint)
            ProceduralMaskType.PRECISION_SCANLINES -> drawPrecisionScanlines(canvas, width, height, paint)
            ProceduralMaskType.POLAROID_INSTANT_FRAME -> drawPolaroidInstantFrame(canvas, width, height, paint)
            ProceduralMaskType.PAPER_DECKLE_FRAME -> drawPaperDeckleFrame(canvas, width, height, paint)
        }
    }

    // ==========================================
    // Buffered Optical / Gobo Generator + LRU Cache
    // ==========================================

    private fun getOrBuildBufferedMask(
        type: ProceduralMaskType,
        width: Float,
        height: Float,
        softness: Float,
        warmth: Float
    ): Bitmap {
        val maxTarget = if (max(width, height) > 1300f) EXPORT_BUFFER_MAX_DIM else PREVIEW_BUFFER_MAX_DIM
        val aspect = (width / height).coerceIn(0.25f, 4.0f)
        val bw: Int
        val bh: Int
        if (aspect >= 1f) {
            bw = maxTarget
            bh = (maxTarget / aspect).roundToInt().coerceAtLeast(64)
        } else {
            bh = maxTarget
            bw = (maxTarget * aspect).roundToInt().coerceAtLeast(64)
        }

        val sBucket = (softness.coerceIn(0f, 1f) * 32f).roundToInt()
        val wBucket = (warmth.coerceIn(0f, 1f) * 20f).roundToInt()
        val key = "${type.name}_${bw}x${bh}_s${sBucket}_w${wBucket}"

        synchronized(goboCache) {
            val cached = goboCache.get(key)
            if (cached != null && !cached.isRecycled) {
                return cached
            }
        }

        val rawBmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val offCanvas = Canvas(rawBmp)
        val sNorm = sBucket / 32f
        val wNorm = wBucket / 20f
        val fw = bw.toFloat()
        val fh = bh.toFloat()

        when (type) {
            ProceduralMaskType.FRENCH_WINDOW -> renderPerspectiveFrenchWindow(offCanvas, fw, fh, wNorm)
            ProceduralMaskType.WINDOW_BLINDS -> renderPerspectiveWindowBlinds(offCanvas, fw, fh, wNorm)
            ProceduralMaskType.GEOMETRIC_ARCH -> renderPerspectiveArch(offCanvas, fw, fh, wNorm)
            ProceduralMaskType.ARCHED_MASHRABIYA -> renderPerspectiveMashrabiya(offCanvas, fw, fh, wNorm)
            ProceduralMaskType.GOLDEN_HOUR_BEAMS -> renderGoldenHourBeams(offCanvas, fw, fh, wNorm)
            ProceduralMaskType.OPTICAL_SUNBURST -> renderOpticalSunburst(offCanvas, fw, fh, wNorm)
            ProceduralMaskType.ANAMORPHIC_STREAK -> renderAnamorphicStreak(offCanvas, fw, fh, wNorm)
            ProceduralMaskType.BOTANICAL_FERN_SHADOW -> renderBotanicalFernShadow(offCanvas, fw, fh, wNorm)
            ProceduralMaskType.LOUVER_GRID_SHADOW -> renderLouverGridShadow(offCanvas, fw, fh, wNorm)
            ProceduralMaskType.PRISM_DIAGONAL_FLARE -> renderPrismDiagonalFlare(offCanvas, fw, fh, wNorm)
            ProceduralMaskType.SOFT_BOKEH_LIGHTS -> renderSoftBokehLights(offCanvas, fw, fh, wNorm)
            ProceduralMaskType.VINTAGE_FILM_BURN -> renderVintageFilmBurn(offCanvas, fw, fh, wNorm)
            else -> {}
        }

        val baseBlurFactor = when (type) {
            ProceduralMaskType.GOLDEN_HOUR_BEAMS -> 0.032f
            ProceduralMaskType.OPTICAL_SUNBURST -> 0.025f
            ProceduralMaskType.ANAMORPHIC_STREAK -> 0.018f
            else -> 0.028f
        }
        val blurRadius = ((0.10f + sNorm * 0.90f) * min(bw, bh) * baseBlurFactor)
            .roundToInt()
            .coerceIn(0, 40)

        val finalBmp = if (blurRadius >= 1 && sNorm > 0.015f) {
            val blurred = AtharImageProcessor.fastStackBlur(rawBmp, blurRadius)
            if (blurred != rawBmp) {
                rawBmp.recycle()
            }
            blurred
        } else {
            rawBmp
        }

        synchronized(goboCache) {
            goboCache.put(key, finalBmp)
        }
        return finalBmp
    }

    private fun ambientShadowColor(warmth: Float): Int {
        // Cool slate (#222938) at warmth=0 -> Warm charcoal umber (#332B26) at warmth=1
        val r = (34 + (warmth * 18f)).roundToInt().coerceIn(0, 255)
        val g = (41 + (warmth * 3f)).roundToInt().coerceIn(0, 255)
        val b = (56 - (warmth * 18f)).roundToInt().coerceIn(0, 255)
        return Color.argb(255, r, g, b)
    }

    private fun penumbraFringeColor(warmth: Float): Int {
        // Soft sunlit edge diffraction color: neutral cool-cream to warm golden amber
        val r = 255
        val g = (242 - (warmth * 26f)).roundToInt().coerceIn(0, 255)
        val b = (232 - (warmth * 96f)).roundToInt().coerceIn(0, 255)
        return Color.argb(215, r, g, b)
    }

    private fun sunCoreColor(warmth: Float): Int {
        val r = 255
        val g = (253 - (warmth * 7f)).roundToInt().coerceIn(0, 255)
        val b = (252 - (warmth * 22f)).roundToInt().coerceIn(0, 255)
        return Color.argb(255, r, g, b)
    }

    private fun buildTrapezoidMatrix(
        srcW: Float,
        srcH: Float,
        dstTlX: Float, dstTlY: Float,
        dstTrX: Float, dstTrY: Float,
        dstBrX: Float, dstBrY: Float,
        dstBlX: Float, dstBlY: Float
    ): Matrix {
        val src = floatArrayOf(0f, 0f, srcW, 0f, srcW, srcH, 0f, srcH)
        val dst = floatArrayOf(dstTlX, dstTlY, dstTrX, dstTrY, dstBrX, dstBrY, dstBlX, dstBlY)
        return Matrix().apply { setPolyToPoly(src, 0, dst, 0, 4) }
    }

    // ==========================================
    // 1. Perspective French Window (نافذة شمسية مائلة)
    // ==========================================
    private fun renderPerspectiveFrenchWindow(canvas: Canvas, w: Float, h: Float, warmth: Float) {
        val shadowCol = ambientShadowColor(warmth)
        canvas.drawColor(shadowCol)

        val pMatrix = buildTrapezoidMatrix(
            w, h,
            w * 0.16f, h * 0.11f,
            w * 0.72f, h * 0.05f,
            w * 0.96f, h * 0.88f,
            w * 0.24f, h * 0.94f
        )

        canvas.save()
        canvas.concat(pMatrix)

        val marginX = w * 0.07f
        val marginY = h * 0.06f
        val mullionX = w * 0.055f
        val transomY = h * 0.048f
        val paneW = (w - marginX * 2f - mullionX) * 0.5f
        val paneH = (h - marginY * 2f - transomY * 2f) / 3f

        val panes = ArrayList<RectF>(6)
        for (row in 0 until 3) {
            val top = marginY + row * (paneH + transomY)
            for (col in 0 until 2) {
                val left = marginX + col * (paneW + mullionX)
                panes.add(RectF(left, top, left + paneW, top + paneH))
            }
        }

        // Warm edge penumbra halo around each sunlit pane
        val fringePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = penumbraFringeColor(warmth)
            style = Paint.Style.FILL
        }
        val fringeExpand = min(w, h) * 0.018f
        for (r in panes) {
            val expanded = RectF(r.left - fringeExpand, r.top - fringeExpand, r.right + fringeExpand, r.bottom + fringeExpand)
            canvas.drawRoundRect(expanded, fringeExpand, fringeExpand, fringePaint)
        }

        // Bright sunlit core with subtle top-to-bottom distance falloff
        val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, marginY, 0f, h - marginY,
                intArrayOf(sunCoreColor(warmth), Color.argb(242, 255, 246, 234), Color.argb(215, 242, 228, 214)),
                floatArrayOf(0f, 0.58f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val corner = min(w, h) * 0.012f
        for (r in panes) {
            canvas.drawRoundRect(r, corner, corner, sunPaint)
        }

        canvas.restore()
    }

    // ==========================================
    // 2. Perspective Venetian Blinds (ظل ستائر شرائح)
    // ==========================================
    private fun renderPerspectiveWindowBlinds(canvas: Canvas, w: Float, h: Float, warmth: Float) {
        canvas.drawColor(ambientShadowColor(warmth))

        val pMatrix = buildTrapezoidMatrix(
            w, h,
            w * 0.08f, h * 0.14f,
            w * 0.78f, h * 0.06f,
            w * 0.98f, h * 0.86f,
            w * 0.16f, h * 0.95f
        )

        canvas.save()
        canvas.concat(pMatrix)

        val winRect = RectF(w * 0.06f, h * 0.06f, w * 0.94f, h * 0.94f)
        // Soft sunlit window aperture glow
        val fringePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                winRect.left, winRect.top, winRect.right, winRect.bottom,
                intArrayOf(sunCoreColor(warmth), penumbraFringeColor(warmth), Color.argb(190, 235, 222, 208)),
                floatArrayOf(0f, 0.65f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(winRect, w * 0.025f, w * 0.025f, fringePaint)

        // Draw realistic angled blind slats inside the projected window envelope
        val slatCount = 11
        val step = winRect.height() / slatCount.toFloat()
        val slatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ambientShadowColor(warmth)
        }
        for (i in 0 until slatCount) {
            val yTop = winRect.top + i * step
            // Slight natural slat thickness gradient (thicker near bottom of room projection)
            val slatThickness = step * (0.40f + 0.08f * (i / slatCount.toFloat()))
            canvas.drawRect(winRect.left - 4f, yTop, winRect.right + 4f, yTop + slatThickness, slatPaint)
        }

        // Subtle dual tilt cords at 22% and 78% of window width
        val cordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(145, 36, 40, 48)
            strokeWidth = max(2f, w * 0.008f)
        }
        val cx1 = winRect.left + winRect.width() * 0.22f
        val cx2 = winRect.left + winRect.width() * 0.78f
        canvas.drawLine(cx1, winRect.top, cx1, winRect.bottom, cordPaint)
        canvas.drawLine(cx2, winRect.top, cx2, winRect.bottom, cordPaint)

        canvas.restore()
    }

    // ==========================================
    // 3. Perspective Architectural Arch (أقواس معمارية)
    // ==========================================
    private fun renderPerspectiveArch(canvas: Canvas, w: Float, h: Float, warmth: Float) {
        canvas.drawColor(ambientShadowColor(warmth))

        val pMatrix = buildTrapezoidMatrix(
            w, h,
            w * 0.14f, h * 0.14f,
            w * 0.76f, h * 0.08f,
            w * 0.94f, h * 0.90f,
            w * 0.20f, h * 0.94f
        )

        canvas.save()
        canvas.concat(pMatrix)

        val archLeft = w * 0.14f
        val archRight = w * 0.86f
        val archWidth = archRight - archLeft
        val archTop = h * 0.12f
        val archBottom = h * 0.90f

        val s = threadScratch.get()!!
        val path = s.path
        path.reset()
        path.moveTo(archLeft, archBottom)
        path.lineTo(archLeft, archTop + archWidth * 0.5f)
        path.arcTo(RectF(archLeft, archTop, archRight, archTop + archWidth), 180f, 180f, false)
        path.lineTo(archRight, archBottom)
        path.close()

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = w * 0.03f
            color = penumbraFringeColor(warmth)
        }
        canvas.drawPath(path, glowPaint)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                archLeft, archTop, archRight, archBottom,
                intArrayOf(sunCoreColor(warmth), Color.argb(235, 250, 240, 226), Color.argb(195, 225, 212, 198)),
                floatArrayOf(0f, 0.60f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(path, fillPaint)
        canvas.restore()
    }

    // ==========================================
    // 4. Perspective Arched Mashrabiya (شمس مشربية عربية)
    // ==========================================
    private fun renderPerspectiveMashrabiya(canvas: Canvas, w: Float, h: Float, warmth: Float) {
        val shadowCol = ambientShadowColor(warmth)
        canvas.drawColor(shadowCol)

        val pMatrix = buildTrapezoidMatrix(
            w, h,
            w * 0.14f, h * 0.10f,
            w * 0.78f, h * 0.05f,
            w * 0.95f, h * 0.90f,
            w * 0.20f, h * 0.94f
        )

        canvas.save()
        canvas.concat(pMatrix)

        val left = w * 0.12f
        val right = w * 0.88f
        val top = h * 0.10f
        val bottom = h * 0.90f
        val archW = right - left
        val shoulderY = top + archW * 0.52f

        // Islamic pointed ogee/horseshoe arch path
        val path = threadScratch.get()!!.path
        path.reset()
        path.moveTo(left, bottom)
        path.lineTo(left, shoulderY)
        path.cubicTo(
            left, top + archW * 0.16f,
            w * 0.42f, top + archW * 0.10f,
            w * 0.50f, top
        )
        path.cubicTo(
            w * 0.58f, top + archW * 0.10f,
            right, top + archW * 0.16f,
            right, shoulderY
        )
        path.lineTo(right, bottom)
        path.close()

        // Warm edge penumbra around the pointed arch
        val fringePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = w * 0.028f
            color = penumbraFringeColor(warmth)
        }
        canvas.drawPath(path, fringePaint)

        // Bright sunlit interior clipped to the arch
        canvas.save()
        canvas.clipPath(path)

        val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                left, top, right, bottom,
                intArrayOf(sunCoreColor(warmth), Color.argb(242, 255, 244, 224), Color.argb(215, 238, 222, 202)),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(left, top, right, bottom, lightPaint)

        // Geometric Mashrabiya wooden lattice ribs (diamond + octagonal cross-bars)
        val ribPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = shadowCol
            style = Paint.Style.STROKE
            strokeWidth = max(3f, w * 0.022f)
        }
        val step = w * 0.135f
        val diagSpan = (bottom - top) + archW
        var d = -diagSpan
        while (d < diagSpan) {
            canvas.drawLine(left + d, top, left + d + archW, top + archW * 1.35f, ribPaint)
            canvas.drawLine(right - d, top, right - d - archW, top + archW * 1.35f, ribPaint)
            d += step
        }
        // Horizontal transom sill divider separating upper arched tympanum from lower screen
        ribPaint.strokeWidth = max(4f, w * 0.032f)
        canvas.drawLine(left, shoulderY + h * 0.04f, right, shoulderY + h * 0.04f, ribPaint)

        canvas.restore()
        canvas.restore()
    }

    // ==========================================
    // 5. Diagonal Golden Hour Beams (أشعة ساعة ذهبية)
    // ==========================================
    private fun renderGoldenHourBeams(canvas: Canvas, w: Float, h: Float, warmth: Float) {
        canvas.drawColor(Color.BLACK)

        val warmCore = Color.argb(235, 255, (242 - warmth * 18).roundToInt(), (210 - warmth * 75).roundToInt())
        val warmMid = Color.argb(145, 255, (195 - warmth * 35).roundToInt(), (110 - warmth * 60).roundToInt())

        canvas.save()
        canvas.rotate(-28f, w * 0.10f, h * 0.05f)

        val beams = listOf(
            Triple(0.05f, 0.18f, 0.92f),
            Triple(0.28f, 0.11f, 0.68f),
            Triple(0.46f, 0.22f, 1.00f),
            Triple(0.75f, 0.14f, 0.72f)
        )
        for ((xStartFrac, beamWFrac, strength) in beams) {
            val bx = w * xStartFrac
            val bw = w * beamWFrac
            val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    bx, 0f, bx + bw, 0f,
                    intArrayOf(Color.TRANSPARENT, warmMid, warmCore, warmMid, Color.TRANSPARENT),
                    floatArrayOf(0f, 0.22f, 0.50f, 0.78f, 1f),
                    Shader.TileMode.CLAMP
                )
                alpha = (strength * 230).roundToInt().coerceIn(0, 255)
            }
            canvas.drawRect(bx, -h * 0.35f, bx + bw, h * 1.45f, beamPaint)
        }
        canvas.restore()

        // Longitudinal attenuation mask so shafts softly dissipate towards bottom-right
        val fadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
            shader = LinearGradient(
                0f, 0f, w * 0.92f, h * 0.95f,
                intArrayOf(Color.WHITE, Color.argb(255, 215, 200, 185), Color.BLACK),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, fadePaint)
    }

    // ==========================================
    // 6. Optical Sunburst & Lens Flare (تسريب شمسي بصري)
    // ==========================================
    private fun renderOpticalSunburst(canvas: Canvas, w: Float, h: Float, warmth: Float) {
        canvas.drawColor(Color.BLACK)
        val cx = w * 0.88f
        val cy = h * 0.10f
        val radius = max(w, h) * 0.88f

        val amberG = (180 - warmth * 40).roundToInt().coerceIn(80, 220)
        val crimsonB = (95 - warmth * 55).roundToInt().coerceIn(15, 130)

        val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(
                    0xFFFFFFFF.toInt(),
                    Color.argb(255, 255, 226, (165 - warmth * 50).roundToInt()),
                    Color.argb(210, 255, amberG, crimsonB),
                    Color.argb(115, 228, 92, 52),
                    Color.argb(35, 145, 58, 120),
                    0x00000000
                ),
                floatArrayOf(0f, 0.08f, 0.25f, 0.48f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, sunPaint)

        // Subtle off-axis optical ghost bokeh along diagonal sensor axis
        val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val ghosts = listOf(
            Triple(0.56f, 0.42f, 0.10f),
            Triple(0.38f, 0.60f, 0.06f)
        )
        for ((gx, gy, gr) in ghosts) {
            val px = w * gx
            val py = h * gy
            val rad = min(w, h) * gr
            ghostPaint.shader = RadialGradient(
                px, py, rad,
                intArrayOf(Color.argb(42, 255, 195, 130), Color.argb(18, 255, 120, 90), 0x00000000),
                floatArrayOf(0f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(px, py, rad, ghostPaint)
        }
    }

    // ==========================================
    // 7. Anamorphic CinemaScope Streak (شريط توهج سينمائي)
    // ==========================================
    private fun renderAnamorphicStreak(canvas: Canvas, w: Float, h: Float, warmth: Float) {
        canvas.drawColor(Color.BLACK)
        val centerY = h * 0.46f
        val halfH = h * 0.14f

        val streakTint = if (warmth > 0.55f) {
            Color.argb(245, 255, 196, 92)
        } else {
            Color.argb(245, 24, 218, 255)
        }
        val streakSoft = if (warmth > 0.55f) {
            Color.argb(75, 255, 155, 50)
        } else {
            Color.argb(75, 0, 185, 255)
        }

        val streakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, centerY - halfH, 0f, centerY + halfH,
                intArrayOf(0x00000000, streakSoft, streakTint, Color.WHITE, streakTint, streakSoft, 0x00000000),
                floatArrayOf(0f, 0.30f, 0.46f, 0.50f, 0.54f, 0.70f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, centerY - halfH, w, centerY + halfH, streakPaint)

        // Taper horizontal ends smoothly and add central anamorphic flare core
        val edgeMask = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
            shader = LinearGradient(
                0f, 0f, w, 0f,
                intArrayOf(Color.BLACK, Color.WHITE, Color.WHITE, Color.BLACK),
                floatArrayOf(0f, 0.18f, 0.82f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, centerY - halfH, w, centerY + halfH, edgeMask)

        val coreRad = min(w, h) * 0.16f
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            shader = RadialGradient(
                w * 0.5f, centerY, coreRad,
                intArrayOf(Color.argb(190, 255, 255, 255), streakSoft, 0x00000000),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(w * 0.5f, centerY, coreRad, corePaint)
    }

    internal fun scaleAlpha(callerAlpha: Int, elementAlpha: Int): Int {
        return (callerAlpha.coerceIn(0, 255) * elementAlpha.coerceIn(0, 255)) / 255
    }

    // ==========================================
    // 8. 35mm Film Frame & Sprockets (Alpha & Background-Safe EVEN_ODD Path)
    // ==========================================
    private fun drawFilmBorder35mm(canvas: Canvas, width: Float, height: Float, paint: Paint) {
        val barHeight = height * 0.11f
        val sprocketW = min(width, height) * 0.038f
        val sprocketH = barHeight * 0.48f
        val sprocketRadius = sprocketW * 0.28f
        val spacing = sprocketW * 2.1f
        val count = (width / spacing).toInt()
        val startX = (width - (count * spacing)) * 0.5f

        val s = threadScratch.get()!!
        val path = s.path
        val drawPaint = s.drawPaint
        path.reset()
        path.fillType = Path.FillType.EVEN_ODD
        path.addRect(0f, 0f, width, barHeight, Path.Direction.CW)
        path.addRect(0f, height - barHeight, width, height, Path.Direction.CW)

        val topY = (barHeight - sprocketH) * 0.5f
        val botY = height - barHeight + (barHeight - sprocketH) * 0.5f
        for (i in 0..count) {
            val sx = startX + i * spacing
            if (sx + sprocketW <= 0f || sx >= width) continue
            path.addRoundRect(
                RectF(sx, topY, sx + sprocketW, topY + sprocketH),
                sprocketRadius, sprocketRadius, Path.Direction.CCW
            )
            path.addRoundRect(
                RectF(sx, botY, sx + sprocketW, botY + sprocketH),
                sprocketRadius, sprocketRadius, Path.Direction.CCW
            )
        }

        val callerAlpha = paint.alpha
        drawPaint.set(paint)
        drawPaint.style = Paint.Style.FILL
        drawPaint.shader = null
        drawPaint.color = 0xFF07080B.toInt()
        drawPaint.alpha = scaleAlpha(callerAlpha, 248)
        canvas.drawPath(path, drawPaint)
    }

    // ==========================================
    // 9. Studio Passepartout Mat (Preserves Alpha & Filters)
    // ==========================================
    private fun drawStudioPassepartout(canvas: Canvas, width: Float, height: Float, paint: Paint) {
        val marginX = width * 0.08f
        val marginY = height * 0.08f
        val callerAlpha = paint.alpha
        val s = threadScratch.get()!!
        val path = s.path
        val drawPaint = s.drawPaint

        path.reset()
        path.fillType = Path.FillType.EVEN_ODD
        path.addRect(0f, 0f, width, height, Path.Direction.CW)
        path.addRect(marginX, marginY, width - marginX, height - marginY, Path.Direction.CCW)

        drawPaint.set(paint)
        drawPaint.style = Paint.Style.FILL
        drawPaint.shader = null
        drawPaint.color = 0xFF12141A.toInt()
        drawPaint.alpha = scaleAlpha(callerAlpha, 238)
        canvas.drawPath(path, drawPaint)

        // Inner bevel hairline respecting caller alpha & filters
        drawPaint.style = Paint.Style.STROKE
        drawPaint.strokeWidth = max(2f, min(width, height) * 0.003f)
        drawPaint.color = 0xFF000000.toInt()
        drawPaint.alpha = scaleAlpha(callerAlpha, 110)
        canvas.drawRect(marginX, marginY, width - marginX, height - marginY, drawPaint)
    }

    // ==========================================
    // 10. Vector Halftone Screen (Preserves Alpha)
    // ==========================================
    private fun drawVectorHalftone(canvas: Canvas, width: Float, height: Float, paint: Paint) {
        val step = max(18f, width / 24f)
        val cols = (width / step).toInt() + 1
        val rows = (height / step).toInt() + 1
        val maxRadius = step * 0.42f

        val callerAlpha = paint.alpha
        val drawPaint = threadScratch.get()!!.drawPaint
        drawPaint.set(paint)
        drawPaint.style = Paint.Style.FILL
        drawPaint.shader = null
        drawPaint.color = 0xFF000000.toInt()
        drawPaint.alpha = scaleAlpha(callerAlpha, 204)

        val centerX = width * 0.5f
        val centerY = height * 0.5f
        val maxDist = hypot(centerX, centerY)

        for (c in 0 until cols) {
            val x = c * step
            for (r in 0 until rows) {
                val y = r * step
                val dist = hypot(x - centerX, y - centerY)
                val factor = (dist / maxDist).coerceIn(0.05f, 1f)
                val dotRadius = maxRadius * factor
                canvas.drawCircle(x, y, dotRadius, drawPaint)
            }
        }
    }

    // ==========================================
    // 11. Analog CRT Scanlines
    // ==========================================
    private fun drawPrecisionScanlines(canvas: Canvas, width: Float, height: Float, paint: Paint) {
        val lineHeight = max(3f, height / 270f)
        val scanlineShader = LinearGradient(
            0f, 0f, 0f, lineHeight,
            intArrayOf(0x00000000, 0x00000000, 0xAA000000.toInt(), 0xAA000000.toInt()),
            floatArrayOf(0f, 0.49f, 0.50f, 1f),
            Shader.TileMode.REPEAT
        )
        val drawPaint = threadScratch.get()!!.drawPaint
        drawPaint.set(paint)
        drawPaint.style = Paint.Style.FILL
        drawPaint.shader = scanlineShader
        canvas.drawRect(0f, 0f, width, height, drawPaint)
        drawPaint.shader = null
    }

    // ==========================================
    // 12. Studio Botanical Fern Shadow Gobo
    // ==========================================
    private fun renderBotanicalFernShadow(canvas: Canvas, width: Float, height: Float, warmth: Float) {
        val warmFactor = warmth.coerceIn(0f, 1f)
        val lightColor = Color.rgb(
            255,
            (252 - warmFactor * 14).toInt().coerceIn(220, 255),
            (244 - warmFactor * 36).toInt().coerceIn(190, 255)
        )
        canvas.drawColor(lightColor)

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(215, 22, 24, 30)
            style = Paint.Style.FILL
        }

        // Draw two organic botanical stems with curved leaflets casting directional shadows
        val stems = listOf(
            Triple(width * 0.12f, -height * 0.05f, 38f),
            Triple(width * 0.88f, height * 0.18f, 148f)
        )
        for ((sx, sy, angleDeg) in stems) {
            canvas.save()
            canvas.translate(sx, sy)
            canvas.rotate(angleDeg)
            val stemLen = max(width, height) * 0.92f
            val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(210, 24, 26, 32)
                style = Paint.Style.STROKE
                strokeWidth = max(width, height) * 0.012f
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(0f, 0f, stemLen, 0f, stemPaint)

            val leafCount = 9
            for (i in 1..leafCount) {
                val t = i / (leafCount + 1f)
                val lx = stemLen * t
                val leafLen = max(width, height) * (0.20f * sin(t * PI).toFloat().coerceAtLeast(0.35f))
                val leafW = leafLen * 0.34f
                for (side in listOf(-1f, 1f)) {
                    val path = Path().apply {
                        moveTo(lx, 0f)
                        quadTo(lx + leafLen * 0.45f, side * leafW * 1.15f, lx + leafLen, side * leafW * 0.55f)
                        quadTo(lx + leafLen * 0.50f, side * leafW * 0.10f, lx, 0f)
                        close()
                    }
                    canvas.drawPath(path, shadowPaint)
                }
            }
            canvas.restore()
        }
    }

    // ==========================================
    // 13. Architectural Skylight Louver Grid Gobo
    // ==========================================
    private fun renderLouverGridShadow(canvas: Canvas, width: Float, height: Float, warmth: Float) {
        val warmFactor = warmth.coerceIn(0f, 1f)
        canvas.drawColor(
            Color.rgb(
                255,
                (251 - warmFactor * 12).toInt().coerceIn(225, 255),
                (242 - warmFactor * 34).toInt().coerceIn(190, 255)
            )
        )
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 24, 27, 34)
            style = Paint.Style.FILL
        }
        canvas.save()
        canvas.rotate(-18f, width * 0.5f, height * 0.5f)
        val step = min(width, height) * 0.16f
        val barThick = step * 0.24f
        val diag = hypot(width, height)
        var pos = -diag
        while (pos < diag * 1.5f) {
            canvas.drawRect(pos, -diag, pos + barThick, diag * 1.5f, barPaint)
            canvas.drawRect(-diag, pos, diag * 1.5f, pos + barThick, barPaint)
            pos += step
        }
        canvas.restore()
    }

    // ==========================================
    // 14. Optical Prism Diagonal Rainbow Flare
    // ==========================================
    private fun renderPrismDiagonalFlare(canvas: Canvas, width: Float, height: Float, warmth: Float) {
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val warm = warmth.coerceIn(0f, 1f)
        val colors = intArrayOf(
            Color.TRANSPARENT,
            Color.argb(120, 64, 180, 255),
            Color.argb(165, 160, 255, 210),
            Color.argb(210, 255, (215 - warm * 25).toInt(), 110),
            Color.argb(180, 255, 110, 145),
            Color.TRANSPARENT
        )
        val positions = floatArrayOf(0f, 0.28f, 0.44f, 0.58f, 0.74f, 1f)
        paint.shader = LinearGradient(0f, height * 0.15f, width, height * 0.85f, colors, positions, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = null
    }

    // ==========================================
    // 15. Cinematic Out-of-Focus Soft Bokeh Lights
    // ==========================================
    private fun renderSoftBokehLights(canvas: Canvas, width: Float, height: Float, warmth: Float) {
        canvas.drawColor(Color.BLACK)
        val warm = warmth.coerceIn(0f, 1f)
        val baseR = min(width, height)
        val orbs = listOf(
            floatArrayOf(0.18f, 0.22f, 0.14f, 0.85f),
            floatArrayOf(0.76f, 0.16f, 0.18f, 0.75f),
            floatArrayOf(0.45f, 0.38f, 0.11f, 0.65f),
            floatArrayOf(0.84f, 0.64f, 0.20f, 0.80f),
            floatArrayOf(0.24f, 0.78f, 0.16f, 0.70f),
            floatArrayOf(0.60f, 0.84f, 0.13f, 0.60f),
            floatArrayOf(0.10f, 0.52f, 0.10f, 0.55f)
        )
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (orb in orbs) {
            val cx = width * orb[0]
            val cy = height * orb[1]
            val r = baseR * orb[2]
            val a = (orb[3] * 225).toInt().coerceIn(40, 255)
            val core = Color.argb(a, 255, (238 - warm * 32).toInt(), (200 - warm * 75).toInt())
            val edge = Color.argb((a * 0.55f).toInt(), 255, (210 - warm * 35).toInt(), (155 - warm * 65).toInt())
            p.shader = RadialGradient(cx, cy, r, intArrayOf(core, edge, Color.TRANSPARENT), floatArrayOf(0f, 0.78f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r, p)
        }
        p.shader = null
    }

    // ==========================================
    // 16. Vintage 35mm Analog Film Light Burn
    // ==========================================
    private fun renderVintageFilmBurn(canvas: Canvas, width: Float, height: Float, warmth: Float) {
        canvas.drawColor(Color.BLACK)
        val warm = warmth.coerceIn(0f, 1f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r1 = max(width, height) * 0.75f
        p.shader = RadialGradient(
            width * 0.92f, height * 0.12f, r1,
            intArrayOf(
                Color.argb(245, 255, (185 - warm * 35).toInt(), 75),
                Color.argb(175, 240, 82, 36),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width, height, p)

        p.shader = RadialGradient(
            width * 0.08f, height * 0.88f, r1 * 0.65f,
            intArrayOf(
                Color.argb(190, 255, 130, 52),
                Color.argb(110, 210, 48, 30),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width, height, p)
        p.shader = null
    }

    // ==========================================
    // 17. Polaroid Instant Film Frame
    // ==========================================
    private fun drawPolaroidInstantFrame(canvas: Canvas, width: Float, height: Float, paint: Paint) {
        val side = min(width, height) * 0.065f
        val bottom = height * 0.18f
        val drawPaint = threadScratch.get()!!.drawPaint
        drawPaint.set(paint)
        drawPaint.style = Paint.Style.FILL
        drawPaint.color = Color.argb(paint.alpha, 248, 246, 240)

        val framePath = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRect(0f, 0f, width, height, Path.Direction.CW)
            addRoundRect(
                RectF(side, side, width - side, height - bottom),
                side * 0.18f, side * 0.18f, Path.Direction.CCW
            )
        }
        canvas.drawPath(framePath, drawPaint)
    }

    // ==========================================
    // 18. Artisan Deckle-Edge Paper Frame
    // ==========================================
    private fun drawPaperDeckleFrame(canvas: Canvas, width: Float, height: Float, paint: Paint) {
        val margin = min(width, height) * 0.06f
        val drawPaint = threadScratch.get()!!.drawPaint
        drawPaint.set(paint)
        drawPaint.style = Paint.Style.FILL
        drawPaint.color = Color.argb(paint.alpha, 244, 240, 232)

        val inner = Path()
        val steps = 40
        val amp = margin * 0.18f
        inner.moveTo(margin, margin)
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val x = margin + (width - 2f * margin) * t
            val jitter = sin(i * 2.7f) * amp * 0.6f + cos(i * 4.3f) * amp * 0.4f
            inner.lineTo(x, margin + jitter)
        }
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val y = margin + (height - 2f * margin) * t
            val jitter = sin(i * 3.1f) * amp * 0.6f + cos(i * 5.1f) * amp * 0.4f
            inner.lineTo(width - margin + jitter, y)
        }
        for (i in steps downTo 0) {
            val t = i / steps.toFloat()
            val x = margin + (width - 2f * margin) * t
            val jitter = sin(i * 2.3f) * amp * 0.6f + cos(i * 4.9f) * amp * 0.4f
            inner.lineTo(x, height - margin + jitter)
        }
        for (i in steps downTo 0) {
            val t = i / steps.toFloat()
            val y = margin + (height - 2f * margin) * t
            val jitter = sin(i * 3.7f) * amp * 0.6f + cos(i * 4.1f) * amp * 0.4f
            inner.lineTo(margin + jitter, y)
        }
        inner.close()

        val outer = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRect(0f, 0f, width, height, Path.Direction.CW)
            addPath(inner)
        }
        canvas.drawPath(outer, drawPaint)
    }

    // ==========================================
    // Ultra-Fast Vector & Gobo Thumbnail Generator
    // ==========================================
    fun generateThumbnailBitmap(type: ProceduralMaskType, size: Int = 120): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val matchingDef = AtharMaskCatalog.ALL_MASKS.find { it.proceduralType == type }
        val isMultiplyStyle = matchingDef?.defaultBlendMode == MaskBlendMode.MULTIPLY

        // Warm neutral studio backdrop for MULTIPLY gobos; deep obsidian backdrop for light/frame masks
        val bgPaint = Paint().apply {
            color = if (isMultiplyStyle) 0xFFDAD2C6.toInt() else 0xFF141720.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
            style = Paint.Style.FILL
            if (isMultiplyStyle) {
                MaskBlendMode.MULTIPLY.applyToPaint(this)
            }
        }

        drawProceduralMask(
            canvas = canvas,
            type = type,
            width = size.toFloat(),
            height = size.toFloat(),
            paint = thumbPaint,
            softness = matchingDef?.defaultSoftness ?: 0.25f,
            warmth = matchingDef?.defaultWarmth ?: 0.35f
        )
        return bitmap
    }
}
