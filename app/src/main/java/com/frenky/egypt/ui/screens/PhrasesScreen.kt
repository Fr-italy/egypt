package com.frenky.egypt.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.frenky.egypt.data.Phrase
import com.frenky.egypt.data.PhraseDto
import com.frenky.egypt.data.PhraseRepository
import com.frenky.egypt.phrases.PhraseTts

@Composable
fun PhrasesScreen(
    modifier: Modifier = Modifier,
    extraFromServer: List<PhraseDto> = emptyList(),
) {
    val context = LocalContext.current
    val tts = remember { PhraseTts(context) }
    DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }

    val localPhrases = remember { PhraseRepository.load(context) }
    val merged = remember(localPhrases, extraFromServer) {
        val extra = extraFromServer.map { Phrase(it.it, it.en, it.ar, it.arLatin) }
        (localPhrases + extra).distinctBy { it.it + it.en }
    }
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, merged) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) merged
        else merged.filter {
            it.it.lowercase().contains(q) ||
                it.en.lowercase().contains(q) ||
                it.ar.contains(query) ||
                it.arLatin.lowercase().contains(q)
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Frasi utili", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Tocca l'altoparlante per sentire la pronuncia in arabo (voce del telefono).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Cerca") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered, key = { it.it + it.en }) { phrase ->
                PhraseCard(phrase = phrase, onSpeak = { tts.speak(phrase.ar, phrase.arLatin) })
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PhraseCard(phrase: Phrase, onSpeak: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(phrase.it, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("🇬🇧 ${phrase.en}", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    phrase.ar,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    textAlign = TextAlign.Start,
                )
                IconButton(
                    onClick = onSpeak,
                    modifier = Modifier.padding(0.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Ascolta pronuncia araba",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                phrase.arLatin,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
