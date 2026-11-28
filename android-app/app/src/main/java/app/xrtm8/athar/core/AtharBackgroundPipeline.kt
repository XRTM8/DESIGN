package app.xrtm8.athar.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import app.xrtm8.athar.model.AtharMaskCatalog
import app.xrtm8.athar.model.AtharMaskState
import app.xrtm8.athar.model.CanvasGradientScrim
import app.xrtm8.athar.model.CanvasState
import app.xrtm8.athar.model.CinematicGlowState
import app.xrtm8.athar.model.CollageCell
import app.xrtm8.athar.model.ColorMixState
import app.xrtm8.athar.model.FilmGrainState
import app.xrtm8.athar.model.FilmPreset
import app.xrtm8.athar.model.FlutedGlassState
import app.xrtm8.athar.model.GlitchEffectState
import app.xrtm8.athar.model.OpticalLensState
import app.xrtm8.athar.model.PatternState
import app.xrtm8.athar.model.ToneCurveState
import app.xrtm8.athar.ui.components.CanvasGradientScrimRenderer
import app.xrtm8.athar.ui.components.CollageCanvasRenderer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Unified background compositing & post-collage effects pipeline shared across
 * Preview, 480p Thumbnail, 1080p PDF, and 1080p/4K Export for 100% parity.
 */
object AtharBackgroundPipeline {

    fun canvasDensity(canvasWidth: Float): Float = (canvasWidth / 380f).coerceAtLeast(0.1f)

    fun createBeforeCompareState(state: CanvasState): CanvasState = state.copy(
        backgroundBlur = 0f,
        backgroundDim = 0f,
        backgroundBrightness = 0f,
        backgroundContrast = 1.0f,
        backgroundSaturation = 1.0f,
        backgroundVignette = 0f,
        backgroundHighlights = 0f,
        backgroundShadows = 0f,
        backgroundTemperature = 0f,
        backgroundGrayscale = false,
        filmPreset = FilmPreset.NONE,
        filmPresetIntensity = 1.0f,
        colorGradingEnabled = false,
        colorMixState = ColorMixState(),
        cinematicGlowState = CinematicGlowState(),
        filmGrainState = FilmGrainState(),
        glitchEffectState = GlitchEffectState(),
        opticalLensState = OpticalLensState(),
        flutedGlassState = FlutedGlassState(),
        toneCurveState = ToneCurveState(),
        patternState = PatternState(),
        maskState = AtharMaskState(),
        gradientScrim = CanvasGradientScrim(isEnabled = false)
    )

    fun computePreviewSize(aspectRatio: Float, maxDim: Int): Pair<Int, Int> {
        val safeRatio = aspectRatio.coerceIn(0.25f, 4.0f)
        return if (safeRatio >= 1f) {
            maxDim to (maxDim / safeRatio).roundToInt().coerceAtLeast(32)
        } else {
            (maxDim * safeRatio).roundToInt().coerceAtLeast(32) to maxDim
        }
    }

    fun hasFilmLut(state: CanvasState): Boolean =
        state.filmPreset != FilmPreset.NONE && state.filmPresetIntensity > 0.001f

    @JvmOverloads
    fun hasColorMatrixAdjustments(state: CanvasState, includeFilmMatrix: Boolean = true): Boolean =
        (includeFilmMatrix && hasFilmLut(state)) ||
            state.colorGradingEnabled ||
            state.backgroundDim > 0.001f ||
            state.backgroundBrightness != 0f ||
            state.backgroundContrast != 1.0f ||
            state.backgroundSaturation != 1.0f ||
            state.backgroundGrayscale

    fun hasColorMix(state: CanvasState): Boolean =
        state.colorMixState.isEnabled && !state.colorMixState.isDefault

    fun hasSinglePhotoRasterEffects(state: CanvasState): Boolean =
        hasFilmLut(state) ||
            AtharPhotoAdjustmentsEngine.isEnabled(
                state.backgroundHighlights,
                state.backgroundShadows,
                state.backgroundTemperature
            ) ||
            state.flutedGlassState.isEnabled ||
            state.toneCurveState.isEnabled ||
            state.opticalLensState.isEnabled ||
            state.cinematicGlowState.isEnabled ||
            state.filmGrainState.isEnabled ||
            state.glitchEffectState.isEnabled

    fun hasHeavyCollageCompositeEffects(state: CanvasState): Boolean =
        hasColorMix(state) || hasSinglePhotoRasterEffects(state)

    fun hasAnyPixelEffects(state: CanvasState): Boolean =
        state.backgroundBlur > 0.5f || hasHeavyCollageCompositeEffects(state)

    @JvmOverloads
    fun buildCompositeColorFilter(state: CanvasState, includeFilmMatrix: Boolean = true): ColorFilter? {
        if (!hasColorMatrixAdjustments(state, includeFilmMatrix)) return null
        return FilmPresetEngine.createCompositeFilter(
            preset = if (includeFilmMatrix && hasFilmLut(state)) state.filmPreset else FilmPreset.NONE,
            dim = state.backgroundDim,
            brightness = state.backgroundBrightness,
            contrast = state.backgroundContrast,
            saturation = state.backgroundSaturation,
            isGrayscale = state.backgroundGrayscale,
            colorGradingEnabled = state.colorGradingEnabled,
            liftR = state.liftRed, liftG = state.liftGreen, liftB = state.liftBlue,
            gammaR = state.gammaRed, gammaG = state.gammaGreen, gammaB = state.gammaBlue,
            gainR = state.gainRed, gainG = state.gainGreen, gainB = state.gainBlue
        )
    }

    fun blurBitmapForCanvas(
        src: Bitmap,
        blurValue: Float,
        referenceCanvasWidth: Int,
        isPreview: Boolean
    ): Bitmap {
        if (blurValue <= 0.5f) return src
        val scaledRadius = if (isPreview) {
            blurValue.toInt().coerceIn(1, 35)
        } else {
            (blurValue * (referenceCanvasWidth / 1080f)).toInt().coerceIn(1, 40)
        }
        val maxDim = if (isPreview) 360f else 1080f
        val downScale = min(1f, maxDim / max(src.width, src.height).toFloat())
        return if (downScale < 0.95f) {
            val tw = (src.width * downScale).toInt().coerceAtLeast(10)
            val th = (src.height * downScale).toInt().coerceAtLeast(10)
            val scaled = Bitmap.createScaledBitmap(src, tw, th, true)
            val r = (scaledRadius * downScale).toInt().coerceIn(1, 35)
            val blurred = AtharImageProcessor.fastStackBlur(scaled, r)
            if (blurred !== scaled && !scaled.isRecycled) scaled.recycle()
            blurred
        } else {
            AtharImageProcessor.fastStackBlur(src, scaledRadius)
        }
    }

    fun assembleCollagePhotoLayer(
        width: Int,
        height: Int,
        state: CanvasState,
        isPreview: Boolean,
        cellBitmapProvider: (cell: CollageCell, reqW: Int, reqH: Int) -> Bitmap?,
        recycleProvidedBitmaps: Boolean
    ): Bitmap {
        val photoLayer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(photoLayer)
        val density = canvasDensity(width.toFloat())
        val spacing = state.collageState.spacingDp * density
        val radius = state.collageState.cornerRadiusDp * density
        val drawPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        state.collageState.cells.forEach { cell ->
            val b = CollageCoverFitMath.computeCellBounds(cell, width.toFloat(), height.toFloat(), spacing)
            val cw = (b[2] - b[0]).toInt().coerceAtLeast(32)
            val ch = (b[3] - b[1]).toInt().coerceAtLeast(32)
            val scale = cell.photoScale.coerceIn(1.0f, 4.0f)
            val provided = cellBitmapProvider(cell, (cw * scale).toInt().coerceAtLeast(32), (ch * scale).toInt().coerceAtLeast(32))
                ?: return@forEach
            if (provided.isRecycled) return@forEach

            var cellReady = provided
            if (state.backgroundBlur > 0.5f) {
                val blurred = blurBitmapForCanvas(provided, state.backgroundBlur, width, isPreview)
                if (recycleProvidedBitmaps && blurred !== provided && !provided.isRecycled) {
                    provided.recycle()
                }
                cellReady = blurred
            }

            CollageCanvasRenderer.drawSingleCellPhoto(
                canvas = canvas,
                cell = cell,
                bitmap = cellReady,
                width = width.toFloat(),
                height = height.toFloat(),
                spacing = spacing,
                radius = radius,
                paint = drawPaint
            )

            if ((cellReady !== provided || recycleProvidedBitmaps) && !cellReady.isRecycled) {
                cellReady.recycle()
            }
        }
        return photoLayer
    }

    fun applyPhotoPassesInPlaceOrReplace(
        photoLayerInput: Bitmap,
        state: CanvasState,
        width: Int,
        height: Int,
        applyColorMix: Boolean = true
    ): Bitmap {
        var current = photoLayerInput
        val activePreset = if (hasFilmLut(state)) state.filmPreset else FilmPreset.NONE

        if (applyColorMix && hasColorMix(state)) {
            val mixed = AtharColorMixEngine.processBitmapInPlace(current, state.colorMixState)
            if (mixed !== current && !current.isRecycled) current.recycle()
            current = mixed
        }

        if (state.flutedGlassState.isEnabled) {
            val fluted = AtharFlutedGlassEngine.processBitmap(current, state.flutedGlassState, inPlace = false)
            if (fluted !== current && !current.isRecycled) current.recycle()
            current = fluted
        }

        val lensGeo = AtharOpticalLensEngine.applyLensGeometryAndEdgeEffects(
            source = current,
            state = state.opticalLensState,
            filmPreset = activePreset,
            referenceWidthPx = width
        )
        if (lensGeo !== current) {
            if (!current.isRecycled) current.recycle()
            current = lensGeo
        }

        if (hasFilmLut(state)) {
            val lutDone = FilmPresetEngine.applyFilmLut(
                source = current,
                preset = state.filmPreset,
                intensity = state.filmPresetIntensity,
                inPlace = true
            )
            if (lutDone !== current && !current.isRecycled) current.recycle()
            current = lutDone
        }

        if (state.toneCurveState.isEnabled) {
            val curved = AtharToneCurveEngine.processBitmap(current, state.toneCurveState, inPlace = true)
            if (curved !== current && !current.isRecycled) current.recycle()
            current = curved
        }

        if (AtharPhotoAdjustmentsEngine.isEnabled(
                state.backgroundHighlights,
                state.backgroundShadows,
                state.backgroundTemperature
            )
        ) {
            val adjusted = AtharPhotoAdjustmentsEngine.processBitmap(
                source = current,
                highlights = state.backgroundHighlights,
                shadows = state.backgroundShadows,
                temperature = state.backgroundTemperature,
                inPlace = true
            )
            if (adjusted !== current && !current.isRecycled) current.recycle()
            current = adjusted
        }

        val colorFilter = buildCompositeColorFilter(state, includeFilmMatrix = false)
        if (colorFilter != null) {
            val filtered = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val c = Canvas(filtered)
            val p = Paint(Paint.FILTER_BITMAP_FLAG).apply { this.colorFilter = colorFilter }
            c.drawBitmap(current, 0f, 0f, p)
            if (!current.isRecycled) current.recycle()
            current = filtered
        }

        if (state.filmGrainState.isEnabled) {
            val grained = AtharFilmGrainEngine.processBitmap(
                source = current,
                state = state.filmGrainState,
                referenceWidthPx = width,
                isMonochrome = activePreset.isMonochrome,
                inPlace = true
            )
            if (grained !== current && !current.isRecycled) current.recycle()
            current = grained
        }

        return current
    }

    @JvmOverloads
    fun renderSinglePhotoHeavyEffects(
        input: Bitmap,
        state: CanvasState,
        includeColorMix: Boolean,
        referenceWidthPx: Int = input.width
    ): Bitmap {
        var current = input
        val activePreset = if (hasFilmLut(state)) state.filmPreset else FilmPreset.NONE

        if (includeColorMix && hasColorMix(state)) {
            val mixed = if (current !== input) {
                AtharColorMixEngine.processBitmapInPlace(current, state.colorMixState)
            } else {
                AtharColorMixEngine.processBitmap(current, state.colorMixState)
            }
            if (current !== input && mixed !== current && !current.isRecycled) current.recycle()
            current = mixed
        }

        if (state.flutedGlassState.isEnabled) {
            val fluted = AtharFlutedGlassEngine.processBitmap(current, state.flutedGlassState, inPlace = false)
            if (fluted !== current) {
                if (current !== input && !current.isRecycled) current.recycle()
                current = fluted
            }
        }

        val lensGeo = AtharOpticalLensEngine.applyLensGeometryAndEdgeEffects(
            source = current,
            state = state.opticalLensState,
            filmPreset = activePreset,
            referenceWidthPx = referenceWidthPx
        )
        if (lensGeo !== current) {
            if (current !== input && !current.isRecycled) current.recycle()
            current = lensGeo
        }

        if (hasFilmLut(state)) {
            val lutDone = FilmPresetEngine.applyFilmLut(
                source = current,
                preset = state.filmPreset,
                intensity = state.filmPresetIntensity,
                inPlace = (current !== input)
            )
            if (lutDone !== current) {
                if (current !== input && !current.isRecycled) current.recycle()
                current = lutDone
            }
        }

        if (state.toneCurveState.isEnabled) {
            val curved = AtharToneCurveEngine.processBitmap(current, state.toneCurveState, inPlace = (current !== input))
            if (curved !== current) {
                if (current !== input && !current.isRecycled) current.recycle()
                current = curved
            }
        }

        if (AtharPhotoAdjustmentsEngine.isEnabled(
                state.backgroundHighlights,
                state.backgroundShadows,
                state.backgroundTemperature
            )
        ) {
            val adjusted = AtharPhotoAdjustmentsEngine.processBitmap(
                source = current,
                highlights = state.backgroundHighlights,
                shadows = state.backgroundShadows,
                temperature = state.backgroundTemperature,
                inPlace = (current !== input)
            )
            if (adjusted !== current) {
                if (current !== input && !current.isRecycled) current.recycle()
                current = adjusted
            }
        }

        if (hasSinglePhotoRasterEffects(state)) {
            val colorFilter = buildCompositeColorFilter(state, includeFilmMatrix = false)
            if (colorFilter != null) {
                val filtered = Bitmap.createBitmap(current.width, current.height, Bitmap.Config.ARGB_8888)
                val p = Paint(Paint.FILTER_BITMAP_FLAG).apply { this.colorFilter = colorFilter }
                Canvas(filtered).drawBitmap(current, 0f, 0f, p)
                if (current !== input && !current.isRecycled) current.recycle()
                current = filtered
            }
        }

        if (state.filmGrainState.isEnabled) {
            val grained = AtharFilmGrainEngine.processBitmap(
                source = current,
                state = state.filmGrainState,
                referenceWidthPx = referenceWidthPx,
                isMonochrome = activePreset.isMonochrome,
                inPlace = (current !== input)
            )
            if (grained !== current) {
                if (current !== input && !current.isRecycled) current.recycle()
                current = grained
            }
        }

        val glowOverlay = AtharOpticalGlowEngine.extractHighlightOverlay(current, state.cinematicGlowState, referenceWidthPx)
        val lensOverlay = AtharOpticalLensEngine.extractLensHighlightOverlay(current, state.opticalLensState, activePreset, referenceWidthPx)
        if (glowOverlay != null || lensOverlay != null) {
            val target = if (current !== input && current.isMutable) current else current.copy(Bitmap.Config.ARGB_8888, true)
            drawHighlightOverlaysScreen(Canvas(target), target.width, target.height, state, glowOverlay, lensOverlay)
            glowOverlay?.recycle()
            lensOverlay?.recycle()
            if (target !== current) {
                if (current !== input && !current.isRecycled) current.recycle()
                current = target
            }
        }

        if (state.glitchEffectState.isEnabled) {
            val glitched = AtharGlitchEngine.processBitmap(current, state.glitchEffectState, inPlace = (current !== input))
            if (glitched !== current) {
                if (current !== input && !current.isRecycled) current.recycle()
                current = glitched
            }
        }
        return current
    }

    fun buildFinalCollageCompositeBitmap(
        width: Int,
        height: Int,
        state: CanvasState,
        processedPhotoLayer: Bitmap,
        isEmptyPredicate: (CollageCell) -> Boolean
    ): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        drawFrameAndPhotoLayer(c, width, height, state, processedPhotoLayer, isEmptyPredicate)
        if (state.glitchEffectState.isEnabled) {
            val glitched = AtharGlitchEngine.processBitmap(out, state.glitchEffectState, inPlace = true)
            if (glitched !== out) {
                out.recycle()
                return glitched
            }
        }
        return out
    }

    fun renderCollageCompositeFromMixedLayer(
        mixedPhotoLayer: Bitmap,
        state: CanvasState,
        isEmptyPredicate: (CollageCell) -> Boolean
    ): Bitmap {
        val copy = mixedPhotoLayer.copy(Bitmap.Config.ARGB_8888, true)
        val photoDone = applyPhotoPassesInPlaceOrReplace(
            copy, state, copy.width, copy.height, applyColorMix = false
        )
        val composite = buildFinalCollageCompositeBitmap(
            photoDone.width, photoDone.height, state, photoDone, isEmptyPredicate
        )
        if (!photoDone.isRecycled) photoDone.recycle()
        return composite
    }

    private fun drawHighlightOverlaysScreen(
        canvas: Canvas,
        width: Int,
        height: Int,
        state: CanvasState,
        glowOverlay: Bitmap?,
        lensOverlay: Bitmap?
    ) {
        if (glowOverlay == null && lensOverlay == null) return
        val dst = Rect(0, 0, width, height)
        val screenPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                blendMode = android.graphics.BlendMode.SCREEN
            } else {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            }
        }
        if (glowOverlay != null && !glowOverlay.isRecycled) {
            screenPaint.alpha = ((state.cinematicGlowState.glow / 100f) * 255f).toInt().coerceIn(0, 255)
            canvas.drawBitmap(glowOverlay, null, dst, screenPaint)
        }
        if (lensOverlay != null && !lensOverlay.isRecycled) {
            screenPaint.alpha = 255
            canvas.drawBitmap(lensOverlay, null, dst, screenPaint)
        }
    }

    private fun drawFrameAndPhotoLayer(
        canvas: Canvas,
        width: Int,
        height: Int,
        state: CanvasState,
        processedPhotoLayer: Bitmap,
        isEmptyPredicate: (CollageCell) -> Boolean
    ) {
        val density = canvasDensity(width.toFloat())
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = state.collageState.frameColorLong.toInt()
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), framePaint)
        CollageCanvasRenderer.drawEmptyPlaceholders(
            canvas, state.collageState, width.toFloat(), height.toFloat(), density, isEmptyPredicate
        )
        canvas.drawBitmap(processedPhotoLayer, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))

        val activePreset = if (hasFilmLut(state)) state.filmPreset else FilmPreset.NONE
        val glowOverlay = AtharOpticalGlowEngine.extractHighlightOverlay(processedPhotoLayer, state.cinematicGlowState, width)
        val lensOverlay = AtharOpticalLensEngine.extractLensHighlightOverlay(processedPhotoLayer, state.opticalLensState, activePreset, width)
        if (glowOverlay != null || lensOverlay != null) {
            drawHighlightOverlaysScreen(canvas, width, height, state, glowOverlay, lensOverlay)
            glowOverlay?.recycle()
            lensOverlay?.recycle()
        }
    }

    fun renderSoftwareBackground(
        context: Context,
        canvas: Canvas,
        state: CanvasState,
        width: Int,
        height: Int
    ) {
        var drawn = false

        if (state.collageState.isEnabled) {
            val emptySet = HashSet<String>()
            var photoLayer = assembleCollagePhotoLayer(
                width = width,
                height = height,
                state = state,
                isPreview = false,
                cellBitmapProvider = { cell, reqW, reqH ->
                    val bmp = cell.imageUri?.let { AtharImageProcessor.decodeSampledBitmap(context, it, reqW, reqH) }
                    if (bmp == null) emptySet.add(cell.id)
                    bmp
                },
                recycleProvidedBitmaps = true
            )
            photoLayer = applyPhotoPassesInPlaceOrReplace(photoLayer, state, width, height)

            if (state.glitchEffectState.isEnabled) {
                val composite = buildFinalCollageCompositeBitmap(width, height, state, photoLayer) { it.id in emptySet }
                if (!photoLayer.isRecycled) photoLayer.recycle()
                canvas.drawBitmap(composite, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                if (!composite.isRecycled) composite.recycle()
            } else {
                drawFrameAndPhotoLayer(canvas, width, height, state, photoLayer) { it.id in emptySet }
                if (!photoLayer.isRecycled) photoLayer.recycle()
            }
            drawn = true
        } else if (state.backgroundImageUri != null) {
            drawn = drawSoftwareSinglePhoto(context, canvas, state, state.backgroundImageUri, width, height)
        }

        if (!drawn) {
            canvas.drawColor(state.solidBgColorLong?.toInt() ?: Color.BLACK)
        }

        val maskBitmap = loadSoftwareMaskBitmapIfNeeded(context, state, width, height)
        try {
            drawPostBackgroundOverlays(canvas, state, width.toFloat(), height.toFloat(), maskBitmap)
        } finally {
            if (maskBitmap != null && !maskBitmap.isRecycled) maskBitmap.recycle()
        }
    }

    private fun drawSoftwareSinglePhoto(
        context: Context,
        canvas: Canvas,
        state: CanvasState,
        bgUri: Uri,
        width: Int,
        height: Int
    ): Boolean {
        val bgScale = state.bgPhotoScale.coerceIn(1.0f, 4.0f)
        val targetW = (width * bgScale).toInt().coerceAtLeast(32)
        val targetH = (height * bgScale).toInt().coerceAtLeast(32)
        val decoded = AtharImageProcessor.decodeSampledBitmap(context, bgUri, targetW, targetH) ?: return false

        var current = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        run {
            val c = Canvas(current)
            val m = Matrix()
            CollageCoverFitMath.populateMatrix(
                outMatrix = m,
                srcW = decoded.width.toFloat(),
                srcH = decoded.height.toFloat(),
                dstLeft = 0f, dstTop = 0f, dstRight = width.toFloat(), dstBottom = height.toFloat(),
                offsetX = state.bgPhotoOffsetX,
                offsetY = state.bgPhotoOffsetY,
                scale = bgScale
            )
            c.drawBitmap(decoded, m, Paint(Paint.FILTER_BITMAP_FLAG))
            decoded.recycle()
        }

        if (state.backgroundBlur > 0.5f) {
            val blurred = blurBitmapForCanvas(current, state.backgroundBlur, width, isPreview = false)
            if (blurred !== current) {
                current.recycle()
                current = if (hasHeavyCollageCompositeEffects(state) && (blurred.width != width || blurred.height != height)) {
                    val upscaled = Bitmap.createScaledBitmap(blurred, width, height, true)
                    if (!blurred.isRecycled) blurred.recycle()
                    upscaled
                } else {
                    blurred
                }
            }
        }

        val processed = renderSinglePhotoHeavyEffects(current, state, includeColorMix = true, referenceWidthPx = width)
        if (processed !== current && !current.isRecycled) current.recycle()

        val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = if (hasSinglePhotoRasterEffects(state)) {
                null
            } else {
                buildCompositeColorFilter(state, includeFilmMatrix = false)
            }
        }
        if (processed.width != width || processed.height != height) {
            val m = Matrix().apply {
                setScale(width.toFloat() / processed.width, height.toFloat() / processed.height)
            }
            canvas.drawBitmap(processed, m, bgPaint)
        } else {
            canvas.drawBitmap(processed, 0f, 0f, bgPaint)
        }
        if (!processed.isRecycled) processed.recycle()
        return true
    }

    private fun loadSoftwareMaskBitmapIfNeeded(
        context: Context,
        state: CanvasState,
        width: Int,
        height: Int
    ): Bitmap? {
        if (state.isTransparentBg || !state.maskState.isMaskActive) return null
        val customUriStr = state.maskState.customMaskUri
        return if (!customUriStr.isNullOrEmpty()) {
            try {
                AtharMaskRenderer.decodeCappedUriBitmap(context, Uri.parse(customUriStr), width, height)
            } catch (_: Exception) {
                null
            }
        } else {
            val maskDef = AtharMaskCatalog.getById(state.maskState.activeMaskId)
            if (AtharMaskRenderer.shouldDecodeAssetBitmap(maskDef)) {
                AtharMaskRenderer.decodeCappedAssetBitmap(context, maskDef, width, height)
            } else null
        }
    }

    fun drawPostBackgroundOverlays(
        canvas: Canvas,
        state: CanvasState,
        width: Float,
        height: Float,
        maskBitmap: Bitmap?
    ) {
        if (state.backgroundVignette > 0f) {
            AtharImageProcessor.drawVignette(canvas, width, height, state.backgroundVignette)
        }
        if (!state.isTransparentBg && state.gradientScrim.isEnabled) {
            CanvasGradientScrimRenderer.drawGradientScrim(canvas, width, height, state.gradientScrim)
        }
        if (!state.isTransparentBg && state.patternState.isEnabled) {
            AtharPatternFactory.drawPattern(canvas, width, height, state.patternState, canvasDensity(width))
        }
        if (!state.isTransparentBg && state.maskState.isEffectActive) {
            AtharMaskRenderer.drawMaskAndGradient(canvas, state.maskState, width, height, maskBitmap)
        }
    }
}
