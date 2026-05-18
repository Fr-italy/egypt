package com.frenky.egypt.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object EgyptApi {
    const val ENDPOINT = "https://www.fr-italy.com/egypt_endpoint.php"

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @Serializable
    data class ApiRequest(
        val action: String,
        val user_id: String? = null,
        val name: String? = null,
        val message: String? = null,
        val to_user_id: String? = null,
        val to_name: String? = null,
        val message_id: String? = null,
        val item_id: String? = null,
        val done: Boolean? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
    )

    @Serializable
    data class ApiResponse(
        val ok: Boolean = false,
        val error: String? = null,
        val messages: List<ChatMessage> = emptyList(),
        val users: List<ChatUser> = emptyList(),
        val config: EgyptConfig? = null,
        val can_moderate: Boolean = false,
        val locations: List<UserLocation> = emptyList(),
    )

    @Serializable
    data class UserLocation(
        val user_id: String = "",
        val name: String = "",
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val updated_at: String = "",
    )

    @Serializable
    data class ChatMessage(
        val id: String = "",
        val user_id: String = "",
        val name: String = "",
        val to_user_id: String = "",
        val to_name: String = "",
        val message: String = "",
        val created_at: String = "",
    ) {
        fun isBroadcast(): Boolean = to_user_id.isBlank()
    }

    @Serializable
    data class ChatUser(
        val user_id: String = "",
        val name: String = "",
        val last_seen: String = "",
    )

    suspend fun fetchConfig(): Result<ApiResponse> = ioPost(ApiRequest(action = "config"))

    suspend fun register(userId: String, name: String): Result<ApiResponse> =
        ioPost(ApiRequest(action = "register", user_id = userId, name = name))

    suspend fun sendMessage(
        userId: String,
        name: String,
        message: String,
        toUserId: String? = null,
        toName: String? = null,
    ): Result<ApiResponse> = ioPost(
        ApiRequest(
            action = "send",
            user_id = userId,
            name = name,
            message = message,
            to_user_id = toUserId?.takeIf { it.isNotBlank() },
            to_name = toName?.takeIf { it.isNotBlank() },
        ),
    )

    suspend fun fetchMessages(viewerUserId: String, viewerName: String): Result<ApiResponse> =
        ioPost(ApiRequest(action = "fetch", user_id = viewerUserId, name = viewerName))

    suspend fun deleteMessage(userId: String, name: String, messageId: String): Result<ApiResponse> =
        ioPost(
            ApiRequest(
                action = "delete_message",
                user_id = userId,
                name = name,
                message_id = messageId,
            ),
        )

    suspend fun clearMessages(userId: String, name: String): Result<ApiResponse> =
        ioPost(ApiRequest(action = "clear_messages", user_id = userId, name = name))

    suspend fun heartbeat(userId: String, name: String): Result<ApiResponse> =
        ioPost(ApiRequest(action = "heartbeat", user_id = userId, name = name))

    suspend fun updateLocation(
        userId: String,
        name: String,
        latitude: Double,
        longitude: Double,
    ): Result<ApiResponse> = ioPost(
        ApiRequest(
            action = "update_location",
            user_id = userId,
            name = name,
            latitude = latitude,
            longitude = longitude,
        ),
    )

    suspend fun getGroupLocations(moderatorName: String): Result<ApiResponse> =
        ioPost(ApiRequest(action = "get_locations", name = moderatorName))

    suspend fun toggleChecklist(
        userId: String,
        name: String,
        itemId: String,
        done: Boolean,
    ): Result<ApiResponse> =
        ioPost(ApiRequest(action = "toggle_checklist", user_id = userId, name = name, item_id = itemId, done = done))

    private suspend fun ioPost(body: ApiRequest): Result<ApiResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = json.encodeToString(ApiRequest.serializer(), body)
            val request = Request.Builder()
                .url(ENDPOINT)
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}: $text")
                }
                json.decodeFromString(ApiResponse.serializer(), text.ifBlank { """{"ok":false}""" })
            }
        }
    }
}
