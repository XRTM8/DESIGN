package app.xrtm8.athar

import app.xrtm8.athar.model.ArabicFontFamily
import app.xrtm8.athar.model.CustomFont
import app.xrtm8.athar.model.LuxuryGradientPreset
import app.xrtm8.athar.model.TextLayer
import app.xrtm8.athar.model.CanvasState
import app.xrtm8.athar.export.AtharSvgExporter
import app.xrtm8.athar.typography.ArabicParagraphJustifier
import app.xrtm8.athar.typography.AtharTextLayout
import app.xrtm8.athar.typography.SfntFontParser
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class AtharFontSystemTest {

    private fun resolveAssetsDir(): File {
        val candidates = listOf(
            File("src/main/assets"),
            File("app/src/main/assets"),
            File("android-app/app/src/main/assets")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("Unable to locate src/main/assets from ${File(".").absolutePath}")
    }

    @Test
    fun allBundledFontAssetsExistAndParseAsLegalValidTtf() {
        val assetsDir = resolveAssetsDir()
        val legacyProprietary = File(assetsDir, "fonts/elgharib_kfgqpc_hafs_taweel.ttf")
        assertFalse("Proprietary KFGQPC font file must be removed", legacyProprietary.exists())

        ArabicFontFamily.entries.forEach { font ->
            val paths = (listOf(font.assetPath) + font.staticWeightAssets.values).distinct()
            paths.forEach { relPath ->
                val f = File(assetsDir, relPath)
                assertTrue("Missing asset for ${font.name}: $relPath", f.exists() && f.length() > 1000L)
                val parsed = SfntFontParser.parse(f.readBytes())
                assertNotNull("Failed SFNT parse for $relPath", parsed)
                assertTrue("Expected TTF or OTF format for $relPath (got ${parsed!!.format})", parsed.format == "TTF" || parsed.format == "OTF")
                assertTrue("Font $relPath must support Arabic", parsed.supportsArabic)
            }
            val baseParsed = SfntFontParser.parse(File(assetsDir, font.assetPath).readBytes())!!
            assertEquals("Variable flag mismatch for ${font.name}", font.isVariable, baseParsed.isVariable)
            assertEquals("Tatweel flag mismatch for ${font.name}", font.supportsTatweel, baseParsed.supportsTatweel)
        }
    }

    @Test
    fun tajawalStaticWeightsAndCairoAlexandriaVariableAxesVerified() {
        val assetsDir = resolveAssetsDir()

        val tajawalReg = SfntFontParser.parse(File(assetsDir, ArabicFontFamily.TAJAWAL.assetPath).readBytes())!!
        assertEquals(400, tajawalReg.weightClass)
        val tajawalBold = SfntFontParser.parse(File(assetsDir, ArabicFontFamily.TAJAWAL.assetPathForWeight(700)).readBytes())!!
        assertEquals(700, tajawalBold.weightClass)
        val tajawalLight = SfntFontParser.parse(File(assetsDir, ArabicFontFamily.TAJAWAL.assetPathForWeight(300)).readBytes())!!
        assertEquals(300, tajawalLight.weightClass)

        val cairo = SfntFontParser.parse(File(assetsDir, ArabicFontFamily.CAIRO.assetPath).readBytes())!!
        assertTrue(cairo.isVariable)
        assertTrue(200 in cairo.supportedWeights && 900 in cairo.supportedWeights)

        val alex = SfntFontParser.parse(File(assetsDir, ArabicFontFamily.ALEXANDRIA.assetPath).readBytes())!!
        assertTrue(alex.isVariable)
        assertTrue(200 in alex.supportedWeights && 900 in alex.supportedWeights)
        assertTrue(alex.supportsTatweel)

        val readex = SfntFontParser.parse(File(assetsDir, ArabicFontFamily.READEX_PRO.assetPath).readBytes())!!
        assertTrue(readex.isVariable)
        assertTrue(200 in readex.supportedWeights && 700 in readex.supportedWeights)
    }

    @Test
    fun sfntParserParsesCffOtfAndInfersSubfamilyWeightAndArabicCoverage() {
        val syntheticOtf = buildMinimalSfnt(
            sfntTag = 0x4F54544F, // OTTO (CFF OTF)
            family = "GE SS Two",
            subfamily = "Medium",
            os2Weight = 400, // Some repacked fonts keep 400 in OS/2 despite Medium subfamily
            codepoints = intArrayOf(0x0627, 0x0628, 0x0640, 0x0644, 0x0645),
            gsubTags = listOf("ss01", "dlig")
        )
        val meta = SfntFontParser.parse(syntheticOtf)
        assertNotNull(meta)
        assertEquals("OTF", meta!!.format)
        assertEquals("otf", meta.extension)
        assertEquals("GE SS Two", meta.familyName)
        assertEquals(500, meta.weightClass)
        assertTrue(meta.supportsArabic)
        assertTrue(meta.supportsTatweel)
        assertTrue("ss01" in meta.openTypeFeatures)
        assertTrue("dlig" in meta.openTypeFeatures)

        assertNull("Invalid bytes must be rejected", SfntFontParser.parse("<html>not a font</html>".toByteArray()))
    }

    @Test
    fun weightResolutionAndCustomFontVariantsWorkWithoutFakeBoldOnRealWeights() {
        assertEquals(400, TextLayer().fontWeight)

        assertTrue(ArabicFontFamily.CAIRO.hasRealWeight(700))
        assertTrue(ArabicFontFamily.TAJAWAL.hasRealWeight(700))
        assertTrue(ArabicFontFamily.ALEXANDRIA.hasRealWeight(800))
        assertFalse(ArabicFontFamily.LALEZAR.hasRealWeight(700))

        val dinFamily = CustomFont(
            id = "DINNextLTArabic-Regular_1234.ttf",
            name = "DIN Next LT Arabic",
            filePath = "/tmp/DINNextLTArabic-Regular_1234.ttf",
            weightClass = 400,
            supportedWeights = listOf(300, 400, 700),
            weightVariants = mapOf(
                300 to "/tmp/DINNextLTArabic-Light_1111.ttf",
                400 to "/tmp/DINNextLTArabic-Regular_1234.ttf",
                700 to "/tmp/DINNextLTArabic-Bold_9999.ttf"
            )
        )
        assertEquals("/tmp/DINNextLTArabic-Bold_9999.ttf", dinFamily.resolvePathForWeight(700))
        assertEquals("/tmp/DINNextLTArabic-Light_1111.ttf", dinFamily.resolvePathForWeight(300))
        assertEquals(700, dinFamily.resolveWeight(600))
    }

    @Test
    fun svgExporterEmitsCssFamilyNamesWeightsAndCustomFontName() {
        val state = CanvasState(
            layers = listOf(
                TextLayer(
                    id = "l1",
                    rawText = "أثر\nتصميم",
                    displayText = "أثر\nتصميم",
                    selectedFont = ArabicFontFamily.ALEXANDRIA,
                    fontWeight = 300,
                    gradientPreset = LuxuryGradientPreset.ANDALUSIAN_GOLD
                ),
                TextLayer(
                    id = "l2",
                    rawText = "عنوان",
                    displayText = "عنوان",
                    selectedFont = ArabicFontFamily.CAIRO,
                    customFontPath = "/data/custom_fonts/DINNextLTArabic-Bold_abcd1234.ttf",
                    customFontName = "DIN Next LT Arabic",
                    fontWeight = 700,
                    isHollowText = true
                )
            )
        )
        val svg = AtharSvgExporter.generateSvgString(state)
        assertTrue(svg.contains("""font-family="Alexandria, sans-serif""""))
        assertTrue(svg.contains("""font-weight="300""""))
        assertTrue(svg.contains("<tspan"))
        assertTrue(svg.contains("""<linearGradient id="grad_l1""""))
        assertTrue(svg.contains("""fill="url(#grad_l1)""""))
        assertTrue(svg.contains("""font-family="DIN Next LT Arabic, sans-serif""""))
        assertTrue(svg.contains("""font-weight="700""""))
        assertTrue(svg.contains("""fill="none""""))
    }

    @Test
    fun paragraphJustifierSkipsZeroWidthTatweelAndHandlesNarrowTatweel() {
        val input = "أثر\nتصميم عربي أصيل"
        val untouched = ArabicParagraphJustifier.justifyLines(
            text = input,
            measureText = { s -> if (s == "ـ") 0f else s.length * 10f }
        )
        assertEquals(input, untouched)

        val justifiedNarrow = ArabicParagraphJustifier.justifyLines(
            text = input,
            measureText = { s ->
                var w = 0f
                for (ch in s) w += if (ch == 'ـ') 2f else 16f
                w
            }
        )
        val lines = justifiedNarrow.split("\n")
        assertTrue(lines[0].count { it == 'ـ' } > 10)
    }

    @Test
    fun textLayoutCacheWorksSafelyWithNullPaint() {
        AtharTextLayout.clearCache()
        val r1 = AtharTextLayout.computeLayout("أثر", measureText = { it.length * 10f })
        val r2 = AtharTextLayout.computeLayout("أثر", measureText = { it.length * 10f })
        assertSame(r1, r2)
    }

    private fun buildMinimalSfnt(
        sfntTag: Int,
        family: String,
        subfamily: String,
        os2Weight: Int,
        codepoints: IntArray,
        gsubTags: List<String>
    ): ByteArray {
        val head = ByteArray(54)
        val os2 = ByteArray(78).also {
            ByteBuffer.wrap(it).order(ByteOrder.BIG_ENDIAN).putShort(4, os2Weight.toShort())
        }
        val nameEntries = listOf(1 to family, 2 to subfamily, 4 to "$family $subfamily")
        val strBuf = ByteArrayOutputStream()
        val nameOut = ByteArrayOutputStream()
        val hdr = ByteBuffer.allocate(6 + nameEntries.size * 12).order(ByteOrder.BIG_ENDIAN)
        hdr.putShort(0)
        hdr.putShort(nameEntries.size.toShort())
        hdr.putShort((6 + nameEntries.size * 12).toShort())
        for ((nid, text) in nameEntries) {
            val encoded = text.toByteArray(StandardCharsets.UTF_16BE)
            val offset = strBuf.size()
            strBuf.write(encoded)
            hdr.putShort(3); hdr.putShort(1); hdr.putShort(0x0409)
            hdr.putShort(nid.toShort()); hdr.putShort(encoded.size.toShort()); hdr.putShort(offset.toShort())
        }
        nameOut.write(hdr.array())
        nameOut.write(strBuf.toByteArray())
        val nameBytes = nameOut.toByteArray()

        // Format 12 cmap
        val sortedCps = codepoints.sorted()
        val cmapBuf = ByteBuffer.allocate(4 + 8 + 16 + sortedCps.size * 12).order(ByteOrder.BIG_ENDIAN)
        cmapBuf.putShort(0); cmapBuf.putShort(1)
        cmapBuf.putShort(3); cmapBuf.putShort(10); cmapBuf.putInt(12)
        cmapBuf.putShort(12); cmapBuf.putShort(0); cmapBuf.putInt(16 + sortedCps.size * 12); cmapBuf.putInt(0); cmapBuf.putInt(sortedCps.size)
        sortedCps.forEachIndexed { idx, cp ->
            cmapBuf.putInt(cp); cmapBuf.putInt(cp); cmapBuf.putInt(idx + 1)
        }
        val cmapBytes = cmapBuf.array()

        val gsubBuf = ByteBuffer.allocate(10 + 2 + gsubTags.size * 6).order(ByteOrder.BIG_ENDIAN)
        gsubBuf.putInt(0x00010000); gsubBuf.putShort(10); gsubBuf.putShort(10); gsubBuf.putShort(10)
        gsubBuf.putShort(gsubTags.size.toShort())
        gsubTags.forEach { tag ->
            gsubBuf.put(tag.toByteArray(StandardCharsets.ISO_8859_1))
            gsubBuf.putShort(0)
        }
        val gsubBytes = gsubBuf.array()

        val tables = linkedMapOf(
            "OS/2" to os2,
            "cmap" to cmapBytes,
            "head" to head,
            "name" to nameBytes,
            "GSUB" to gsubBytes
        )
        val dirSize = 12 + tables.size * 16
        val out = ByteArrayOutputStream()
        val top = ByteBuffer.allocate(dirSize).order(ByteOrder.BIG_ENDIAN)
        top.putInt(sfntTag)
        top.putShort(tables.size.toShort())
        top.putShort(0); top.putShort(0); top.putShort(0)
        var curOffset = dirSize
        val payloads = ByteArrayOutputStream()
        for ((tag, data) in tables) {
            top.put(tag.toByteArray(StandardCharsets.ISO_8859_1))
            top.putInt(0)
            top.putInt(curOffset)
            top.putInt(data.size)
            payloads.write(data)
            curOffset += data.size
        }
        out.write(top.array())
        out.write(payloads.toByteArray())
        return out.toByteArray()
    }
}
