package com.frenky.egypt.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.frenky.egypt.R
import androidx.core.content.ContextCompat
import com.frenky.egypt.data.EgyptApi
import com.frenky.egypt.location.GpsPosition
import com.frenky.egypt.location.LocationHelper
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.flow.catch

private val RESORT_CENTER = LatLng(28.0428139, 34.429247)

private fun isInEgypt(lat: Double, lon: Double): Boolean =
    lat in 22.0..32.0 && lon in 24.0..37.0

/** Solo chiamare se [com.frenky.egypt.map.MapsSupport.canUseGoogleMaps] è true. */
@Composable
fun GoogleMapScreen(
    modifier: Modifier = Modifier,
    groupLocations: List<EgyptApi.UserLocation> = emptyList(),
    showGroupLegend: Boolean = false,
) {
    val context = LocalContext.current
    val locationHelper = remember { LocationHelper(context) }
    var gps by remember { mutableStateOf<GpsPosition?>(null) }
    var permissionGranted by remember { mutableStateOf(locationHelper.hasPermission()) }
    var centered by remember { mutableStateOf(false) }
    var mapType by remember { mutableStateOf(MapType.NORMAL) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        permissionGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(permissionGranted) {
        if (!permissionGranted) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
            return@LaunchedEffect
        }
        locationHelper.locationUpdates()
            .catch { }
            .collect { gps = it }
    }

    val cameraPositionState = rememberCameraPositionState {
        this.position = CameraPosition.fromLatLngZoom(RESORT_CENTER, 14f)
    }

    LaunchedEffect(gps, permissionGranted) {
        if (centered) return@LaunchedEffect
        val pos = gps
        val target = when {
            pos != null && isInEgypt(pos.latitude, pos.longitude) ->
                LatLng(pos.latitude, pos.longitude)
            else -> RESORT_CENTER
        }
        val zoom = if (pos != null && isInEgypt(pos.latitude, pos.longitude)) 16f else 14f
        runCatching {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(target, zoom),
                durationMs = 600,
            )
        }
        centered = true
    }

    val hasFineLocation = permissionGranted &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    Box(modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                mapType = mapType,
                isMyLocationEnabled = hasFineLocation,
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                scrollGesturesEnabled = true,
                zoomGesturesEnabled = true,
                rotationGesturesEnabled = true,
                myLocationButtonEnabled = hasFineLocation,
            ),
        ) {
            gps?.let { p ->
                if (isInEgypt(p.latitude, p.longitude)) {
                    Marker(
                        state = MarkerState(LatLng(p.latitude, p.longitude)),
                        title = "Tu sei qui",
                    )
                }
            }
            groupLocations.forEach { person ->
                if (isInEgypt(person.latitude, person.longitude)) {
                    Marker(
                        state = MarkerState(LatLng(person.latitude, person.longitude)),
                        title = person.name,
                        snippet = "Aggiornato: ${person.updated_at}",
                    )
                }
            }
            Marker(state = MarkerState(RESORT_CENTER), title = "Villaggio Pickalbatros")
        }
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp),
        ) {
            if (showGroupLegend) {
                Text(
                    if (groupLocations.isEmpty()) {
                        "In attesa posizioni del gruppo (serve GPS attivo e app in background)"
                    } else {
                        "Gruppo: ${groupLocations.joinToString { it.name }}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                gps?.let { "GPS: ${"%.5f".format(it.latitude)}, ${"%.5f".format(it.longitude)}" }
                    ?: "Fuori Egitto → centrato sul villaggio",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MapTypeChip(
                label = stringResource(R.string.map_type_normal),
                selected = mapType == MapType.NORMAL,
                onClick = { mapType = MapType.NORMAL },
            )
            MapTypeChip(
                label = stringResource(R.string.map_type_satellite),
                selected = mapType == MapType.SATELLITE,
                onClick = { mapType = MapType.SATELLITE },
            )
            MapTypeChip(
                label = stringResource(R.string.map_type_hybrid),
                selected = mapType == MapType.HYBRID,
                onClick = { mapType = MapType.HYBRID },
            )
        }
    }
}

@Composable
private fun MapTypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Black,
            labelColor = Color.White,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
