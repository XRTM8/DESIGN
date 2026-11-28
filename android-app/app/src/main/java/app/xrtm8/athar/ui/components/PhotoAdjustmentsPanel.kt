package app.xrtm8.athar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.Vignette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xrtm8.athar.model.CanvasState
import app.xrtm8.athar.model.FlutedGlassState
import app.xrtm8.athar.ui.theme.GlassBorder
import app.xrtm8.athar.ui.theme.GlassSurface
import app.xrtm8.athar.ui.theme.CherryAccent
import app.xrtm8.athar.ui.theme.MutedWhite
import app.xrtm8.athar.ui.theme.PureWhite

@Composable
fun PhotoAdjustmentsPanel(
    state: CanvasState,
    onBlurChange: (Float) -> Unit,
    onFlutedGlassChange: (FlutedGlassState) -> Unit = {},
    onDimChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onVignetteChange: (Float) -> Unit,
    onHighlightsChange: (Float) -> Unit = {},
    onShadowsChange: (Float) -> Unit = {},
    onTemperatureChange: (Float) -> Unit = {},
    onResetAdjustments: () -> Unit,
    modifier: Modifier = Modifier,
    onSliderChangeFinished: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current

    Column(modifier = modifier.fillMaxWidth()) {
        // Blur Slider
        StudioSlider(
            title = "الضبابية",
            value = state.backgroundBlur,
            onValueChange = onBlurChange,
            onValueChangeFinished = onSliderChangeFinished,
            valueRange = 0f..30f,
            step = 1f,
            valueDisplay = "${((state.backgroundBlur / 30f) * 100).toInt()}%",
            icon = Icons.Default.BlurOn
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Fluted / Ribbed Glass Slider + Full Customization
        val fluted = state.flutedGlassState
        var showFlutedDetails by remember { mutableStateOf(fluted.isEnabled) }
        StudioSlider(
            title = "الزجاج المضلّع",
            value = fluted.intensity,
            onValueChange = { next ->
                if (next > 0.005f && !showFlutedDetails) showFlutedDetails = true
                onFlutedGlassChange(fluted.copy(intensity = next))
            },
            onValueChangeFinished = onSliderChangeFinished,
            valueRange = 0f..1f,
            step = 0.02f,
            valueDisplay = "${(fluted.intensity * 100).toInt()}%",
            icon = Icons.Default.ViewColumn
        )

        AnimatedVisibility(visible = fluted.isEnabled && showFlutedDetails) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassSurface)
                    .border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "تخصيص الزجاج المضلّع", color = PureWhite, fontSize = 11.sp)
                    Text(
                        text = "إخفاء",
                        color = MutedWhite,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showFlutedDetails = false }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf(
                        "كلاسيكي" to FlutedGlassState(intensity = 0.65f, ribCount = 28, specular = 0.55f, coverage = 1f, angleDegrees = 0f),
                        "تقسيم نصفي" to FlutedGlassState(intensity = 0.72f, ribCount = 30, specular = 0.60f, coverage = 0.60f, coverageFromStart = true, angleDegrees = 0f),
                        "مائل درامي" to FlutedGlassState(intensity = 0.75f, ribCount = 24, specular = 0.65f, coverage = 1f, angleDegrees = 20f),
                        "منشور زجاجي" to FlutedGlassState(intensity = 0.80f, ribCount = 22, specular = 0.70f, coverage = 1f, chromaticDispersion = 0.65f)
                    )
                    presets.forEach { (label, preset) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(GlassSurface)
                                .border(0.5.dp, GlassBorder, RoundedCornerShape(8.dp))
                                .clickable {
                                    onFlutedGlassChange(preset)
                                    onSliderChangeFinished?.invoke()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(text = label, color = PureWhite, fontSize = 10.sp)
                        }
                    }
                }
                StudioSlider(
                    title = "كثافة الأضلاع",
                    value = fluted.ribCount.toFloat(),
                    onValueChange = { onFlutedGlassChange(fluted.copy(ribCount = it.toInt())) },
                    onValueChangeFinished = onSliderChangeFinished,
                    valueRange = 8f..80f,
                    step = 2f,
                    valueDisplay = "${fluted.ribCount}",
                    icon = Icons.Default.Tune
                )
                StudioSlider(
                    title = "اللمعان الزجاجي",
                    value = fluted.specular,
                    onValueChange = { onFlutedGlassChange(fluted.copy(specular = it)) },
                    onValueChangeFinished = onSliderChangeFinished,
                    valueRange = 0f..1f,
                    step = 0.05f,
                    valueDisplay = "${(fluted.specular * 100).toInt()}%",
                    icon = Icons.Default.AutoAwesome
                )
                StudioSlider(
                    title = "نسبة التغطية",
                    value = fluted.coverage,
                    onValueChange = { onFlutedGlassChange(fluted.copy(coverage = it)) },
                    onValueChangeFinished = onSliderChangeFinished,
                    valueRange = 0.1f..1.0f,
                    step = 0.05f,
                    valueDisplay = "${(fluted.coverage * 100).toInt()}%",
                    icon = Icons.Default.ViewColumn
                )
                if (fluted.coverage < 0.99f) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val leftLabel = "من اليسار"
                        val rightLabel = "من اليمين"
                        listOf(true to leftLabel, false to rightLabel).forEach { (fromStart, label) ->
                            val selected = fluted.coverageFromStart == fromStart
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) CherryAccent.copy(alpha = 0.25f) else GlassSurface)
                                    .border(0.5.dp, if (selected) CherryAccent else GlassBorder, RoundedCornerShape(8.dp))
                                    .clickable {
                                        onFlutedGlassChange(fluted.copy(coverageFromStart = fromStart))
                                        onSliderChangeFinished?.invoke()
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = label, color = PureWhite, fontSize = 11.sp)
                            }
                        }
                    }
                }
                StudioSlider(
                    title = "زاوية الأضلاع",
                    value = fluted.angleDegrees,
                    onValueChange = { onFlutedGlassChange(fluted.copy(angleDegrees = it)) },
                    onValueChangeFinished = onSliderChangeFinished,
                    valueRange = -45f..90f,
                    step = 5f,
                    valueDisplay = "${fluted.angleDegrees.toInt()}°",
                    icon = Icons.AutoMirrored.Filled.RotateRight
                )
                StudioSlider(
                    title = "تشتت لوني (منشور)",
                    value = fluted.chromaticDispersion,
                    onValueChange = { onFlutedGlassChange(fluted.copy(chromaticDispersion = it)) },
                    onValueChangeFinished = onSliderChangeFinished,
                    valueRange = 0f..1f,
                    step = 0.05f,
                    valueDisplay = "${(fluted.chromaticDispersion * 100).toInt()}%",
                    icon = Icons.Default.ColorLens
                )
                if (fluted.coverage < 0.99f) {
                    StudioSlider(
                        title = "نعومة حد التقسيم",
                        value = fluted.edgeSoftness,
                        onValueChange = { onFlutedGlassChange(fluted.copy(edgeSoftness = it)) },
                        onValueChangeFinished = onSliderChangeFinished,
                        valueRange = 0f..1f,
                        step = 0.05f,
                        valueDisplay = "${(fluted.edgeSoftness * 100).toInt()}%",
                        icon = Icons.Default.BlurOn
                    )
                }
            }
        }
        if (fluted.isEnabled && !showFlutedDetails) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "تخصيص الأضلاع والزاوية ▾",
                    color = CherryAccent,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showFlutedDetails = true }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Dim Overlay Slider
        StudioSlider(
            title = "التعتيم",
            value = state.backgroundDim,
            onValueChange = onDimChange,
            onValueChangeFinished = onSliderChangeFinished,
            valueRange = 0f..0.90f,
            step = 0.05f,
            valueDisplay = "${(state.backgroundDim * 100).toInt()}%",
            icon = Icons.Default.DarkMode
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Brightness Slider
        StudioSlider(
            title = "السطوع",
            value = state.backgroundBrightness,
            onValueChange = onBrightnessChange,
            onValueChangeFinished = onSliderChangeFinished,
            valueRange = -0.5f..0.5f,
            step = 0.05f,
            valueDisplay = if (state.backgroundBrightness > 0f) "+${(state.backgroundBrightness * 100).toInt()}%" else "${(state.backgroundBrightness * 100).toInt()}%",
            icon = Icons.Default.Brightness6
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Contrast Slider
        StudioSlider(
            title = "التباين",
            value = state.backgroundContrast,
            onValueChange = onContrastChange,
            onValueChangeFinished = onSliderChangeFinished,
            valueRange = 0.5f..1.8f,
            step = 0.05f,
            valueDisplay = "${(state.backgroundContrast * 100).toInt()}%",
            icon = Icons.Default.Contrast
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Saturation Slider
        StudioSlider(
            title = "التشبع",
            value = state.backgroundSaturation,
            onValueChange = onSaturationChange,
            onValueChangeFinished = onSliderChangeFinished,
            valueRange = 0f..2.0f,
            step = 0.05f,
            valueDisplay = "${(state.backgroundSaturation * 100).toInt()}%",
            icon = Icons.Default.ColorLens
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Vignette Slider
        StudioSlider(
            title = "تدرج الحواف (Vignette)",
            value = state.backgroundVignette,
            onValueChange = onVignetteChange,
            onValueChangeFinished = onSliderChangeFinished,
            valueRange = 0f..1.0f,
            step = 0.05f,
            valueDisplay = "${(state.backgroundVignette * 100).toInt()}%",
            icon = Icons.Default.Vignette
        )

        Spacer(modifier = Modifier.height(2.dp))

        // PicsArt-style: Highlights (أبرز الأحداث)
        StudioSlider(
            title = "أبرز الأحداث",
            value = state.backgroundHighlights,
            onValueChange = onHighlightsChange,
            onValueChangeFinished = onSliderChangeFinished,
            valueRange = -1f..1f,
            step = 0.05f,
            valueDisplay = if (state.backgroundHighlights > 0f) "+${(state.backgroundHighlights * 100).toInt()}" else "${(state.backgroundHighlights * 100).toInt()}",
            icon = Icons.Default.Brightness6
        )

        Spacer(modifier = Modifier.height(2.dp))

        // PicsArt-style: Shadows (الظلال)
        StudioSlider(
            title = "الظلال",
            value = state.backgroundShadows,
            onValueChange = onShadowsChange,
            onValueChangeFinished = onSliderChangeFinished,
            valueRange = -1f..1f,
            step = 0.05f,
            valueDisplay = if (state.backgroundShadows > 0f) "+${(state.backgroundShadows * 100).toInt()}" else "${(state.backgroundShadows * 100).toInt()}",
            icon = Icons.Default.DarkMode
        )

        Spacer(modifier = Modifier.height(2.dp))

        // PicsArt-style: Temperature (درجة الحرارة)
        StudioSlider(
            title = "درجة الحرارة",
            value = state.backgroundTemperature,
            onValueChange = onTemperatureChange,
            onValueChangeFinished = onSliderChangeFinished,
            valueRange = -1f..1f,
            step = 0.05f,
            valueDisplay = if (state.backgroundTemperature > 0f) "+${(state.backgroundTemperature * 100).toInt()}" else "${(state.backgroundTemperature * 100).toInt()}",
            icon = Icons.Default.ColorLens
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Reset Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onResetAdjustments()
                },
                colors = ButtonDefaults.buttonColors(containerColor = GlassSurface),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, GlassBorder)
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, tint = CherryAccent, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "إعادة ضبط التعديلات", color = PureWhite, fontSize = 11.sp)
            }
        }
    }
}
