package com.phisherman.kavach.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phisherman.kavach.ui.Results
import com.phisherman.kavach.ui.strings

@Composable
fun MetricsScreen(results: Results, modifier: Modifier = Modifier) {
    val s = strings()
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Text(s.metrics, style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Precision, recall (fraud detection rate) & FPR per channel. " +
                "'Fraud detection rate' = recall on the phishing class.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (results.dataNote.isNotEmpty()) {
            NoteCard(results.dataNote)
        }

        // Link — held-out
        MetricCard(
            title = "Link model", subtitle = results.link.dataset,
            m = results.link.test,
        )
        // Link — unseen (the honest generalisation drop)
        MetricCard(
            title = "Link model · ${s.unseenDataset}",
            subtitle = "A different source, never used for training",
            m = results.link.unseen,
        )
        // SMS
        MetricCard(
            title = "SMS classifier",
            subtitle = results.sms.dataset + " · ${results.sms.nSynthetic} synthetic",
            m = results.sms.test,
        )

        LatencyCard(results)
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun MetricCard(title: String, subtitle: String, m: Results.Metric) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("Precision", pct(m.precision))
                Stat("Recall (FDR)", pct(m.recall))
                Stat("FPR", pct(m.fpr))
                Stat("N", m.n.toString())
            }
            if (m.tp + m.fp + m.fn + m.tn > 0) {
                Text(
                    "TP ${m.tp} · FP ${m.fp} · FN ${m.fn} · TN ${m.tn}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LatencyCard(results: Results) {
    val s = strings()
    val l = results.latencyMs
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(s.latency, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Text(
                "Link p50 ${ms(l.linkP50)} · p95 ${ms(l.linkP95)}   |   SMS p50 ${ms(l.smsP50)} · p95 ${ms(l.smsP95)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (l.note.isNotEmpty()) {
                Text(l.note, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NoteCard(note: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp).height(18.dp).width(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(note, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun pct(x: Double): String = if (x <= 0.0) "—" else "${(x * 100).toInt()}%"
private fun ms(x: Double?): String = x?.let { "${it.toInt()} ms" } ?: "—"
