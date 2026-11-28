package app.xrtm8.athar.ui.viewmodel

import android.content.Context
import android.graphics.Paint
import app.xrtm8.athar.core.PureArabicKashidaEngine
import app.xrtm8.athar.model.*
import app.xrtm8.athar.typography.ArabicParagraphJustifier
import app.xrtm8.athar.typography.AtharFontManager
import java.util.UUID

/**
 * Pure state transformations for multi-layer composition, typography, kashida,
 * alignment, style copy/paste, and viewport aspect-ratio refitting.
 */
internal object StudioLayerTypographyOps {

    fun TextLayer.withKashida(context: Context? = null): TextLayer {
        val stretched = PureArabicKashidaEngine.stretchText(rawText, stretchLevel, kashidaMode, kashidaTargetIndex)
        val finalDisplay = if (stretched.contains('\n')) {
            val paint = context?.let { buildTextMeasurePaint(it, this) }
            when (paragraphBalanceMode) {
                ParagraphBalanceMode.JUSTIFY -> ArabicParagraphJustifier.justifyLines(stretched, paint)
                ParagraphBalanceMode.BALANCE -> ArabicParagraphJustifier.balanceArabicBlock(stretched, paint)
                ParagraphBalanceMode.NONE -> stretched
            }
        } else {
            stretched
        }
        return copy(displayText = finalDisplay)
    }

    fun addLayer(state: CanvasState, initialText: String): CanvasState {
        val newLayer = TextLayer(
            id = UUID.randomUUID().toString(),
            name = "نص ${state.layers.size + 1}",
            rawText = initialText,
            fontSize = 44f,
            normOffsetX = 0f,
            normOffsetY = (state.layers.size * 0.08f).coerceAtMost(0.35f)
        ).withKashida()
        return state.copy(layers = state.layers + newLayer, activeLayerId = newLayer.id)
    }

    fun duplicateActiveLayer(state: CanvasState): CanvasState {
        val active = state.activeLayer
        val dup = active.copy(
            id = UUID.randomUUID().toString(),
            name = "${active.name} (نسخة)",
            normOffsetX = (active.normOffsetX + 0.04f).coerceAtMost(0.4f),
            normOffsetY = (active.normOffsetY + 0.04f).coerceAtMost(0.4f)
        )
        return state.copy(layers = state.layers + dup, activeLayerId = dup.id)
    }

    fun removeActiveLayer(state: CanvasState): CanvasState {
        if (state.layers.size <= 1) {
            val activeId = state.activeLayer.id
            val cleared = state.layers.map {
                if (it.id == activeId) it.copy(rawText = "", displayText = "", textSpans = emptyList(), shapeConfig = ShapeLayerConfig()) else it
            }
            return state.copy(layers = cleared)
        }
        val activeId = state.activeLayer.id
        val remaining = state.layers.filter { it.id != activeId }
        return state.copy(layers = remaining, activeLayerId = remaining.lastOrNull()?.id ?: "")
    }

    fun moveLayer(state: CanvasState, delta: Int): CanvasState? {
        val activeId = state.activeLayer.id
        val index = state.layers.indexOfFirst { it.id == activeId }
        val targetIndex = index + delta
        if (index < 0 || targetIndex !in state.layers.indices) return null
        val mutable = state.layers.toMutableList()
        val item = mutable.removeAt(index)
        mutable.add(targetIndex, item)
        return state.copy(layers = mutable)
    }

    fun applyStyle(target: TextLayer, style: TextLayer): TextLayer = target.copy(
        selectedFont = style.selectedFont,
        fontWeight = style.fontWeight,
        customFontPath = style.customFontPath,
        customFontName = style.customFontName,
        selectedEffect = style.selectedEffect,
        fontSize = style.fontSize,
        customTextColorLong = style.customTextColorLong,
        strokeWidth = style.strokeWidth,
        strokeColorLong = style.strokeColorLong,
        shadowRadius = style.shadowRadius,
        shadowDx = style.shadowDx,
        shadowDy = style.shadowDy,
        shadowColorLong = style.shadowColorLong,
        textAlign = style.textAlign,
        lineSpacingMultiplier = style.lineSpacingMultiplier,
        letterSpacing = style.letterSpacing,
        textOpacity = style.textOpacity,
        isBold = style.isBold,
        isItalic = style.isItalic,
        isUnderline = style.isUnderline,
        enableGlassBackdrop = style.enableGlassBackdrop,
        glassBackdropOpacity = style.glassBackdropOpacity,
        glassBackdropColorLong = style.glassBackdropColorLong,
        glassBackdropBorderColorLong = style.glassBackdropBorderColorLong,
        glassBackdropBorderWidth = style.glassBackdropBorderWidth,
        glassBackdropBorderOpacity = style.glassBackdropBorderOpacity,
        glassBackdropCornerRadius = style.glassBackdropCornerRadius,
        glassBackdropPadding = style.glassBackdropPadding,
        blendMode = style.blendMode,
        gradientPreset = style.gradientPreset,
        gradientAngle = style.gradientAngle,
        isRadialGradient = style.isRadialGradient,
        isHollowText = style.isHollowText,
        stylisticSet = style.stylisticSet,
        enableSwash = style.enableSwash,
        enableDiscretionaryLigatures = style.enableDiscretionaryLigatures,
        wordSpacing = style.wordSpacing,
        boxWidthRatio = style.boxWidthRatio
    )

    fun buildTextMeasurePaint(context: Context, active: TextLayer): Paint {
        val effectiveWeight = if (active.isBold && active.fontWeight < 700) 700 else active.fontWeight
        return Paint().apply {
            isAntiAlias = true
            typeface = AtharFontManager.getTypeface(context, active.selectedFont, active.customFontPath, effectiveWeight)
            isFakeBoldText = active.isBold && !AtharFontManager.hasRealWeight(context, active.selectedFont, effectiveWeight, active.customFontPath)
            textSize = active.fontSize * 3f
            letterSpacing = active.letterSpacing
        }
    }

    fun justifyLines(context: Context, active: TextLayer): String? {
        if (!active.rawText.contains('\n')) return null
        val justified = ArabicParagraphJustifier.justifyLines(active.rawText, buildTextMeasurePaint(context, active))
        return justified.takeIf { it != active.displayText }
    }

    fun balanceBlock(context: Context?, active: TextLayer): String? {
        if (!active.displayText.contains('\n')) return null
        val paint = context?.let { buildTextMeasurePaint(it, active) }
        return ArabicParagraphJustifier.balanceArabicBlock(active.displayText, paint)
    }

    fun refitForAspectRatio(state: CanvasState, ratio: CanvasAspectRatio, autoRefit: Boolean): CanvasState {
        val oldRatio = state.aspectRatio
        if (!autoRefit || oldRatio.ratio == null || ratio.ratio == null) {
            return state.copy(aspectRatio = ratio)
        }
        val heightFactor = (1080f / ratio.ratio) / (1080f / oldRatio.ratio)
        val refittedLayers = state.layers.map { layer ->
            val newNormY = (layer.normOffsetY * heightFactor).coerceIn(-0.42f, 0.42f)
            val newScale = if (heightFactor < 1.0f) {
                (layer.scaleFactor * heightFactor.coerceAtLeast(0.70f)).coerceIn(0.2f, 3.0f)
            } else {
                layer.scaleFactor
            }
            layer.copy(normOffsetY = newNormY, scaleFactor = newScale)
        }
        return state.copy(aspectRatio = ratio, layers = refittedLayers)
    }
}
