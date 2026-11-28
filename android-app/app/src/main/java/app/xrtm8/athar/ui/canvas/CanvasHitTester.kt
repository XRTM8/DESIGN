package app.xrtm8.athar.ui.canvas

import android.content.Context
import android.graphics.Paint
import app.xrtm8.athar.model.CanvasState
import app.xrtm8.athar.model.TextLayer
import app.xrtm8.athar.typography.AtharFontManager
import app.xrtm8.athar.typography.AtharTextLayout
import kotlin.math.abs

object CanvasHitTester {

    /**
     * Precision canvas hit-testing to detect which text layer the user tapped.
     * Accounts for canvas offset, scaling, rotation angle, and finger touch targets.
     */
    fun findHitLayer(
        context: Context,
        touchX: Float,
        touchY: Float,
        canvasW: Float,
        canvasH: Float,
        state: CanvasState,
        density: Float
    ): TextLayer? {
        val pad = 28f * density
        // Search top-to-bottom in visual z-order
        for (layer in state.layers.asReversed()) {
            if (!layer.isVisible) continue
            if (!layer.shapeConfig.isShape && layer.displayText.isEmpty()) continue

            val cx = canvasW / 2f + (layer.normOffsetX * canvasW)
            val cy = canvasH / 2f + (layer.normOffsetY * canvasH)
            val scale = layer.scaleFactor.coerceAtLeast(0.1f)

            val halfW: Float
            val halfH: Float
            if (layer.shapeConfig.isShape) {
                val shapeW = if (layer.shapeConfig.type == app.xrtm8.athar.model.ShapeType.DIVIDER_V) {
                    (layer.shapeConfig.strokeWidth * (canvasW / 380f)).coerceAtLeast(12f)
                } else {
                    canvasW * layer.shapeConfig.widthRatio
                }
                val shapeH = when (layer.shapeConfig.type) {
                    app.xrtm8.athar.model.ShapeType.CIRCLE -> shapeW
                    app.xrtm8.athar.model.ShapeType.DIVIDER_H -> (layer.shapeConfig.strokeWidth * (canvasW / 380f)).coerceAtLeast(12f)
                    app.xrtm8.athar.model.ShapeType.DIVIDER_V -> canvasH * layer.shapeConfig.heightRatio
                    else -> canvasH * layer.shapeConfig.heightRatio
                }
                halfW = (shapeW / 2f) * scale + pad
                halfH = (shapeH / 2f) * scale + pad
            } else {
                val scaledTextSize = (layer.fontSize / 380f) * canvasW
                val effectiveWeight = if (layer.isBold && layer.fontWeight < 700) 700 else layer.fontWeight
                val testPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = AtharFontManager.getTypeface(context, layer.selectedFont, layer.customFontPath, effectiveWeight)
                    isFakeBoldText = layer.isBold && !AtharFontManager.hasRealWeight(context, layer.selectedFont, effectiveWeight, layer.customFontPath)
                    textSkewX = if (layer.isItalic) -0.25f else 0f
                    textSize = scaledTextSize
                    letterSpacing = layer.letterSpacing
                }
                val layout = AtharTextLayout.computeLayout(
                    text = layer.displayText,
                    paint = testPaint,
                    lineSpacingMultiplier = layer.lineSpacingMultiplier,
                    textAlign = layer.textAlign,
                    maxWidth = canvasW * layer.boxWidthRatio.coerceIn(0.2f, 1.0f)
                )

                val totalW = layout.maxLineWidth.coerceAtLeast(scaledTextSize * 1.5f)
                val totalH = (layout.totalHeight + scaledTextSize).coerceAtLeast(scaledTextSize)

                halfW = (totalW / 2f) * scale + pad
                halfH = (totalH / 2f) * scale + pad
            }

            val dx = touchX - cx
            val dy = touchY - cy

            val rad = Math.toRadians(-layer.rotationAngle.toDouble())
            val cosA = Math.cos(rad).toFloat()
            val sinA = Math.sin(rad).toFloat()
            val localX = dx * cosA - dy * sinA
            val localY = dx * sinA + dy * cosA

            if (abs(localX) <= halfW && abs(localY) <= halfH) {
                return layer
            }
        }
        return null
    }

    /**
     * Detects if the user tapped inside any collage cell on the canvas.
     */
    fun findHitCollageCell(
        touchX: Float,
        touchY: Float,
        canvasW: Float,
        canvasH: Float,
        state: CanvasState
    ): String? {
        val collage = state.collageState
        if (!collage.isEnabled || canvasW <= 0f || canvasH <= 0f) return null
        val normX = touchX / canvasW
        val normY = touchY / canvasH
        return collage.cells.firstOrNull { cell ->
            normX >= cell.leftNorm && normX <= cell.rightNorm &&
            normY >= cell.topNorm && normY <= cell.bottomNorm
        }?.id
    }
}
