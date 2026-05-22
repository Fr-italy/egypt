package com.frenky.egypt.camera

import java.time.Instant
import java.time.format.DateTimeParseException

enum class FeedFreshness { Live, Recent, Stale, Unknown }

data class FeedStatusInfo(
    val freshness: FeedFreshness,
    val label: String,
)

fun cameraFeedStatus(updatedAt: String, hasSignal: Boolean): FeedStatusInfo {
    if (!hasSignal) {
        return FeedStatusInfo(FeedFreshness.Stale, "In attesa segnale…")
    }
    val ageSec = parseAgeSeconds(updatedAt) ?: return FeedStatusInfo(
        FeedFreshness.Unknown,
        "Segnale presente",
    )
    return when {
        ageSec <= 25 -> FeedStatusInfo(FeedFreshness.Live, "Online · ${ageSec}s fa")
        ageSec <= 90 -> FeedStatusInfo(FeedFreshness.Recent, "Attivo · ${ageSec}s fa")
        ageSec < 3600 -> FeedStatusInfo(
            FeedFreshness.Stale,
            "Fermo da ${ageSec / 60} min — controlla telefono",
        )
        else -> FeedStatusInfo(FeedFreshness.Stale, "Fermo da molto — app spenta?")
    }
}

private fun parseAgeSeconds(iso: String): Long? {
    if (iso.isBlank()) return null
    val instant = try {
        Instant.parse(iso)
    } catch (_: DateTimeParseException) {
        return null
    }
    val diff = Instant.now().epochSecond - instant.epochSecond
    return diff.coerceAtLeast(0)
}
