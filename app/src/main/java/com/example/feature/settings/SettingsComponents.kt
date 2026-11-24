package com.example.feature.settings

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.ime.KeyboardThemeTokens
import com.example.core.preferences.KeyboardSettings
import com.example.ui.theme.NotoNaskhArabicFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPageScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("nav_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = snackbarHost,
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            content(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
            )
        }
    }
}

@Composable
fun SettingsNavCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ToggleSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
fun SliderSettingRow(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    testTag: String,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )
    }
}

@Composable
fun MiniKeyboardPreview(
    settings: KeyboardSettings,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val theme = KeyboardThemeTokens.resolve(context, settings)
    val bgColor = Color(theme.backgroundColor)
    val keyColor = Color(theme.primaryKeyColor)
    val funcColor = Color(theme.functionKeyColor)
    val accentColor = Color(theme.accentColor)
    val textColor = Color(theme.primaryTextColor)
    val hintColor = Color(theme.hintTextColor)
    val borderColor = Color(theme.borderColor)
    val radius = theme.cornerRadiusDp.dp

    val sampleRow1 = listOf("ق" to "1", "و" to "2", "ع" to "3", "ر" to "4", "ت" to "5", "ے" to "6", "ی" to "7", "ء" to "8", "ہ" to "9", "پ" to "0")
    val sampleRow2 = listOf("ا", "س", "د", "ف", "گ", "ح", "ج", "ک", "ل", "ط")

    Column(
        modifier = modifier
            .fillMaxWidth((settings.widthPercent / 100f).coerceIn(0.7f, 1f))
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Suggestion strip preview
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(theme.stripBackgroundColor))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("ملتان", "سرائیکی", "سوہݨا").forEachIndexed { idx, word ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (idx == 1) accentColor else funcColor)
                        .padding(vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = word,
                        fontFamily = NotoNaskhArabicFamily,
                        fontSize = 12.sp,
                        color = if (idx == 1) Color(theme.accentTextColor) else textColor
                    )
                }
            }
        }

        if (settings.showNumberRow) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..9).plus(0).forEach { d ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(22.dp)
                            .clip(RoundedCornerShape(radius))
                            .background(keyColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = d.toString(), color = textColor, fontSize = 10.sp)
                    }
                }
            }
        }

        // Row 1
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            sampleRow1.forEach { (ch, hint) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(radius))
                        .background(keyColor)
                        .then(if (theme.showKeyBorders) Modifier.border(0.75.dp, borderColor, RoundedCornerShape(radius)) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    if (theme.showSecondaryHints) {
                        Text(
                            text = hint,
                            color = hintColor,
                            fontSize = 8.sp,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 3.dp, top = 1.dp)
                        )
                    }
                    Text(
                        text = ch,
                        fontFamily = NotoNaskhArabicFamily,
                        color = textColor,
                        fontSize = (theme.labelFontSizeSp * 0.65f).sp
                    )
                }
            }
        }

        // Row 2
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            sampleRow2.forEach { ch ->
                val hint = when (ch) {
                    "گ" -> "ڳ"
                    "د" -> "ڋ"
                    "ج" -> "ڄ"
                    else -> null
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(radius))
                        .background(keyColor)
                        .then(if (theme.showKeyBorders) Modifier.border(0.75.dp, borderColor, RoundedCornerShape(radius)) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    if (theme.showSecondaryHints && hint != null) {
                        Text(
                            text = hint,
                            fontFamily = NotoNaskhArabicFamily,
                            color = hintColor,
                            fontSize = 8.sp,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 2.dp, top = 1.dp)
                        )
                    }
                    Text(
                        text = ch,
                        fontFamily = NotoNaskhArabicFamily,
                        color = textColor,
                        fontSize = (theme.labelFontSizeSp * 0.65f).sp
                    )
                }
            }
        }

        // Bottom functional row preview
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(radius))
                    .background(funcColor),
                contentAlignment = Alignment.Center
            ) { Text("?123", color = textColor, fontSize = 10.sp) }

            Box(
                modifier = Modifier
                    .weight(0.8f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(radius))
                    .background(funcColor),
                contentAlignment = Alignment.Center
            ) { Text("🌐", fontSize = 11.sp) }

            Box(
                modifier = Modifier
                    .weight(3.5f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(radius))
                    .background(keyColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = settings.activeLanguage.nativeName,
                    fontFamily = NotoNaskhArabicFamily,
                    color = hintColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Box(
                modifier = Modifier
                    .weight(1.4f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(radius))
                    .background(accentColor),
                contentAlignment = Alignment.Center
            ) { Text("↵", color = Color(theme.accentTextColor), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
