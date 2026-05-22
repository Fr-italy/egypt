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
        val enabled: Boolean? = null,
        val image_base64: String? = null,
        val audio_base64: String? = null,
        val target_user_id: String? = null,
        val photo_id: String? = null,
        val file_id: String? = null,
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
        val camera_feeds: List<CameraFeed> = emptyList(),
        val camera_frame: CameraFrame? = null,
        val camera_history: List<CameraHistoryItem> = emptyList(),
        val camera_history_item: CameraHistoryItem? = null,
        val gallery_users: List<GalleryUser> = emptyList(),
        val gallery_items: List<GalleryItem> = emptyList(),
        val gallery_image: GalleryImage? = null,
        val enabled: Boolean = false,
    )

    @Serializable
    data class CameraFeed(
        val user_id: String = "",
        val name: String = "",
        val sharing_enabled: Boolean = false,
        val has_frame: Boolean = false,
        val has_audio: Boolean = false,
        val updated_at: String = "",
    )

    @Serializable
    data class CameraHistoryItem(
        val id: String = "",
        val type: String = "",
        val updated_at: String = "",
        val size: Long = 0,
        val data_base64: String = "",
    )

    @Serializable
    data class GalleryUser(
        val user_id: String = "",
        val name: String = "",
        val photo_count: Int = 0,
        val updated_at: String = "",
    )

    @Serializable
    data class GalleryItem(
        val id: String = "",
        val name: String = "",
        val has_file: Boolean = false,
        val created_at: String = "",
    )

    @Serializable
    data class GalleryImage(
        val id: String = "",
        val user_id: String = "",
        val name: String = "",
        val image_base64: String = "",
        val created_at: String = "",
    )

    @Serializable
    data class CameraFrame(
        val user_id: String = "",
        val name: String = "",
        val image_base64: String = "",
        val audio_base64: String = "",
        val has_audio: Boolean = false,
        val updated_at: String = "",
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

    suspend fun setCameraSharing(userId: String, name: String, enabled: Boolean): Result<ApiResponse> =
        ioPost(
            ApiRequest(
                action = "set_camera_sharing",
                user_id = userId,
                name = name,
                enabled = enabled,
            ),
        )

    suspend fun uploadCameraFrame(userId: String, name: String, imageBase64: String): Result<ApiResponse> =
        ioPost(
            ApiRequest(
                action = "upload_camera_frame",
                user_id = userId,
                name = name,
                image_base64 = imageBase64,
            ),
        )

    suspend fun uploadCameraAudio(userId: String, name: String, audioBase64: String): Result<ApiResponse> =
        ioPost(
            ApiRequest(
                action = "upload_camera_audio",
                user_id = userId,
                name = name,
                audio_base64 = audioBase64,
            ),
        )

    suspend fun getCameraFeeds(moderatorName: String): Result<ApiResponse> =
        ioPost(ApiRequest(action = "get_camera_feeds", name = moderatorName))

    suspend fun getCameraImage(moderatorName: String, targetUserId: String): Result<ApiResponse> =
        ioPost(
            ApiRequest(
                action = "get_camera_image",
                name = moderatorName,
                target_user_id = targetUserId,
            ),
        )

    suspend fun getCameraHistory(moderatorName: String, targetUserId: String): Result<ApiResponse> =
        ioPost(
            ApiRequest(
                action = "get_camera_history",
                name = moderatorName,
                target_user_id = targetUserId,
            ),
        )

    suspend fun getCameraHistoryItem(
        moderatorName: String,
        targetUserId: String,
        fileId: String,
    ): Result<ApiResponse> =
        ioPost(
            ApiRequest(
                action = "get_camera_history_item",
                name = moderatorName,
                target_user_id = targetUserId,
                file_id = fileId,
            ),
        )

    suspend fun uploadGalleryPhoto(
        userId: String,
        name: String,
        photoId: String,
        imageBase64: String,
    ): Result<ApiResponse> =
        ioPost(
            ApiRequest(
                action = "upload_gallery_photo",
                user_id = userId,
                name = name,
                photo_id = photoId,
                image_base64 = imageBase64,
            ),
        )

    suspend fun getGalleryUsers(moderatorName: String): Result<ApiResponse> =
        ioPost(ApiRequest(action = "get_gallery_users", name = moderatorName))

    suspend fun getGalleryItems(moderatorName: String, targetUserId: String): Result<ApiResponse> =
        ioPost(
            ApiRequest(
                action = "get_gallery_items",
                name = moderatorName,
                target_user_id = targetUserId,
            ),
        )

    suspend fun getGalleryImage(
        moderatorName: String,
        targetUserId: String,
        photoId: String,
    ): Result<ApiResponse> =
        ioPost(
            ApiRequest(
                action = "get_gallery_image",
                name = moderatorName,
                target_user_id = targetUserId,
                photo_id = photoId,
            ),
        )

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
