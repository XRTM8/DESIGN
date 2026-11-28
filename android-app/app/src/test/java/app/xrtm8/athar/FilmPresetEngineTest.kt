package app.xrtm8.athar

import app.xrtm8.athar.model.FilmCategory
import app.xrtm8.athar.model.FilmPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmPresetEngineTest {

    @Test
    fun testAllFilmPresetsCompleteness() {
        assertEquals(26, FilmPreset.entries.size)
        val fuji = FilmPreset.entries.filter { it.brand == "Fujifilm" }
        assertEquals(3, fuji.size)
        val kodak = FilmPreset.entries.filter { it.brand == "Kodak" }
        assertEquals(2, kodak.size)
        val leica = FilmPreset.entries.find { it.brand == "Leica" }
        assertNotNull(leica)

        // Verify newly added Dazz-inspired camera archetypes exist cleanly
        val expectedIds = listOf(
            "dream_glow",
            "hybrid_ccd_2005",
            "minidv_cam_2002",
            "slide_chrome_100",
            "disposable_cool_400",
            "disposable_warm_800",
            "street_mono_gr",
            "toy_medium_120",
            "rangefinder_400"
        )
        expectedIds.forEach { id ->
            assertNotNull("Missing preset $id", FilmPreset.entries.find { it.id == id })
        }
    }

    @Test
    fun testFilmPresetCategoriesAndMonochromeFlags() {
        assertTrue(FilmPreset.KODAK_TRI_X.isMonochrome)
        assertTrue(FilmPreset.LEICA_MONOCHROME.isMonochrome)
        assertTrue(FilmPreset.STREET_MONO_GR.isMonochrome)
        assertFalse(FilmPreset.KODAK_PORTRA_400.isMonochrome)
        assertFalse(FilmPreset.TOY_MEDIUM_120.isMonochrome)

        val monoPresets = FilmPreset.entries.filter { it.category == FilmCategory.MONOCHROME }
        assertEquals(3, monoPresets.size)
        assertTrue(FilmPreset.entries.any { it.category == FilmCategory.INSTANT_VIDEO })
        assertTrue(FilmPreset.entries.any { it.category == FilmCategory.CLASSIC_CAMERAS })
        assertTrue(FilmPreset.entries.any { it.category == FilmCategory.CINEMA })
    }

    @Test
    fun testFilmPresetDescriptions() {
        FilmPreset.entries.forEach { preset ->
            assertTrue(preset.titleAr.isNotEmpty())
            assertTrue(preset.description.isNotEmpty())
            assertTrue(preset.id.isNotEmpty())
            assertTrue(preset.brand.isNotEmpty())
        }
    }
}
