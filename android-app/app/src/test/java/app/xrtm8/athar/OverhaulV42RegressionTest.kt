package app.xrtm8.athar

import app.xrtm8.athar.core.AtharPhotoAdjustmentsEngine
import app.xrtm8.athar.core.FilmPresetEngine
import app.xrtm8.athar.export.AtharSvgExporter
import app.xrtm8.athar.model.ArabicFontFamily
import app.xrtm8.athar.model.CanvasState
import app.xrtm8.athar.model.CustomGradientState
import app.xrtm8.athar.model.CustomGradientStop
import app.xrtm8.athar.model.FilmPreset
import app.xrtm8.athar.model.TextLayer
import app.xrtm8.athar.project.AtharProjectSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverhaulV42RegressionTest {

    @Test
    fun photoAdjustmentsEngine_adjustsHighlightsShadowsTemperature_andPreservesAlpha() {
        val darkPixel = (0x80 shl 24) or (30 shl 16) or (30 shl 8) or 30
        val brightPixel = (0xCC shl 24) or (220 shl 16) or (220 shl 8) or 220
        val transparentPixel = 0x00123456

        val pixels = intArrayOf(darkPixel, brightPixel, transparentPixel)
        AtharPhotoAdjustmentsEngine.applyToBuffer(
            pixels = pixels,
            highlights = -0.5f,
            shadows = 0.6f,
            temperature = 0.5f
        )

        // Alpha must be preserved exactly
        assertEquals(0x80, (pixels[0] ushr 24) and 0xFF)
        assertEquals(0xCC, (pixels[1] ushr 24) and 0xFF)
        assertEquals(transparentPixel, pixels[2])

        // Shadows lifted on dark pixel, red > blue due to warm temperature
        val darkR = (pixels[0] shr 16) and 0xFF
        val darkB = pixels[0] and 0xFF
        assertTrue("Shadow lift should brighten dark pixel", darkR > 30)
        assertTrue("Warm temperature should keep red > blue", darkR > darkB)

        // Highlights recovered on bright pixel
        val brightB = pixels[1] and 0xFF
        assertTrue("Negative highlights + warm temp should reduce bright blue", brightB < 220)
    }

    @Test
    fun projectSerializer_roundTripsNewAdjustmentsGlassBorderAndFilterIntensity() {
        val layer = TextLayer(
            id = "l1",
            enableGlassBackdrop = true,
            glassBackdropColorLong = 0xFF112233L,
            glassBackdropBorderColorLong = 0xFF445566L,
            glassBackdropBorderWidth = 3.4f,
            glassBackdropBorderOpacity = 0.75f
        )
        val state = CanvasState(
            layers = listOf(layer),
            activeLayerId = "l1",
            backgroundHighlights = -0.35f,
            backgroundShadows = 0.45f,
            backgroundTemperature = 0.25f,
            filmPreset = FilmPreset.BRNZ2,
            filmPresetIntensity = 1.35f
        )

        val json = AtharProjectSerializer.serializeCanvasState(state, hasBackgroundPhoto = false)
        val restored = AtharProjectSerializer.deserializeCanvasState(json)

        assertEquals(-0.35f, restored.backgroundHighlights, 0.001f)
        assertEquals(0.45f, restored.backgroundShadows, 0.001f)
        assertEquals(0.25f, restored.backgroundTemperature, 0.001f)
        assertEquals(FilmPreset.BRNZ2, restored.filmPreset)
        assertEquals(1.35f, restored.filmPresetIntensity, 0.001f)
        assertEquals(3.4f, restored.activeLayer.glassBackdropBorderWidth, 0.001f)
        assertEquals(0.75f, restored.activeLayer.glassBackdropBorderOpacity, 0.001f)
    }

    @Test
    fun picsArtStyleFilters_bakeLutAndScaleWithIntensityUpTo150Percent() {
        val requiredPresets = listOf(
            FilmPreset.NTRL1, FilmPreset.NTRL2, FilmPreset.NTRL3,
            FilmPreset.BRNZ1, FilmPreset.BRNZ2, FilmPreset.BRNZ4,
            FilmPreset.TL1, FilmPreset.MIL1
        )
        val sampleRgb = (0xFF shl 24) or (140 shl 16) or (120 shl 8) or 100
        requiredPresets.forEach { preset ->
            val half = FilmPresetEngine.evaluateFilmLutRgb(sampleRgb, preset, 0.5f)
            val full = FilmPresetEngine.evaluateFilmLutRgb(sampleRgb, preset, 1.0f)
            val boosted = FilmPresetEngine.evaluateFilmLutRgb(sampleRgb, preset, 1.5f)
            assertNotEquals("Preset ${preset.id} should transform RGB", sampleRgb, full)
            assertTrue("Intensity 0.5 should differ from 1.5 for ${preset.id}", half != boosted)
        }
    }

    @Test
    fun svgExporter_emitsCustomGradientDefinitions() {
        val layer = TextLayer(
            id = "grad_layer",
            rawText = "أثر",
            displayText = "أثر",
            customGradient = CustomGradientState(
                isEnabled = true,
                stops = listOf(
                    CustomGradientStop(0xFFFF0000L, 0f),
                    CustomGradientStop(0xFF0000FFL, 1f)
                ),
                angle = 45f
            )
        )
        val svg = AtharSvgExporter.generateSvgString(CanvasState(layers = listOf(layer), activeLayerId = "grad_layer"))
        assertTrue("SVG should contain linearGradient for customGradient", svg.contains("""<linearGradient id="grad_grad_layer""""))
        assertTrue("SVG text should reference custom gradient", svg.contains("""fill="url(#grad_grad_layer)""""))
    }

    @Test
    fun newFonts_includeThmanyahAndCorporateAndHandwritten() {
        assertTrue(ArabicFontFamily.entries.any { it == ArabicFontFamily.THMANYAH_SANS })
        assertTrue(ArabicFontFamily.entries.any { it == ArabicFontFamily.PLAYPEN_SANS_ARABIC })
        assertTrue(ArabicFontFamily.entries.any { it == ArabicFontFamily.IBM_PLEX_SANS_ARABIC })
        assertTrue("Total bundled fonts should be >= 35", ArabicFontFamily.entries.size >= 35)
    }
}
