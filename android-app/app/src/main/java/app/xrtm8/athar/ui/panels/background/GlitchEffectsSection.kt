package app.xrtm8.athar.ui.panels.background

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xrtm8.athar.model.GlitchEffectState
import app.xrtm8.athar.ui.components.StudioSlider
import app.xrtm8.athar.ui.theme.*

/**
 * Analog Glitch, RGB Split & CRT Control Section (< 180 lines).
 */
@Composable
fun GlitchEffectsSection(
    state: GlitchEffectState,
    onRgbSplitChange: (Float) -> Unit,
    onSliceJitterChange: (Float) -> Unit,
    onScanlinesChange: (Float) -> Unit,
    onNoiseChange: (Float) -> Unit,
    onPixelateChange: (Float) -> Unit,
    onRandomizeSeed: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    onSliderChangeFinished: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val isAmoledWhite = LocalAmoledWhite.current
    val cardBg = if (isAmoledWhite) WhiteSurface else StudioSurface
    val cardElevated = if (isAmoledWhite) WhiteSurfaceElevated else StudioSurfaceElevated
    val cardBorder = if (isAmoledWhite) WhiteBorder else GlassBorder
    val titleText = if (isAmoledWhite) DarkText else PureWhite

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(0.5.dp, cardBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "الخلل التناظري والرقمي (Glitch & CRT)",
                color = titleText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            if (state.isEnabled) {
                Text(
                    text = "إعادة تعيين",
                    color = CherryAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onReset()
                        onSliderChangeFinished?.invoke()
                    }
                )
            }
        }

        // 1. RGB Split Slider
        StudioSlider(
            title = "انحراف القنوات (RGB Split)",
            value = state.rgbSplit,
            valueRange = 0f..100f,
            valueDisplay = "${state.rgbSplit.toInt()}",
            onValueChange = onRgbSplitChange,
            onValueChangeFinished = onSliderChangeFinished
        )

        // 2. Slice Tearing Slider
        StudioSlider(
            title = "تمزق الشرائح (Slice Tearing)",
            value = state.sliceJitter,
            valueRange = 0f..100f,
            valueDisplay = "${state.sliceJitter.toInt()}",
            onValueChange = onSliceJitterChange,
            onValueChangeFinished = onSliderChangeFinished
        )

        // 3. Scanlines Slider
        StudioSlider(
            title = "خطوط المسح (CRT Scanlines)",
            value = state.scanlines,
            valueRange = 0f..100f,
            valueDisplay = "${state.scanlines.toInt()}",
            onValueChange = onScanlinesChange,
            onValueChangeFinished = onSliderChangeFinished
        )

        // 4. Static Noise Slider
        StudioSlider(
            title = "الشوشرة الرقمية (Noise Static)",
            value = state.noise,
            valueRange = 0f..100f,
            valueDisplay = "${state.noise.toInt()}",
            onValueChange = onNoiseChange,
            onValueChangeFinished = onSliderChangeFinished
        )

        // 5. Pixelate Slider
        StudioSlider(
            title = "التقطيع البكسلي (Pixelate)",
            value = state.pixelate,
            valueRange = 0f..100f,
            valueDisplay = "${state.pixelate.toInt()}",
            onValueChange = onPixelateChange,
            onValueChangeFinished = onSliderChangeFinished
        )

        // 6. Pattern Randomizer Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(cardElevated)
                .border(0.5.dp, cardBorder, RoundedCornerShape(9.dp))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onRandomizeSeed()
                    onSliderChangeFinished?.invoke()
                }
                .padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Casino,
                contentDescription = null,
                tint = CherryAccent,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "توليد نمط تمزق جديد (Randomize Pattern)",
                color = titleText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
