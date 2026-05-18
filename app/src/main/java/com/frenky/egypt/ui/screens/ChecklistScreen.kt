package com.frenky.egypt.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.frenky.egypt.data.ConfigRepository
import com.frenky.egypt.data.EgyptApi
import com.frenky.egypt.data.EgyptConfig
import kotlinx.coroutines.launch

@Composable
fun ChecklistScreen(
    modifier: Modifier = Modifier,
    config: EgyptConfig,
    userId: String,
    userName: String,
    configRepository: ConfigRepository,
) {
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Checklist viaggio", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Condivisa con tutti: le spunte si vedono su ogni telefono.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        if (config.checklist.isEmpty()) {
            Text("Nessuna voce. Aggiungile in egypt_data/config.json sul server.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(config.checklist, key = { it.id }) { item ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = item.done,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        EgyptApi.toggleChecklist(userId, userName, item.id, checked)
                                            .onSuccess { r ->
                                                r.config?.let { configRepository.save(it) }
                                            }
                                    }
                                },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(item.text, style = MaterialTheme.typography.bodyLarge)
                                if (item.done && item.done_by.isNotBlank()) {
                                    Text(
                                        "✓ ${item.done_by}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
