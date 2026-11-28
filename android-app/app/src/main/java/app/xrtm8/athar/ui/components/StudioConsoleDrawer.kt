package app.xrtm8.athar.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xrtm8.athar.model.CanvasState
import app.xrtm8.athar.ui.AtharStudioViewModel
import app.xrtm8.athar.ui.panels.CanvasBackgroundPanel
import app.xrtm8.athar.ui.panels.StyleEffectsPanel
import app.xrtm8.athar.ui.panels.TransformPositionPanel
import app.xrtm8.athar.ui.panels.TypographyPanel
import app.xrtm8.athar.ui.theme.CherryAccent
import app.xrtm8.athar.ui.theme.DarkText
import app.xrtm8.athar.ui.theme.DarkTextSubtle
import app.xrtm8.athar.ui.theme.GlassBackground
import app.xrtm8.athar.ui.theme.GlassBackgroundWhite
import app.xrtm8.athar.ui.theme.GlassBorder
import app.xrtm8.athar.ui.theme.LocalAmoledWhite
import app.xrtm8.athar.ui.theme.PureWhite
import app.xrtm8.athar.ui.theme.SubtleGray
import app.xrtm8.athar.ui.theme.WhiteBorder

@Composable
fun StudioConsoleDrawer(
    selectedTab: Int,
    isPanelExpanded: Boolean,
    canvasState: CanvasState,
    viewModel: AtharStudioViewModel,
    onClosePanel: () -> Unit,
    onOpenTextEditor: () -> Unit,
    onOpenFontBrowser: () -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onPickCollagePhoto: (String) -> Unit = {},
    onPickCustomMask: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val isAmoledWhite = LocalAmoledWhite.current
    val drawerBg = if (isAmoledWhite) GlassBackgroundWhite else GlassBackground
    val drawerBorder = if (isAmoledWhite) WhiteBorder else GlassBorder
    val titleColor = if (isAmoledWhite) DarkText else PureWhite
    val closeTint = if (isAmoledWhite) DarkTextSubtle else SubtleGray
    val extractedPalette by viewModel.extractedPalette.collectAsState()
    val customFonts by viewModel.customFonts.collectAsState()

    AnimatedVisibility(
        visible = isPanelExpanded,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(drawerBg)
                .border(width = 0.5.dp, color = drawerBorder, shape = RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 295.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Panel Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val currentTabItem = STUDIO_DOCK_TABS.getOrNull(selectedTab)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (currentTabItem != null) {
                            Icon(
                                imageVector = currentTabItem.icon,
                                contentDescription = null,
                                tint = CherryAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = currentTabItem.title,
                                color = titleColor,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClosePanel()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "إغلاق",
                            tint = closeTint
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "StudioConsoleTabs"
                ) { currentTab ->
                    when (currentTab) {
                        0 -> TypographyPanel(
                            state = canvasState,
                            onOpenTextEditor = onOpenTextEditor,
                            onOpenFontBrowser = onOpenFontBrowser,
                            onSelectFont = { viewModel.setFont(it) },
                            customFonts = customFonts,
                            onSelectCustomFont = { viewModel.setCustomFont(it) },
                            onFontWeightChange = { viewModel.setFontWeight(it) },
                            onFontSizeChange = { viewModel.setFontSize(it) },
                            onJustifyLines = { viewModel.justifyActiveLayerLines(context) },
                            onToggleBold = { viewModel.toggleBold() },
                            onToggleItalic = { viewModel.toggleItalic() },
                            onToggleUnderline = { viewModel.toggleUnderline() },
                            onOpacityChange = { viewModel.setTextOpacity(it) },
                            onLetterSpacingChange = { viewModel.setLetterSpacing(it) },
                            onLineSpacingChange = { viewModel.setLineSpacing(it) },
                            onTextAlignChange = { viewModel.setTextAlign(it) },
                            onBoxWidthRatioChange = { viewModel.setBoxWidthRatio(it) },
                            onStretchLevelChange = { viewModel.setStretchLevel(it) },
                            onKashidaModeChange = { viewModel.setKashidaMode(it) },
                            onTargetIndexChange = { viewModel.setKashidaTargetIndex(it) },
                            onToggleCurvedText = { viewModel.toggleCurvedText(it) },
                            onCurveAngleChange = { viewModel.setCurveAngle(it) },
                            onAutoBalanceBlock = { viewModel.autoBalanceTypographicBlock(context) },
                            onStylisticSetChange = { viewModel.setStylisticSet(it) },
                            onToggleSwash = { viewModel.toggleSwash(it) },
                            onToggleDiscretionaryLigatures = { viewModel.toggleDiscretionaryLigatures(it) },
                            onWordSpacingChange = { viewModel.setWordSpacing(it) },
                            onSliderChangeFinished = { viewModel.commitHistoryCheckpoint() }
                        )

                        1 -> StyleEffectsPanel(
                            state = canvasState,
                            onSelectCustomColor = { viewModel.setCustomColor(it) },
                            onStrokeWidthChange = { viewModel.setStrokeWidth(it) },
                            onStrokeColorChange = { viewModel.setStrokeColor(it) },
                            onShadowRadiusChange = { viewModel.setShadowRadius(it) },
                            onShadowDyChange = { viewModel.setShadowDy(it) },
                            onShadowDxChange = { viewModel.setShadowDx(it) },
                            onShadowColorChange = { viewModel.setShadowColor(it) },
                            onToggleGlassBackdrop = { viewModel.toggleGlassBackdrop(it) },
                            onSelectGlassPreset = { viewModel.setGlassBackdropPreset(it) },
                            onGlassOpacityChange = { viewModel.setGlassBackdropOpacity(it) },
                            onGlassCornerRadiusChange = { viewModel.setGlassBackdropCornerRadius(it) },
                            onGlassPaddingChange = { viewModel.setGlassBackdropPadding(it) },
                            onGlassColorChange = { viewModel.setGlassBackdropColor(it) },
                            onGlassBorderColorChange = { viewModel.setGlassBackdropBorderColor(it) },
                            onGlassBorderWidthChange = { viewModel.setGlassBackdropBorderWidth(it) },
                            onGlassBorderOpacityChange = { viewModel.setGlassBackdropBorderOpacity(it) },
                            onBlendModeChange = { viewModel.setBlendMode(it) },
                            extractedPalette = extractedPalette,
                            onSelectGradientPreset = { viewModel.setLuxuryGradient(it) },
                            onGradientAngleChange = { viewModel.setGradientAngle(it) },
                            onToggleRadialGradient = { viewModel.setGradientRadial(it) },
                            onCustomGradientChange = { viewModel.setCustomGradient(it) },
                            onOuterGlowRadiusChange = { viewModel.setOuterGlowRadius(it) },
                            onOuterGlowColorChange = { viewModel.setOuterGlowColor(it) },
                            onToggleHollowText = { viewModel.setHollowText(it) },
                            onSliderChangeFinished = { viewModel.commitHistoryCheckpoint() }
                        )

                        2 -> CanvasBackgroundPanel(
                            state = canvasState,
                            viewModel = viewModel,
                            onPickImage = onPickImage,
                            onRemoveImage = onRemoveImage,
                            onPickCollagePhoto = onPickCollagePhoto,
                            onPickCustomMask = onPickCustomMask
                        )

                        else -> TransformPositionPanel(
                            state = canvasState,
                            onSnapPosition = { x, y, a -> viewModel.snapToPosition(x, y, a) },
                            onNudgeOffset = { dx, dy -> viewModel.nudgeOffset(dx, dy) },
                            onRotationChange = { viewModel.setRotationAngle(it) },
                            onScaleChange = { viewModel.setScale(it) },
                            onResetTransform = { viewModel.resetCanvasTransform() },
                            onSliderChangeFinished = { viewModel.commitHistoryCheckpoint() }
                        )
                    }
                }
            }
        }
    }
}
