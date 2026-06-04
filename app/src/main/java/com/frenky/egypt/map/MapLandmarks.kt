package com.frenky.egypt.map

import com.google.android.gms.maps.model.LatLng

data class MapLandmark(
    val name: String,
    val latitude: Double,
    val longitude: Double,
) {
    fun latLng(): LatLng = LatLng(latitude, longitude)
}

/** Punti fissi sempre visibili sulle mappe Nabq / Villaggio. */
object MapLandmarks {
    val points: List<MapLandmark> = listOf(
        MapLandmark("Naama Bay", 27.912596779847508, 34.32224295500381),
        MapLandmark("Royal Albatros Moderna", 28.059805856597155, 34.43267033173746),
        MapLandmark("Soho Square", 27.963818082616726, 34.38938820571699),
        MapLandmark("Sharm El Sheik", 27.86621455344679, 34.29472135545498),
    )

    /** Centro mappa di default (villaggio / resort). */
    val defaultCenter: LatLng = points[1].latLng()
}
