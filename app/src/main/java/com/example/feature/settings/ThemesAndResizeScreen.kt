package com.example.feature.settings

import android.content.Intent
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.preferences.ErgonomicMode
import com.example.core.preferences.KeyboardThemePreset
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemesAndResizeScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Non-persistable URI still works in-session
            }
            viewModel.updateCustomTheme { it.copy(wallpaperUri = uri.toString()) }
        }
    }

    SettingsPageScaffold(title = "Themes, Studio & Resize", onBack = onBack) { widthModifier ->
        Column(
            modifier = widthModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Live Preview
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Interactive Live Preview",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MiniKeyboardPreview(settings = settings)
                }
            }

            // Built-in Themes Gallery
            Text(text = "Built-in Themes", style = MaterialTheme.typography.titleMedium)
            KeyboardThemePreset.entries.forEach { preset ->
                val selected = settings.themePreset == preset
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("theme_preset_${preset.id}")
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { viewModel.selectThemePreset(preset) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = preset.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = preset.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Custom Theme Studio
            Text(text = "Custom Theme Studio", style = MaterialTheme.typography.titleMedium)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("custom_theme_pick_wallpaper")
                        ) {
                            Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gallery Wallpaper")
                        }
                        if (settings.customTheme.wallpaperUri != null) {
                            OutlinedButton(
                                onClick = { viewModel.updateCustomTheme { it.copy(wallpaperUri = null) } },
                                modifier = Modifier.testTag("custom_theme_clear_wallpaper")
                            ) {
                                Text("Clear")
                            }
                        }
                    }

                    ColorSwatchRow(
                        label = "Background Color",
                        selectedColor = settings.customTheme.backgroundColor,
                        swatches = listOf("#1E222B", "#000000", "#0D262B", "#1E1B2E", "#2C1810", "#ECEFF4"),
                        onSelect = { color -> viewModel.updateCustomTheme { it.copy(backgroundColor = color) } }
                    )
                    ColorSwatchRow(
                        label = "Primary Key Fill",
                        selectedColor = settings.customTheme.primaryKeyColor,
                        swatches = listOf("#38404F", "#16181D", "#173D45", "#332E4D", "#4A2E20", "#FFFFFF"),
                        onSelect = { color -> viewModel.updateCustomTheme { it.copy(primaryKeyColor = color) } }
                    )
                    ColorSwatchRow(
                        label = "Accent Color",
                        selectedColor = settings.customTheme.accentColor,
                        swatches = listOf("#38BDF8", "#E6C79C", "#10B981", "#F59E0B", "#EC4899", "#0284C7"),
                        onSelect = { color -> viewModel.updateCustomTheme { it.copy(accentColor = color) } }
                    )

                    SliderSettingRow(
                        title = "Wallpaper Dim",
                        valueLabel = "${(settings.customTheme.wallpaperDim * 100).roundToInt()}%",
                        value = settings.customTheme.wallpaperDim,
                        valueRange = 0f..0.9f,
                        testTag = "slider_wallpaper_dim",
                        onValueChange = { v -> viewModel.updateCustomTheme { it.copy(wallpaperDim = v) } }
                    )
                    SliderSettingRow(
                        title = "Key Opacity",
                        valueLabel = "${(settings.customTheme.keyOpacity * 100).roundToInt()}%",
                        value = settings.customTheme.keyOpacity,
                        valueRange = 0.35f..1f,
                        testTag = "slider_key_opacity",
                        onValueChange = { v -> viewModel.updateCustomTheme { it.copy(keyOpacity = v) } }
                    )
                    SliderSettingRow(
                        title = "Key Corner Radius",
                        valueLabel = "${settings.customTheme.cornerRadiusDp.roundToInt()} dp",
                        value = settings.customTheme.cornerRadiusDp,
                        valueRange = 0f..16f,
                        testTag = "slider_corner_radius",
                        onValueChange = { v -> viewModel.updateCustomTheme { it.copy(cornerRadiusDp = v) } }
                    )
                    SliderSettingRow(
                        title = "Key Label Font Size",
                        valueLabel = "${settings.customTheme.labelFontSizeSp.roundToInt()} sp",
                        value = settings.customTheme.labelFontSizeSp,
                        valueRange = 14f..26f,
                        testTag = "slider_font_size",
                        onValueChange = { v -> viewModel.updateCustomTheme { it.copy(labelFontSizeSp = v) } }
                    )
                    ToggleSettingRow(
                        title = "Show Key Borders",
                        subtitle = "Outline each keycap for tactile contrast",
                        checked = settings.customTheme.showKeyBorders,
                        testTag = "toggle_key_borders",
                        onCheckedChange = { v -> viewModel.updateCustomTheme { it.copy(showKeyBorders = v) } }
                    )
                    ToggleSettingRow(
                        title = "Show Secondary Hints",
                        subtitle = "Display top-right 1-0 and Saraiki long-press hint indicators",
                        checked = settings.customTheme.showSecondaryHints,
                        testTag = "toggle_secondary_hints",
                        onCheckedChange = { v -> viewModel.updateCustomTheme { it.copy(showSecondaryHints = v) } }
                    )
                }
            }

            // Live Size & Ergonomic Mode
            Text(text = "Keyboard Size & Ergonomic Mode", style = MaterialTheme.typography.titleMedium)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    SliderSettingRow(
                        title = "Keyboard Height",
                        valueLabel = "${settings.heightPercent}%",
                        value = settings.heightPercent.toFloat(),
                        valueRange = 60f..130f,
                        testTag = "slider_height_percent",
                        onValueChange = { v -> viewModel.updateSettings { it.copy(heightPercent = v.roundToInt()) } }
                    )
                    SliderSettingRow(
                        title = "Keyboard Width",
                        valueLabel = "${settings.widthPercent}%",
                        value = settings.widthPercent.toFloat(),
                        valueRange = 70f..100f,
                        testTag = "slider_width_percent",
                        onValueChange = { v -> viewModel.updateSettings { it.copy(widthPercent = v.roundToInt()) } }
                    )
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ErgonomicMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.ergonomicMode == mode,
                                onClick = { viewModel.setErgonomicMode(mode) },
                                label = { Text(mode.displayName) },
                                modifier = Modifier.testTag("ergonomic_${mode.id}")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSwatchRow(
    label: String,
    selectedColor: Int,
    swatches: List<String>,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            swatches.forEach { hex ->
                val colorInt = AndroidColor.parseColor(hex)
                val selected = colorInt == selectedColor
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(colorInt))
                        .border(
                            width = if (selected) 2.5.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .clickable { onSelect(colorInt) }
                )
            }
        }
    }
}
