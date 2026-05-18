package com.frenky.egypt.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.frenky.egypt.data.EgyptConfig
import com.frenky.egypt.documents.DocumentItem
import com.frenky.egypt.documents.DocumentManifest
import com.frenky.egypt.documents.DocumentRepository
import com.frenky.egypt.documents.DocumentSection
import com.frenky.egypt.documents.PdfOpener
import kotlinx.coroutines.launch

@Composable
fun DocumentsScreen(modifier: Modifier = Modifier, config: EgyptConfig) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manifest = remember(config.documents) {
        val bundled = DocumentRepository.loadBundled(context)
        DocumentRepository.mergeWithRemote(bundled, config.documents)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        item {
            Text("Documenti di viaggio", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Biglietti e polizza disponibili offline per tutto il gruppo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
        manifest.sections.forEach { section ->
            item { SectionHeader(section) }
            items(section.items, key = { section.id + it.id }) { doc ->
                DocumentRow(doc) {
                    scope.launch { PdfOpener.open(context, doc) }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(section: DocumentSection) {
    val icon = when (section.id) {
        "boarding" -> Icons.Default.Flight
        "insurance" -> Icons.Default.HealthAndSafety
        else -> Icons.Default.Description
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(8.dp))
        Text(section.title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun DocumentRow(item: DocumentItem, onOpen: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onOpen),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (item.url.isNotBlank()) "Scarica dal server" else "PDF offline",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
