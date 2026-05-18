package com.frenky.egypt.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frenky.egypt.data.ConfigRepository
import com.frenky.egypt.data.EgyptApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class MessageRecipient(
    val userId: String?,
    val displayName: String,
)

@Composable
fun MessagesScreen(
    modifier: Modifier = Modifier,
    userName: String,
    userId: String,
    configRepository: ConfigRepository,
) {
    var messages by remember { mutableStateOf<List<EgyptApi.ChatMessage>>(emptyList()) }
    var users by remember { mutableStateOf<List<EgyptApi.ChatUser>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var selectedRecipient by remember { mutableStateOf(MessageRecipient(null, "Tutti")) }
    var showClearDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val recipients = remember(users, userId) {
        listOf(MessageRecipient(null, "Tutti")) +
            users
                .filter { it.user_id != userId && it.name.isNotBlank() }
                .sortedBy { it.name.lowercase() }
                .map { MessageRecipient(it.user_id, it.name) }
    }

    fun applyResponse(r: EgyptApi.ApiResponse) {
        if (r.ok) {
            messages = r.messages
            users = r.users
            r.config?.let { cfg ->
                scope.launch { configRepository.save(cfg) }
            }
            status = null
        } else {
            status = r.error ?: "Errore server"
        }
    }

    fun refresh() {
        scope.launch {
            loading = true
            EgyptApi.fetchMessages(userId)
                .onSuccess { applyResponse(it) }
                .onFailure { status = "Connessione: ${it.message}" }
            loading = false
        }
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || sending) return
        scope.launch {
            sending = true
            val target = selectedRecipient
            status = if (target.userId == null) "Invio a tutti…" else "Invio a ${target.displayName}…"
            EgyptApi.sendMessage(
                userId = userId,
                name = userName,
                message = text,
                toUserId = target.userId,
                toName = if (target.userId != null) target.displayName else null,
            )
                .onSuccess {
                    if (it.ok) {
                        draft = ""
                        applyResponse(it)
                        status = if (target.userId == null) {
                            "Inviato a tutti"
                        } else {
                            "Inviato a ${target.displayName}"
                        }
                    } else {
                        status = it.error ?: "Invio rifiutato"
                    }
                }
                .onFailure { status = "Errore: ${it.message}" }
            sending = false
        }
    }

    LaunchedEffect(userId) {
        refresh()
        while (true) {
            delay(12_000)
            refresh()
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Messaggi", style = MaterialTheme.typography.headlineSmall)
                Text("Tu: $userName", style = MaterialTheme.typography.bodySmall)
            }
            Row {
                OutlinedButton(
                    onClick = { showClearDialog = true },
                    enabled = !loading && !sending,
                ) {
                    Text("Svuota")
                }
                IconButton(onClick = { refresh() }, enabled = !loading && !sending) {
                    Icon(Icons.Default.Refresh, contentDescription = "Aggiorna")
                }
            }
        }

        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Svuotare tutta la chat?") },
                text = { Text("Elimina tutti i messaggi per tutti. Operazione irreversibile.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearDialog = false
                            scope.launch {
                                sending = true
                                EgyptApi.clearMessages(userId, userName)
                                    .onSuccess { applyResponse(it); status = "Chat svuotata" }
                                    .onFailure { status = "Errore: ${it.message}" }
                                sending = false
                            }
                        },
                    ) { Text("Elimina tutto") }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) { Text("Annulla") }
                },
            )
        }

        Text(
            "Scrivi a:",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            recipients.forEach { recipient ->
                FilterChip(
                    selected = selectedRecipient.userId == recipient.userId &&
                        selectedRecipient.displayName == recipient.displayName,
                    onClick = { selectedRecipient = recipient },
                    label = { Text(recipient.displayName) },
                )
            }
        }
        if (recipients.size <= 1) {
            Text(
                "Nessun altro utente online ancora. Gli altri devono aprire l'app almeno una volta.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        status?.let {
            Text(
                it,
                color = if (it.startsWith("Errore") || it.contains("rifiutato")) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (loading && messages.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = true,
        ) {
            if (messages.isEmpty() && !loading) {
                item {
                    Text(
                        "Nessun messaggio.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(messages.reversed(), key = { it.id.ifEmpty { "${it.created_at}_${it.name}" } }) { msg ->
                MessageBubble(
                    msg = msg,
                    myUserId = userId,
                    onDelete = if (msg.user_id == userId && msg.id.isNotBlank()) {
                        {
                            scope.launch {
                                EgyptApi.deleteMessage(userId, msg.id)
                                    .onSuccess { applyResponse(it) }
                                    .onFailure { status = "Errore: ${it.message}" }
                            }
                        }
                    } else {
                        null
                    },
                )
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = {
                    Text(
                        if (selectedRecipient.userId == null) "Messaggio per tutti"
                        else "Messaggio per ${selectedRecipient.displayName}",
                    )
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = !sending,
            )
            IconButton(onClick = { send() }, enabled = !sending && draft.isNotBlank()) {
                if (sending) {
                    CircularProgressIndicator(Modifier.padding(8.dp))
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Invia")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    msg: EgyptApi.ChatMessage,
    myUserId: String,
    onDelete: (() -> Unit)?,
) {
    val isMe = msg.user_id == myUserId
    val isPrivate = !msg.isBroadcast()
    val privateLabel = when {
        isPrivate && isMe -> "Tu → ${msg.to_name}"
        isPrivate && msg.to_user_id == myUserId -> "${msg.name} → Te"
        isPrivate -> "${msg.name} → ${msg.to_name}"
        else -> null
    }

    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(
                if (onDelete != null) {
                    Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = onDelete,
                    )
                } else {
                    Modifier
                },
            ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isPrivate && isMe -> MaterialTheme.colorScheme.tertiaryContainer
                isPrivate -> MaterialTheme.colorScheme.surfaceVariant
                isMe -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (isMe) "Tu" else msg.name,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (msg.isBroadcast()) {
                    Text(
                        "Tutti",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            privateLabel?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Text(msg.message)
            Text(msg.created_at, style = MaterialTheme.typography.labelSmall)
            if (onDelete != null) {
                Text(
                    "Tieni premuto per eliminare",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
