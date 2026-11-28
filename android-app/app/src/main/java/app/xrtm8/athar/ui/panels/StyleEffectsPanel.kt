package app.xrtm8.athar.ui.panels

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import app.xrtm8.athar.core.AtharPaletteExtractor
import app.xrtm8.athar.model.*
import app.xrtm8.athar.ui.components.TextGlassBackdropPanel
import app.xrtm8.athar.ui.panels.effects.ColorsMaterialsSection
import app.xrtm8.athar.ui.panels.effects.StrokeShadowSection
import app.xrtm8.athar.ui.theme.*

/**
 * Clean Style & Effects Panel Coordinator.
 * Modularized into small, focused sub-units in app.xrtm8.athar.ui.panels.effects.*:
 * - ColorsMaterialsSection (Gradients, Swatches, Blend Modes, Photo Palette)
 * - StrokeShadowSection (Hollow text, Stroke, 2D/3D Shadow)
 * - TextGlassBackdropPanel (Glass plaque properties)
 */
@Composable
fun StyleEffectsPanel(
    state: CanvasState,
    onSelectCustomColor: (Long?) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onStrokeColorChange: (Long) -> Unit,
    onShadowRadiusChange: (Float) -> Unit,
    onShadowDyChange: (Float) -> Unit,
    onShadowDxChange: (Float) -> Unit,
    onShadowColorChange: (Long) -> Unit,
    onToggleGlassBackdrop: (Boolean) -> Unit,
    onSelectGlassPreset: (GlassBackdropPreset) -> Unit,
    onGlassOpacityChange: (Float) -> Unit,
    onGlassCornerRadiusChange: (Float) -> Unit,
    onGlassPaddingChange: (Float) -> Unit,
    onGlassColorChange: (Long) -> Unit = {},
    onGlassBorderColorChange: (Long) -> Unit = {},
    onGlassBorderWidthChange: (Float) -> Unit = {},
    onGlassBorderOpacityChange: (Float) -> Unit = {},
    onBlendModeChange: (LayerBlendMode) -> Unit = {},
    extractedPalette: AtharPaletteExtractor.ExtractedPalette? = null,
    onSelectGradientPreset: ((LuxuryGradientPreset) -> Unit)? = null,
    onGradientAngleChange: ((Float) -> Unit)? = null,
    onToggleRadialGradient: ((Boolean) -> Unit)? = null,
    onCustomGradientChange: ((CustomGradientState) -> Unit)? = null,
    onOuterGlowRadiusChange: ((Float) -> Unit)? = null,
    onOuterGlowColorChange: ((Long) -> Unit)? = null,
    onToggleHollowText: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    onSliderChangeFinished: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val isAmoledWhite = LocalAmoledWhite.current
    val tabBarBg = if (isAmoledWhite) WhiteSurfaceElevated else GlassSurface
    val tabBarBorder = if (isAmoledWhite) WhiteBorder else GlassBorder
    val unselectedText = if (isAmoledWhite) DarkTextMuted else MutedWhite
    var subTab by remember { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Sub-segmented Tab Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(tabBarBg)
                .border(0.5.dp, tabBarBorder, RoundedCornerShape(12.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                0 to ("الألوان والخامات" to Icons.Default.AutoAwesome),
                1 to ("الظل والتحديد" to Icons.Default.Layers),
                2 to ("اللوح الزجاجي" to Icons.Default.FilterFrames)
            ).forEach { (idx, item) ->
                val (title, icon) = item
                val isSelected = subTab == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isSelected) CherryAccent else Color.Transparent)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            subTab = idx
                        }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) PureWhite else unselectedText,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = title,
                            color = if (isSelected) PureWhite else unselectedText,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        AnimatedContent(
            targetState = subTab,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { width -> -width } + fadeOut()
                } else {
                    slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { width -> -width } + fadeIn() togetherWith
                            slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { width -> width } + fadeOut()
                }
            },
            label = "StyleEffectsSubTabs"
        ) { currentSubTab ->
            when (currentSubTab) {
                0 -> ColorsMaterialsSection(
                    state = state,
                    onSelectCustomColor = onSelectCustomColor,
                    onBlendModeChange = onBlendModeChange,
                    extractedPalette = extractedPalette,
                    onSelectGradientPreset = onSelectGradientPreset,
                    onGradientAngleChange = onGradientAngleChange,
                    onToggleRadialGradient = onToggleRadialGradient,
                    onCustomGradientChange = onCustomGradientChange,
                    onSliderChangeFinished = onSliderChangeFinished
                )

                1 -> StrokeShadowSection(
                    strokeWidth = state.strokeWidth,
                    strokeColorLong = state.strokeColorLong,
                    isHollowText = state.isHollowText,
                    shadowRadius = state.shadowRadius,
                    shadowDy = state.shadowDy,
                    shadowDx = state.shadowDx,
                    shadowColorLong = state.shadowColorLong,
                    outerGlowRadius = state.activeLayer.outerGlowRadius,
                    outerGlowColorLong = state.activeLayer.outerGlowColorLong,
                    onStrokeWidthChange = onStrokeWidthChange,
                    onStrokeColorChange = onStrokeColorChange,
                    onToggleHollowText = onToggleHollowText,
                    onShadowRadiusChange = onShadowRadiusChange,
                    onShadowDyChange = onShadowDyChange,
                    onShadowDxChange = onShadowDxChange,
                    onShadowColorChange = onShadowColorChange,
                    onOuterGlowRadiusChange = onOuterGlowRadiusChange,
                    onOuterGlowColorChange = onOuterGlowColorChange,
                    onSliderChangeFinished = onSliderChangeFinished
                )

                else -> TextGlassBackdropPanel(
                    state = state,
                    onToggleGlassBackdrop = onToggleGlassBackdrop,
                    onSelectPreset = onSelectGlassPreset,
                    onOpacityChange = onGlassOpacityChange,
                    onCornerRadiusChange = onGlassCornerRadiusChange,
                    onPaddingChange = onGlassPaddingChange,
                    onColorChange = onGlassColorChange,
                    onBorderColorChange = onGlassBorderColorChange,
                    onBorderWidthChange = onGlassBorderWidthChange,
                    onBorderOpacityChange = onGlassBorderOpacityChange,
                    onSliderChangeFinished = onSliderChangeFinished
                )
            }
        }
    }
}
