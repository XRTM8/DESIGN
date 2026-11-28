package app.xrtm8.athar.export

import android.content.Context
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.os.Build
import app.xrtm8.athar.model.CanvasState
import app.xrtm8.athar.model.LuxuryGradientPreset
import app.xrtm8.athar.model.TextAlignOption
import app.xrtm8.athar.model.TextLayer
import app.xrtm8.athar.model.VisualEffect
import app.xrtm8.athar.typography.AtharFontManager
import app.xrtm8.athar.typography.AtharRichTextEngine
import app.xrtm8.athar.typography.AtharTextLayout
import app.xrtm8.athar.typography.CurvedTextRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Open Vector SVG exporter for Athar Studio.
 * Emits standards-compliant SVG with real CSS font family names, numeric weights,
 * multi-stop gradients, OpenType features, and converts custom/proprietary fonts
 * and curved Arabic paths to vector `<path>` outlines via `Paint.getTextPath()`.
 */
object AtharSvgExporter {

    fun generateSvgString(state: CanvasState): String = generateSvgString(state, context = null)

    fun generateSvgString(state: CanvasState, context: Context?): String {
        val ratio = state.aspectRatio.ratio ?: (9f / 16f)
        val width = 1080
        val height = (width / ratio).toInt()
        val density = width / 380f

        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $width $height" width="$width" height="$height">""")

        // Defs: Filters and Gradients
        sb.appendLine("  <defs>")
        for (layer in state.layers) {
            if (!layer.isVisible) continue
            val safeId = sanitizeId(layer.id)
            if (layer.shadowRadius > 0f) {
                val shadowColor = colorToHex(layer.shadowColorLong)
                val shadowAlpha = ((layer.shadowColorLong ushr 24) and 0xFF) / 255f
                sb.appendLine("""    <filter id="shadow_$safeId" x="-30%" y="-30%" width="160%" height="160%">""")
                sb.appendLine("""      <feDropShadow dx="${layer.shadowDx * density}" dy="${layer.shadowDy * density}" stdDeviation="${(layer.shadowRadius * density) / 2f}" flood-color="$shadowColor" flood-opacity="$shadowAlpha" />""")
                sb.appendLine("""    </filter>""")
            }
            val cg = layer.customGradient
            val activeGrad = resolveActiveGradient(layer)
            if (cg.isEnabled && cg.stops.size >= 2 && !layer.isHollowText) {
                appendGradientStopsDef(
                    sb = sb,
                    safeId = safeId,
                    colors = cg.stops.map { it.colorLong },
                    stops = cg.stops.map { it.position },
                    angleDeg = cg.angle,
                    isRadial = cg.isRadial
                )
            } else if (activeGrad != LuxuryGradientPreset.NONE && !layer.isHollowText) {
                appendGradientDef(sb, safeId, activeGrad, layer.gradientAngle, layer.isRadialGradient)
            }
        }
        sb.appendLine("  </defs>")

        // Canvas Background
        if (!state.isTransparentBg) {
            val bgHex = if (state.solidBgColorLong != null) colorToHex(state.solidBgColorLong) else "#000000"
            sb.appendLine("""  <rect width="100%" height="100%" fill="$bgHex" />""")
        }

        // Text & Shape Layers
        for (layer in state.layers) {
            if (!layer.isVisible) continue
            if (layer.shapeConfig.isShape) {
                val cx = (width * 0.5f) + (layer.normOffsetX * width)
                val cy = (height * 0.5f) + (layer.normOffsetY * height)
                val sc = layer.shapeConfig
                val shapeW = width * sc.widthRatio
                val shapeH = when (sc.type) {
                    app.xrtm8.athar.model.ShapeType.CIRCLE -> shapeW
                    app.xrtm8.athar.model.ShapeType.DIVIDER_H -> (sc.strokeWidth * density).coerceAtLeast(2f)
                    else -> height * sc.heightRatio
                }
                val fillAttr = if (sc.isFilled && sc.type != app.xrtm8.athar.model.ShapeType.FRAME &&
                    sc.type != app.xrtm8.athar.model.ShapeType.DIVIDER_H &&
                    sc.type != app.xrtm8.athar.model.ShapeType.DIVIDER_V
                ) colorToHex(sc.fillColorLong) else "none"
                val strokeAttrShape = if (sc.isStroked || sc.type == app.xrtm8.athar.model.ShapeType.FRAME ||
                    sc.type == app.xrtm8.athar.model.ShapeType.DIVIDER_H ||
                    sc.type == app.xrtm8.athar.model.ShapeType.DIVIDER_V
                ) {
                    val col = if (sc.isStroked || sc.type == app.xrtm8.athar.model.ShapeType.FRAME) sc.strokeColorLong else sc.fillColorLong
                    """stroke="${colorToHex(col)}" stroke-width="${fmt(sc.strokeWidth * density)}""""
                } else ""
                sb.appendLine("""  <g transform="translate(${fmt(cx)}, ${fmt(cy)}) rotate(${fmt(layer.rotationAngle)}) scale(${fmt(layer.scaleFactor)})" opacity="${fmt(layer.textOpacity)}">""")
                when (sc.type) {
                    app.xrtm8.athar.model.ShapeType.CIRCLE ->
                        sb.appendLine("""    <circle cx="0" cy="0" r="${fmt(shapeW / 2f)}" fill="$fillAttr" $strokeAttrShape />""")
                    app.xrtm8.athar.model.ShapeType.DIVIDER_H ->
                        sb.appendLine("""    <line x1="${fmt(-shapeW / 2f)}" y1="0" x2="${fmt(shapeW / 2f)}" y2="0" $strokeAttrShape stroke-linecap="round" />""")
                    app.xrtm8.athar.model.ShapeType.DIVIDER_V ->
                        sb.appendLine("""    <line x1="0" y1="${fmt(-shapeH / 2f)}" x2="0" y2="${fmt(shapeH / 2f)}" $strokeAttrShape stroke-linecap="round" />""")
                    else -> {
                        val rx = fmt(sc.cornerRadius * density)
                        sb.appendLine("""    <rect x="${fmt(-shapeW / 2f)}" y="${fmt(-shapeH / 2f)}" width="${fmt(shapeW)}" height="${fmt(shapeH)}" rx="$rx" ry="$rx" fill="$fillAttr" $strokeAttrShape />""")
                    }
                }
                sb.appendLine("  </g>")
                continue
            }
            val textToRender = if (layer.displayText.isNotEmpty()) layer.displayText else layer.rawText
            if (textToRender.isEmpty()) continue

            val safeId = sanitizeId(layer.id)
            val cx = (width * 0.5f) + (layer.normOffsetX * width)
            val cy = (height * 0.5f) + (layer.normOffsetY * height)
            val fontSize = layer.fontSize * density
            val effectiveWeight = if (layer.isBold && layer.fontWeight < 700) 700 else layer.fontWeight
            val cg = layer.customGradient
            val activeGrad = resolveActiveGradient(layer)
            val fillValue = when {
                layer.isHollowText -> "none"
                (cg.isEnabled && cg.stops.size >= 2) || activeGrad != LuxuryGradientPreset.NONE -> "url(#grad_$safeId)"
                layer.customTextColorLong != null -> colorToHex(layer.customTextColorLong)
                else -> "#FFFFFF"
            }
            val effectiveStroke = if (layer.isHollowText && layer.strokeWidth <= 0f) 2.0f else layer.strokeWidth
            val strokeAttr = if (effectiveStroke > 0f) {
                val strokeHex = colorToHex(layer.strokeColorLong)
                """stroke="$strokeHex" stroke-width="${effectiveStroke * density}" stroke-linejoin="round" stroke-linecap="round" paint-order="stroke fill""""
            } else ""
            val filterAttr = if (layer.shadowRadius > 0f) """filter="url(#shadow_$safeId)"""" else ""

            sb.appendLine("""  <g transform="translate($cx, $cy) rotate(${layer.rotationAngle}) scale(${layer.scaleFactor})" $filterAttr>""")

            if (context != null && layer.textSpans.any { it.hasHighlight }) {
                val rPaint = AtharRichTextEngine.buildTextPaintFromLayer(context, layer, width.toFloat(), buildFontFeatureString(layer))
                val (rLayout, rMapped) = AtharRichTextEngine.buildRichStaticLayout(context, layer, rPaint, width.toFloat(), includeColorSpans = false)
                val minL = (0 until rLayout.lineCount).minOfOrNull { rLayout.getLineLeft(it) } ?: 0f
                val maxR = (0 until rLayout.lineCount).maxOfOrNull { rLayout.getLineRight(it) } ?: rLayout.width.toFloat()
                val ox = -(minL + maxR) / 2f
                val oy = -rLayout.height / 2f
                val highlights = AtharRichTextEngine.computeHighlightRects(rLayout, layer.displayText, rMapped, width.toFloat())
                highlights.forEach { hl ->
                    val hex = String.format("#%06X", 0xFFFFFF and hl.color)
                    val alpha = ((hl.color ushr 24) and 0xFF) / 255f
                    val rx = ox + hl.rect.left
                    val ry = oy + hl.rect.top
                    val rw = hl.rect.width()
                    val rh = hl.rect.height()
                    val border = if (hl.borderColor != null && hl.borderWidthPx > 0.5f) {
                        """ stroke="${String.format("#%06X", 0xFFFFFF and hl.borderColor)}" stroke-width="${hl.borderWidthPx}""""
                    } else ""
                    sb.appendLine("""    <rect x="$rx" y="$ry" width="$rw" height="$rh" rx="${hl.cornerRadiusPx}" ry="${hl.cornerRadiusPx}" fill="$hex" fill-opacity="$alpha"$border />""")
                }
            }

            val shouldOutline = context != null && (!layer.customFontPath.isNullOrBlank() || layer.isCurved)
            var outlined = false
            if (shouldOutline && context != null) {
                outlined = tryAppendVectorOutlines(
                    sb = sb,
                    context = context,
                    layer = layer,
                    textToRender = textToRender,
                    canvasWidth = width.toFloat(),
                    textSizePx = fontSize,
                    effectiveWeight = effectiveWeight,
                    fillValue = fillValue,
                    strokeAttr = strokeAttr
                )
            }

            if (!outlined) {
                appendTextElement(
                    sb = sb,
                    layer = layer,
                    textToRender = textToRender,
                    fontSize = fontSize,
                    effectiveWeight = effectiveWeight,
                    fillValue = fillValue,
                    strokeAttr = strokeAttr,
                    density = density
                )
            }

            sb.appendLine("""  </g>""")
        }

        sb.appendLine("</svg>")
        return sb.toString()
    }

    private fun tryAppendVectorOutlines(
        sb: StringBuilder,
        context: Context,
        layer: TextLayer,
        textToRender: String,
        canvasWidth: Float,
        textSizePx: Float,
        effectiveWeight: Int,
        fillValue: String,
        strokeAttr: String
    ): Boolean {
        val tf = AtharFontManager.getTypeface(context, layer.selectedFont, layer.customFontPath, effectiveWeight)
        val useFakeBold = layer.isBold && !AtharFontManager.hasRealWeight(context, layer.selectedFont, effectiveWeight, layer.customFontPath)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = textSizePx
            letterSpacing = layer.letterSpacing
            isUnderlineText = layer.isUnderline
            isFakeBoldText = useFakeBold
            textSkewX = if (layer.isItalic) -0.25f else 0f
            textAlign = when (layer.textAlign) {
                TextAlignOption.RIGHT -> Paint.Align.RIGHT
                TextAlignOption.CENTER -> Paint.Align.CENTER
                TextAlignOption.LEFT -> Paint.Align.LEFT
            }
            fontFeatureSettings = buildFontFeatureString(layer)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && layer.wordSpacing != 0f) {
                wordSpacing = (layer.wordSpacing / 380f) * canvasWidth
            }
        }
        val layout = AtharTextLayout.computeLayout(
            text = textToRender,
            paint = paint,
            lineSpacingMultiplier = layer.lineSpacingMultiplier,
            textAlign = layer.textAlign,
            maxWidth = canvasWidth * layer.boxWidthRatio.coerceIn(0.2f, 1.0f)
        )

        val combinedPath = Path()
        var hasData = false
        layout.lines.forEachIndexed { index, line ->
            if (line.isEmpty()) return@forEachIndexed
            val lineY = layout.startY + (index * layout.lineHeight)
            val linePath = if (layer.isCurved && abs(layer.curveAngle) >= 1f) {
                CurvedTextRenderer.buildCurvedOutlinePath(line, layout.lineX, lineY, paint, layer.curveAngle)
            } else {
                Path().also { p -> paint.getTextPath(line, 0, line.length, layout.lineX, lineY, p) }
            }
            if (linePath != null && !linePath.isEmpty) {
                combinedPath.fillType = linePath.fillType
                combinedPath.addPath(linePath)
                hasData = true
            }
        }
        if (!hasData) return false

        val d = pathToSvgData(combinedPath)
        if (d.isEmpty()) return false
        val fillRule = if (combinedPath.fillType == Path.FillType.EVEN_ODD) "evenodd" else "nonzero"
        sb.appendLine("""    <path d="$d" fill="$fillValue" fill-rule="$fillRule" opacity="${layer.textOpacity}" $strokeAttr aria-label="${escapeXml(textToRender)}" />""")
        return true
    }

    private fun appendTextElement(
        sb: StringBuilder,
        layer: TextLayer,
        textToRender: String,
        fontSize: Float,
        effectiveWeight: Int,
        fillValue: String,
        strokeAttr: String,
        density: Float
    ) {
        val textAnchor = when (layer.textAlign) {
            TextAlignOption.LEFT -> "start"
            TextAlignOption.RIGHT -> "end"
            TextAlignOption.CENTER -> "middle"
        }
        val cssFamily = resolveSvgFontFamily(layer)
        val features = buildFontFeatureString(layer)
        val extraAttrs = buildString {
            if (!features.isNullOrBlank()) append(""" font-feature-settings="${escapeXml(features)}"""")
            if (layer.wordSpacing != 0f) append(""" word-spacing="${layer.wordSpacing * density}px"""")
            if (layer.isUnderline) append(""" text-decoration="underline"""")
        }

        val lines = textToRender.split("\n")
        if (lines.size <= 1) {
            val escaped = escapeXml(textToRender)
            sb.appendLine("""    <text x="0" y="0" font-family="${escapeXml(cssFamily)}, sans-serif" font-size="$fontSize" font-weight="$effectiveWeight" font-style="${if (layer.isItalic) "italic" else "normal"}" fill="$fillValue" text-anchor="$textAnchor" direction="rtl" letter-spacing="${layer.letterSpacing}em" opacity="${layer.textOpacity}"$extraAttrs $strokeAttr>$escaped</text>""")
        } else {
            val lineHeight = fontSize * layer.lineSpacingMultiplier
            val startY = -((lines.size - 1) * lineHeight) / 2f + (fontSize * 0.35f)
            sb.appendLine("""    <text x="0" y="$startY" font-family="${escapeXml(cssFamily)}, sans-serif" font-size="$fontSize" font-weight="$effectiveWeight" font-style="${if (layer.isItalic) "italic" else "normal"}" fill="$fillValue" text-anchor="$textAnchor" direction="rtl" letter-spacing="${layer.letterSpacing}em" opacity="${layer.textOpacity}"$extraAttrs $strokeAttr>""")
            lines.forEachIndexed { idx, line ->
                val dy = if (idx == 0) 0f else lineHeight
                sb.appendLine("""      <tspan x="0" dy="$dy">${escapeXml(line)}</tspan>""")
            }
            sb.appendLine("""    </text>""")
        }
    }

    internal fun resolveSvgFontFamily(layer: TextLayer): String {
        val custom = layer.customFontName?.trim()
        if (!custom.isNullOrEmpty()) return custom
        return layer.selectedFont.cssFamilyName
    }

    private fun resolveActiveGradient(layer: TextLayer): LuxuryGradientPreset {
        if (layer.gradientPreset != LuxuryGradientPreset.NONE) return layer.gradientPreset
        if (layer.customTextColorLong != null) return LuxuryGradientPreset.NONE
        return when (layer.selectedEffect) {
            VisualEffect.LIQUID_CHROME -> LuxuryGradientPreset.TITANIUM_CHROME
            VisualEffect.CYBER_NEON -> LuxuryGradientPreset.CYBER_EMERALD
            VisualEffect.FROSTED_GLASS -> LuxuryGradientPreset.FROSTED_PEARL
            VisualEffect.MINIMAL_LUXURY, VisualEffect.CINEMATIC_SHADOW -> LuxuryGradientPreset.NONE
            else -> LuxuryGradientPreset.ANDALUSIAN_GOLD
        }
    }

    private fun appendGradientDef(
        sb: StringBuilder,
        safeId: String,
        preset: LuxuryGradientPreset,
        angleDeg: Float,
        isRadial: Boolean
    ) {
        if (preset.colors.isEmpty()) return
        appendGradientStopsDef(
            sb = sb,
            safeId = safeId,
            colors = preset.colors,
            stops = preset.colors.indices.map { idx ->
                preset.stops.getOrElse(idx) { idx.toFloat() / (preset.colors.size - 1).coerceAtLeast(1) }
            },
            angleDeg = angleDeg,
            isRadial = isRadial
        )
    }

    private fun appendGradientStopsDef(
        sb: StringBuilder,
        safeId: String,
        colors: List<Long>,
        stops: List<Float>,
        angleDeg: Float,
        isRadial: Boolean
    ) {
        if (colors.isEmpty()) return
        if (isRadial) {
            sb.appendLine("""    <radialGradient id="grad_$safeId" cx="50%" cy="50%" r="55%">""")
            colors.forEachIndexed { idx, col ->
                val offset = ((stops.getOrElse(idx) { idx.toFloat() / (colors.size - 1).coerceAtLeast(1) }) * 100f).toInt()
                val alpha = ((col ushr 24) and 0xFF) / 255f
                sb.appendLine("""      <stop offset="$offset%" stop-color="${colorToHex(col)}" stop-opacity="$alpha" />""")
            }
            sb.appendLine("    </radialGradient>")
        } else {
            val rad = Math.toRadians(angleDeg.toDouble())
            val dx = cos(rad) * 0.5
            val dy = sin(rad) * 0.5
            val x1 = ((0.5 - dx) * 100.0).toInt()
            val y1 = ((0.5 - dy) * 100.0).toInt()
            val x2 = ((0.5 + dx) * 100.0).toInt()
            val y2 = ((0.5 + dy) * 100.0).toInt()
            sb.appendLine("""    <linearGradient id="grad_$safeId" x1="$x1%" y1="$y1%" x2="$x2%" y2="$y2%">""")
            colors.forEachIndexed { idx, col ->
                val offset = ((stops.getOrElse(idx) { idx.toFloat() / (colors.size - 1).coerceAtLeast(1) }) * 100f).toInt()
                val alpha = ((col ushr 24) and 0xFF) / 255f
                sb.appendLine("""      <stop offset="$offset%" stop-color="${colorToHex(col)}" stop-opacity="$alpha" />""")
            }
            sb.appendLine("    </linearGradient>")
        }
    }

    private fun buildFontFeatureString(layer: TextLayer): String? = buildString {
        if (layer.stylisticSet in 1..20) {
            append(String.format(Locale.US, "\"ss%02d\" 1, ", layer.stylisticSet))
        }
        if (layer.enableSwash) append("\"swsh\" 1, ")
        if (layer.enableDiscretionaryLigatures) append("\"dlig\" 1, ")
    }.trimEnd(',', ' ').ifEmpty { null }

    /**
     * Flattens an Android [Path] into an SVG `d` path string using adaptive chord-error
     * subdivision over [PathMeasure], keeping straight stems/kashidas minimal and curves crisp.
     */
    private fun pathToSvgData(path: Path, maxChordErrorPx: Float = 0.35f): String {
        val pm = PathMeasure(path, false)
        val out = StringBuilder()
        val pA = FloatArray(2)
        val pB = FloatArray(2)
        val pM = FloatArray(2)

        fun emitAdaptive(d0: Float, x0: Float, y0: Float, d1: Float, x1: Float, y1: Float, depth: Int) {
            if (depth < 6 && (d1 - d0) > 0.6f) {
                val dm = (d0 + d1) * 0.5f
                pm.getPosTan(dm, pM, null)
                val mx = pM[0]
                val my = pM[1]
                val dx = x1 - x0
                val dy = y1 - y0
                val segLen = hypot(dx, dy)
                val deviation = if (segLen > 1e-3f) {
                    abs(dy * mx - dx * my + x1 * y0 - y1 * x0) / segLen
                } else {
                    hypot(mx - x0, my - y0)
                }
                if (deviation > maxChordErrorPx) {
                    emitAdaptive(d0, x0, y0, dm, mx, my, depth + 1)
                    emitAdaptive(dm, mx, my, d1, x1, y1, depth + 1)
                    return
                }
            }
            out.append("L").append(fmt(x1)).append(",").append(fmt(y1))
        }

        do {
            val len = pm.length
            if (len <= 0.01f) continue
            val seedSteps = 8
            pm.getPosTan(0f, pA, null)
            out.append("M").append(fmt(pA[0])).append(",").append(fmt(pA[1]))
            var prevD = 0f
            var prevX = pA[0]
            var prevY = pA[1]
            for (i in 1..seedSteps) {
                val curD = (len * i) / seedSteps
                pm.getPosTan(curD, pB, null)
                emitAdaptive(prevD, prevX, prevY, curD, pB[0], pB[1], depth = 0)
                prevD = curD
                prevX = pB[0]
                prevY = pB[1]
            }
            out.append("Z")
        } while (pm.nextContour())

        return out.toString()
    }

    private fun fmt(v: Float): String = String.format(Locale.US, "%.2f", v)

    private fun sanitizeId(id: String): String = id.replace(Regex("[^a-zA-Z0-9_-]"), "_")

    suspend fun exportSvg(
        context: Context,
        state: CanvasState,
        onShareRequested: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val svgContent = generateSvgString(state, context)
            val timestamp = System.currentTimeMillis()
            val filename = "Athar_Vector_${timestamp}.svg"

            val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val shareFile = File(cacheDir, filename)
            FileOutputStream(shareFile).use { out ->
                out.write(svgContent.toByteArray(Charsets.UTF_8))
            }

            AtharDocumentFileHelper.saveDocumentToDownloads(context, shareFile, filename, "image/svg+xml")

            if (onShareRequested) {
                AtharDocumentFileHelper.shareDocumentFile(
                    context = context,
                    shareFile = shareFile,
                    filename = filename,
                    mimeType = "image/svg+xml",
                    chooserTitle = "مشاركة ملف SVG من أثر"
                )
            }

            Result.success("تم تصدير النصوص متجهياً بنجاح (SVG)")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun colorToHex(colorLong: Long): String {
        val r = ((colorLong ushr 16) and 0xFF).toString(16).padStart(2, '0')
        val g = ((colorLong ushr 8) and 0xFF).toString(16).padStart(2, '0')
        val b = (colorLong and 0xFF).toString(16).padStart(2, '0')
        return "#$r$g$b"
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
