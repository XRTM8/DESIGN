package app.xrtm8.athar.ui.components

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.*
import app.xrtm8.athar.core.AtharBackgroundPipeline
import app.xrtm8.athar.core.AtharColorMixEngine
import app.xrtm8.athar.core.AtharImageProcessor
import app.xrtm8.athar.core.AtharMaskRenderer
import app.xrtm8.athar.model.AtharMaskCatalog
import app.xrtm8.athar.model.CanvasState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.max

data class CanvasBitmapHolder(
    val rawBitmap: Bitmap?,
    val processedBitmap: Bitmap?,
    val cellBitmaps: Map<String, Bitmap>,
    val maskBitmap: Bitmap? = null,
    val blurredCellBitmaps: Map<String, Bitmap> = emptyMap(),
    val processedCollagePhotoLayer: Bitmap? = null,
    val processedCollageBitmap: Bitmap? = null
)

@Composable
fun rememberCanvasBackgroundBitmap(
    context: Context,
    state: CanvasState,
    viewportAspectRatio: Float? = null,
    isLiveFramingDrag: Boolean = false
): CanvasBitmapHolder {
    var rawBgBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedBgBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var maskBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(state.maskState.isEnabled, state.maskState.activeMaskId, state.maskState.customMaskUri) {
        val oldMask = maskBitmap
        if (!state.maskState.isEnabled) {
            maskBitmap = null
            recycleIfUnused(oldMask)
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) {
            val customUriStr = state.maskState.customMaskUri
            if (!customUriStr.isNullOrEmpty()) {
                runCatching { AtharMaskRenderer.decodeCappedUriBitmap(context, android.net.Uri.parse(customUriStr), 1080, 1440) }.getOrNull()
            } else {
                val def = AtharMaskCatalog.getById(state.maskState.activeMaskId)
                if (AtharMaskRenderer.shouldDecodeAssetBitmap(def)) AtharMaskRenderer.decodeCappedAssetBitmap(context, def, 1080, 1440) else null
            }
        }
        maskBitmap = loaded
        recycleIfUnused(oldMask, loaded)
    }

    val cellPhotos = remember(state.collageState.cells) {
        state.collageState.cells.mapNotNull { cell -> cell.imageUri?.let { cell.id to it } }
    }
    var cellBitmaps by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    LaunchedEffect(cellPhotos) {
        val oldMap = cellBitmaps
        val newMap = withContext(Dispatchers.IO) {
            cellPhotos.mapNotNull { (id, uri) -> AtharImageProcessor.decodeSampledBitmap(context, uri, 1080, 1920)?.let { id to it } }.toMap()
        }
        cellBitmaps = newMap
        val kept = newMap.values.toSet()
        oldMap.values.forEach { bmp ->
            if (bmp !in kept && !bmp.isRecycled) bmp.recycle()
        }
    }

    var singleStageBlur by remember { mutableStateOf<Bitmap?>(null) }
    var singleStageMix by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(state.backgroundImageUri) {
        val oldBg = rawBgBitmap
        val loaded = state.backgroundImageUri?.let { withContext(Dispatchers.IO) { AtharImageProcessor.decodeSampledBitmap(context, it, 1080, 1920) } }
        rawBgBitmap = loaded
        recycleIfUnused(oldBg, loaded, singleStageBlur, singleStageMix, processedBgBitmap)
    }

    LaunchedEffect(rawBgBitmap, state.backgroundBlur, state.collageState.isEnabled) {
        val base = rawBgBitmap
        val next = when {
            base == null || state.collageState.isEnabled -> null
            state.backgroundBlur <= 0.5f -> base
            else -> withContext(Dispatchers.Default) { AtharImageProcessor.fastPreviewBlur(base, state.backgroundBlur.toInt().coerceIn(1, 35)) }
        }
        val prev = singleStageBlur; singleStageBlur = next
        recycleIfUnused(prev, next, rawBgBitmap, singleStageMix, processedBgBitmap)
    }

    LaunchedEffect(singleStageBlur, state.colorMixState, state.collageState.isEnabled) {
        val base = singleStageBlur
        fun setMix(next: Bitmap?) {
            val prev = singleStageMix; singleStageMix = next
            recycleIfUnused(prev, next, rawBgBitmap, singleStageBlur, processedBgBitmap)
        }
        if (base == null || base.isRecycled || state.collageState.isEnabled) { setMix(null); return@LaunchedEffect }
        if (!AtharBackgroundPipeline.hasColorMix(state)) { setMix(base); return@LaunchedEffect }
        setMix(runTwoTierBitmapPass(base, 540) { input -> AtharColorMixEngine.processBitmap(input, state.colorMixState) })
        if (max(base.width, base.height) > 540) { delay(140L); setMix(withContext(Dispatchers.Default) { AtharColorMixEngine.processBitmap(base, state.colorMixState) }) }
    }

    val singleNeedsHeavy = AtharBackgroundPipeline.hasSinglePhotoRasterEffects(state)
    LaunchedEffect(
        singleStageMix, singleNeedsHeavy, state.filmPreset, state.filmPresetIntensity, state.opticalLensState,
        state.colorGradingEnabled, state.liftRed, state.liftGreen, state.liftBlue, state.gammaRed, state.gammaGreen, state.gammaBlue,
        state.gainRed, state.gainGreen, state.gainBlue, state.backgroundDim, state.backgroundBrightness, state.backgroundContrast,
        state.backgroundSaturation, state.backgroundGrayscale, state.backgroundHighlights, state.backgroundShadows, state.backgroundTemperature,
        state.cinematicGlowState, state.filmGrainState, state.glitchEffectState,
        state.flutedGlassState, state.toneCurveState, state.collageState.isEnabled
    ) {
        val base = singleStageMix
        fun setProcessed(next: Bitmap?) {
            val prev = processedBgBitmap; processedBgBitmap = next
            recycleIfUnused(prev, next, rawBgBitmap, singleStageBlur, singleStageMix)
        }
        if (base == null || base.isRecycled || state.collageState.isEnabled) { setProcessed(null); return@LaunchedEffect }
        if (!singleNeedsHeavy) { setProcessed(base); return@LaunchedEffect }
        setProcessed(runTwoTierBitmapPass(base, 540) { input -> AtharBackgroundPipeline.renderSinglePhotoHeavyEffects(input, state, false) })
        if (max(base.width, base.height) > 540) { delay(140L); setProcessed(withContext(Dispatchers.Default) { AtharBackgroundPipeline.renderSinglePhotoHeavyEffects(base, state, false) }) }
    }

    var blurredCellBitmaps by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    val collageNeedsHeavy = state.collageState.isEnabled && !state.isTransparentBg && AtharBackgroundPipeline.hasHeavyCollageCompositeEffects(state)

    LaunchedEffect(cellBitmaps, state.backgroundBlur, state.collageState.isEnabled, state.isTransparentBg, collageNeedsHeavy) {
        val old = blurredCellBitmaps
        if (!state.collageState.isEnabled || state.isTransparentBg || collageNeedsHeavy || state.backgroundBlur <= 0.5f) {
            if (old.isNotEmpty()) blurredCellBitmaps = emptyMap()
            return@LaunchedEffect
        }
        val radius = state.backgroundBlur.toInt().coerceIn(1, 35)
        val next = withContext(Dispatchers.Default) { cellBitmaps.mapValues { (_, bmp) -> if (bmp.isRecycled) bmp else AtharImageProcessor.fastPreviewBlur(bmp, radius) } }
        blurredCellBitmaps = next
    }

    val effectiveRatio = state.aspectRatio.ratio ?: viewportAspectRatio ?: (9f / 16f)
    var collageStageBase by remember { mutableStateOf<Bitmap?>(null) }
    var collageStageMix by remember { mutableStateOf<Bitmap?>(null) }
    var processedCollagePhotoLayer by remember { mutableStateOf<Bitmap?>(null) }
    var processedCollageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(
        cellBitmaps, state.collageState.layout, state.collageState.cells,
        state.collageState.spacingDp, state.collageState.cornerRadiusDp,
        state.backgroundBlur, effectiveRatio, collageNeedsHeavy, isLiveFramingDrag
    ) {
        fun setBase(next: Bitmap?) {
            val prev = collageStageBase; collageStageBase = next
            recycleIfUnused(prev, next, collageStageMix, processedCollagePhotoLayer, processedCollageBitmap)
        }
        if (!collageNeedsHeavy) { setBase(null); return@LaunchedEffect }
        if (isLiveFramingDrag) return@LaunchedEffect
        val (pw, ph) = AtharBackgroundPipeline.computePreviewSize(effectiveRatio, 540)
        setBase(withContext(Dispatchers.Default) { AtharBackgroundPipeline.assembleCollagePhotoLayer(pw, ph, state, true, { cell, _, _ -> cellBitmaps[cell.id] }, false) })
        delay(140L)
        val (fw, fh) = AtharBackgroundPipeline.computePreviewSize(effectiveRatio, 1080)
        setBase(withContext(Dispatchers.Default) { AtharBackgroundPipeline.assembleCollagePhotoLayer(fw, fh, state, true, { cell, _, _ -> cellBitmaps[cell.id] }, false) })
    }

    LaunchedEffect(collageStageBase, state.colorMixState, collageNeedsHeavy) {
        val base = collageStageBase
        fun setMix(next: Bitmap?) {
            val prev = collageStageMix; collageStageMix = next
            recycleIfUnused(prev, next, collageStageBase, processedCollagePhotoLayer, processedCollageBitmap)
        }
        if (base == null || base.isRecycled || !collageNeedsHeavy) { setMix(null); return@LaunchedEffect }
        if (!AtharBackgroundPipeline.hasColorMix(state)) { setMix(base); return@LaunchedEffect }
        setMix(runTwoTierBitmapPass(base, 540) { input -> AtharColorMixEngine.processBitmap(input, state.colorMixState) })
        if (max(base.width, base.height) > 540) { delay(140L); setMix(withContext(Dispatchers.Default) { AtharColorMixEngine.processBitmap(base, state.colorMixState) }) }
    }

    val needsRasterFinal = singleNeedsHeavy
    LaunchedEffect(
        collageStageMix, needsRasterFinal, collageNeedsHeavy,
        state.collageState.frameColorLong, state.collageState.cells, state.collageState.spacingDp, state.collageState.cornerRadiusDp,
        state.filmPreset, state.filmPresetIntensity, state.opticalLensState, state.colorGradingEnabled,
        state.liftRed, state.liftGreen, state.liftBlue, state.gammaRed, state.gammaGreen, state.gammaBlue, state.gainRed, state.gainGreen, state.gainBlue,
        state.backgroundDim, state.backgroundBrightness, state.backgroundContrast, state.backgroundSaturation, state.backgroundGrayscale,
        state.backgroundHighlights, state.backgroundShadows, state.backgroundTemperature,
        state.cinematicGlowState, state.filmGrainState, state.glitchEffectState, state.flutedGlassState, state.toneCurveState
    ) {
        val mixed = collageStageMix
        fun setFinal(photoLayer: Bitmap?, composite: Bitmap?) {
            val prevLayer = processedCollagePhotoLayer; val prevComp = processedCollageBitmap
            processedCollagePhotoLayer = photoLayer; processedCollageBitmap = composite
            recycleIfUnused(prevLayer, photoLayer, composite, collageStageBase, collageStageMix)
            recycleIfUnused(prevComp, photoLayer, composite, collageStageBase, collageStageMix)
        }
        if (mixed == null || mixed.isRecycled || !collageNeedsHeavy) { setFinal(null, null); return@LaunchedEffect }
        if (!needsRasterFinal) { setFinal(mixed, null); return@LaunchedEffect }
        val proxy = runTwoTierBitmapPass(mixed, 540) { input ->
            AtharBackgroundPipeline.renderCollageCompositeFromMixedLayer(input, state) { cell -> cellBitmaps[cell.id] == null }
        }
        setFinal(null, proxy)
        if (max(mixed.width, mixed.height) > 540) {
            delay(140L)
            val full = withContext(Dispatchers.Default) {
                AtharBackgroundPipeline.renderCollageCompositeFromMixedLayer(mixed, state) { cell -> cellBitmaps[cell.id] == null }
            }
            setFinal(null, full)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            rawBgBitmap = null
            singleStageBlur = null
            singleStageMix = null
            processedBgBitmap = null
            maskBitmap = null
            cellBitmaps = emptyMap()
            blurredCellBitmaps = emptyMap()
            collageStageBase = null
            collageStageMix = null
            processedCollagePhotoLayer = null
            processedCollageBitmap = null
        }
    }

    return CanvasBitmapHolder(
        rawBitmap = rawBgBitmap, processedBitmap = processedBgBitmap, cellBitmaps = cellBitmaps,
        maskBitmap = maskBitmap, blurredCellBitmaps = blurredCellBitmaps,
        processedCollagePhotoLayer = processedCollagePhotoLayer, processedCollageBitmap = processedCollageBitmap
    )
}

/**
 * UI-bound Bitmaps on API 26+ are backed by native memory reclaimed safely by GC once
 * Compose drops references; manual recycle() while RenderThread still holds a RecordingCanvas
 * risks "Canvas: trying to use a recycled bitmap" crashes during rapid scrubbing.
 */
@Suppress("UNUSED_PARAMETER")
private fun recycleIfUnused(candidate: Bitmap?, vararg keep: Bitmap?) {
    // Intentionally left as a no-op for Compose-referenced Bitmaps; GC handles native memory safely.
}

private suspend fun runTwoTierBitmapPass(base: Bitmap, proxyMaxDim: Int, transform: (Bitmap) -> Bitmap): Bitmap =
    withContext(Dispatchers.Default) {
        val maxSide = max(base.width, base.height)
        if (maxSide <= proxyMaxDim) return@withContext transform(base)
        val scale = proxyMaxDim.toFloat() / maxSide
        val scaled = Bitmap.createScaledBitmap(base, (base.width * scale).toInt().coerceAtLeast(16), (base.height * scale).toInt().coerceAtLeast(16), true)
        val result = transform(scaled)
        if (result !== scaled && !scaled.isRecycled) scaled.recycle()
        result
    }
