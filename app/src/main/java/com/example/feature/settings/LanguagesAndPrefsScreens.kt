package com.example.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.preferences.KeyboardLanguage
import com.example.ui.theme.NotoNaskhArabicFamily
import kotlin.math.roundToInt

@Composable
fun LanguagesScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsPageScaffold(title = "Languages & Layouts", onBack = onBack) { widthModifier ->
        Column(
            modifier = widthModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Enable and reorder your active keyboard languages. Tap the Globe key on the keyboard to cycle in this exact order.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Active ordered list + disabled languages
            val ordered = settings.enabledLanguages +
                KeyboardLanguage.entries.filter { it !in settings.enabledLanguages }

            ordered.forEach { lang ->
                val isEnabled = lang in settings.enabledLanguages
                val idx = settings.enabledLanguages.indexOf(lang)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${lang.englishName} (${lang.nativeName})",
                                fontFamily = if (lang.isRtl) NotoNaskhArabicFamily else MaterialTheme.typography.titleMedium.fontFamily,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (lang == KeyboardLanguage.SARAIKI) {
                                    "First-class Saraiki Unicode (ٻ ڄ ڳ ڱ ڋ ݨ ݮ) • 4 rows + bottom bar"
                                } else if (lang == KeyboardLanguage.URDU) {
                                    "Phonetic Arabic-script RTL • 4 rows + bottom bar"
                                } else {
                                    "International QWERTY • Western Arabic numerals (1-0)"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isEnabled && settings.enabledLanguages.size > 1) {
                            IconButton(
                                onClick = { viewModel.moveLanguageOrder(lang, moveUp = true) },
                                enabled = idx > 0,
                                modifier = Modifier.testTag("lang_up_${lang.code}")
                            ) {
                                Icon(Icons.Filled.ArrowUpward, contentDescription = "Move ${lang.englishName} up")
                            }
                            IconButton(
                                onClick = { viewModel.moveLanguageOrder(lang, moveUp = false) },
                                enabled = idx < settings.enabledLanguages.lastIndex,
                                modifier = Modifier.testTag("lang_down_${lang.code}")
                            ) {
                                Icon(Icons.Filled.ArrowDownward, contentDescription = "Move ${lang.englishName} down")
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { checked -> viewModel.toggleLanguageEnabled(lang, checked) },
                            modifier = Modifier.testTag("lang_toggle_${lang.code}")
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                ToggleSettingRow(
                    title = "Multilingual Mixed-Script Typing",
                    subtitle = "Automatically rank Saraiki, Urdu & English words in the same sentence without forced transliteration",
                    checked = settings.multilingualTyping,
                    testTag = "toggle_multilingual",
                    onCheckedChange = { v -> viewModel.updateSettings { it.copy(multilingualTyping = v) } }
                )
            }
        }
    }
}

@Composable
fun PreferencesScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsPageScaffold(title = "Preferences", onBack = onBack) { widthModifier ->
        Column(
            modifier = widthModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    ToggleSettingRow(
                        title = "Show Permanent Top Number Row",
                        subtitle = "Optional extra 5th row for 1-0 (OFF by default; long-press top row for 1-0)",
                        checked = settings.showNumberRow,
                        testTag = "toggle_number_row",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(showNumberRow = v) } }
                    )
                    ToggleSettingRow(
                        title = "Keypress Popup Preview",
                        subtitle = "Show magnified character bubble above pressed keys",
                        checked = settings.showKeypressPreview,
                        testTag = "toggle_keypress_preview",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(showKeypressPreview = v) } }
                    )
                    SliderSettingRow(
                        title = "Long-Press Delay",
                        valueLabel = "${settings.longPressDelayMs} ms",
                        value = settings.longPressDelayMs.toFloat(),
                        valueRange = 150f..600f,
                        testTag = "slider_long_press_delay",
                        onValueChange = { v -> viewModel.updateSettings { it.copy(longPressDelayMs = v.roundToInt()) } }
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    ToggleSettingRow(
                        title = "Auto-Capitalization (English)",
                        subtitle = "Capitalize the first letter of each sentence automatically",
                        checked = settings.autoCapitalization,
                        testTag = "toggle_auto_cap",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(autoCapitalization = v) } }
                    )
                    ToggleSettingRow(
                        title = "Double-Space Period",
                        subtitle = "Double-tap Spacebar to insert a period (۔ in Saraiki/Urdu, . in English)",
                        checked = settings.doubleSpacePeriod,
                        testTag = "toggle_double_space",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(doubleSpacePeriod = v) } }
                    )
                    ToggleSettingRow(
                        title = "Smart Punctuation Spacing",
                        subtitle = "Automatically append a space after commas and sentence endings",
                        checked = settings.smartPunctuation,
                        testTag = "toggle_smart_punctuation",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(smartPunctuation = v) } }
                    )
                }
            }
        }
    }
}

@Composable
fun GlideAndClipboardScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsPageScaffold(title = "Glide, Voice & Clipboard", onBack = onBack) { widthModifier ->
        Column(
            modifier = widthModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    ToggleSettingRow(
                        title = "Glide / Swipe Typing",
                        subtitle = "Slide across keys to input words via spatial path decoding",
                        checked = settings.glideTypingEnabled,
                        testTag = "toggle_glide_typing",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(glideTypingEnabled = v) } }
                    )
                    ToggleSettingRow(
                        title = "Show Bézier Swipe Trail",
                        subtitle = "Render smooth glowing gesture trail while gliding",
                        checked = settings.showGlideTrail,
                        testTag = "toggle_glide_trail",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(showGlideTrail = v) } }
                    )
                    ToggleSettingRow(
                        title = "Spacebar Cursor Trackpad",
                        subtitle = "Glide left or right on the Spacebar to move the text cursor",
                        checked = settings.spacebarCursorTrackpad,
                        testTag = "toggle_spacebar_trackpad",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(spacebarCursorTrackpad = v) } }
                    )
                    ToggleSettingRow(
                        title = "Gesture Delete",
                        subtitle = "Slide left from Backspace to delete whole words with Undo chip",
                        checked = settings.gestureDeleteEnabled,
                        testTag = "toggle_gesture_delete",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(gestureDeleteEnabled = v) } }
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    ToggleSettingRow(
                        title = "Smart Clipboard Manager",
                        subtitle = "Store recent copied snippets locally (automatically paused in password & incognito fields)",
                        checked = settings.clipboardEnabled,
                        testTag = "toggle_clipboard_enabled",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(clipboardEnabled = v) } }
                    )
                    SliderSettingRow(
                        title = "Clipboard History Retention Limit",
                        valueLabel = "${settings.clipboardRetentionLimit} items",
                        value = settings.clipboardRetentionLimit.toFloat(),
                        valueRange = 5f..100f,
                        testTag = "slider_clipboard_limit",
                        onValueChange = { v -> viewModel.updateSettings { it.copy(clipboardRetentionLimit = v.roundToInt()) } }
                    )
                }
            }
        }
    }
}
