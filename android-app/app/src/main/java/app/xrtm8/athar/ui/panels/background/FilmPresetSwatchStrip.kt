package app.xrtm8.athar.ui.panels.background

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xrtm8.athar.core.FilmPresetEngine
import app.xrtm8.athar.model.AtharFavoritesManager
import app.xrtm8.athar.model.FilmCategory
import app.xrtm8.athar.model.FilmPreset
import app.xrtm8.athar.ui.components.StudioSlider
import app.xrtm8.athar.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Curated Film & Camera Presets Strip with live cached 64px swatches, category filtering,
 * favorites support, and intensity control (< 210 lines).
 */
@Composable
fun FilmPresetSwatchStrip(
    selectedPreset: FilmPreset,
    filmPresetIntensity: Float,
    previewThumbnailBitmap: Bitmap?,
    onSelectFilmPreset: (FilmPreset) -> Unit,
    onFilmPresetIntensityChange: (Float) -> Unit,
    onSliderChangeFinished: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var selectedCategory by remember { mutableStateOf("الكل") }
    var favoriteFilters by remember { mutableStateOf(AtharFavoritesManager.getFavoriteFilters(context)) }

    val swatches by produceState<Map<FilmPreset, ImageBitmap>>(
        initialValue = emptyMap(),
        key1 = previewThumbnailBitmap
    ) {
        value = withContext(Dispatchers.Default) {
            FilmPreset.entries.associateWith { preset ->
                FilmPresetEngine.generatePresetSwatch(previewThumbnailBitmap, preset, sizePx = 64).asImageBitmap()
            }
        }
    }

    val categories = listOf(
        "الكل",
        "المفضلة",
        FilmCategory.PICS_STYLE.labelAr,
        FilmCategory.CLASSIC_CAMERAS.labelAr,
        FilmCategory.INSTANT_VIDEO.labelAr,
        FilmCategory.MONOCHROME.labelAr,
        FilmCategory.CINEMA.labelAr
    )

    val filteredPresets = FilmPreset.entries.filter { preset ->
        when (selectedCategory) {
            "الكل" -> true
            "المفضلة" -> favoriteFilters.contains(preset.id)
            FilmCategory.PICS_STYLE.labelAr -> preset.category == FilmCategory.PICS_STYLE
            FilmCategory.CLASSIC_CAMERAS.labelAr -> preset.category == FilmCategory.CLASSIC_CAMERAS
            FilmCategory.INSTANT_VIDEO.labelAr -> preset.category == FilmCategory.INSTANT_VIDEO
            FilmCategory.MONOCHROME.labelAr -> preset.category == FilmCategory.MONOCHROME
            FilmCategory.CINEMA.labelAr -> preset.category == FilmCategory.CINEMA
            else -> true
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { cat ->
                val isSelected = selectedCategory == cat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isSelected) BurgundyAccent else StudioSurface)
                        .border(0.5.dp, if (isSelected) CherryAccent else GlassBorder, RoundedCornerShape(9.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedCategory = cat
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) PureWhite else MutedWhite,
                        fontSize = 10.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Camera & Film Presets Horizontal Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filteredPresets.forEach { preset ->
                val isSelected = selectedPreset == preset
                val isFav = favoriteFilters.contains(preset.id)
                val swatch = swatches[preset]

                Column(
                    modifier = Modifier
                        .width(96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) StudioSurfaceElevated else StudioSurface)
                        .border(
                            width = if (isSelected) 1.5.dp else 0.5.dp,
                            color = if (isSelected) CherryAccent else GlassBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectFilmPreset(preset)
                        }
                        .padding(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(StudioBackground)
                    ) {
                        if (swatch != null) {
                            Image(
                                bitmap = swatch,
                                contentDescription = preset.titleAr,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        // Brand badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(3.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(AmoledBlack.copy(alpha = 0.65f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = preset.brand,
                                color = PureWhite,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                        if (preset != FilmPreset.NONE) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        AtharFavoritesManager.toggleFilterFavorite(context, preset.id)
                                        favoriteFilters = AtharFavoritesManager.getFavoriteFilters(context)
                                    }
                            ) {
                                Icon(
                                    imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "مفضلة",
                                    tint = if (isFav) Color(0xFFFF4081) else PureWhite.copy(alpha = 0.78f),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    Text(
                        text = preset.titleAr,
                        color = if (isSelected) PureWhite else MutedWhite,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (selectedPreset != FilmPreset.NONE) {
            Spacer(modifier = Modifier.height(8.dp))
            StudioSlider(
                title = "كثافة الفلتر (تقوية وتخفيف)",
                value = filmPresetIntensity.coerceIn(0f, 1.5f) * 100f,
                valueRange = 0f..150f,
                step = 5f,
                valueDisplay = "${(filmPresetIntensity.coerceIn(0f, 1.5f) * 100f).toInt()}%",
                onValueChange = { onFilmPresetIntensityChange((it / 100f).coerceIn(0f, 1.5f)) },
                onValueChangeFinished = onSliderChangeFinished
            )
        }
    }
}
