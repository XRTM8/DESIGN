package app.xrtm8.athar

import app.xrtm8.athar.core.AtharMaskRenderer
import app.xrtm8.athar.core.AtharProceduralMaskRenderer
import app.xrtm8.athar.model.*
import app.xrtm8.athar.project.AtharProjectSerializer
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AtharMaskSystemTest {

    private fun resolveAppMainDir(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val candidates = listOf(
            File(cwd, "src/main"),
            File(cwd, "app/src/main"),
            File(cwd, "android-app/app/src/main")
        )
        return candidates.firstOrNull { it.exists() && File(it, "assets").exists() }
            ?: error("Could not locate app/src/main from ${cwd.absolutePath}")
    }

    @Test
    fun testAtharMaskCatalogIntegrity() {
        val masks = AtharMaskCatalog.ALL_MASKS
        assertEquals(27, masks.size)

        val uniqueIds = masks.map { it.id }.toSet()
        assertEquals("Mask IDs must all be unique", 27, uniqueIds.size)

        masks.forEach { mask ->
            assertTrue("Mask title must not be empty", mask.title.isNotEmpty())
            assertTrue("Default opacity must be in valid range", mask.defaultOpacity in 0.1f..1.0f)
            assertTrue("Default softness must be in valid range", mask.defaultSoftness in 0.0f..1.0f)
            assertTrue("Default warmth must be in valid range", mask.defaultWarmth in 0.0f..1.0f)
            assertNotNull("Must find by ID", AtharMaskCatalog.getById(mask.id))

            if (mask.proceduralType != null) {
                assertFalse("Procedural mask should not require asset bitmap", mask.requiresAssetBitmap)
                assertFalse("shouldDecodeAssetBitmap must be false for procedural masks", AtharMaskRenderer.shouldDecodeAssetBitmap(mask))
            } else {
                assertTrue("Asset file must start with masks/", mask.assetFileName.startsWith("masks/"))
                assertTrue("Asset file must end with .webp", mask.assetFileName.endsWith(".webp"))
                assertTrue("Thumb file must start with masks/thumbs/", mask.thumbFileName.startsWith("masks/thumbs/"))
                assertTrue("Raster mask must require asset bitmap", mask.requiresAssetBitmap)
                assertTrue("shouldDecodeAssetBitmap must be true for WebP masks", AtharMaskRenderer.shouldDecodeAssetBitmap(mask))
            }
        }

        // Verify all 12 legacy IDs remain resolvable for saved .athar compatibility
        val legacyIds = listOf(
            "solar_dust", "prism_flares", "sunset_leak", "antique_linen",
            "dark_mesh", "chroma_dream", "cyan_burn", "crt_scanlines",
            "window_shadow", "halftone_wave", "fluted_glass", "folded_poster"
        )
        legacyIds.forEach { id ->
            assertNotNull("Legacy ID '$id' must remain in catalog", AtharMaskCatalog.getById(id))
        }

        // Verify new smoke, botanical & sunlight masks exist
        val newIds = listOf(
            "arched_mashrabiya", "golden_hour_beams", "olive_dappled",
            "smoke_incense", "smoke_floor_fog", "smoke_diagonal_plume",
            "smoke_halo", "smoke_ambient_mist"
        )
        newIds.forEach { id ->
            assertNotNull("New ID '$id' must exist in catalog", AtharMaskCatalog.getById(id))
        }
    }

    @Test
    fun testZeroDeadMaskAssetsAndFilesExist() {
        val mainDir = resolveAppMainDir()
        val assetsRoot = File(mainDir, "assets")
        val masksDir = File(assetsRoot, "masks")
        val thumbsDir = File(masksDir, "thumbs")

        assertTrue("assets/masks directory must exist", masksDir.exists())
        assertTrue("assets/masks/thumbs directory must exist", thumbsDir.exists())

        val rasterMasks = AtharMaskCatalog.ALL_MASKS.filter { it.requiresAssetBitmap }
        assertEquals("Expected 9 WebP-backed studio masks (5 smoke + 2 botanical + 2 optical leaks)", 9, rasterMasks.size)

        val expectedAssets = rasterMasks.map { File(assetsRoot, it.assetFileName).canonicalPath }.toSet()
        val expectedThumbs = rasterMasks.map { File(assetsRoot, it.thumbFileName).canonicalPath }.toSet()

        rasterMasks.forEach { mask ->
            val assetFile = File(assetsRoot, mask.assetFileName)
            val thumbFile = File(assetsRoot, mask.thumbFileName)
            assertTrue("Missing asset WebP: ${mask.assetFileName}", assetFile.exists() && assetFile.length() > 1000L)
            assertTrue("Missing thumb WebP: ${mask.thumbFileName}", thumbFile.exists() && thumbFile.length() > 100L)
        }

        val diskAssets = masksDir.listFiles { f -> f.isFile }?.map { it.canonicalPath }?.toSet() ?: emptySet()
        val diskThumbs = thumbsDir.listFiles { f -> f.isFile }?.map { it.canonicalPath }?.toSet() ?: emptySet()

        assertEquals("Zero dead/unreferenced WebPs allowed in assets/masks/", expectedAssets, diskAssets)
        assertEquals("Zero dead/unreferenced WebPs allowed in assets/masks/thumbs/", expectedThumbs, diskThumbs)

        val totalSizeBytes = (masksDir.listFiles { f -> f.isFile }?.sumOf { it.length() } ?: 0L) +
            (thumbsDir.listFiles { f -> f.isFile }?.sumOf { it.length() } ?: 0L)
        assertTrue("Total assets/masks size must stay compact (<= 1.1 MB), was $totalSizeBytes B", totalSizeBytes <= 1_150_000L)
    }

    @Test
    fun testMaskCategoriesAndBlendModes() {
        val categories = MaskCategory.values().toList()
        assertEquals(
            listOf(
                MaskCategory.ALL,
                MaskCategory.SUNLIGHT_SHADOWS,
                MaskCategory.SMOKE_ATMOSPHERE,
                MaskCategory.LIGHT_LEAKS,
                MaskCategory.TEXTURES_FRAMES
            ),
            categories
        )
        categories.forEach { cat ->
            assertTrue("Category ${cat.name} label must be non-empty", cat.label.isNotBlank())
        }

        assertEquals(9, AtharMaskCatalog.ALL_MASKS.count { it.category == MaskCategory.SUNLIGHT_SHADOWS })
        assertEquals(5, AtharMaskCatalog.ALL_MASKS.count { it.category == MaskCategory.SMOKE_ATMOSPHERE })
        assertEquals(7, AtharMaskCatalog.ALL_MASKS.count { it.category == MaskCategory.LIGHT_LEAKS })
        assertEquals(6, AtharMaskCatalog.ALL_MASKS.count { it.category == MaskCategory.TEXTURES_FRAMES })

        assertTrue(MaskBlendMode.values().contains(MaskBlendMode.SOFT_LIGHT))
        assertTrue(MaskBlendMode.values().contains(MaskBlendMode.COLOR_DODGE))
        assertEquals(android.graphics.PorterDuff.Mode.ADD, MaskBlendMode.ADD.porterDuffMode)
    }

    @Test
    fun testAtharMaskStateDefaults() {
        val defaultState = AtharMaskState()
        assertFalse(defaultState.isEnabled)
        assertNull(defaultState.activeMaskId)
        assertFalse(defaultState.isMaskActive)
        assertFalse(defaultState.isEffectActive)
        assertEquals(0.75f, defaultState.opacity, 0.001f)
        assertEquals(0f, defaultState.hueShift, 0.001f)
        assertEquals(0f, defaultState.offsetX, 0.001f)
        assertEquals(0f, defaultState.offsetY, 0.001f)
        assertEquals(1.0f, defaultState.scale, 0.001f)
        assertFalse(defaultState.isFlippedH)
        assertFalse(defaultState.isFlippedV)
        assertEquals(0, defaultState.rotationDegrees)
        assertEquals(1.0f, defaultState.contrast, 0.001f)
        assertFalse(defaultState.isInverted)
        assertNull(defaultState.tintColorLong)
        assertFalse(defaultState.isLuminanceMode)
        assertEquals(0.25f, defaultState.softness, 0.001f)
        assertEquals(0.35f, defaultState.warmth, 0.001f)
        assertFalse(defaultState.blackGradient.isEnabled)
        assertFalse(defaultState.blackGradient.isEffectActive)
        assertEquals(1440, AtharMaskRenderer.MAX_MASK_ASSET_DIM_PX)
    }

    @Test
    fun testAlphaPreservationHelper() {
        assertEquals(0, AtharProceduralMaskRenderer.scaleAlpha(0, 248))
        assertEquals(248, AtharProceduralMaskRenderer.scaleAlpha(255, 248))
        assertEquals(124, AtharProceduralMaskRenderer.scaleAlpha(128, 248))
        assertEquals(102, AtharProceduralMaskRenderer.scaleAlpha(128, 204))
    }

    @Test
    fun testAtharMaskStateSerializationRoundtrip() {
        val originalMask = AtharMaskState(
            isEnabled = true,
            activeMaskId = "window_shadow",
            opacity = 0.85f,
            blendMode = MaskBlendMode.MULTIPLY,
            hueShift = 45f,
            offsetX = 0.25f,
            offsetY = -0.15f,
            scale = 1.35f,
            isFlippedH = true,
            isFlippedV = false,
            rotationDegrees = 90,
            contrast = 1.45f,
            isInverted = true,
            tintColorLong = 0xFFFFD54FL,
            isLuminanceMode = true,
            blackGradient = BlackGradientState(
                isEnabled = true,
                opacity = 0.70f,
                placement = BlackGradientPlacement.BOTTOM,
                coverage = 0.65f
            )
        )

        val canvasState = CanvasState(maskState = originalMask)
        val json = AtharProjectSerializer.serializeCanvasState(canvasState, hasBackgroundPhoto = false)
        val deserialized = AtharProjectSerializer.deserializeCanvasState(json)

        val restored = deserialized.maskState
        assertTrue(restored.isEnabled)
        assertEquals("window_shadow", restored.activeMaskId)
        assertEquals(0.85f, restored.opacity, 0.01f)
        assertEquals(MaskBlendMode.MULTIPLY, restored.blendMode)
        assertEquals(45f, restored.hueShift, 0.01f)
        assertEquals(0.25f, restored.offsetX, 0.01f)
        assertEquals(-0.15f, restored.offsetY, 0.01f)
        assertEquals(1.35f, restored.scale, 0.01f)
        assertTrue(restored.isFlippedH)
        assertFalse(restored.isFlippedV)
        assertEquals(90, restored.rotationDegrees)
        assertEquals(1.45f, restored.contrast, 0.01f)
        assertTrue(restored.isInverted)
        assertEquals(0xFFFFD54FL, restored.tintColorLong)
        assertTrue(restored.isLuminanceMode)
        assertTrue(restored.blackGradient.isEnabled)
        assertEquals(0.70f, restored.blackGradient.opacity, 0.01f)
        assertEquals(BlackGradientPlacement.BOTTOM, restored.blackGradient.placement)
        assertEquals(0.65f, restored.blackGradient.coverage, 0.01f)
        assertTrue(restored.isEffectActive)
    }

    @Test
    fun testBlackGradientPlacements() {
        assertEquals("أسفل (Bottom)", BlackGradientPlacement.BOTTOM.label)
        assertEquals("أعلى (Top)", BlackGradientPlacement.TOP.label)
        assertEquals("يسار (Left)", BlackGradientPlacement.LEFT.label)
        assertEquals("يمين (Right)", BlackGradientPlacement.RIGHT.label)
        assertEquals("دائري (Radial)", BlackGradientPlacement.RADIAL.label)
        assertEquals("شريطين (Cinematic Bars)", BlackGradientPlacement.VERTICAL_SPLIT.label)
    }

    @Test
    fun testMaskUndoRedoWorkflow() {
        val vm = app.xrtm8.athar.ui.AtharStudioViewModel()
        assertFalse(vm.canvasState.value.maskState.isEnabled)
        assertFalse(vm.canUndo.value)

        vm.selectMask("solar_dust")
        assertTrue(vm.canvasState.value.maskState.isEnabled)
        assertEquals("solar_dust", vm.canvasState.value.maskState.activeMaskId)
        assertTrue(vm.canUndo.value)

        val origOpacity = vm.canvasState.value.maskState.opacity
        vm.setMaskOpacity(0.35f)
        assertEquals(0.35f, vm.canvasState.value.maskState.opacity, 0.001f)
        vm.commitHistoryCheckpoint()

        vm.undo()
        assertEquals(origOpacity, vm.canvasState.value.maskState.opacity, 0.001f)
        assertTrue(vm.canRedo.value)

        vm.redo()
        assertEquals(0.35f, vm.canvasState.value.maskState.opacity, 0.001f)

        vm.setBlackGradientEnabled(true)
        assertTrue(vm.canvasState.value.maskState.blackGradient.isEnabled)
        vm.setBlackGradientCoverage(0.90f)
        vm.commitHistoryCheckpoint()
        assertEquals(0.90f, vm.canvasState.value.maskState.blackGradient.coverage, 0.001f)

        vm.undo()
        assertEquals(0.55f, vm.canvasState.value.maskState.blackGradient.coverage, 0.001f)

        vm.resetMask()
        assertFalse(vm.canvasState.value.maskState.isEnabled)
        assertFalse(vm.canvasState.value.maskState.blackGradient.isEnabled)
    }

    @Test
    fun testMaskStudio2AdvancedFeatures() {
        val vm = app.xrtm8.athar.ui.AtharStudioViewModel()
        vm.selectMask("window_shadow")
        assertTrue(vm.canvasState.value.maskState.isEnabled)
        assertEquals(ProceduralMaskType.WINDOW_BLINDS, vm.canvasState.value.maskState.proceduralType)

        vm.setMaskContrast(1.85f)
        assertEquals(1.85f, vm.canvasState.value.maskState.contrast, 0.001f)

        assertFalse(vm.canvasState.value.maskState.isInverted)
        vm.toggleMaskInvert()
        assertTrue(vm.canvasState.value.maskState.isInverted)
        vm.toggleMaskInvert()
        assertFalse(vm.canvasState.value.maskState.isInverted)

        assertNull(vm.canvasState.value.maskState.tintColorLong)
        vm.setMaskTintColor(0xFFFFD54FL)
        assertEquals(0xFFFFD54FL, vm.canvasState.value.maskState.tintColorLong)
        vm.setMaskTintColor(null)
        assertNull(vm.canvasState.value.maskState.tintColorLong)

        assertFalse(vm.canvasState.value.maskState.isLuminanceMode)
        vm.toggleMaskLuminanceMode()
        assertTrue(vm.canvasState.value.maskState.isLuminanceMode)

        vm.setMaskRotationDegrees(135)
        assertEquals(135, vm.canvasState.value.maskState.rotationDegrees)

        vm.setMaskBlendMode(MaskBlendMode.ADD)
        assertEquals(MaskBlendMode.ADD, vm.canvasState.value.maskState.blendMode)
        assertEquals(android.graphics.PorterDuff.Mode.ADD, vm.canvasState.value.maskState.blendMode.porterDuffMode)
    }

    @Test
    fun testProceduralMaskTypesIntegrity() {
        val types = ProceduralMaskType.values()
        assertEquals(18, types.size)
        val catalogTypes = AtharMaskCatalog.ALL_MASKS.mapNotNull { it.proceduralType }.toSet()
        assertEquals("All 18 procedural types must be mapped in catalog", 18, catalogTypes.size)

        types.forEach { type ->
            assertTrue("Type $type must be represented in catalog", catalogTypes.contains(type))
        }
        assertEquals(ProceduralMaskType.FRENCH_WINDOW, AtharMaskCatalog.getById("chroma_dream")?.proceduralType)
        assertEquals(ProceduralMaskType.WINDOW_BLINDS, AtharMaskCatalog.getById("window_shadow")?.proceduralType)
        assertEquals(ProceduralMaskType.ARCHED_MASHRABIYA, AtharMaskCatalog.getById("arched_mashrabiya")?.proceduralType)
        assertEquals(ProceduralMaskType.GOLDEN_HOUR_BEAMS, AtharMaskCatalog.getById("golden_hour_beams")?.proceduralType)
    }

    @Test
    fun testUiFilesRespectSmallUnitsLineCap() {
        val mainDir = resolveAppMainDir()
        val uiDir = File(mainDir, "java/app/xrtm8/athar/ui/panels/background")
        val maskUiFiles = listOf(
            "MaskPanel.kt",
            "MaskCarouselSection.kt",
            "MaskAdjustmentsSection.kt",
            "MaskTransformControls.kt",
            "MaskThumbnails.kt"
        )
        maskUiFiles.forEach { fname ->
            val file = File(uiDir, fname)
            assertTrue("Missing UI file $fname", file.exists())
            val lineCount = file.readLines().size
            assertTrue("$fname has $lineCount lines; must stay <= 230 lines per ARCHITECTURE.md", lineCount <= 230)
        }
    }
}
