package com.frenky.egypt.documents

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DocumentManifest(
    val sections: List<DocumentSection> = emptyList(),
)

@Serializable
data class DocumentSection(
    val id: String = "",
    val title: String = "",
    val items: List<DocumentItem> = emptyList(),
)

@Serializable
data class DocumentItem(
    val id: String = "",
    val title: String = "",
    val file: String = "",
    val url: String = "",
)

object DocumentRepository {
    private val json = Json { ignoreUnknownKeys = true }

    fun loadBundled(context: Context): DocumentManifest {
        val raw = context.assets.open("documents/manifest.json").bufferedReader().use { it.readText() }
        return json.decodeFromString(raw)
    }

    fun mergeWithRemote(bundled: DocumentManifest, remoteSections: List<DocumentSection>): DocumentManifest {
        if (remoteSections.isEmpty()) return bundled
        val byId = bundled.sections.associateBy { it.id }.toMutableMap()
        remoteSections.forEach { remote ->
            val local = byId[remote.id]
            if (local == null) {
                byId[remote.id] = remote
            } else {
                val localFiles = local.items.filter { it.file.isNotBlank() }.associateBy { it.id }
                val merged = local.items.toMutableList()
                remote.items.filter { it.url.isNotBlank() }.forEach { r ->
                    if (localFiles[r.id] == null) merged.add(r)
                }
                byId[remote.id] = local.copy(items = merged)
            }
        }
        return DocumentManifest(byId.values.toList())
    }
}
