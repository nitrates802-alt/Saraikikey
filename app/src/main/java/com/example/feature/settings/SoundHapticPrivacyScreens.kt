package com.example.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.preferences.KeyboardLanguage
import com.example.core.preferences.SoundProfile
import com.example.ui.theme.NotoNaskhArabicFamily
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SoundHapticsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsPageScaffold(title = "Sound & Vibration", onBack = onBack) { widthModifier ->
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
                        title = "Keypress Sound Effects",
                        subtitle = "Low-latency SoundPool tactile audio on keypress",
                        checked = settings.soundEnabled,
                        testTag = "toggle_sound_enabled",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(soundEnabled = v) } }
                    )
                    SliderSettingRow(
                        title = "Sound Volume",
                        valueLabel = "${(settings.soundVolume * 100).roundToInt()}%",
                        value = settings.soundVolume,
                        valueRange = 0f..1f,
                        testTag = "slider_sound_volume",
                        onValueChange = { v -> viewModel.updateSettings { it.copy(soundVolume = v) } }
                    )
                    Text(
                        text = "Sound Profile",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SoundProfile.entries.forEach { profile ->
                            FilterChip(
                                selected = settings.soundProfile == profile,
                                onClick = {
                                    viewModel.updateSettings { it.copy(soundProfile = profile) }
                                    viewModel.previewSound(profile, settings.soundVolume)
                                },
                                label = { Text(profile.displayName) },
                                trailingIcon = {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.testTag("sound_profile_${profile.id}")
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    ToggleSettingRow(
                        title = "Haptic Vibration on Keypress",
                        subtitle = "Differentiated VibrationEffect waveforms for keys, space & popups",
                        checked = settings.hapticEnabled,
                        testTag = "toggle_haptic_enabled",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(hapticEnabled = v) } }
                    )
                    SliderSettingRow(
                        title = "Vibration Strength / Duration",
                        valueLabel = "${settings.hapticDurationMs} ms",
                        value = settings.hapticDurationMs.toFloat(),
                        valueRange = 1f..50f,
                        testTag = "slider_haptic_duration",
                        onValueChange = { v -> viewModel.updateSettings { it.copy(hapticDurationMs = v.roundToInt()) } }
                    )
                    OutlinedButton(
                        onClick = { viewModel.previewHaptic(settings.hapticDurationMs) },
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .testTag("button_test_haptic")
                    ) {
                        Icon(Icons.Filled.Vibration, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Haptic Pulse")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DictionariesScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val words by viewModel.filteredLearnedWords.collectAsStateWithLifecycle()
    val filterLang by viewModel.dictFilterLang.collectAsStateWithLifecycle()
    val searchQuery by viewModel.dictSearchQuery.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    var newWordInput by remember { mutableStateOf("") }
    var newWordLang by remember { mutableStateOf(KeyboardLanguage.SARAIKI) }

    LaunchedEffect(statusBanner) {
        statusBanner?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusBanner()
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val payload = viewModel.exportDictionaryText(asCsv = false)
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(payload) }
                snackbarHostState.showSnackbar("Dictionary exported as JSON")
            }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val payload = viewModel.exportDictionaryText(asCsv = true)
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(payload) }
                snackbarHostState.showSnackbar("Dictionary exported as CSV")
            }
        }
    }

    val importDictLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            viewModel.importDictionaryText(raw, newWordLang)
        }
    }

    SettingsPageScaffold(
        title = "Correction & Dictionaries",
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { widthModifier ->
        LazyColumn(
            modifier = widthModifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column {
                        ToggleSettingRow(
                            title = "Learn From Typed Words",
                            subtitle = "Capture newly typed words & N-grams locally on-device for instant next-sentence prediction",
                            checked = settings.learnFromTypedWords,
                            testTag = "toggle_learn_words",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(learnFromTypedWords = v) } }
                        )
                        ToggleSettingRow(
                            title = "Next-Word Prediction",
                            subtitle = "Suggest likely next words from local N-gram context",
                            checked = settings.nextWordPrediction,
                            testTag = "toggle_next_word",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(nextWordPrediction = v) } }
                        )
                        ToggleSettingRow(
                            title = "Autocorrect — Saraiki (سرائیکی)",
                            subtitle = "Never normalizes Saraiki graphemes into Urdu",
                            checked = settings.autocorrectSaraiki,
                            testTag = "toggle_autocorrect_srk",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(autocorrectSaraiki = v) } }
                        )
                        ToggleSettingRow(
                            title = "Autocorrect — Urdu (اردو)",
                            subtitle = "Fuzzy edit-distance correction for Urdu",
                            checked = settings.autocorrectUrdu,
                            testTag = "toggle_autocorrect_urd",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(autocorrectUrdu = v) } }
                        )
                        ToggleSettingRow(
                            title = "Autocorrect — English",
                            subtitle = "Fuzzy edit-distance correction for English",
                            checked = settings.autocorrectEnglish,
                            testTag = "toggle_autocorrect_eng",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(autocorrectEnglish = v) } }
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Add Personal Word", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KeyboardLanguage.entries.forEach { lang ->
                                FilterChip(
                                    selected = newWordLang == lang,
                                    onClick = { newWordLang = lang },
                                    label = { Text(lang.englishName) }
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newWordInput,
                                onValueChange = { newWordInput = it },
                                label = { Text("Word (${newWordLang.nativeName})") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_add_word")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    viewModel.addCustomWord(newWordInput, newWordLang)
                                    newWordInput = ""
                                },
                                modifier = Modifier.testTag("button_add_word")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Add")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { exportJsonLauncher.launch("saraiki_pro_dictionary.json") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("JSON")
                            }
                            OutlinedButton(
                                onClick = { exportCsvLauncher.launch("saraiki_pro_dictionary.csv") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("CSV")
                            }
                            OutlinedButton(
                                onClick = { importDictLauncher.launch(arrayOf("application/json", "text/csv", "text/*")) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Import")
                            }
                        }
                    }
                }
            }

            item {
                Text("Learned Vocabulary (${words.size})", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filterLang == null,
                        onClick = { viewModel.setDictionaryFilter(null) },
                        label = { Text("All") }
                    )
                    KeyboardLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = filterLang == lang,
                            onClick = { viewModel.setDictionaryFilter(lang) },
                            label = { Text(lang.englishName) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setDictionarySearch(it) },
                    label = { Text("Search learned words") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_search_dictionary")
                )
            }

            if (words.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "No learned words yet. Type normally in any app or add custom words above — every word is stored locally in Room/SQLite.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(words, key = { it.id }) { item ->
                    val lang = KeyboardLanguage.fromCode(item.language)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.word,
                                    fontFamily = if (lang.isRtl) NotoNaskhArabicFamily else MaterialTheme.typography.titleMedium.fontFamily,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${lang.englishName} • Used ${item.frequencyCount}×",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { viewModel.deleteLearnedWord(item) },
                                modifier = Modifier.testTag("delete_word_${item.id}")
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Forget ${item.word}")
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun PrivacyBackupScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    var showConfirmWipe by remember { mutableStateOf(false) }

    LaunchedEffect(statusBanner) {
        statusBanner?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusBanner()
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val json = viewModel.exportFullBackupJson()
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                snackbarHostState.showSnackbar("Settings & learned vocabulary backed up")
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            viewModel.importFullBackupJson(raw)
        }
    }

    if (showConfirmWipe) {
        AlertDialog(
            onDismissRequest = { showConfirmWipe = false },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
            title = { Text("Delete all learned words & history?") },
            text = {
                Text("This permanently clears all locally learned words, N-gram predictions, and clipboard snippets from this device.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllLearnedHistory()
                        showConfirmWipe = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmWipe = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    SettingsPageScaffold(
        title = "Privacy, Backup & About",
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { widthModifier ->
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
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("100% Offline Privacy Guarantee", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Saraiki Pro Keyboard operates with zero internet permission, zero analytics SDKs, and zero telemetry. All learned vocabulary and N-grams live exclusively in your device's local Room/SQLite database, and learning/clipboard are automatically disabled in password, PIN, OTP, and incognito fields.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Local Backup & Restore", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { backupLauncher.launch("saraiki_pro_backup.json") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("button_backup_export")
                        ) {
                            Text("Backup JSON")
                        }
                        OutlinedButton(
                            onClick = { restoreLauncher.launch(arrayOf("application/json")) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("button_backup_restore")
                        ) {
                            Text("Restore JSON")
                        }
                    }

                    Button(
                        onClick = { showConfirmWipe = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("button_wipe_learned")
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete All Learned Words & History")
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Saraiki Pro Keyboard v1.0", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "First-class Saraiki Unicode (ٻ ڄ ڳ ڱ ڋ ݨ ݮ), Urdu, and English trilingual IME with Noto Naskh Arabic typography.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
