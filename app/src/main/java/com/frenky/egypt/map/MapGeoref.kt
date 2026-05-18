package com.frenky.egypt.map

/**
 * Georeferencing: lat/lon corners map to image pixel coordinates.
 * Calibrate on site if the dot is offset — adjust corners in [MapBounds].
 */
data class MapBounds(
    val northLat: Double,
    val southLat: Double,
    val westLon: Double,
    val eastLon: Double,
    val imageWidth: Float = 1f,
    val imageHeight: Float = 1f,
)

object MapGeoref {
    /** Nabq / Sharm area — satellite overview (offline asset). */
    val areaBounds = MapBounds(
        northLat = 28.0525,
        southLat = 28.0345,
        westLon = 34.4050,
        eastLon = 34.4420,
    )

    /** Pickalbatros Royal Moderna — resort illustrated map. */
    val resortBounds = MapBounds(
        northLat = 28.0486,
        southLat = 28.0438,
        westLon = 34.4248,
        eastLon = 34.4312,
    )

    fun latLonToFraction(lat: Double, lon: Double, bounds: MapBounds): Pair<Float, Float>? {
        if (lat > bounds.northLat || lat < bounds.southLat) return null
        if (lon < bounds.westLon || lon > bounds.eastLon) return null
        val x = ((lon - bounds.westLon) / (bounds.eastLon - bounds.westLon)).toFloat()
        val y = ((bounds.northLat - lat) / (bounds.northLat - bounds.southLat)).toFloat()
        return x to y
    }
}
