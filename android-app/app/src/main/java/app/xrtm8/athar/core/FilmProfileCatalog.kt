package app.xrtm8.athar.core

import app.xrtm8.athar.model.FilmPreset

/**
 * Parametric film camera and stock color science profiles consumed by [FilmPresetEngine].
 */
internal object FilmProfileCatalog {

    fun getFilmProfile(preset: FilmPreset): FilmPresetEngine.FilmProfile = when (preset) {
        FilmPreset.NONE -> FilmPresetEngine.FilmProfile(
            highlightDesat = 0f,
            shadowDesat = 0f
        )

        FilmPreset.FUJI_CLASSIC_CHROME -> FilmPresetEngine.FilmProfile(
            blackLift = 0.020f,
            contrastS = 0.22f,
            highlightRolloff = 0.22f,
            saturation = 0.86f,
            rFromR = 1.03f, rFromG = -0.02f,
            gFromG = 0.99f,
            bFromB = 0.95f,
            shadowTintR = -0.015f, shadowTintG = 0.014f, shadowTintB = 0.022f,
            highlightTintR = 0.020f, highlightTintG = 0.010f, highlightTintB = -0.022f
        )

        FilmPreset.FUJI_VELVIA -> FilmPresetEngine.FilmProfile(
            blackCrush = 0.022f,
            contrastS = 0.40f,
            highlightRolloff = 0.20f,
            saturation = 1.36f,
            rFromR = 1.04f, gFromG = 1.02f, bFromB = 1.03f,
            shadowTintR = 0.012f, shadowTintG = -0.010f, shadowTintB = 0.028f,
            highlightTintR = 0.018f, highlightTintG = 0.005f, highlightTintB = -0.010f
        )

        FilmPreset.FUJI_PROVIA -> FilmPresetEngine.FilmProfile(
            contrastS = 0.18f,
            highlightRolloff = 0.16f,
            saturation = 1.09f,
            shadowTintR = -0.006f, shadowTintB = 0.010f,
            highlightTintR = 0.008f, highlightTintG = 0.004f
        )

        FilmPreset.KODAK_PORTRA_400 -> FilmPresetEngine.FilmProfile(
            blackLift = 0.036f,
            contrastS = 0.16f,
            highlightRolloff = 0.32f,
            exposureGain = 1.05f,
            saturation = 1.02f,
            rFromR = 1.02f, rFromG = 0.03f, rFromB = -0.02f,
            gFromG = 1.01f,
            bFromG = -0.02f, bFromB = 0.97f,
            shadowTintR = -0.008f, shadowTintG = 0.010f, shadowTintB = -0.008f,
            highlightTintR = 0.042f, highlightTintG = 0.022f, highlightTintB = -0.034f
        )

        FilmPreset.KODAK_TRI_X -> FilmPresetEngine.FilmProfile(
            blackCrush = 0.032f,
            contrastS = 0.48f,
            highlightRolloff = 0.20f,
            rFromR = 0.36f, rFromG = 0.54f, rFromB = 0.10f,
            isMonochrome = true
        )

        FilmPreset.LEICA_MONOCHROME -> FilmPresetEngine.FilmProfile(
            blackLift = 0.018f,
            contrastS = 0.26f,
            highlightRolloff = 0.30f,
            rFromR = 0.26f, rFromG = 0.62f, rFromB = 0.12f,
            isMonochrome = true
        )

        FilmPreset.CINEMATIC_TEAL_ORANGE -> FilmPresetEngine.FilmProfile(
            blackLift = 0.018f,
            contrastS = 0.36f,
            highlightRolloff = 0.26f,
            saturation = 1.08f,
            rFromR = 1.03f, rFromB = -0.03f,
            bFromR = -0.03f, bFromB = 1.02f,
            shadowTintR = -0.068f, shadowTintG = 0.036f, shadowTintB = 0.095f,
            highlightTintR = 0.105f, highlightTintG = 0.032f, highlightTintB = -0.088f
        )

        FilmPreset.VINTAGE_WARMTH -> FilmPresetEngine.FilmProfile(
            blackLift = 0.056f,
            contrastS = 0.12f,
            highlightRolloff = 0.28f,
            saturation = 0.88f,
            rFromR = 1.02f, rFromG = 0.04f,
            bFromB = 0.92f,
            shadowTintR = 0.042f, shadowTintG = 0.018f, shadowTintB = -0.032f,
            highlightTintR = 0.068f, highlightTintG = 0.038f, highlightTintB = -0.068f
        )

        FilmPreset.DREAM_GLOW -> FilmPresetEngine.FilmProfile(
            blackLift = 0.042f,
            contrastS = -0.10f,
            highlightRolloff = 0.36f,
            exposureGain = 1.06f,
            saturation = 1.04f,
            shadowTintR = 0.024f, shadowTintG = 0.012f, shadowTintB = 0.030f,
            highlightTintR = 0.044f, highlightTintG = 0.024f, highlightTintB = -0.022f
        )

        FilmPreset.HYBRID_CCD_2005 -> FilmPresetEngine.FilmProfile(
            blackCrush = 0.015f,
            contrastS = 0.30f,
            highlightRolloff = 0.10f,
            exposureGain = 1.03f,
            saturation = 1.12f,
            rFromR = 1.05f, rFromG = -0.02f,
            bFromB = 1.04f,
            shadowTintR = -0.022f, shadowTintG = 0.004f, shadowTintB = 0.042f,
            highlightTintR = 0.030f, highlightTintG = 0.012f, highlightTintB = 0.002f
        )

        FilmPreset.MINIDV_CAM_2002 -> FilmPresetEngine.FilmProfile(
            blackLift = 0.046f,
            contrastS = 0.16f,
            highlightRolloff = 0.12f,
            saturation = 0.90f,
            gFromG = 1.02f,
            bFromB = 1.04f,
            shadowTintR = -0.042f, shadowTintG = 0.022f, shadowTintB = 0.062f,
            highlightTintR = -0.010f, highlightTintG = 0.022f, highlightTintB = 0.020f
        )

        FilmPreset.SLIDE_CHROME_100 -> FilmPresetEngine.FilmProfile(
            blackCrush = 0.045f,
            contrastS = 0.50f,
            highlightRolloff = 0.16f,
            saturation = 1.24f,
            rFromR = 1.03f, bFromB = 1.03f,
            shadowTintR = -0.022f, shadowTintG = -0.008f, shadowTintB = 0.042f,
            highlightTintR = 0.032f, highlightTintG = 0.012f, highlightTintB = -0.018f
        )

        FilmPreset.DISPOSABLE_COOL_400 -> FilmPresetEngine.FilmProfile(
            blackLift = 0.050f,
            contrastS = 0.22f,
            highlightRolloff = 0.24f,
            exposureGain = 1.04f,
            saturation = 0.96f,
            gFromG = 1.02f,
            shadowTintR = -0.032f, shadowTintG = 0.056f, shadowTintB = 0.032f,
            highlightTintR = 0.032f, highlightTintG = 0.028f, highlightTintB = -0.022f
        )

        FilmPreset.DISPOSABLE_WARM_800 -> FilmPresetEngine.FilmProfile(
            blackLift = 0.054f,
            contrastS = 0.24f,
            highlightRolloff = 0.26f,
            exposureGain = 1.03f,
            saturation = 1.04f,
            rFromR = 1.03f, rFromG = 0.03f,
            bFromB = 0.94f,
            shadowTintR = 0.030f, shadowTintG = 0.012f, shadowTintB = -0.040f,
            highlightTintR = 0.080f, highlightTintG = 0.040f, highlightTintB = -0.068f
        )

        FilmPreset.STREET_MONO_GR -> FilmPresetEngine.FilmProfile(
            blackCrush = 0.058f,
            contrastS = 0.62f,
            highlightRolloff = 0.12f,
            rFromR = 0.40f, rFromG = 0.50f, rFromB = 0.10f,
            isMonochrome = true
        )

        FilmPreset.TOY_MEDIUM_120 -> FilmPresetEngine.FilmProfile(
            blackLift = 0.046f,
            contrastS = 0.28f,
            highlightRolloff = 0.24f,
            saturation = 0.94f,
            rFromR = 1.02f, gFromB = 0.02f, bFromB = 0.96f,
            shadowTintR = -0.032f, shadowTintG = 0.032f, shadowTintB = 0.040f,
            highlightTintR = 0.060f, highlightTintG = 0.038f, highlightTintB = -0.060f
        )

        FilmPreset.RANGEFINDER_400 -> FilmPresetEngine.FilmProfile(
            blackLift = 0.026f,
            contrastS = 0.26f,
            highlightRolloff = 0.26f,
            saturation = 1.06f,
            gFromG = 1.01f, gFromB = 0.02f,
            bFromG = -0.02f, bFromB = 0.99f,
            shadowTintR = -0.020f, shadowTintG = 0.022f, shadowTintB = 0.020f,
            highlightTintR = 0.040f, highlightTintG = 0.020f, highlightTintB = -0.030f
        )

        // PicsArt-style Natural, Bronze, Teal & Milk Filters
        FilmPreset.NTRL1 -> FilmPresetEngine.FilmProfile(
            blackLift = 0.025f,
            contrastS = 0.12f,
            highlightRolloff = 0.20f,
            exposureGain = 1.03f,
            saturation = 0.94f,
            shadowTintR = 0.010f, shadowTintG = 0.012f, shadowTintB = 0.008f,
            highlightTintR = 0.024f, highlightTintG = 0.016f, highlightTintB = -0.012f
        )

        FilmPreset.NTRL2 -> FilmPresetEngine.FilmProfile(
            blackLift = 0.014f,
            contrastS = 0.24f,
            highlightRolloff = 0.18f,
            exposureGain = 1.04f,
            saturation = 1.05f,
            rFromR = 1.01f, gFromG = 1.01f, bFromB = 0.99f,
            shadowTintR = -0.006f, shadowTintG = 0.008f, shadowTintB = 0.014f,
            highlightTintR = 0.020f, highlightTintG = 0.014f, highlightTintB = -0.010f
        )

        FilmPreset.NTRL3 -> FilmPresetEngine.FilmProfile(
            blackLift = 0.048f,
            contrastS = -0.04f,
            highlightRolloff = 0.28f,
            exposureGain = 1.02f,
            saturation = 0.84f,
            shadowTintR = 0.016f, shadowTintG = 0.014f, shadowTintB = 0.020f,
            highlightTintR = 0.022f, highlightTintG = 0.018f, highlightTintB = 0.004f
        )

        FilmPreset.BRNZ1 -> FilmPresetEngine.FilmProfile(
            blackLift = 0.028f,
            contrastS = 0.22f,
            highlightRolloff = 0.24f,
            exposureGain = 1.03f,
            saturation = 1.06f,
            rFromR = 1.04f, rFromG = 0.03f,
            gFromG = 1.00f,
            bFromB = 0.90f,
            shadowTintR = 0.036f, shadowTintG = 0.016f, shadowTintB = -0.028f,
            highlightTintR = 0.072f, highlightTintG = 0.038f, highlightTintB = -0.058f
        )

        FilmPreset.BRNZ2 -> FilmPresetEngine.FilmProfile(
            blackCrush = 0.028f,
            blackLift = 0.016f,
            contrastS = 0.38f,
            highlightRolloff = 0.22f,
            saturation = 1.12f,
            rFromR = 1.05f, rFromG = 0.04f,
            gFromG = 0.99f,
            bFromB = 0.88f,
            shadowTintR = 0.042f, shadowTintG = 0.012f, shadowTintB = -0.038f,
            highlightTintR = 0.088f, highlightTintG = 0.042f, highlightTintB = -0.072f
        )

        FilmPreset.BRNZ4 -> FilmPresetEngine.FilmProfile(
            blackLift = 0.034f,
            contrastS = 0.30f,
            highlightRolloff = 0.30f,
            saturation = 0.88f,
            rFromR = 1.04f, rFromG = 0.02f, rFromB = -0.02f,
            gFromG = 0.97f,
            bFromB = 0.91f,
            shadowTintR = 0.024f, shadowTintG = 0.006f, shadowTintB = -0.018f,
            highlightTintR = 0.075f, highlightTintG = 0.032f, highlightTintB = -0.052f
        )

        FilmPreset.TL1 -> FilmPresetEngine.FilmProfile(
            blackLift = 0.024f,
            contrastS = 0.28f,
            highlightRolloff = 0.22f,
            saturation = 1.02f,
            rFromR = 0.98f,
            gFromG = 1.02f,
            bFromB = 1.05f,
            shadowTintR = -0.072f, shadowTintG = 0.042f, shadowTintB = 0.085f,
            highlightTintR = 0.038f, highlightTintG = 0.020f, highlightTintB = -0.022f
        )

        FilmPreset.MIL1 -> FilmPresetEngine.FilmProfile(
            blackLift = 0.072f,
            contrastS = -0.14f,
            highlightRolloff = 0.34f,
            exposureGain = 1.05f,
            saturation = 0.82f,
            shadowTintR = 0.022f, shadowTintG = 0.022f, shadowTintB = 0.028f,
            highlightTintR = 0.036f, highlightTintG = 0.030f, highlightTintB = 0.016f
        )
    }
}
