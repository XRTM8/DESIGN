package app.xrtm8.athar.model

import android.graphics.BlendMode
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Build

enum class MaskCategory(val label: String) {
    ALL("الكل"),
    SUNLIGHT_SHADOWS("ضوء وظلال"),
    SMOKE_ATMOSPHERE("دخان وضباب"),
    LIGHT_LEAKS("تسريب وموشور"),
    TEXTURES_FRAMES("خامات وإطارات")
}

enum class MaskBlendMode(val label: String, val porterDuffMode: PorterDuff.Mode) {
    SCREEN("إضاءة Screen", PorterDuff.Mode.SCREEN),
    MULTIPLY("مضاعفة Multiply", PorterDuff.Mode.MULTIPLY),
    OVERLAY("تراكب Overlay", PorterDuff.Mode.OVERLAY),
    SOFT_LIGHT("ضوء ناعم Soft Light", PorterDuff.Mode.OVERLAY),
    COLOR_DODGE("توهج ضوئي Dodge", PorterDuff.Mode.SCREEN),
    ADD("توهج إشعاعي Add", PorterDuff.Mode.ADD),
    LIGHTEN("تفتيح Lighten", PorterDuff.Mode.LIGHTEN),
    DARKEN("تعتيم Darken", PorterDuff.Mode.DARKEN),
    NORMAL("عادي Normal", PorterDuff.Mode.SRC_OVER);

    /**
     * Applies the blend mode onto [paint] with API 29+ BlendMode precision
     * (enabling true SOFT_LIGHT & COLOR_DODGE) and deterministic PorterDuff fallback on API 26-28.
     */
    fun applyToPaint(paint: Paint) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            paint.blendMode = when (this) {
                SCREEN -> BlendMode.SCREEN
                MULTIPLY -> BlendMode.MULTIPLY
                OVERLAY -> BlendMode.OVERLAY
                SOFT_LIGHT -> BlendMode.SOFT_LIGHT
                COLOR_DODGE -> BlendMode.COLOR_DODGE
                ADD -> BlendMode.PLUS
                LIGHTEN -> BlendMode.LIGHTEN
                DARKEN -> BlendMode.DARKEN
                NORMAL -> BlendMode.SRC_OVER
            }
        } else {
            paint.xfermode = PorterDuffXfermode(porterDuffMode)
        }
    }
}

enum class ProceduralMaskType {
    WINDOW_BLINDS,       // ستائر شرائح بمنظور ضوئي (Perspective Venetian Blinds)
    FRENCH_WINDOW,       // إضاءة نافذة مقسمة بمنظور (Perspective French Window Sunlight)
    GEOMETRIC_ARCH,      // أقواس معمارية بمنظور (Perspective Architectural Arch)
    ARCHED_MASHRABIYA,   // شمس مشربية عربية (Arched Mashrabiya Lattice)
    GOLDEN_HOUR_BEAMS,   // أشعة ساعة ذهبية مائلة (Diagonal Golden Hour Shafts)
    OPTICAL_SUNBURST,    // تسريب شمسي بصري (Optical Sunburst & Dust Motes)
    ANAMORPHIC_STREAK,   // شريط توهج سينمائي (CinemaScope Anamorphic Flare)
    FILM_BORDER_35MM,    // إطار فيلم 35mm أصيل (35mm Film Frame & Sprockets)
    STUDIO_PASSEPARTOUT, // باسبارتو استوديو (Studio Passepartout Mat)
    VECTOR_HALFTONE,     // تنقيط مطبعي نقي (Vector Halftone Screen)
    PRECISION_SCANLINES, // خطوط مسح أنالوج (Analog CRT Scanlines)
    BOTANICAL_FERN_SHADOW,  // ظل أوراق نباتية (Studio Botanical Leaf Gobo)
    LOUVER_GRID_SHADOW,     // ظل شبك معماري (Skylight Louver Grid Gobo)
    PRISM_DIAGONAL_FLARE,   // موشور ضوئي مائل (Optical Prism Rainbow Streak)
    SOFT_BOKEH_LIGHTS,      // بوكيه إضاءة ناعم (Cinematic Out-of-Focus Bokeh)
    VINTAGE_FILM_BURN,      // احتراق فيلم كلاسيكي (35mm Analog Film Light Burn)
    POLAROID_INSTANT_FRAME, // إطار بولارويد فوري (Instant Film Matte Frame)
    PAPER_DECKLE_FRAME      // إطار ورق مقطوع (Artisan Deckle-Edge Paper Frame)
}

enum class BlackGradientPlacement(val label: String) {
    BOTTOM("أسفل (Bottom)"),
    TOP("أعلى (Top)"),
    LEFT("يسار (Left)"),
    RIGHT("يمين (Right)"),
    RADIAL("دائري (Radial)"),
    VERTICAL_SPLIT("شريطين (Cinematic Bars)")
}

data class BlackGradientState(
    val isEnabled: Boolean = false,
    val opacity: Float = 0.60f,
    val placement: BlackGradientPlacement = BlackGradientPlacement.BOTTOM,
    val coverage: Float = 0.55f // 0.20f to 1.0f
) {
    val isEffectActive: Boolean get() = isEnabled && opacity > 0.01f
}

data class AtharMaskDefinition(
    val id: String,
    val title: String,
    val category: MaskCategory,
    val assetFileName: String = "",
    val thumbFileName: String = if (assetFileName.isNotEmpty()) assetFileName.replace("masks/", "masks/thumbs/") else "",
    val defaultBlendMode: MaskBlendMode = MaskBlendMode.SCREEN,
    val defaultOpacity: Float = 0.75f,
    val defaultSoftness: Float = 0.25f,
    val defaultWarmth: Float = 0.35f,
    val proceduralType: ProceduralMaskType? = null
) {
    val requiresAssetBitmap: Boolean get() = proceduralType == null && assetFileName.isNotBlank()
}

data class AtharMaskState(
    val isEnabled: Boolean = false,
    val activeMaskId: String? = null,
    val customMaskUri: String? = null,
    val opacity: Float = 0.75f,
    val blendMode: MaskBlendMode = MaskBlendMode.SCREEN,
    val hueShift: Float = 0f, // -180f to +180f
    val offsetX: Float = 0f, // -1f to +1f
    val offsetY: Float = 0f, // -1f to +1f
    val scale: Float = 1.0f, // 0.5f to 3.0f
    val isFlippedH: Boolean = false,
    val isFlippedV: Boolean = false,
    val rotationDegrees: Int = 0, // 0 to 360
    val contrast: Float = 1.0f, // 0.5f to 2.5f
    val isInverted: Boolean = false,
    val tintColorLong: Long? = null,
    val isLuminanceMode: Boolean = false,
    val softness: Float = 0.25f, // 0f to 1f
    val warmth: Float = 0.35f, // 0f to 1f
    val blackGradient: BlackGradientState = BlackGradientState()
) {
    val proceduralType: ProceduralMaskType? get() = AtharMaskCatalog.getById(activeMaskId)?.proceduralType
    val isMaskActive: Boolean get() = isEnabled && (!activeMaskId.isNullOrEmpty() || !customMaskUri.isNullOrEmpty()) && opacity > 0.01f
    val isEffectActive: Boolean get() = isMaskActive || blackGradient.isEffectActive
}

object AtharMaskCatalog {
    val ALL_MASKS = listOf(
        // ==========================================
        // 1. Sunlight & Cast Shadows (ضوء وظلال)
        // ==========================================
        AtharMaskDefinition(
            id = "chroma_dream",
            title = "نافذة شمسية مائلة",
            category = MaskCategory.SUNLIGHT_SHADOWS,
            defaultBlendMode = MaskBlendMode.MULTIPLY,
            defaultOpacity = 0.76f,
            defaultSoftness = 0.30f,
            defaultWarmth = 0.45f,
            proceduralType = ProceduralMaskType.FRENCH_WINDOW
        ),
        AtharMaskDefinition(
            id = "window_shadow",
            title = "ظل ستائر شرائح",
            category = MaskCategory.SUNLIGHT_SHADOWS,
            defaultBlendMode = MaskBlendMode.MULTIPLY,
            defaultOpacity = 0.78f,
            defaultSoftness = 0.28f,
            defaultWarmth = 0.40f,
            proceduralType = ProceduralMaskType.WINDOW_BLINDS
        ),
        AtharMaskDefinition(
            id = "arched_mashrabiya",
            title = "شمس مشربية عربية",
            category = MaskCategory.SUNLIGHT_SHADOWS,
            defaultBlendMode = MaskBlendMode.MULTIPLY,
            defaultOpacity = 0.74f,
            defaultSoftness = 0.26f,
            defaultWarmth = 0.45f,
            proceduralType = ProceduralMaskType.ARCHED_MASHRABIYA
        ),
        AtharMaskDefinition(
            id = "golden_hour_beams",
            title = "أشعة ساعة ذهبية",
            category = MaskCategory.SUNLIGHT_SHADOWS,
            defaultBlendMode = MaskBlendMode.SCREEN,
            defaultOpacity = 0.75f,
            defaultSoftness = 0.35f,
            defaultWarmth = 0.65f,
            proceduralType = ProceduralMaskType.GOLDEN_HOUR_BEAMS
        ),
        AtharMaskDefinition(
            id = "dark_mesh",
            title = "أقواس معمارية",
            category = MaskCategory.SUNLIGHT_SHADOWS,
            defaultBlendMode = MaskBlendMode.MULTIPLY,
            defaultOpacity = 0.70f,
            defaultSoftness = 0.30f,
            defaultWarmth = 0.35f,
            proceduralType = ProceduralMaskType.GEOMETRIC_ARCH
        ),
        AtharMaskDefinition(
            id = "antique_linen",
            title = "ظل سعف نخل واقعي",
            category = MaskCategory.SUNLIGHT_SHADOWS,
            assetFileName = "masks/mask_palm_canopy.webp",
            defaultBlendMode = MaskBlendMode.MULTIPLY,
            defaultOpacity = 0.72f,
            defaultSoftness = 0.20f,
            defaultWarmth = 0.35f
        ),
        AtharMaskDefinition(
            id = "olive_dappled",
            title = "تسلل شمس بين أغصان",
            category = MaskCategory.SUNLIGHT_SHADOWS,
            assetFileName = "masks/mask_olive_dappled.webp",
            defaultBlendMode = MaskBlendMode.MULTIPLY,
            defaultOpacity = 0.72f,
            defaultSoftness = 0.20f,
            defaultWarmth = 0.40f
        ),

        // ==========================================
        // 2. Smoke & Atmospheric Vapor (دخان وضباب)
        // ==========================================
        AtharMaskDefinition(
            id = "smoke_incense",
            title = "خيوط بخور صاعدة",
            category = MaskCategory.SMOKE_ATMOSPHERE,
            assetFileName = "masks/mask_smoke_incense.webp",
            defaultBlendMode = MaskBlendMode.SCREEN,
            defaultOpacity = 0.78f,
            defaultSoftness = 0.15f,
            defaultWarmth = 0.30f
        ),
        AtharMaskDefinition(
            id = "smoke_floor_fog",
            title = "ضباب أرضي منخفض",
            category = MaskCategory.SMOKE_ATMOSPHERE,
            assetFileName = "masks/mask_smoke_floor_fog.webp",
            defaultBlendMode = MaskBlendMode.SCREEN,
            defaultOpacity = 0.72f,
            defaultSoftness = 0.20f,
            defaultWarmth = 0.20f
        ),
        AtharMaskDefinition(
            id = "smoke_diagonal_plume",
            title = "نفثة دخان مائلة",
            category = MaskCategory.SMOKE_ATMOSPHERE,
            assetFileName = "masks/mask_smoke_diagonal_plume.webp",
            defaultBlendMode = MaskBlendMode.SCREEN,
            defaultOpacity = 0.75f,
            defaultSoftness = 0.18f,
            defaultWarmth = 0.25f
        ),
        AtharMaskDefinition(
            id = "smoke_halo",
            title = "هالة بخار محيطة",
            category = MaskCategory.SMOKE_ATMOSPHERE,
            assetFileName = "masks/mask_smoke_halo.webp",
            defaultBlendMode = MaskBlendMode.SCREEN,
            defaultOpacity = 0.74f,
            defaultSoftness = 0.15f,
            defaultWarmth = 0.25f
        ),
        AtharMaskDefinition(
            id = "smoke_ambient_mist",
            title = "ضباب سينمائي ناعم",
            category = MaskCategory.SMOKE_ATMOSPHERE,
            assetFileName = "masks/mask_smoke_ambient_mist.webp",
            defaultBlendMode = MaskBlendMode.SCREEN,
            defaultOpacity = 0.65f,
            defaultSoftness = 0.25f,
            defaultWarmth = 0.20f
        ),

        // ==========================================
        // 3. Light Leaks & Optical Caustics (تسريب وموشور)
        // ==========================================
        AtharMaskDefinition(
            id = "sunset_leak",
            title = "تسريب غروب دافئ",
            category = MaskCategory.LIGHT_LEAKS,
            assetFileName = "masks/mask_sunset_leak.webp",
            defaultBlendMode = MaskBlendMode.SCREEN,
            defaultOpacity = 0.80f,
            defaultSoftness = 0.15f,
            defaultWarmth = 0.55f
        ),
        AtharMaskDefinition(
            id = "prism_flares",
            title = "انكسار ضوئي موشوري",
            category = MaskCategory.LIGHT_LEAKS,
            assetFileName = "masks/mask_prism_caustic.webp",
            defaultBlendMode = MaskBlendMode.SCREEN,
            defaultOpacity = 0.82f,
            defaultSoftness = 0.15f,
            defaultWarmth = 0.35f
        ),
        AtharMaskDefinition(
            id = "solar_dust",
            title = "تسريب شمسي نقي",
            category = MaskCategory.LIGHT_LEAKS,
            defaultBlendMode = MaskBlendMode.SCREEN,
            defaultOpacity = 0.82f,
            defaultSoftness = 0.35f,
            defaultWarmth = 0.60f,
            proceduralType = ProceduralMaskType.OPTICAL_SUNBURST
        ),
        AtharMaskDefinition(
            id = "cyan_burn",
            title = "شريط توهج سينمائي",
            category = MaskCategory.LIGHT_LEAKS,
            defaultBlendMode = MaskBlendMode.ADD,
            defaultOpacity = 0.80f,
            defaultSoftness = 0.25f,
            defaultWarmth = 0.20f,
            proceduralType = ProceduralMaskType.ANAMORPHIC_STREAK
        ),

        // ==========================================
        // 4. Textures & Analog Frames (خامات وإطارات)
        // ==========================================
        AtharMaskDefinition(
            id = "folded_poster",
            title = "إطار فيلم 35mm",
            category = MaskCategory.TEXTURES_FRAMES,
            defaultBlendMode = MaskBlendMode.NORMAL,
            defaultOpacity = 0.85f,
            defaultSoftness = 0.0f,
            defaultWarmth = 0.0f,
            proceduralType = ProceduralMaskType.FILM_BORDER_35MM
        ),
        AtharMaskDefinition(
            id = "fluted_glass",
            title = "باسبارتو استوديو",
            category = MaskCategory.TEXTURES_FRAMES,
            defaultBlendMode = MaskBlendMode.NORMAL,
            defaultOpacity = 0.80f,
            defaultSoftness = 0.0f,
            defaultWarmth = 0.0f,
            proceduralType = ProceduralMaskType.STUDIO_PASSEPARTOUT
        ),
        AtharMaskDefinition(
            id = "halftone_wave",
            title = "تنقيط مطبعي نقي",
            category = MaskCategory.TEXTURES_FRAMES,
            defaultBlendMode = MaskBlendMode.OVERLAY,
            defaultOpacity = 0.55f,
            defaultSoftness = 0.0f,
            defaultWarmth = 0.0f,
            proceduralType = ProceduralMaskType.VECTOR_HALFTONE
        ),
        AtharMaskDefinition(
            id = "crt_scanlines",
            title = "خطوط مسح تلفاز",
            category = MaskCategory.TEXTURES_FRAMES,
            defaultBlendMode = MaskBlendMode.OVERLAY,
            defaultOpacity = 0.45f,
            defaultSoftness = 0.0f,
            defaultWarmth = 0.0f,
            proceduralType = ProceduralMaskType.PRECISION_SCANLINES
        ),
        AtharMaskDefinition(
            id = "botanical_fern_gobo",
            title = "ظل أوراق نباتية",
            category = MaskCategory.SUNLIGHT_SHADOWS,
            defaultBlendMode = MaskBlendMode.MULTIPLY,
            defaultOpacity = 0.72f,
            defaultSoftness = 0.28f,
            defaultWarmth = 0.42f,
            proceduralType = ProceduralMaskType.BOTANICAL_FERN_SHADOW
        ),
        AtharMaskDefinition(
            id = "louver_grid_gobo",
            title = "ظل شبك معماري",
            category = MaskCategory.SUNLIGHT_SHADOWS,
            defaultBlendMode = MaskBlendMode.MULTIPLY,
            defaultOpacity = 0.74f,
            defaultSoftness = 0.24f,
            defaultWarmth = 0.35f,
            proceduralType = ProceduralMaskType.LOUVER_GRID_SHADOW
        ),
        AtharMaskDefinition(
            id = "prism_diagonal_flare",
            title = "موشور ضوئي مائل",
            category = MaskCategory.LIGHT_LEAKS,
            defaultBlendMode = MaskBlendMode.SCREEN,
            defaultOpacity = 0.78f,
            defaultSoftness = 0.32f,
            defaultWarmth = 0.40f,
            proceduralType = ProceduralMaskType.PRISM_DIAGONAL_FLARE
        ),
        AtharMaskDefinition(
            id = "soft_bokeh_lights",
            title = "بوكيه إضاءة ناعم",
            category = MaskCategory.LIGHT_LEAKS,
            defaultBlendMode = MaskBlendMode.SCREEN,
            defaultOpacity = 0.72f,
            defaultSoftness = 0.35f,
            defaultWarmth = 0.55f,
            proceduralType = ProceduralMaskType.SOFT_BOKEH_LIGHTS
        ),
        AtharMaskDefinition(
            id = "vintage_film_burn",
            title = "احتراق فيلم كلاسيكي",
            category = MaskCategory.LIGHT_LEAKS,
            defaultBlendMode = MaskBlendMode.SCREEN,
            defaultOpacity = 0.80f,
            defaultSoftness = 0.30f,
            defaultWarmth = 0.70f,
            proceduralType = ProceduralMaskType.VINTAGE_FILM_BURN
        ),
        AtharMaskDefinition(
            id = "polaroid_instant_frame",
            title = "إطار بولارويد فوري",
            category = MaskCategory.TEXTURES_FRAMES,
            defaultBlendMode = MaskBlendMode.NORMAL,
            defaultOpacity = 0.92f,
            defaultSoftness = 0.0f,
            defaultWarmth = 0.0f,
            proceduralType = ProceduralMaskType.POLAROID_INSTANT_FRAME
        ),
        AtharMaskDefinition(
            id = "paper_deckle_frame",
            title = "إطار ورق مقطوع",
            category = MaskCategory.TEXTURES_FRAMES,
            defaultBlendMode = MaskBlendMode.NORMAL,
            defaultOpacity = 0.90f,
            defaultSoftness = 0.0f,
            defaultWarmth = 0.0f,
            proceduralType = ProceduralMaskType.PAPER_DECKLE_FRAME
        )
    )

    fun getById(id: String?): AtharMaskDefinition? = ALL_MASKS.find { it.id == id }
}
