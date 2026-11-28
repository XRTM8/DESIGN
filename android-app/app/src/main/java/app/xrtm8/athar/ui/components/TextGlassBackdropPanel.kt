package app.xrtm8.athar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xrtm8.athar.model.CanvasState
import app.xrtm8.athar.model.GLASS_BACKDROP_PRESETS
import app.xrtm8.athar.model.GlassBackdropPreset
import app.xrtm8.athar.ui.dialogs.CustomColorPickerDialog
import app.xrtm8.athar.ui.theme.AmoledWhite
import app.xrtm8.athar.ui.theme.CherryAccent
import app.xrtm8.athar.ui.theme.DarkText
import app.xrtm8.athar.ui.theme.DarkTextMuted
import app.xrtm8.athar.ui.theme.DarkTextSubtle
import app.xrtm8.athar.ui.theme.GlassBorder
import app.xrtm8.athar.ui.theme.GlassSurface
import app.xrtm8.athar.ui.theme.LocalAmoledWhite
import app.xrtm8.athar.ui.theme.MutedWhite
import app.xrtm8.athar.ui.theme.PureWhite
import app.xrtm8.athar.ui.theme.SubtleGray
import app.xrtm8.athar.ui.theme.WhiteBorder
import app.xrtm8.athar.ui.theme.WhiteSurface

private val GLASS_QUICK_COLORS = listOf(
    0xFF0D0F14L,
    0xFF1E293BL,
    0xFFFFFFFFL,
    0xFFE5C07BL,
    0xFFE02447L,
    0xFF10B981L,
    0xFF2563EBL,
    0xFF8B5CF6L,
    0xFFF59E0BL,
    0xFFEC4899L,
    0xFF06B6D4L,
    0xFF000000L
)

@Composable
fun TextGlassBackdropPanel(
    state: CanvasState,
    onToggleGlassBackdrop: (Boolean) -> Unit,
    onSelectPreset: (GlassBackdropPreset) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onCornerRadiusChange: (Float) -> Unit,
    onPaddingChange: (Float) -> Unit,
    onColorChange: (Long) -> Unit = {},
    onBorderColorChange: (Long) -> Unit = {},
    onBorderWidthChange: (Float) -> Unit = {},
    onBorderOpacityChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
    onSliderChangeFinished: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val isAmoledWhite = LocalAmoledWhite.current
    val titleColor = if (isAmoledWhite) DarkText else PureWhite
    val mutedColor = if (isAmoledWhite) DarkTextMuted else MutedWhite
    val subtleColor = if (isAmoledWhite) DarkTextSubtle else SubtleGray
    val surfaceColor = if (isAmoledWhite) WhiteSurface else GlassSurface
    val borderColor = if (isAmoledWhite) WhiteBorder else GlassBorder

    var showBgColorPicker by remember { mutableStateOf(false) }
    var showBorderColorPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Toggle Switch for Frosted Glass Card
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "لوح زجاجي خلف النص",
                    color = titleColor,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "طبقة مصنفرة شبه شفافة تبرز النص فوق الصور",
                    color = subtleColor,
                    fontSize = 10.5.sp
                )
            }
            Switch(
                checked = state.enableGlassBackdrop,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleGlassBackdrop(it)
                },
                colors = SwitchDefaults.colors(checkedThumbColor = AmoledWhite, checkedTrackColor = CherryAccent)
            )
        }

        if (state.enableGlassBackdrop) {
            Spacer(modifier = Modifier.height(10.dp))

            // Presets Horizontal Carousel
            Text(text = "أنماط الزجاج:", color = mutedColor, fontSize = 11.5.sp)
            Spacer(modifier = Modifier.height(5.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GLASS_BACKDROP_PRESETS.forEach { preset ->
                    val isSelected = state.glassBackdropColorLong == preset.bgArgb &&
                        state.glassBackdropBorderColorLong == preset.strokeArgb
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) CherryAccent else surfaceColor)
                            .border(0.5.dp, if (isSelected) CherryAccent else borderColor, RoundedCornerShape(10.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectPreset(preset)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(preset.bgArgb.toInt()))
                                .border(1.dp, Color(preset.strokeArgb.toInt()), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = preset.name,
                            color = if (isSelected) AmoledWhite else titleColor,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Manual Background Color Picker Section
            GlassColorSelectorRow(
                label = "لون خلفية النص",
                selectedColorLong = state.glassBackdropColorLong,
                surfaceColor = surfaceColor,
                borderColor = borderColor,
                labelColor = mutedColor,
                textColor = titleColor,
                onSelectColor = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onColorChange(it)
                },
                onOpenFullPicker = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showBgColorPicker = true
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Background Opacity Slider
            StudioSlider(
                title = "عتامة وشفافية الخلفية",
                value = state.glassBackdropOpacity,
                onValueChange = onOpacityChange,
                onValueChangeFinished = onSliderChangeFinished,
                valueRange = 0.10f..0.98f,
                step = 0.05f,
                valueDisplay = "${(state.glassBackdropOpacity * 100).toInt()}%",
                icon = Icons.Default.Opacity
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Manual Border Color Picker Section
            GlassColorSelectorRow(
                label = "لون إطار خلفية النص",
                selectedColorLong = state.glassBackdropBorderColorLong,
                surfaceColor = surfaceColor,
                borderColor = borderColor,
                labelColor = mutedColor,
                textColor = titleColor,
                onSelectColor = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onBorderColorChange(it)
                },
                onOpenFullPicker = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showBorderColorPicker = true
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Border Opacity Slider
            StudioSlider(
                title = "شفافية الإطار",
                value = state.glassBackdropBorderOpacity,
                onValueChange = onBorderOpacityChange,
                onValueChangeFinished = onSliderChangeFinished,
                valueRange = 0f..1f,
                step = 0.05f,
                valueDisplay = "${(state.glassBackdropBorderOpacity * 100).toInt()}%",
                icon = Icons.Default.BorderColor
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Border Width Slider
            StudioSlider(
                title = "سماكة الإطار",
                value = state.glassBackdropBorderWidth,
                onValueChange = onBorderWidthChange,
                onValueChangeFinished = onSliderChangeFinished,
                valueRange = 0f..6f,
                step = 0.2f,
                valueDisplay = String.format("%.1fdp", state.glassBackdropBorderWidth),
                icon = Icons.Default.LineWeight
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Corner Radius Slider
            StudioSlider(
                title = "استدارة حواف البطاقة",
                value = state.glassBackdropCornerRadius,
                onValueChange = onCornerRadiusChange,
                onValueChangeFinished = onSliderChangeFinished,
                valueRange = 6f..44f,
                step = 2f,
                valueDisplay = "${state.glassBackdropCornerRadius.toInt()}dp",
                icon = Icons.Default.RoundedCorner
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Padding Slider
            StudioSlider(
                title = "اتساع وهوامش البطاقة",
                value = state.glassBackdropPadding,
                onValueChange = onPaddingChange,
                onValueChangeFinished = onSliderChangeFinished,
                valueRange = 10f..48f,
                step = 2f,
                valueDisplay = "${state.glassBackdropPadding.toInt()}dp",
                icon = Icons.Default.CropSquare
            )
        }
    }

    if (showBgColorPicker) {
        CustomColorPickerDialog(
            initialColorLong = state.glassBackdropColorLong,
            title = "اختيار لون خلفية النص",
            onColorSelected = {
                onColorChange(it)
                showBgColorPicker = false
            },
            onDismiss = { showBgColorPicker = false }
        )
    }

    if (showBorderColorPicker) {
        CustomColorPickerDialog(
            initialColorLong = (state.glassBackdropBorderColorLong or 0xFF000000L),
            title = "اختيار لون إطار خلفية النص",
            onColorSelected = {
                onBorderColorChange(it)
                showBorderColorPicker = false
            },
            onDismiss = { showBorderColorPicker = false }
        )
    }
}

@Composable
private fun GlassColorSelectorRow(
    label: String,
    selectedColorLong: Long,
    surfaceColor: Color,
    borderColor: Color,
    labelColor: Color,
    textColor: Color,
    onSelectColor: (Long) -> Unit,
    onOpenFullPicker: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, color = labelColor, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(surfaceColor)
                    .border(0.5.dp, borderColor, RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenFullPicker)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Palette, contentDescription = null, tint = CherryAccent, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "كل الألوان", color = textColor, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color((selectedColorLong.toInt() and 0x00FFFFFF) or (0xFF shl 24)))
                    .border(1.5.dp, CherryAccent, CircleShape)
                    .clickable(onClick = onOpenFullPicker),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ColorLens, contentDescription = null, tint = CherryAccent, modifier = Modifier.size(14.dp))
            }
            GLASS_QUICK_COLORS.forEach { argb ->
                val isSelected = (selectedColorLong and 0x00FFFFFFL) == (argb and 0x00FFFFFFL)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(argb.toInt()))
                        .border(
                            width = if (isSelected) 2.dp else 0.8.dp,
                            color = if (isSelected) CherryAccent else borderColor,
                            shape = CircleShape
                        )
                        .clickable { onSelectColor(argb) }
                )
            }
        }
    }
}
