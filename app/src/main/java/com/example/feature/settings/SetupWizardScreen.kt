package com.example.feature.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.theme.NotoNaskhArabicFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupWizardScreen(
    viewModel: SettingsViewModel,
    onOpenSettingsHome: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val imeStatus by viewModel.imeStatus.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var testBoxText by remember { mutableStateOf("") }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshImeStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsPageScaffold(
        title = stringResource(R.string.wizard_title),
        onBack = onBack
    ) { widthModifier ->
        Column(
            modifier = widthModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.wizard_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Step 1: Enable in System Settings
            WizardStepCard(
                stepNumber = 1,
                title = stringResource(R.string.wizard_step1_title),
                description = stringResource(R.string.wizard_step1_desc),
                completed = imeStatus.isEnabledInSystem,
                buttonLabel = if (imeStatus.isEnabledInSystem) "Enabled ✓" else stringResource(R.string.wizard_step1_cta),
                testTag = "wizard_step1_button",
                onAction = {
                    context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                }
            )

            // Step 2: Select Input Method
            WizardStepCard(
                stepNumber = 2,
                title = stringResource(R.string.wizard_step2_title),
                description = stringResource(R.string.wizard_step2_desc),
                completed = imeStatus.isSelectedAsDefault,
                buttonLabel = if (imeStatus.isSelectedAsDefault) "Active Keyboard ✓" else stringResource(R.string.wizard_step2_cta),
                testTag = "wizard_step2_button",
                onAction = {
                    viewModel.showInputMethodPicker()
                }
            )

            // Step 3: Live Interactive Test Box
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Keyboard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.wizard_step3_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = stringResource(R.string.wizard_step3_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "گ → ڳ / ڱ",
                            "ج → ڄ",
                            "د → ڋ",
                            "ن → ݨ",
                            "ب → ٻ",
                            "1-0 Top Row"
                        ).forEach { tip ->
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = tip,
                                        fontFamily = NotoNaskhArabicFamily,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = testBoxText,
                        onValueChange = { testBoxText = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.wizard_test_hint),
                                fontFamily = NotoNaskhArabicFamily
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wizard_test_input"),
                        minLines = 3
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = { testBoxText = "" },
                            modifier = Modifier.testTag("wizard_clear_test")
                        ) {
                            Text("Clear")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onOpenSettingsHome,
                            modifier = Modifier.testTag("wizard_finish_button")
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Settings")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardStepCard(
    stepNumber: Int,
    title: String,
    description: String,
    completed: Boolean,
    buttonLabel: String,
    testTag: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stepNumber.toString(),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                }
                Icon(
                    imageVector = if (completed) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (completed) "Completed" else "Pending",
                    tint = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag)
            ) {
                Text(buttonLabel)
            }
        }
    }
}
