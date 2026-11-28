package app.xrtm8.athar.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.xrtm8.athar.core.AtharPaletteExtractor
import app.xrtm8.athar.export.AtharExportManager
import app.xrtm8.athar.model.*
import app.xrtm8.athar.project.AtharProjectManager
import app.xrtm8.athar.project.AtharProjectMeta
import app.xrtm8.athar.typography.AtharFontManager
import app.xrtm8.athar.typography.RichTextSpanMapper
import app.xrtm8.athar.ui.viewmodel.StudioEffectsCollageOps
import app.xrtm8.athar.ui.viewmodel.StudioHistoryController
import app.xrtm8.athar.ui.viewmodel.StudioLayerTypographyOps
import app.xrtm8.athar.ui.viewmodel.StudioLayerTypographyOps.withKashida
import app.xrtm8.athar.ui.viewmodel.StudioUiEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Orchestrating ViewModel for Athar Studio.
 * Delegates history/undo state to [StudioHistoryController], typography/layer mutations
 * to [StudioLayerTypographyOps], and background/collage mutations to [StudioEffectsCollageOps].
 */
class AtharStudioViewModel : ViewModel() {

    private val history = StudioHistoryController()
    val canvasState: StateFlow<CanvasState> = history.canvasState
    val canUndo: StateFlow<Boolean> = history.canUndo
    val canRedo: StateFlow<Boolean> = history.canRedo

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

    private val _projectsList = MutableStateFlow<List<AtharProjectMeta>>(emptyList())
    val projectsList: StateFlow<List<AtharProjectMeta>> = _projectsList.asStateFlow()

    private val _extractedPalette = MutableStateFlow<AtharPaletteExtractor.ExtractedPalette?>(null)
    val extractedPalette: StateFlow<AtharPaletteExtractor.ExtractedPalette?> = _extractedPalette.asStateFlow()

    private val _isAmoledWhite = MutableStateFlow(false)
    val isAmoledWhite: StateFlow<Boolean> = _isAmoledWhite.asStateFlow()

    private val _customFonts = MutableStateFlow<List<CustomFont>>(emptyList())
    val customFonts: StateFlow<List<CustomFont>> = _customFonts.asStateFlow()

    private var copiedLayerStyle: TextLayer? = null
    private val _hasCopiedStyle = MutableStateFlow(false)
    val hasCopiedStyle: StateFlow<Boolean> = _hasCopiedStyle.asStateFlow()

    private val _uiEvents = MutableSharedFlow<StudioUiEvent>(extraBufferCapacity = 8)
    val uiEvents: SharedFlow<StudioUiEvent> = _uiEvents.asSharedFlow()

    @Volatile
    var currentProjectId: String? = null
        private set

    private var initialProjectOpened = false

    fun hasUnsavedChanges(): Boolean = history.isDirty

    fun initTheme(context: Context) {
        runCatching {
            val sp = context.getSharedPreferences("athar_prefs", Context.MODE_PRIVATE)
            _isAmoledWhite.value = sp.getBoolean("pref_amoled_white", false)
        }
    }

    fun toggleAmoledWhite(context: Context) {
        val next = !_isAmoledWhite.value
        _isAmoledWhite.value = next
        runCatching {
            context.getSharedPreferences("athar_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("pref_amoled_white", next).apply()
        }
    }

    fun commitHistoryCheckpoint() = history.commitCheckpoint()
    fun undo() = history.undo()
    fun redo() = history.redo()

    // ==========================================
    // Layers & Typography
    // ==========================================

    fun addTextLayer(initialText: String = "نص جديد") =
        history.pushHistory(StudioLayerTypographyOps.addLayer(history.current(), initialText))

    fun duplicateActiveLayer() =
        history.pushHistory(StudioLayerTypographyOps.duplicateActiveLayer(history.current()))

    fun removeActiveLayer() =
        history.pushHistory(StudioLayerTypographyOps.removeActiveLayer(history.current()))

    fun moveActiveLayerUp() =
        StudioLayerTypographyOps.moveLayer(history.current(), 1)?.let { history.pushHistory(it) }

    fun moveActiveLayerDown() =
        StudioLayerTypographyOps.moveLayer(history.current(), -1)?.let { history.pushHistory(it) }

    fun selectLayer(layerId: String) {
        if (history.current().activeLayerId != layerId) {
            history.updateState(markDirty = false) { it.copy(activeLayerId = layerId) }
        }
    }

    fun toggleLayerVisibility(layerId: String = history.current().activeLayer.id) {
        val cur = history.current()
        val updated = cur.layers.map { if (it.id == layerId) it.copy(isVisible = !it.isVisible) else it }
        if (updated != cur.layers) history.pushHistory(cur.copy(layers = updated))
    }

    fun renameLayer(layerId: String, newName: String) {
        val clean = newName.trim().take(24).ifBlank { return }
        val cur = history.current()
        val updated = cur.layers.map { if (it.id == layerId) it.copy(name = clean) else it }
        if (updated != cur.layers) history.pushHistory(cur.copy(layers = updated))
    }

    fun setText(newText: String) {
        if (newText == history.current().rawText) return
        history.pushActiveLayerHistory { layer ->
            val adjusted = RichTextSpanMapper.adjustSpansOnTextEdit(layer.rawText, newText, layer.textSpans)
            layer.copy(rawText = newText, textSpans = adjusted).withKashida()
        }
    }

    fun setTextWithSpans(newText: String, spans: List<TextSpanStyle>) {
        history.pushActiveLayerHistory { layer ->
            layer.copy(rawText = newText, textSpans = spans).withKashida()
        }
    }

    fun setTextSpans(spans: List<TextSpanStyle>) {
        history.pushActiveLayerHistory { it.copy(textSpans = spans) }
    }

    fun setCustomGradient(gradient: CustomGradientState) {
        history.markSliderChange()
        history.updateActiveLayer {
            it.copy(
                customGradient = gradient,
                customTextColorLong = if (gradient.isEnabled) null else it.customTextColorLong
            )
        }
    }

    fun setOuterGlowRadius(radius: Float) {
        history.markSliderChange()
        history.updateActiveLayer { it.copy(outerGlowRadius = radius.coerceIn(0f, 60f)) }
    }

    fun setOuterGlowColor(colorLong: Long) =
        history.pushActiveLayerHistory { it.copy(outerGlowColorLong = colorLong) }

    fun addShapeLayer(type: ShapeType) {
        val defaults = when (type) {
            ShapeType.DIVIDER_H -> ShapeLayerConfig(type = type, widthRatio = 0.55f, heightRatio = 0.02f, strokeWidth = 2f)
            ShapeType.DIVIDER_V -> ShapeLayerConfig(type = type, widthRatio = 0.02f, heightRatio = 0.45f, strokeWidth = 2f)
            ShapeType.CIRCLE -> ShapeLayerConfig(type = type, widthRatio = 0.35f, heightRatio = 0.35f)
            ShapeType.FRAME -> ShapeLayerConfig(type = type, widthRatio = 0.85f, heightRatio = 0.85f, isFilled = false, isStroked = true, strokeWidth = 2f)
            else -> ShapeLayerConfig(type = type, widthRatio = 0.50f, heightRatio = 0.14f, cornerRadius = 12f)
        }
        val shapeLayer = TextLayer(
            id = java.util.UUID.randomUUID().toString(),
            name = type.labelAr,
            shapeConfig = defaults
        )
        val cur = history.current()
        history.pushHistory(cur.copy(layers = cur.layers + shapeLayer, activeLayerId = shapeLayer.id))
    }

    fun updateActiveShape(config: ShapeLayerConfig) {
        history.markSliderChange()
        history.updateActiveLayer { it.copy(shapeConfig = config) }
    }

    fun setStretchLevel(newLevel: Int) {
        val level = newLevel.coerceIn(0, 8)
        if (level == history.current().stretchLevel &&
            (level != 0 || history.current().activeLayer.paragraphBalanceMode == ParagraphBalanceMode.NONE)
        ) return
        history.markSliderChange()
        history.updateActiveLayer {
            val nextBalance = if (level == 0) ParagraphBalanceMode.NONE else it.paragraphBalanceMode
            it.copy(stretchLevel = level, paragraphBalanceMode = nextBalance).withKashida()
        }
    }

    fun setKashidaMode(mode: KashidaMode) {
        if (mode != history.current().kashidaMode) {
            history.pushActiveLayerHistory { it.copy(kashidaMode = mode).withKashida() }
        }
    }

    fun setKashidaTargetIndex(targetIndex: Int) =
        history.pushActiveLayerHistory { it.copy(kashidaTargetIndex = targetIndex, kashidaMode = KashidaMode.CUSTOM).withKashida() }

    fun justifyActiveLayerLines(context: Context) {
        val active = history.current().activeLayer
        val updated = active.copy(paragraphBalanceMode = ParagraphBalanceMode.JUSTIFY).withKashida(context)
        if (updated.displayText != active.displayText || active.paragraphBalanceMode != ParagraphBalanceMode.JUSTIFY) {
            history.pushActiveLayerHistory { updated }
        }
    }

    fun autoBalanceTypographicBlock(context: Context? = null) {
        val active = history.current().activeLayer
        val updated = active.copy(paragraphBalanceMode = ParagraphBalanceMode.BALANCE).withKashida(context)
        if (updated.displayText != active.displayText || active.paragraphBalanceMode != ParagraphBalanceMode.BALANCE) {
            history.pushActiveLayerHistory { updated }
        }
    }

    fun setEffect(effect: VisualEffect) {
        val mappedPreset = when (effect) {
            VisualEffect.ROYAL_GOLD -> LuxuryGradientPreset.ANDALUSIAN_GOLD
            VisualEffect.LIQUID_CHROME -> LuxuryGradientPreset.TITANIUM_CHROME
            VisualEffect.CYBER_NEON -> LuxuryGradientPreset.CYBER_EMERALD
            VisualEffect.FROSTED_GLASS -> LuxuryGradientPreset.FROSTED_PEARL
            else -> LuxuryGradientPreset.NONE
        }
        val customColor = when (effect) {
            VisualEffect.MINIMAL_LUXURY -> 0xFFFFFFFF
            VisualEffect.CINEMATIC_SHADOW -> 0xFFF2F2F2
            else -> null
        }
        history.pushActiveLayerHistory {
            it.copy(selectedEffect = effect, gradientPreset = mappedPreset, customTextColorLong = customColor)
        }
    }

    fun setFont(font: ArabicFontFamily) {
        val defWeight = font.defaultWeight
        history.pushActiveLayerHistory {
            it.copy(selectedFont = font, customFontPath = null, customFontName = null, fontWeight = defWeight, isBold = defWeight >= 700)
        }
    }

    fun setFontWeight(weight: Int) {
        val clamped = weight.coerceIn(100, 900)
        history.pushActiveLayerHistory { it.copy(fontWeight = clamped, isBold = clamped >= 700) }
    }

    fun setCustomColor(colorLong: Long?) = history.pushActiveLayerHistory {
        it.copy(customTextColorLong = colorLong, gradientPreset = if (colorLong != null) LuxuryGradientPreset.NONE else it.gradientPreset)
    }

    fun setStrokeWidth(width: Float) { history.markSliderChange(); history.updateActiveLayer { it.copy(strokeWidth = width.coerceIn(0f, 20f)) } }
    fun setStrokeColor(colorLong: Long) = history.pushActiveLayerHistory { it.copy(strokeColorLong = colorLong) }
    fun setShadowRadius(radius: Float) { history.markSliderChange(); history.updateActiveLayer { it.copy(shadowRadius = radius.coerceIn(0f, 60f)) } }
    fun setShadowDx(dx: Float) { history.markSliderChange(); history.updateActiveLayer { it.copy(shadowDx = dx.coerceIn(-30f, 30f)) } }
    fun setShadowDy(dy: Float) { history.markSliderChange(); history.updateActiveLayer { it.copy(shadowDy = dy.coerceIn(-30f, 40f)) } }
    fun setShadowColor(colorLong: Long) = history.pushActiveLayerHistory { it.copy(shadowColorLong = colorLong) }
    fun setLetterSpacing(spacing: Float) { history.markSliderChange(); history.updateActiveLayer { it.copy(letterSpacing = spacing.coerceIn(-0.05f, 0.35f)) } }
    fun setTextOpacity(opacity: Float) { history.markSliderChange(); history.updateActiveLayer { it.copy(textOpacity = opacity.coerceIn(0.1f, 1.0f)) } }
    fun toggleBold() = history.pushActiveLayerHistory { it.copy(isBold = !it.isBold) }
    fun toggleItalic() = history.pushActiveLayerHistory { it.copy(isItalic = !it.isItalic) }
    fun toggleUnderline() = history.pushActiveLayerHistory { it.copy(isUnderline = !it.isUnderline) }
    fun toggleLock() = history.updateActiveLayer { it.copy(isLocked = !it.isLocked) }
    fun toggleGridGuides() = history.updateState(markDirty = false) { it.copy(showGridGuides = !it.showGridGuides) }
    private fun normalizeAngle180(angle: Float): Float {
        var a = angle % 360f
        if (a > 180f) a -= 360f
        if (a < -180f) a += 360f
        return if (a == -0f) 0f else a
    }

    fun setRotationAngle(angle: Float) { history.markSliderChange(); history.updateActiveLayer { it.copy(rotationAngle = normalizeAngle180(angle)) } }
    fun setScale(scale: Float) { history.markSliderChange(); history.updateActiveLayer { it.copy(scaleFactor = scale.coerceIn(0.25f, 4.0f)) } }
    fun setTextAlign(alignment: TextAlignOption) = history.pushActiveLayerHistory { it.copy(textAlign = alignment) }
    fun setLineSpacing(multiplier: Float) { history.markSliderChange(); history.updateActiveLayer { it.copy(lineSpacingMultiplier = multiplier.coerceIn(1.0f, 2.5f)) } }
    fun setBoxWidthRatio(ratio: Float) { history.markSliderChange(); history.updateActiveLayer { it.copy(boxWidthRatio = ratio.coerceIn(0.30f, 1.0f)) } }
    fun setStylisticSet(set: Int) = history.pushActiveLayerHistory { it.copy(stylisticSet = set.coerceIn(0, 20)) }
    fun toggleSwash(enabled: Boolean) = history.pushActiveLayerHistory { it.copy(enableSwash = enabled) }
    fun toggleDiscretionaryLigatures(enabled: Boolean) = history.pushActiveLayerHistory { it.copy(enableDiscretionaryLigatures = enabled) }
    fun setWordSpacing(spacing: Float) { history.markSliderChange(); history.updateActiveLayer { it.copy(wordSpacing = spacing.coerceIn(-20f, 60f)) } }
    fun toggleGlassBackdrop(enabled: Boolean) = history.pushActiveLayerHistory { it.copy(enableGlassBackdrop = enabled) }
    fun setGlassBackdropOpacity(opacity: Float) { history.markSliderChange(); history.updateActiveLayer { it.copy(glassBackdropOpacity = opacity.coerceIn(0.1f, 0.98f)) } }
    fun setGlassBackdropCornerRadius(radius: Float) { history.markSliderChange(); history.updateActiveLayer { it.copy(glassBackdropCornerRadius = radius.coerceIn(4f, 48f)) } }
    fun setGlassBackdropPadding(padding: Float) { history.markSliderChange(); history.updateActiveLayer { it.copy(glassBackdropPadding = padding.coerceIn(8f, 56f)) } }
    fun setFontSize(size: Float) { history.markSliderChange(); history.updateActiveLayer { it.copy(fontSize = size.coerceIn(6f, 100f)) } }
    fun setBlendMode(mode: LayerBlendMode) = history.pushActiveLayerHistory { it.copy(blendMode = mode) }
    fun toggleCurvedText(enabled: Boolean) = history.pushActiveLayerHistory { it.copy(isCurved = enabled) }
    fun setCurveAngle(angle: Float) { history.markSliderChange(); history.updateActiveLayer { it.copy(curveAngle = angle.coerceIn(-180f, 180f)) } }
    fun setLuxuryGradient(preset: LuxuryGradientPreset) = history.pushActiveLayerHistory { it.copy(gradientPreset = preset, customTextColorLong = null) }
    fun setGradientAngle(angle: Float) { history.markSliderChange(); history.updateActiveLayer { it.copy(gradientAngle = angle % 360f) } }
    fun setGradientRadial(isRadial: Boolean) = history.pushActiveLayerHistory { it.copy(isRadialGradient = isRadial) }
    fun setHollowText(isHollow: Boolean) = history.pushActiveLayerHistory { it.copy(isHollowText = isHollow) }

    fun setGlassBackdropPreset(preset: GlassBackdropPreset) = history.pushActiveLayerHistory {
        it.copy(
            enableGlassBackdrop = true,
            glassBackdropColorLong = preset.bgArgb,
            glassBackdropBorderColorLong = preset.strokeArgb,
            glassBackdropOpacity = preset.opacity,
            glassBackdropBorderOpacity = 1.0f
        )
    }
    fun setGlassBackdropColor(colorLong: Long) = history.pushActiveLayerHistory {
        it.copy(enableGlassBackdrop = true, glassBackdropColorLong = colorLong)
    }
    fun setGlassBackdropBorderColor(colorLong: Long) = history.pushActiveLayerHistory {
        it.copy(enableGlassBackdrop = true, glassBackdropBorderColorLong = colorLong)
    }
    fun setGlassBackdropBorderWidth(width: Float) {
        history.markSliderChange()
        history.updateActiveLayer { it.copy(glassBackdropBorderWidth = width.coerceIn(0f, 6f)) }
    }
    fun setGlassBackdropBorderOpacity(opacity: Float) {
        history.markSliderChange()
        history.updateActiveLayer { it.copy(glassBackdropBorderOpacity = opacity.coerceIn(0f, 1f)) }
    }

    fun updateTransform(normX: Float, normY: Float, scale: Float, rotation: Float) =
        history.updateActiveLayer { it.copy(normOffsetX = normX, normOffsetY = normY, scaleFactor = scale.coerceIn(0.25f, 4.0f), rotationAngle = normalizeAngle180(rotation)) }

    fun commitTransform(normX: Float, normY: Float, scale: Float, rotation: Float) =
        history.pushActiveLayerHistory { it.copy(normOffsetX = normX, normOffsetY = normY, scaleFactor = scale.coerceIn(0.25f, 4.0f), rotationAngle = normalizeAngle180(rotation)) }

    fun alignCenter() = history.pushActiveLayerHistory { it.copy(normOffsetX = 0f, normOffsetY = 0f, rotationAngle = 0f) }
    fun alignTop() = history.pushActiveLayerHistory { it.copy(normOffsetX = 0f, normOffsetY = -0.28f, rotationAngle = 0f) }
    fun alignBottom() = history.pushActiveLayerHistory { it.copy(normOffsetX = 0f, normOffsetY = 0.28f, rotationAngle = 0f) }
    fun resetCanvasTransform() = history.pushActiveLayerHistory { it.copy(normOffsetX = 0f, normOffsetY = 0f, scaleFactor = 1.0f, rotationAngle = 0f) }

    fun snapToPosition(normX: Float, normY: Float, textAlign: TextAlignOption? = null) =
        history.pushActiveLayerHistory { it.copy(normOffsetX = normX, normOffsetY = normY, textAlign = textAlign ?: it.textAlign) }

    fun nudgeOffset(deltaNormX: Float, deltaNormY: Float) = history.pushActiveLayerHistory {
        it.copy(normOffsetX = (it.normOffsetX + deltaNormX).coerceIn(-0.45f, 0.45f), normOffsetY = (it.normOffsetY + deltaNormY).coerceIn(-0.45f, 0.45f))
    }

    fun copyActiveLayerStyle() {
        copiedLayerStyle = history.current().activeLayer.copy()
        _hasCopiedStyle.value = true
        _exportMessage.value = "تم نسخ تنسيق الطبقة"
    }

    fun pasteStyleToActiveLayer() {
        val style = copiedLayerStyle ?: return
        history.pushActiveLayerHistory { StudioLayerTypographyOps.applyStyle(it, style) }
        _exportMessage.value = "تم تطبيق التنسيق على الطبقة"
    }

    // ==========================================
    // Canvas, Photo, Grading, Effects, Masks, Collage
    // ==========================================

    fun setAspectRatio(ratio: CanvasAspectRatio, autoRefit: Boolean = true) {
        val cur = history.current()
        if (ratio != cur.aspectRatio) {
            history.pushHistory(StudioLayerTypographyOps.refitForAspectRatio(cur, ratio, autoRefit))
        }
    }

    fun setSolidBgColor(colorLong: Long?) = history.pushHistory(history.current().copy(solidBgColorLong = colorLong, isTransparentBg = false))
    fun setBackgroundImageUri(uri: Uri?) = history.pushHistory(history.current().copy(backgroundImageUri = uri, bgPhotoOffsetX = 0f, bgPhotoOffsetY = 0f, bgPhotoScale = 1.0f))
    fun setBackgroundDim(dim: Float) { history.markSliderChange(); history.updateState { it.copy(backgroundDim = dim.coerceIn(0f, 0.95f)) } }
    fun setBackgroundBlur(blur: Float) { history.markSliderChange(); history.updateState { it.copy(backgroundBlur = blur.coerceIn(0f, 35f)) } }
    fun setFlutedGlassState(fluted: FlutedGlassState) {
        history.markSliderChange()
        val sanitized = fluted.copy(
            intensity = fluted.intensity.coerceIn(0f, 1f),
            ribCount = fluted.ribCount.coerceIn(8, 80),
            specular = fluted.specular.coerceIn(0f, 1f),
            coverage = fluted.coverage.coerceIn(0.1f, 1.0f),
            angleDegrees = fluted.angleDegrees.coerceIn(-45f, 90f),
            chromaticDispersion = fluted.chromaticDispersion.coerceIn(0f, 1f),
            edgeSoftness = fluted.edgeSoftness.coerceIn(0f, 1f)
        )
        history.updateState { it.copy(flutedGlassState = sanitized) }
    }

    fun setToneCurveState(tc: ToneCurveState) {
        history.markSliderChange()
        val sanitized = tc.copy(
            blacks = tc.blacks.coerceIn(-0.5f, 0.5f),
            shadows = tc.shadows.coerceIn(-0.5f, 0.5f),
            highlights = tc.highlights.coerceIn(-0.5f, 0.5f),
            whites = tc.whites.coerceIn(-0.5f, 0.5f),
            splitShadowHue = ((tc.splitShadowHue % 360f) + 360f) % 360f,
            splitShadowSat = tc.splitShadowSat.coerceIn(0f, 0.6f),
            splitHighlightHue = ((tc.splitHighlightHue % 360f) + 360f) % 360f,
            splitHighlightSat = tc.splitHighlightSat.coerceIn(0f, 0.6f)
        )
        history.updateState { it.copy(toneCurveState = sanitized) }
    }

    fun resetToneCurve() = history.pushHistory(history.current().copy(toneCurveState = ToneCurveState()))
    fun setBackgroundBrightness(brightness: Float) { history.markSliderChange(); history.updateState { it.copy(backgroundBrightness = brightness.coerceIn(-0.5f, 0.5f)) } }
    fun setBackgroundContrast(contrast: Float) { history.markSliderChange(); history.updateState { it.copy(backgroundContrast = contrast.coerceIn(0.5f, 1.8f)) } }
    fun setBackgroundSaturation(saturation: Float) { history.markSliderChange(); history.updateState { it.copy(backgroundSaturation = saturation.coerceIn(0f, 2.0f)) } }
    fun setBackgroundVignette(vignette: Float) { history.markSliderChange(); history.updateState { it.copy(backgroundVignette = vignette.coerceIn(0f, 1.0f)) } }
    fun setBackgroundHighlights(highlights: Float) { history.markSliderChange(); history.updateState { it.copy(backgroundHighlights = highlights.coerceIn(-1f, 1f)) } }
    fun setBackgroundShadows(shadows: Float) { history.markSliderChange(); history.updateState { it.copy(backgroundShadows = shadows.coerceIn(-1f, 1f)) } }
    fun setBackgroundTemperature(temperature: Float) { history.markSliderChange(); history.updateState { it.copy(backgroundTemperature = temperature.coerceIn(-1f, 1f)) } }
    fun setBackgroundGrayscale(enabled: Boolean) = history.pushHistory(history.current().copy(backgroundGrayscale = enabled))
    fun setTransparentBg(enabled: Boolean) = history.pushHistory(history.current().copy(isTransparentBg = enabled))

    fun setGradientScrimEnabled(enabled: Boolean) = history.pushHistory(history.current().let { it.copy(gradientScrim = it.gradientScrim.copy(isEnabled = enabled)) })
    fun setGradientScrimDirection(direction: CanvasGradientDirection) = history.pushHistory(history.current().let { it.copy(gradientScrim = it.gradientScrim.copy(direction = direction)) })
    fun setGradientScrimColor(colorLong: Long) = history.pushHistory(history.current().let { it.copy(gradientScrim = it.gradientScrim.copy(colorLong = colorLong)) })
    fun setGradientScrimOpacity(opacity: Float) { history.markSliderChange(); history.updateState { it.copy(gradientScrim = it.gradientScrim.copy(opacity = opacity.coerceIn(0f, 1f))) } }
    fun setGradientScrimMidpoint(midpoint: Float) { history.markSliderChange(); history.updateState { it.copy(gradientScrim = it.gradientScrim.copy(midpoint = midpoint.coerceIn(0.1f, 0.9f))) } }

    fun toggleFramingMode(enabled: Boolean) = history.updateState(markDirty = false) { it.copy(isFramingMode = enabled) }
    fun commitPhotoFraming(normX: Float, normY: Float, scale: Float) =
        history.pushHistory(history.current().copy(bgPhotoOffsetX = normX.coerceIn(-1f, 1f), bgPhotoOffsetY = normY.coerceIn(-1f, 1f), bgPhotoScale = scale.coerceIn(1.0f, 4.0f)))
    fun setBgPhotoOffset(normX: Float, normY: Float) { history.markSliderChange(); history.updateState { it.copy(bgPhotoOffsetX = normX.coerceIn(-1f, 1f), bgPhotoOffsetY = normY.coerceIn(-1f, 1f)) } }
    fun setBgPhotoScale(scale: Float) { history.markSliderChange(); history.updateState { it.copy(bgPhotoScale = scale.coerceIn(1.0f, 4.0f)) } }

    fun setFilmPreset(preset: FilmPreset) = history.pushHistory(history.current().copy(filmPreset = preset))
    fun setFilmPresetIntensity(intensity: Float) { history.markSliderChange(); history.updateState { it.copy(filmPresetIntensity = intensity.coerceIn(0f, 1.5f)) } }
    fun setOpticalLensState(lensState: OpticalLensState) {
        history.markSliderChange()
        val sanitized = lensState.copy(
            intensity = lensState.intensity.coerceIn(0f, 1f),
            spread = lensState.spread.coerceIn(0.1f, 1f),
            angleDegrees = ((lensState.angleDegrees % 360f) + 360f) % 360f
        )
        history.updateState { it.copy(opticalLensState = sanitized) }
    }
    fun resetOpticalLens() = history.pushHistory(history.current().copy(opticalLensState = OpticalLensState()))

    fun setColorGradingEnabled(enabled: Boolean) = history.pushHistory(history.current().copy(colorGradingEnabled = enabled))
    fun setColorGradingLift(r: Float, g: Float, b: Float) { history.markSliderChange(); history.updateState { it.copy(liftRed = r.coerceIn(-0.5f, 0.5f), liftGreen = g.coerceIn(-0.5f, 0.5f), liftBlue = b.coerceIn(-0.5f, 0.5f)) } }
    fun setColorGradingGamma(r: Float, g: Float, b: Float) { history.markSliderChange(); history.updateState { it.copy(gammaRed = r.coerceIn(-0.5f, 0.5f), gammaGreen = g.coerceIn(-0.5f, 0.5f), gammaBlue = b.coerceIn(-0.5f, 0.5f)) } }
    fun setColorGradingGain(r: Float, g: Float, b: Float) { history.markSliderChange(); history.updateState { it.copy(gainRed = r.coerceIn(-0.5f, 0.5f), gainGreen = g.coerceIn(-0.5f, 0.5f), gainBlue = b.coerceIn(-0.5f, 0.5f)) } }
    fun resetColorGrading() = history.pushHistory(history.current().copy(liftRed = 0f, liftGreen = 0f, liftBlue = 0f, gammaRed = 0f, gammaGreen = 0f, gammaBlue = 0f, gainRed = 0f, gainGreen = 0f, gainBlue = 0f))

    fun setColorMixEnabled(enabled: Boolean) = history.pushHistory(history.current().let { it.copy(colorMixState = it.colorMixState.copy(isEnabled = enabled)) })
    fun setColorMixChannel(channel: ColorMixChannel, hue: Float, sat: Float, lum: Float) {
        history.markSliderChange()
        history.updateState { StudioEffectsCollageOps.setColorMixChannel(it, channel, hue, sat, lum) }
    }
    fun setIsolatedColorChannel(channel: ColorMixChannel?) = history.pushHistory(history.current().let { it.copy(colorMixState = it.colorMixState.copy(isEnabled = true, isolatedChannel = channel, monochromeChannel = null)) })
    fun setMonochromeColorChannel(channel: ColorMixChannel?) = history.pushHistory(history.current().let { it.copy(colorMixState = it.colorMixState.copy(isEnabled = true, monochromeChannel = channel, isolatedChannel = null)) })
    fun resetColorMixChannel(channel: ColorMixChannel) = history.pushHistory(StudioEffectsCollageOps.resetColorMixChannel(history.current(), channel))
    fun resetAllColorMix() = history.pushHistory(history.current().copy(colorMixState = ColorMixState()))

    fun setPatternType(type: PatternType) = history.pushHistory(history.current().let { it.copy(patternState = it.patternState.copy(type = type)) })
    fun setPatternOpacity(opacity: Float) { history.markSliderChange(); history.updateState { it.copy(patternState = it.patternState.copy(opacity = opacity.coerceIn(0f, 1f))) } }
    fun setPatternScale(scale: Float) { history.markSliderChange(); history.updateState { it.copy(patternState = it.patternState.copy(scale = scale.coerceIn(0.5f, 3.0f))) } }
    fun setPatternBlendMode(blend: PatternBlend) = history.pushHistory(history.current().let { it.copy(patternState = it.patternState.copy(blendMode = blend)) })
    fun setPatternLightTint(isLight: Boolean) = history.pushHistory(history.current().let { it.copy(patternState = it.patternState.copy(isLightTint = isLight, customColorLong = null)) })
    fun setPatternCustomColor(colorLong: Long?) = history.pushHistory(history.current().let { it.copy(patternState = it.patternState.copy(customColorLong = colorLong)) })
    fun setPatternRotation(deg: Float) { history.markSliderChange(); history.updateState { it.copy(patternState = it.patternState.copy(rotationDegrees = deg.coerceIn(0f, 180f))) } }
    fun resetPattern() = history.pushHistory(history.current().copy(patternState = PatternState()))

    fun selectMask(maskId: String?) = history.pushHistory(StudioEffectsCollageOps.selectMask(history.current(), maskId))
    fun setCustomMaskUri(uri: Uri?) = history.pushHistory(StudioEffectsCollageOps.setCustomMaskUri(history.current(), uri))
    fun setMaskOpacity(opacity: Float) { history.markSliderChange(); history.updateState { it.copy(maskState = it.maskState.copy(opacity = opacity.coerceIn(0f, 1f))) } }
    fun setMaskBlendMode(mode: MaskBlendMode) = history.pushHistory(history.current().let { it.copy(maskState = it.maskState.copy(blendMode = mode)) })
    fun setMaskHueShift(hue: Float) { history.markSliderChange(); history.updateState { it.copy(maskState = it.maskState.copy(hueShift = hue.coerceIn(-180f, 180f))) } }
    fun setMaskOffsetX(x: Float) { history.markSliderChange(); history.updateState { it.copy(maskState = it.maskState.copy(offsetX = x.coerceIn(-1f, 1f))) } }
    fun setMaskOffsetY(y: Float) { history.markSliderChange(); history.updateState { it.copy(maskState = it.maskState.copy(offsetY = y.coerceIn(-1f, 1f))) } }
    fun setMaskScale(scale: Float) { history.markSliderChange(); history.updateState { it.copy(maskState = it.maskState.copy(scale = scale.coerceIn(0.5f, 3.0f))) } }
    fun toggleMaskFlipH() = history.pushHistory(history.current().let { it.copy(maskState = it.maskState.copy(isFlippedH = !it.maskState.isFlippedH)) })
    fun toggleMaskFlipV() = history.pushHistory(history.current().let { it.copy(maskState = it.maskState.copy(isFlippedV = !it.maskState.isFlippedV)) })
    fun rotateMask90() = history.pushHistory(history.current().let { it.copy(maskState = it.maskState.copy(rotationDegrees = (it.maskState.rotationDegrees + 90) % 360)) })
    fun setMaskRotationDegrees(degrees: Int) { history.markSliderChange(); history.updateState { it.copy(maskState = it.maskState.copy(rotationDegrees = ((degrees % 360) + 360) % 360)) } }
    fun setMaskContrast(contrast: Float) { history.markSliderChange(); history.updateState { it.copy(maskState = it.maskState.copy(contrast = contrast.coerceIn(0.5f, 2.5f))) } }
    fun setMaskSoftness(softness: Float) { history.markSliderChange(); history.updateState { it.copy(maskState = it.maskState.copy(softness = softness.coerceIn(0f, 1f))) } }
    fun setMaskWarmth(warmth: Float) { history.markSliderChange(); history.updateState { it.copy(maskState = it.maskState.copy(warmth = warmth.coerceIn(0f, 1f))) } }
    fun toggleMaskInvert() = history.pushHistory(history.current().let { it.copy(maskState = it.maskState.copy(isInverted = !it.maskState.isInverted)) })
    fun setMaskTintColor(colorLong: Long?) = history.pushHistory(history.current().let { it.copy(maskState = it.maskState.copy(tintColorLong = colorLong)) })
    fun toggleMaskLuminanceMode() = history.pushHistory(history.current().let { it.copy(maskState = it.maskState.copy(isLuminanceMode = !it.maskState.isLuminanceMode)) })
    fun setBlackGradientEnabled(enabled: Boolean) = history.pushHistory(history.current().let { it.copy(maskState = it.maskState.copy(blackGradient = it.maskState.blackGradient.copy(isEnabled = enabled))) })
    fun setBlackGradientOpacity(opacity: Float) { history.markSliderChange(); history.updateState { it.copy(maskState = it.maskState.copy(blackGradient = it.maskState.blackGradient.copy(opacity = opacity.coerceIn(0f, 1f)))) } }
    fun setBlackGradientPlacement(placement: BlackGradientPlacement) = history.pushHistory(history.current().let { it.copy(maskState = it.maskState.copy(blackGradient = it.maskState.blackGradient.copy(placement = placement))) })
    fun setBlackGradientCoverage(coverage: Float) { history.markSliderChange(); history.updateState { it.copy(maskState = it.maskState.copy(blackGradient = it.maskState.blackGradient.copy(coverage = coverage.coerceIn(0.15f, 1.0f)))) } }
    fun resetMask() = history.pushHistory(history.current().copy(maskState = AtharMaskState()))

    fun setCinematicGlow(glow: Float) { history.markSliderChange(); history.updateState { it.copy(cinematicGlowState = it.cinematicGlowState.copy(glow = glow.coerceIn(0f, 100f))) } }
    fun setCinematicRange(range: Float) { history.markSliderChange(); history.updateState { it.copy(cinematicGlowState = it.cinematicGlowState.copy(range = range.coerceIn(0f, 100f))) } }
    fun setCinematicSpread(spread: Float) { history.markSliderChange(); history.updateState { it.copy(cinematicGlowState = it.cinematicGlowState.copy(spread = spread.coerceIn(0f, 100f))) } }
    fun setCinematicWarmth(warmth: Float) { history.markSliderChange(); history.updateState { it.copy(cinematicGlowState = it.cinematicGlowState.copy(warmth = warmth.coerceIn(0f, 100f))) } }
    fun setCinematicStyle(style: GlowStyle) = history.pushHistory(history.current().let { it.copy(cinematicGlowState = it.cinematicGlowState.copy(style = style)) })
    fun resetCinematicGlow() = history.pushHistory(history.current().copy(cinematicGlowState = CinematicGlowState()))

    fun setFilmGrain(grain: Float) { history.markSliderChange(); history.updateState { it.copy(filmGrainState = it.filmGrainState.copy(grain = grain.coerceIn(0f, 100f))) } }
    fun setFilmGrainSize(size: Float) { history.markSliderChange(); history.updateState { it.copy(filmGrainState = it.filmGrainState.copy(size = size.coerceIn(0f, 100f))) } }
    fun setFilmGrainRoughness(roughness: Float) { history.markSliderChange(); history.updateState { it.copy(filmGrainState = it.filmGrainState.copy(roughness = roughness.coerceIn(0f, 100f))) } }
    fun resetFilmGrain() = history.pushHistory(history.current().copy(filmGrainState = FilmGrainState()))

    fun setGlitchRgbSplit(split: Float) { history.markSliderChange(); history.updateState { it.copy(glitchEffectState = it.glitchEffectState.copy(rgbSplit = split.coerceIn(0f, 100f))) } }
    fun setGlitchSliceJitter(jitter: Float) { history.markSliderChange(); history.updateState { it.copy(glitchEffectState = it.glitchEffectState.copy(sliceJitter = jitter.coerceIn(0f, 100f))) } }
    fun setGlitchScanlines(scanlines: Float) { history.markSliderChange(); history.updateState { it.copy(glitchEffectState = it.glitchEffectState.copy(scanlines = scanlines.coerceIn(0f, 100f))) } }
    fun setGlitchNoise(noise: Float) { history.markSliderChange(); history.updateState { it.copy(glitchEffectState = it.glitchEffectState.copy(noise = noise.coerceIn(0f, 100f))) } }
    fun setGlitchPixelate(pixelate: Float) { history.markSliderChange(); history.updateState { it.copy(glitchEffectState = it.glitchEffectState.copy(pixelate = pixelate.coerceIn(0f, 100f))) } }
    fun randomizeGlitchSeed() = history.pushHistory(history.current().let { it.copy(glitchEffectState = it.glitchEffectState.copy(seed = System.currentTimeMillis())) })
    fun resetGlitchEffect() = history.pushHistory(history.current().copy(glitchEffectState = GlitchEffectState()))

    fun setCollageLayout(layout: CollageLayoutType) = history.pushHistory(StudioEffectsCollageOps.setCollageLayout(history.current(), layout))
    fun setCollageCellPhoto(cellId: String, uri: Uri?) = history.pushHistory(StudioEffectsCollageOps.updateCollageCell(history.current(), cellId) { StudioEffectsCollageOps.resetCellFraming(it).copy(imageUri = uri) })
    fun selectCollageCell(cellId: String) = history.updateState(markDirty = false) { it.copy(collageState = it.collageState.copy(activeCellId = cellId)) }
    fun setCollageSpacing(spacing: Float) { history.markSliderChange(); history.updateState { it.copy(collageState = it.collageState.copy(spacingDp = spacing.coerceIn(0f, 24f))) } }
    fun setCollageCornerRadius(radius: Float) { history.markSliderChange(); history.updateState { it.copy(collageState = it.collageState.copy(cornerRadiusDp = radius.coerceIn(0f, 32f))) } }
    fun setCollageFrameColor(colorLong: Long) = history.pushHistory(history.current().let { it.copy(collageState = it.collageState.copy(frameColorLong = colorLong)) })
    fun resetCollage() = history.pushHistory(history.current().copy(collageState = CollageState()))
    fun setCollageCellPhotoOffset(cellId: String, offsetX: Float, offsetY: Float) {
        history.markSliderChange()
        history.updateState { StudioEffectsCollageOps.updateCollageCell(it, cellId) { cell -> cell.copy(photoOffsetX = offsetX.coerceIn(-1f, 1f), photoOffsetY = offsetY.coerceIn(-1f, 1f)) } }
    }
    fun setCollageCellPhotoScale(cellId: String, scale: Float) {
        history.markSliderChange()
        history.updateState { StudioEffectsCollageOps.updateCollageCell(it, cellId) { cell -> cell.copy(photoScale = scale.coerceIn(CollageState.MIN_CELL_SCALE, CollageState.MAX_CELL_SCALE)) } }
    }
    fun commitCollageCellFraming(cellId: String, offsetX: Float, offsetY: Float, scale: Float) =
        history.pushHistory(StudioEffectsCollageOps.updateCollageCell(history.current(), cellId) {
            it.copy(photoOffsetX = offsetX.coerceIn(-1f, 1f), photoOffsetY = offsetY.coerceIn(-1f, 1f), photoScale = scale.coerceIn(CollageState.MIN_CELL_SCALE, CollageState.MAX_CELL_SCALE))
        })
    fun resetCollageCellFraming(cellId: String) = history.pushHistory(StudioEffectsCollageOps.updateCollageCell(history.current(), cellId) { StudioEffectsCollageOps.resetCellFraming(it) })
    fun rotateCollageCell90(cellId: String) = history.pushHistory(StudioEffectsCollageOps.updateCollageCell(history.current(), cellId) { it.copy(photoRotationDeg = (it.photoRotationDeg + 90) % 360) })
    fun toggleCollageCellFlipH(cellId: String) = history.pushHistory(StudioEffectsCollageOps.updateCollageCell(history.current(), cellId) { it.copy(isFlippedH = !it.isFlippedH) })
    fun toggleCollageCellFlipV(cellId: String) = history.pushHistory(StudioEffectsCollageOps.updateCollageCell(history.current(), cellId) { it.copy(isFlippedV = !it.isFlippedV) })
    fun swapCollageCellWithNext(cellId: String) {
        val cells = history.current().collageState.cells
        if (cells.size < 2) return
        val idx = cells.indexOfFirst { it.id == cellId }
        if (idx < 0) return
        val nextId = cells[(idx + 1) % cells.size].id
        history.pushHistory(StudioEffectsCollageOps.swapCollageCells(history.current(), cellId, nextId))
    }
    fun fillCollageWithPhotos(uris: List<Uri>) = history.pushHistory(StudioEffectsCollageOps.fillCollageCellsWithUris(history.current(), uris))
    fun removeCollageCellPhoto(cellId: String) = history.pushHistory(StudioEffectsCollageOps.updateCollageCell(history.current(), cellId) { StudioEffectsCollageOps.resetCellFraming(it, clearPhoto = true) })

    fun resetPhotoAdjustments() = history.pushHistory(
        history.current().copy(
            backgroundBlur = 0f,
            flutedGlassState = FlutedGlassState(),
            backgroundBrightness = 0f,
            backgroundContrast = 1.0f,
            backgroundSaturation = 1.0f,
            backgroundVignette = 0f,
            backgroundHighlights = 0f,
            backgroundShadows = 0f,
            backgroundTemperature = 0f,
            backgroundDim = 0.25f
        )
    )

    // ==========================================
    // Project Lifecycle, Fonts, Palette & Export
    // ==========================================

    fun loadExtractedPalette(context: Context) {
        val uri = history.current().backgroundImageUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
                val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return@launch
                val palette = AtharPaletteExtractor.extractPalette(bmp)
                bmp.recycle()
                withContext(Dispatchers.Main) { _extractedPalette.value = palette }
            }
        }
    }

    fun createNewProject(aspectRatio: CanvasAspectRatio = CanvasAspectRatio.STORY_9_16, initialText: String = "") {
        currentProjectId = null
        val newLayer = TextLayer(id = "layer_primary", name = "نص 1", rawText = initialText).withKashida()
        history.resetSession(CanvasState(layers = listOf(newLayer), activeLayerId = "layer_primary", aspectRatio = aspectRatio))
    }

    fun createNewProjectWithImage(context: Context, imageUri: Uri, aspectRatio: CanvasAspectRatio = CanvasAspectRatio.STORY_9_16) {
        currentProjectId = null
        val newLayer = TextLayer(id = "layer_primary", name = "نص 1", rawText = "").withKashida()
        history.resetSession(CanvasState(layers = listOf(newLayer), activeLayerId = "layer_primary", aspectRatio = aspectRatio, backgroundImageUri = imageUri))
        loadExtractedPalette(context)
    }

    fun createNewCollageProject(
        aspectRatio: CanvasAspectRatio = CanvasAspectRatio.STORY_9_16,
        layout: CollageLayoutType = CollageLayoutType.QUAD_GRID
    ) {
        currentProjectId = null
        val cells = app.xrtm8.athar.core.CollageLayoutCalculator.generateCellsForLayout(layout)
        val newLayer = TextLayer(id = "layer_primary", name = "نص 1", rawText = "").withKashida()
        history.resetSession(
            CanvasState(
                layers = listOf(newLayer),
                activeLayerId = "layer_primary",
                aspectRatio = aspectRatio,
                collageState = CollageState(
                    layout = layout,
                    cells = cells,
                    activeCellId = cells.firstOrNull()?.id
                )
            )
        )
    }

    fun clearExportMessage() { _exportMessage.value = null }

    fun loadProjectsList(context: Context) {
        viewModelScope.launch { _projectsList.value = AtharProjectManager.listProjects(context) }
    }

    fun saveCurrentProject(context: Context, title: String? = null, onSaved: ((AtharProjectMeta) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val meta = AtharProjectManager.saveProject(context, history.current(), title, existingProjectId = currentProjectId)
                currentProjectId = meta.id
                history.markSaved()
                loadProjectsList(context)
                onSaved?.invoke(meta)
                _exportMessage.value = "تم حفظ المشروع في مسوداتي بنجاح"
            } catch (e: Exception) {
                _exportMessage.value = "فشل حفظ المشروع: ${e.message}"
            }
        }
    }

    fun exportProjectAsAthar(context: Context, title: String? = null) {
        viewModelScope.launch {
            try {
                val meta = AtharProjectManager.saveProject(context, history.current(), title, existingProjectId = currentProjectId)
                currentProjectId = meta.id
                history.markSaved()
                val file = AtharProjectManager.getExportableProjectFile(context, meta.id, meta.title)
                _uiEvents.emit(StudioUiEvent.ShareFile(file, "application/octet-stream", "مشاركة مشروع أَثَــر (.athar)"))
                _exportMessage.value = "تم تجهيز ملف المشروع (.athar) للمشاركة"
            } catch (e: Exception) {
                _exportMessage.value = "فشل تصدير المشروع: ${e.message}"
            }
        }
    }

    fun openProjectById(context: Context, projectId: String) {
        viewModelScope.launch {
            try {
                val state = AtharProjectManager.loadProjectById(context, projectId)
                currentProjectId = projectId
                history.resetSession(state)
                _exportMessage.value = "تم فتح المشروع بنجاح"
            } catch (e: Exception) {
                _exportMessage.value = "فشل فتح المشروع: ${e.message}"
            }
        }
    }

    fun shareProjectById(context: Context, projectId: String) {
        viewModelScope.launch {
            try {
                val proj = _projectsList.value.find { it.id == projectId }
                val file = AtharProjectManager.getExportableProjectFile(context, projectId, proj?.title ?: "مشروع_أثر")
                _uiEvents.emit(StudioUiEvent.ShareFile(file, "application/octet-stream", "مشاركة مشروع أَثَــر (.athar)"))
                _exportMessage.value = "تم تجهيز ملف المشروع للمشاركة"
            } catch (e: Exception) {
                _exportMessage.value = "فشل مشاركة المشروع: ${e.message}"
            }
        }
    }

    fun openInitialProject(context: Context, uri: Uri) {
        if (initialProjectOpened) return
        initialProjectOpened = true
        openProjectFromUri(context, uri)
    }

    fun openProjectFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val stream = context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("تعذر قراءة الملف")
                val state = stream.use { AtharProjectManager.loadProjectFromStream(context, it) }
                currentProjectId = null
                history.resetSession(state)
                _exportMessage.value = "تم استيراد المشروع بنجاح"
            } catch (e: Exception) {
                _exportMessage.value = "فشل استيراد المشروع: ${e.message}"
            }
        }
    }

    fun deleteProject(context: Context, projectId: String) {
        viewModelScope.launch {
            if (currentProjectId == projectId) currentProjectId = null
            AtharProjectManager.deleteProject(context, projectId)
            loadProjectsList(context)
        }
    }

    fun renameProject(context: Context, projectId: String, newTitle: String) {
        viewModelScope.launch {
            val success = AtharProjectManager.renameProject(context, projectId, newTitle)
            if (success) {
                loadProjectsList(context)
                _exportMessage.value = "تمت إعادة تسمية المشروع بنجاح"
            } else {
                _exportMessage.value = "تعذرت إعادة تسمية المشروع"
            }
        }
    }

    fun loadCustomFonts(context: Context) {
        viewModelScope.launch(Dispatchers.IO) { _customFonts.value = AtharFontManager.listCustomFonts(context) }
    }

    fun importCustomFont(context: Context, uri: Uri) {
        val layer = history.current().activeLayer
        viewModelScope.launch {
            val font = withContext(Dispatchers.IO) { AtharFontManager.importFontFromUri(context, uri) }
            if (font != null) {
                _customFonts.value = withContext(Dispatchers.IO) { AtharFontManager.listCustomFonts(context) }
                if (history.current().activeLayer === layer) setCustomFont(font)
                _exportMessage.value = "تم استيراد الخط: ${font.name}"
            } else {
                _exportMessage.value = "تعذر استيراد الخط، تأكد من صحة الملف (.ttf / .otf)"
            }
        }
    }

    fun deleteCustomFont(context: Context, fontId: String) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) { AtharFontManager.deleteCustomFont(context, fontId) }
            if (success) {
                _customFonts.value = withContext(Dispatchers.IO) { AtharFontManager.listCustomFonts(context) }
                history.updateState { state ->
                    state.copy(layers = state.layers.map { layer ->
                        if (layer.customFontPath?.substringAfterLast('/') == fontId) {
                            layer.copy(customFontPath = null, customFontName = null)
                        } else layer
                    })
                }
                _exportMessage.value = "تم حذف الخط المخصص"
            }
        }
    }

    fun setCustomFont(customFont: CustomFont?) {
        val weight = (customFont?.weightClass ?: 400).coerceIn(100, 900)
        history.pushActiveLayerHistory {
            it.copy(customFontPath = customFont?.filePath, customFontName = customFont?.name, fontWeight = weight, isBold = weight >= 700)
        }
    }

    fun exportDesign(
        context: Context,
        resolution: ExportResolution = ExportResolution.HD_1080P,
        isTransparent: Boolean = false,
        isStickerTrimmed: Boolean = true,
        onShareRequested: Boolean = false
    ) {
        if (_isExporting.value) return
        viewModelScope.launch {
            _isExporting.value = true
            val prefs = app.xrtm8.athar.project.AtharAppSettings.load(context)
            val result = AtharExportManager.exportDesign(
                context = context,
                state = history.current(),
                resolution = resolution,
                isTransparent = isTransparent,
                isStickerTrimmed = isStickerTrimmed,
                onShareRequested = onShareRequested,
                format = prefs.defaultFormat,
                quality = prefs.defaultQuality
            )
            _isExporting.value = false
            _exportMessage.value = if (result.isSuccess) result.getOrNull() else "فشل التصدير: ${result.exceptionOrNull()?.message}"
        }
    }

    fun exportPdf(context: Context, onShareRequested: Boolean = false) {
        if (_isExporting.value) return
        viewModelScope.launch {
            _isExporting.value = true
            val result = AtharExportManager.exportPdf(context, history.current(), onShareRequested)
            _isExporting.value = false
            _exportMessage.value = if (result.isSuccess) result.getOrNull() else "فشل تصدير PDF: ${result.exceptionOrNull()?.message}"
        }
    }

    fun exportSvg(context: Context, onShareRequested: Boolean = false) {
        if (_isExporting.value) return
        viewModelScope.launch {
            _isExporting.value = true
            val result = AtharExportManager.exportSvg(context, history.current(), onShareRequested)
            _isExporting.value = false
            _exportMessage.value = if (result.isSuccess) result.getOrNull() else "فشل تصدير SVG: ${result.exceptionOrNull()?.message}"
        }
    }
}
