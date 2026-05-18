package com.frenky.egypt.data

import com.frenky.egypt.documents.DocumentSection
import com.frenky.egypt.map.MapBounds
import kotlinx.serialization.Serializable

@Serializable
data class EgyptConfig(
    val egp_to_eur: Double = DEFAULT_EGP_TO_EUR,
    val rate_source: String = "OANDA",
    val rate_updated_at: String = "",
    val announcement: String = "",
    val area_bounds: BoundsDto = BoundsDto.areaDefault(),
    val resort_bounds: BoundsDto = BoundsDto.resortDefault(),
    val checklist: List<ChecklistItem> = emptyList(),
    val phrases_extra: List<PhraseDto> = emptyList(),
    val documents: List<DocumentSection> = emptyList(),
) {
    fun areaMapBounds(): MapBounds = area_bounds.toMapBounds()
    fun resortMapBounds(): MapBounds = resort_bounds.toMapBounds()

    companion object {
        const val DEFAULT_EGP_TO_EUR = 0.01625

        fun defaults() = EgyptConfig()
    }
}

@Serializable
data class BoundsDto(
    val north_lat: Double = 0.0,
    val south_lat: Double = 0.0,
    val west_lon: Double = 0.0,
    val east_lon: Double = 0.0,
) {
    fun toMapBounds() = MapBounds(
        northLat = north_lat,
        southLat = south_lat,
        westLon = west_lon,
        eastLon = east_lon,
    )

    companion object {
        fun areaDefault() = BoundsDto(28.0525, 28.0345, 34.4050, 34.4420)
        fun resortDefault() = BoundsDto(28.0486, 28.0438, 34.4248, 34.4312)
    }
}

@Serializable
data class ChecklistItem(
    val id: String = "",
    val text: String = "",
    val done: Boolean = false,
    val done_by: String = "",
    val done_at: String = "",
)

@Serializable
data class PhraseDto(
    val it: String = "",
    val en: String = "",
    val ar: String = "",
    val arLatin: String = "",
)
