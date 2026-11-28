package app.xrtm8.athar.ui.dialogs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.xrtm8.athar.model.ArabicFontFamily
import app.xrtm8.athar.model.AtharFavoritesManager
import app.xrtm8.athar.model.CustomFont
import app.xrtm8.athar.typography.AtharFontManager
import app.xrtm8.athar.ui.theme.*

@Composable
fun FontBrowserDialog(
    selectedFont: ArabicFontFamily,
    customFontPath: String? = null,
    sampleText: String,
    customFonts: List<CustomFont> = emptyList(),
    onSelectFont: (ArabicFontFamily) -> Unit,
    onSelectCustomFont: (CustomFont) -> Unit = {},
    onImportFont: (Uri) -> Unit = {},
    onDeleteCustomFont: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportFont(uri)
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("الكل") }
    var favoriteFonts by remember { mutableStateOf(AtharFavoritesManager.getFavoriteFonts(context)) }
    val categories = remember {
        listOf("الكل", "خطوط مخصصة", "المفضلة", "شركات ومجلات", "يدوي", "كلاسيكي", "رقعة", "كوفي", "نستعليق", "عناوين", "حديث", "تعبيري", "قرآني")
    }

    val groupedCustom = remember(customFonts, searchQuery) {
        customFonts.filter {
            it.name.contains(searchQuery, true) || it.familyName.contains(searchQuery, true)
        }.groupBy { cf ->
            val key = cf.familyName.lowercase().replace(Regex("[\\s_-]+"), "")
            if (key.isBlank() || key == "customfont") cf.id else key
        }.values.toList()
    }
    val filteredFonts = remember(searchQuery, selectedCategory, favoriteFonts) {
        ArabicFontFamily.entries.filter { font ->
            val matchesQ = font.fontName.contains(searchQuery, true) ||
                font.cssFamilyName.contains(searchQuery, true) || font.category.contains(searchQuery, true)
            val matchesC = when (selectedCategory) {
                "الكل" -> true
                "المفضلة" -> favoriteFonts.contains(font.name)
                "خطوط مخصصة" -> false
                else -> font.category == selectedCategory
            }
            matchesQ && matchesC
        }
    }

    val isWhite = LocalAmoledWhite.current
    val primaryText = if (isWhite) DarkText else PureWhite
    val subtleText = if (isWhite) DarkTextSubtle else SubtleGray
    val dialogBg = if (isWhite) AmoledWhite else GlassBackground
    val cardBg = if (isWhite) WhiteSurface else GlassSurface
    val cardBorder = if (isWhite) WhiteBorder else GlassBorder

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier.fillMaxSize().background((if (isWhite) AmoledWhite else AmoledBlack).copy(alpha = 0.85f)).padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.95f).fillMaxHeight(0.85f)
                    .clip(RoundedCornerShape(24.dp)).background(dialogBg)
                    .border(1.dp, if (isWhite) WhiteBorder else GlassBorderActive, RoundedCornerShape(24.dp))
                    .padding(18.dp)
            ) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text("مكتبة الخطوط العربية", color = CherryAccent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("اختر الخط المناسب أو استورد خطوط .ttf / .otf", color = subtleText, fontSize = 11.sp)
                    }
                    IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onDismiss() }) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = primaryText)
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("ابحث عن اسم الخط أو النمط...", color = subtleText, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CherryAccent) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = cardBg, unfocusedContainerColor = cardBg,
                        focusedBorderColor = CherryAccent, unfocusedBorderColor = cardBorder,
                        focusedTextColor = primaryText, unfocusedTextColor = primaryText
                    ),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), Arrangement.spacedBy(6.dp)) {
                    categories.forEach { cat ->
                        val active = selectedCategory == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) CherryAccent else cardBg)
                                .border(0.5.dp, if (active) CherryAccent else cardBorder, RoundedCornerShape(10.dp))
                                .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); selectedCategory = cat }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(cat, color = if (active) PureWhite else primaryText, fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { fontPicker.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-opentype", "application/octet-stream", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = CherryAccent.copy(alpha = 0.10f)),
                    border = BorderStroke(1.dp, CherryAccent)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = CherryAccent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("استيراد خط خارجي (.ttf / .otf)", color = CherryAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selectedCategory == "خطوط مخصصة" || (selectedCategory == "الكل" && groupedCustom.isNotEmpty())) {
                        items(groupedCustom, key = { "fam_${it.first().id}" }) { variants ->
                            val primary = variants.find { it.weightClass == 400 } ?: variants.first()
                            CustomFontItemRow(
                                customFont = primary, isSelected = customFontPath == primary.filePath,
                                sampleText = sampleText, isAmoledWhite = isWhite,
                                familyVariants = variants, selectedCustomPath = customFontPath,
                                onSelect = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelectCustomFont(primary); onDismiss() },
                                onSelectVariant = { chosen -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelectCustomFont(chosen); onDismiss() },
                                onDelete = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onDeleteCustomFont(primary.id) },
                                onDeleteVariant = { id -> haptic.performHapticFeedback(HapticFeedbackType.LongPress); onDeleteCustomFont(id) }
                            )
                        }
                    }
                    if (selectedCategory == "خطوط مخصصة" && groupedCustom.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "لا توجد خطوط مخصصة بعد — اضغط استيراد لإضافة خط .ttf أو .otf",
                                    color = subtleText,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    if (selectedCategory != "خطوط مخصصة") {
                        items(filteredFonts, key = { it.name }) { font ->
                            val composeFont = remember(font) { AtharFontManager.getComposeFontFamily(context, font, font.defaultWeight) }
                            BuiltInFontRow(
                                font = font, composeFont = composeFont,
                                isSelected = selectedFont == font && customFontPath == null,
                                isFav = favoriteFonts.contains(font.name),
                                sampleText = sampleText, isWhite = isWhite,
                                primaryText = primaryText, subtleText = subtleText,
                                cardBg = cardBg, cardBorder = cardBorder,
                                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelectFont(font); onDismiss() },
                                onToggleFav = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    AtharFavoritesManager.toggleFontFavorite(context, font.name)
                                    favoriteFonts = AtharFavoritesManager.getFavoriteFonts(context)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuiltInFontRow(
    font: ArabicFontFamily, composeFont: FontFamily, isSelected: Boolean, isFav: Boolean,
    sampleText: String, isWhite: Boolean, primaryText: Color, subtleText: Color,
    cardBg: Color, cardBorder: Color, onClick: () -> Unit, onToggleFav: () -> Unit
) {
    val badgeBg = if (isWhite) WhiteSurfaceElevated else AmoledBlack
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) CherryAccent.copy(alpha = 0.16f) else cardBg)
            .border(if (isSelected) 1.5.dp else 0.5.dp, if (isSelected) CherryAccent else cardBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(font.fontName, color = if (isSelected) CherryAccent else primaryText, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(badgeBg).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(font.category, color = subtleText, fontSize = 9.5.sp)
                    }
                    if (font.supportedWeights.size > 1) {
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(badgeBg).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("${font.supportedWeights.size} أوزان", color = CherryAccent, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (!font.supportsTatweel) {
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(badgeBg).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("بدون مدّ", color = Color(0xFFF59E0B), fontSize = 9.5.sp)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = sampleText.take(28).ifBlank { font.specimen },
                    fontFamily = composeFont,
                    color = if (isSelected) primaryText else (if (isWhite) DarkTextMuted else MutedWhite),
                    fontSize = 15.5.sp, maxLines = 1
                )
            }
            IconButton(onClick = onToggleFav) {
                Icon(if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "مفضلة", tint = if (isFav) Color(0xFFFF4081) else SubtleGray, modifier = Modifier.size(20.dp))
            }
        }
    }
}
