package app.xrtm8.athar.ui.panels.background

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xrtm8.athar.model.CinematicGlowState
import app.xrtm8.athar.model.FilmGrainState
import app.xrtm8.athar.model.GlitchEffectState
import app.xrtm8.athar.model.GlowStyle
import app.xrtm8.athar.ui.components.StudioSlider
import app.xrtm8.athar.ui.theme.*

/**
 * Lightroom-Grade Cinematic & Glitch Effects Panel (< 250 lines):
 * - Glow, Motion Light Streaks, Halation, Warmth.
 * - Organic Silver Halide Film Grain (Grain, Size, Roughness).
 * - Analog Glitch, RGB Split, CRT Scanlines & Digital Noise.
 */
@Composable
fun CinematicEffectsPanel(
    glowState: CinematicGlowState,
    grainState: FilmGrainState,
    glitchState: GlitchEffectState = GlitchEffectState(),
    onGlowChange: (Float) -> Unit,
    onRangeChange: (Float) -> Unit,
    onSpreadChange: (Float) -> Unit,
    onWarmthChange: (Float) -> Unit,
    onStyleChange: (GlowStyle) -> Unit,
    onResetGlow: () -> Unit,
    onGrainChange: (Float) -> Unit,
    onGrainSizeChange: (Float) -> Unit,
    onGrainRoughnessChange: (Float) -> Unit,
    onResetGrain: () -> Unit,
    onGlitchRgbSplitChange: (Float) -> Unit = {},
    onGlitchSliceJitterChange: (Float) -> Unit = {},
    onGlitchScanlinesChange: (Float) -> Unit = {},
    onGlitchNoiseChange: (Float) -> Unit = {},
    onGlitchPixelateChange: (Float) -> Unit = {},
    onRandomizeGlitchSeed: () -> Unit = {},
    onResetGlitch: () -> Unit = {},
    modifier: Modifier = Modifier,
    onSliderChangeFinished: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val isAmoledWhite = LocalAmoledWhite.current
    val cardBg = if (isAmoledWhite) WhiteSurface else StudioSurface
    val cardElevated = if (isAmoledWhite) WhiteSurfaceElevated else StudioSurfaceElevated
    val cardBorder = if (isAmoledWhite) WhiteBorder else GlassBorder
    val titleText = if (isAmoledWhite) DarkText else PureWhite
    val mutedText = if (isAmoledWhite) DarkTextMuted else MutedWhite
    val subtleText = if (isAmoledWhite) DarkTextSubtle else SubtleGray

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Section 1: Optical Glow & Motion Streaks
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(cardBg)
                .border(0.5.dp, cardBorder, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "التوهج والسحب الضوئي (Glow & Motion)",
                    color = titleText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                if (glowState.isEnabled) {
                    Text(
                        text = "إعادة تعيين",
                        color = CherryAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onResetGlow()
                        }
                    )
                }
            }

            // Style selector chips (Bloom, Diffusion, Halation, Motion, Streak)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GlowStyle.values().forEach { style ->
                    val isSelected = glowState.style == style
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isSelected) BurgundyAccent else cardElevated)
                            .border(0.5.dp, if (isSelected) CherryAccent else cardBorder, RoundedCornerShape(9.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onStyleChange(style)
                            }
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = style.label,
                            color = if (isSelected) PureWhite else mutedText,
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Text(
                text = glowState.style.description,
                color = subtleText,
                fontSize = 10.sp
            )

            StudioSlider(
                title = "قوة التوهج (Glow)",
                value = glowState.glow,
                valueRange = 0f..100f,
                valueDisplay = "${glowState.glow.toInt()}",
                onValueChange = onGlowChange,
                onValueChangeFinished = onSliderChangeFinished
            )

            if (glowState.isEnabled) {
                StudioSlider(
                    title = "نطاق الإضاءة (Range)",
                    value = glowState.range,
                    valueRange = 10f..100f,
                    valueDisplay = "${glowState.range.toInt()}",
                    onValueChange = onRangeChange,
                    onValueChangeFinished = onSliderChangeFinished
                )

                StudioSlider(
                    title = "امتداد السحب (Spread)",
                    value = glowState.spread,
                    valueRange = 10f..100f,
                    valueDisplay = "${glowState.spread.toInt()}",
                    onValueChange = onSpreadChange,
                    onValueChangeFinished = onSliderChangeFinished
                )

                StudioSlider(
                    title = "دفء الضوء الذهبي (Warmth)",
                    value = glowState.warmth,
                    valueRange = 0f..100f,
                    valueDisplay = "${glowState.warmth.toInt()}",
                    onValueChange = onWarmthChange,
                    onValueChangeFinished = onSliderChangeFinished
                )
            }
        }

        // Section 2: Organic Analog Film Grain
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(cardBg)
                .border(0.5.dp, cardBorder, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "حبيبات الفيلم (Film Grain)",
                    color = titleText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                if (grainState.isEnabled) {
                    Text(
                        text = "إعادة تعيين",
                        color = CherryAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onResetGrain()
                        }
                    )
                }
            }

            StudioSlider(
                title = "كثافة الحبيبات (Grain)",
                value = grainState.grain,
                valueRange = 0f..100f,
                valueDisplay = "${grainState.grain.toInt()}",
                onValueChange = onGrainChange,
                onValueChangeFinished = onSliderChangeFinished
            )

            if (grainState.isEnabled) {
                StudioSlider(
                    title = "حجم الحبيبات (Size)",
                    value = grainState.size,
                    valueRange = 10f..100f,
                    valueDisplay = "${grainState.size.toInt()}",
                    onValueChange = onGrainSizeChange,
                    onValueChangeFinished = onSliderChangeFinished
                )

                StudioSlider(
                    title = "خشونة وتنوع الحبيبات (Roughness)",
                    value = grainState.roughness,
                    valueRange = 10f..100f,
                    valueDisplay = "${grainState.roughness.toInt()}",
                    onValueChange = onGrainRoughnessChange,
                    onValueChangeFinished = onSliderChangeFinished
                )
            }
        }

        // Section 3: Analog Glitch, RGB Split & CRT
        GlitchEffectsSection(
            state = glitchState,
            onRgbSplitChange = onGlitchRgbSplitChange,
            onSliceJitterChange = onGlitchSliceJitterChange,
            onScanlinesChange = onGlitchScanlinesChange,
            onNoiseChange = onGlitchNoiseChange,
            onPixelateChange = onGlitchPixelateChange,
            onRandomizeSeed = onRandomizeGlitchSeed,
            onReset = onResetGlitch,
            onSliderChangeFinished = onSliderChangeFinished
        )
    }
}
