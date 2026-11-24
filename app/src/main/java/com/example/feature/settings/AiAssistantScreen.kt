package com.example.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ai.AiTaskType
import com.example.core.ai.PlaceholderAiRepository
import com.example.core.preferences.KeyboardLanguage

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiAssistantScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var targetLanguage by remember { mutableStateOf(settings.activeLanguage) }

    SettingsPageScaffold(
        title = stringResource(R.string.ai_studio_title),
        onBack = onBack
    ) { widthModifier ->
        Column(
            modifier = widthModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Required Firebase notice card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = aiState.notice.ifBlank { PlaceholderAiRepository.NOTICE_FIREBASE_REQUIRED },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Target language chips
            Text(
                text = "Target Language",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KeyboardLanguage.entries.forEach { lang ->
                    FilterChip(
                        selected = targetLanguage == lang,
                        onClick = { targetLanguage = lang },
                        label = { Text("${lang.englishName} (${lang.nativeName})") },
                        modifier = Modifier.testTag("ai_lang_${lang.code}")
                    )
                }
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Enter Saraiki, Urdu, or English text") },
                placeholder = { Text(stringResource(R.string.ai_drawer_input_hint)) },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_input_field")
            )

            Text(
                text = "AI Writing Functions",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiTaskType.entries.forEach { task ->
                    Button(
                        onClick = { viewModel.runAiTask(task, inputText, targetLanguage) },
                        enabled = !aiState.isLoading,
                        modifier = Modifier.testTag("ai_task_${task.name.lowercase()}")
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(task.shortLabel)
                    }
                }
            }

            if (aiState.outputText.isNotBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_output_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = aiState.lastTaskTitle ?: "Sample AI Output",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = aiState.outputText,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = aiState.notice,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("Saraiki Pro AI", aiState.outputText))
                            },
                            modifier = Modifier.testTag("ai_copy_output_button")
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Output")
                        }
                    }
                }
            }
        }
    }
}
