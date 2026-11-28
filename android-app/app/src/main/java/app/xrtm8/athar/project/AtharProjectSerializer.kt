package app.xrtm8.athar.project

import android.net.Uri
import app.xrtm8.athar.model.*
import app.xrtm8.athar.project.AtharLayerJsonCodec.enumOrDefault
import org.json.JSONObject

/**
 * JSON serializer & deserializer for .athar design documents.
 * Delegates layer and effect codecs to dedicated modules while keeping full schema compatibility.
 */
object AtharProjectSerializer {

    fun serializeCanvasState(state: CanvasState, hasBackgroundPhoto: Boolean): JSONObject {
        return JSONObject().apply {
            AtharLayerJsonCodec.writeLayers(this, state.layers, state.activeLayer, state.activeLayerId)

            put("show_grid_guides", state.showGridGuides)
            put("aspect_ratio", state.aspectRatio.name)
            put("solid_bg_color", state.solidBgColorLong ?: 0L)
            put("has_background_photo", hasBackgroundPhoto)
            put("background_dim", state.backgroundDim.toDouble())
            put("background_blur", state.backgroundBlur.toDouble())
            put("background_brightness", state.backgroundBrightness.toDouble())
            put("background_contrast", state.backgroundContrast.toDouble())
            put("background_saturation", state.backgroundSaturation.toDouble())
            put("background_vignette", state.backgroundVignette.toDouble())
            put("background_highlights", state.backgroundHighlights.toDouble())
            put("background_shadows", state.backgroundShadows.toDouble())
            put("background_temperature", state.backgroundTemperature.toDouble())
            put("background_grayscale", state.backgroundGrayscale)
            put("is_transparent_bg", state.isTransparentBg)
            put("bg_photo_offset_x", state.bgPhotoOffsetX.toDouble())
            put("bg_photo_offset_y", state.bgPhotoOffsetY.toDouble())
            put("bg_photo_scale", state.bgPhotoScale.toDouble())

            AtharEffectsJsonCodec.writeEffects(this, state)
        }
    }

    fun deserializeCanvasState(c: JSONObject, backgroundUri: Uri? = null): CanvasState {
        val (layers, activeLayerId) = AtharLayerJsonCodec.readLayers(c)
        val solidBg = c.optLong("solid_bg_color", 0L)

        return CanvasState(
            layers = layers,
            activeLayerId = activeLayerId,
            showGridGuides = c.optBoolean("show_grid_guides", false),
            aspectRatio = enumOrDefault(c.optString("aspect_ratio", CanvasAspectRatio.STORY_9_16.name), CanvasAspectRatio.STORY_9_16),
            solidBgColorLong = if (solidBg != 0L) solidBg else null,
            backgroundImageUri = backgroundUri,
            backgroundDim = c.optDouble("background_dim", 0.25).toFloat(),
            backgroundBlur = c.optDouble("background_blur", 0.0).toFloat(),
            backgroundBrightness = c.optDouble("background_brightness", 0.0).toFloat(),
            backgroundContrast = c.optDouble("background_contrast", 1.0).toFloat(),
            backgroundSaturation = c.optDouble("background_saturation", 1.0).toFloat(),
            backgroundVignette = c.optDouble("background_vignette", 0.0).toFloat(),
            backgroundHighlights = c.optDouble("background_highlights", 0.0).toFloat().coerceIn(-1f, 1f),
            backgroundShadows = c.optDouble("background_shadows", 0.0).toFloat().coerceIn(-1f, 1f),
            backgroundTemperature = c.optDouble("background_temperature", 0.0).toFloat().coerceIn(-1f, 1f),
            backgroundGrayscale = c.optBoolean("background_grayscale", false),
            isTransparentBg = c.optBoolean("is_transparent_bg", false),
            filmPreset = enumOrDefault(c.optString("film_preset", FilmPreset.NONE.name), FilmPreset.NONE),
            filmPresetIntensity = c.optDouble("film_preset_intensity", 1.0).toFloat().coerceIn(0f, 1.5f),
            gradientScrim = CanvasGradientScrim(
                isEnabled = c.optBoolean("scrim_enabled", false),
                direction = enumOrDefault(c.optString("scrim_direction", CanvasGradientDirection.BOTTOM_TO_TOP.name), CanvasGradientDirection.BOTTOM_TO_TOP),
                colorLong = c.optLong("scrim_color", 0xFF000000),
                opacity = c.optDouble("scrim_opacity", 0.65).toFloat(),
                midpoint = c.optDouble("scrim_midpoint", 0.5).toFloat()
            ),
            bgPhotoOffsetX = c.optDouble("bg_photo_offset_x", 0.0).toFloat(),
            bgPhotoOffsetY = c.optDouble("bg_photo_offset_y", 0.0).toFloat(),
            bgPhotoScale = c.optDouble("bg_photo_scale", 1.0).toFloat(),
            colorGradingEnabled = c.optBoolean("color_grading_enabled", false),
            liftRed = c.optDouble("lift_red", 0.0).toFloat(),
            liftGreen = c.optDouble("lift_green", 0.0).toFloat(),
            liftBlue = c.optDouble("lift_blue", 0.0).toFloat(),
            gammaRed = c.optDouble("gamma_red", 0.0).toFloat(),
            gammaGreen = c.optDouble("gamma_green", 0.0).toFloat(),
            gammaBlue = c.optDouble("gamma_blue", 0.0).toFloat(),
            gainRed = c.optDouble("gain_red", 0.0).toFloat(),
            gainGreen = c.optDouble("gain_green", 0.0).toFloat(),
            gainBlue = c.optDouble("gain_blue", 0.0).toFloat(),
            colorMixState = AtharEffectsJsonCodec.readColorMix(c),
            patternState = PatternState(
                type = enumOrDefault(c.optString("pattern_type", PatternType.NONE.name), PatternType.NONE),
                opacity = c.optDouble("pattern_opacity", 0.30).toFloat(),
                scale = c.optDouble("pattern_scale", 1.0).toFloat(),
                blendMode = enumOrDefault(c.optString("pattern_blend", PatternBlend.OVERLAY.name), PatternBlend.OVERLAY),
                isLightTint = c.optBoolean("pattern_light_tint", true),
                customColorLong = if (c.has("pattern_custom_color")) c.optLong("pattern_custom_color") else null,
                rotationDegrees = c.optDouble("pattern_rotation", 0.0).toFloat().coerceIn(0f, 180f)
            ),
            cinematicGlowState = CinematicGlowState(
                style = enumOrDefault(c.optString("glow_style", GlowStyle.MOTION.name), GlowStyle.MOTION),
                glow = c.optDouble("glow_intensity", 0.0).toFloat(),
                range = c.optDouble("glow_range", 70.0).toFloat(),
                spread = c.optDouble("glow_spread", 65.0).toFloat(),
                warmth = c.optDouble("glow_warmth", 70.0).toFloat()
            ),
            filmGrainState = FilmGrainState(
                grain = c.optDouble("film_grain", 0.0).toFloat(),
                size = c.optDouble("grain_size", 47.0).toFloat(),
                roughness = c.optDouble("grain_roughness", 50.0).toFloat()
            ),
            collageState = AtharEffectsJsonCodec.readCollage(c),
            maskState = AtharEffectsJsonCodec.readMask(c),
            glitchEffectState = GlitchEffectState(
                rgbSplit = c.optDouble("glitch_rgb_split", 0.0).toFloat(),
                sliceJitter = c.optDouble("glitch_slice_jitter", 0.0).toFloat(),
                scanlines = c.optDouble("glitch_scanlines", 0.0).toFloat(),
                noise = c.optDouble("glitch_noise", 0.0).toFloat(),
                pixelate = c.optDouble("glitch_pixelate", 0.0).toFloat(),
                seed = c.optLong("glitch_seed", 1337L)
            ),
            opticalLensState = OpticalLensState(
                type = enumOrDefault(c.optString("lens_type", OpticalLensType.NONE.name), OpticalLensType.NONE),
                intensity = c.optDouble("lens_intensity", 0.65).toFloat().coerceIn(0f, 1f),
                spread = c.optDouble("lens_spread", 0.50).toFloat().coerceIn(0.1f, 1f),
                angleDegrees = c.optDouble("lens_angle", 45.0).toFloat()
            ),
            flutedGlassState = FlutedGlassState(
                intensity = c.optDouble("fluted_glass_intensity", 0.0).toFloat().coerceIn(0f, 1f),
                ribCount = c.optInt("fluted_glass_ribs", 28).coerceIn(6, 120),
                specular = c.optDouble("fluted_glass_specular", 0.55).toFloat().coerceIn(0f, 1f),
                coverage = c.optDouble("fluted_glass_coverage", 1.0).toFloat().coerceIn(0.1f, 1.0f),
                coverageFromStart = c.optBoolean("fluted_glass_from_start", true),
                angleDegrees = c.optDouble("fluted_glass_angle", 0.0).toFloat().coerceIn(-45f, 90f),
                chromaticDispersion = c.optDouble("fluted_glass_dispersion", 0.0).toFloat().coerceIn(0f, 1f),
                edgeSoftness = c.optDouble("fluted_glass_softness", 0.0).toFloat().coerceIn(0f, 1f)
            ),
            toneCurveState = ToneCurveState(
                blacks = c.optDouble("tc_blacks", 0.0).toFloat().coerceIn(-0.5f, 0.5f),
                shadows = c.optDouble("tc_shadows", 0.0).toFloat().coerceIn(-0.5f, 0.5f),
                highlights = c.optDouble("tc_highlights", 0.0).toFloat().coerceIn(-0.5f, 0.5f),
                whites = c.optDouble("tc_whites", 0.0).toFloat().coerceIn(-0.5f, 0.5f),
                splitShadowHue = c.optDouble("tc_sh_hue", 220.0).toFloat().coerceIn(0f, 360f),
                splitShadowSat = c.optDouble("tc_sh_sat", 0.0).toFloat().coerceIn(0f, 0.6f),
                splitHighlightHue = c.optDouble("tc_hi_hue", 40.0).toFloat().coerceIn(0f, 360f),
                splitHighlightSat = c.optDouble("tc_hi_sat", 0.0).toFloat().coerceIn(0f, 0.6f)
            )
        )
    }
}
