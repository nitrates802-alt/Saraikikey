package com.example.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsHomeScreen(
    viewModel: SettingsViewModel,
    onOpenWizard: () -> Unit,
    onOpenLanguages: () -> Unit,
    onOpenPreferences: () -> Unit,
    onOpenThemes: () -> Unit,
    onOpenSoundHaptics: () -> Unit,
    onOpenDictionaries: () -> Unit,
    onOpenGlideClipboard: () -> Unit,
    onOpenAiAssistant: () -> Unit,
    onOpenPrivacy: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val imeStatus by viewModel.imeStatus.collectAsStateWithLifecycle()

    SettingsPageScaffold(title = "Saraiki Pro Keyboard") { widthModifier ->
        Column(
            modifier = widthModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Setup status card
            val ready = imeStatus.isEnabledInSystem && imeStatus.isSelectedAsDefault
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (ready) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (ready) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            contentDescription = null,
                            tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (ready) "Saraiki Pro Keyboard is Active" else "Finish 3-Step Keyboard Setup",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (ready) {
                                    "Offline learning & ${settings.activeLanguage.nativeName} layout ready"
                                } else {
                                    "Enable in System Settings & test Saraiki popups"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = onOpenWizard,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_open_wizard_button")
                    ) {
                        Icon(Icons.Filled.RocketLaunch, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (ready) "Open Setup & Live Test Box" else "Start 3-Step Setup Wizard")
                    }
                }
            }

            // Live keyboard preview card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Live Theme & Layout Preview",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = settings.themePreset.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    MiniKeyboardPreview(settings = settings)
                }
            }

            SettingsNavCard(
                icon = Icons.Filled.Language,
                title = "Languages & Layouts",
                subtitle = settings.enabledLanguages.joinToString(" • ") { it.nativeName },
                testTag = "nav_languages",
                onClick = onOpenLanguages
            )
            SettingsNavCard(
                icon = Icons.Filled.Tune,
                title = "Preferences",
                subtitle = "Number row, long-press delay (${settings.longPressDelayMs}ms), popup preview",
                testTag = "nav_preferences",
                onClick = onOpenPreferences
            )
            SettingsNavCard(
                icon = Icons.Filled.Palette,
                title = "Themes, Studio & Resize",
                subtitle = "${settings.themePreset.displayName} • ${settings.heightPercent}% height • ${settings.ergonomicMode.displayName}",
                testTag = "nav_themes",
                onClick = onOpenThemes
            )
            SettingsNavCard(
                icon = Icons.Filled.GraphicEq,
                title = "Sound & Vibration",
                subtitle = "${settings.soundProfile.displayName} • ${settings.hapticDurationMs}ms haptic",
                testTag = "nav_sound_haptics",
                onClick = onOpenSoundHaptics
            )
            SettingsNavCard(
                icon = Icons.Filled.Book,
                title = "Text Correction & Dictionaries",
                subtitle = "Real-time local word learning, autocorrect & JSON/CSV export",
                testTag = "nav_dictionaries",
                onClick = onOpenDictionaries
            )
            SettingsNavCard(
                icon = Icons.Filled.Gesture,
                title = "Glide Typing, Voice & Clipboard",
                subtitle = "Swipe trail, spacebar trackpad, gesture delete, clip retention",
                testTag = "nav_glide_clipboard",
                onClick = onOpenGlideClipboard
            )
            SettingsNavCard(
                icon = Icons.Filled.AutoAwesome,
                title = "AI Writing Assistant",
                subtitle = "Translate, fix grammar, rewrite tone & smart replies (sample mode)",
                testTag = "nav_ai_assistant",
                onClick = onOpenAiAssistant
            )
            SettingsNavCard(
                icon = Icons.Filled.Security,
                title = "Privacy, Backup & About",
                subtitle = "100% offline local SQLite storage • Wipe or backup vocabulary",
                testTag = "nav_privacy",
                onClick = onOpenPrivacy
            )
        }
    }
}
