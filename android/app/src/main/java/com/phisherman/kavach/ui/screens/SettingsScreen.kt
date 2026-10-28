package com.phisherman.kavach.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phisherman.kavach.ui.strings

@Composable
fun SettingsScreen(
    hindi: Boolean,
    onHindiChange: (Boolean) -> Unit,
    isDefaultBrowser: Boolean,
    onSetDefaultBrowser: () -> Unit,
    sensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(s.settings, style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground)

        // Language
        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Translate, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(0.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(s.language, style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(if (hindi) "हिन्दी" else "English",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = hindi, onCheckedChange = onHindiChange)
            }
        }

        // Default browser / interception
        SettingCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(s.defaultBrowser, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (isDefaultBrowser) "✓ Phisherman is your default browser"
                    else "Not set — risky links won't be intercepted automatically",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!isDefaultBrowser) {
                    OutlinedButton(onClick = onSetDefaultBrowser, shape = RoundedCornerShape(12.dp)) {
                        Text(s.setDefaultBrowser)
                    }
                }
            }
        }

        // Sensitivity slider (live precision/recall trade-off, PS4 Section 6.7)
        SettingCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.thresholdSlider, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Slider(value = sensitivity, onValueChange = onSensitivityChange, valueRange = -1f..1f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(s.recall, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(s.precision, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Privacy
        SettingCard {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(s.privacyTitle, style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Everything runs on this phone. The log keeps only verdicts, reasons and a " +
                            "subject — never message bodies or audio. Nothing is uploaded by the core checks.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        OutlinedButton(onClick = onClearHistory, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)) {
            Text(s.clearHistory)
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp)) { content() }
    }
}
