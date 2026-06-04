package com.frenky.egypt.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.frenky.egypt.chat.ChatNotificationHelper
import com.frenky.egypt.data.EgyptApi
import com.frenky.egypt.data.PreferencesRepository
import kotlinx.coroutines.delay

/** Controlla nuovi messaggi e mostra notifica se non sei nella tab Chat. */
@Composable
fun MessageNotificationSync(
    userId: String,
    userName: String,
    preferences: PreferencesRepository,
    chatTabSelected: Boolean,
) {
    val context = LocalContext.current

    LaunchedEffect(userId, userName, chatTabSelected) {
        ChatNotificationHelper.ensureChannel(context)
        var seen = preferences.getSeenMessageIds().toMutableSet()
        var isFirstSync = seen.isEmpty()

        while (true) {
            EgyptApi.fetchMessages(userId, userName)
                .onSuccess { response ->
                    if (!response.ok) return@onSuccess
                    val incoming = response.messages.filter { it.id.isNotBlank() }

                    if (chatTabSelected) {
                        seen.addAll(incoming.map { it.id })
                        preferences.addSeenMessageIds(incoming.map { it.id })
                        isFirstSync = false
                        return@onSuccess
                    }

                    for (message in incoming) {
                        if (message.user_id == userId) {
                            seen.add(message.id)
                            continue
                        }
                        if (message.id in seen) continue

                        if (!isFirstSync && ChatNotificationHelper.hasPermission(context)) {
                            val preview = message.message.trim().ifBlank { "Nuovo messaggio" }
                            val sender = when {
                                message.isBroadcast() -> message.name
                                message.to_user_id == userId -> message.name
                                else -> message.name
                            }
                            ChatNotificationHelper.notifyNewMessage(
                                context = context,
                                senderName = sender,
                                preview = preview,
                                messageId = message.id,
                            )
                        }
                        seen.add(message.id)
                    }

                    preferences.addSeenMessageIds(incoming.map { it.id })
                    isFirstSync = false
                }

            delay(12_000)
        }
    }
}
