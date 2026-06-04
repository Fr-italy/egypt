package com.frenky.egypt.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.frenky.egypt.data.EgyptApi
import com.frenky.egypt.location.GpsPosition
import com.frenky.egypt.location.LocationHelper
import com.frenky.egypt.map.MapBounds
import com.frenky.egypt.map.MapGeoref
import kotlinx.coroutines.flow.catch

@Composable
fun OfflineMapScreen(
    modifier: Modifier = Modifier,
    assetPath: String,
    title: String,
    bounds: MapBounds,
    subtitle: String? = null,
    groupLocations: List<EgyptApi.UserLocation> = emptyList(),
    showGroupLegend: Boolean = false,
) {
    val context = LocalContext.current
    val locationHelper = remember { LocationHelper(context) }
    var position by remember { mutableStateOf<GpsPosition?>(null) }
    var permissionGranted by remember { mutableStateOf(locationHelper.hasPermission()) }
    var mapFraction by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var outsideBounds by remember { mutableStateOf(false) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

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
            .collect { gps ->
                position = gps
                val frac = MapGeoref.latLonToFraction(gps.latitude, gps.longitude, bounds)
                mapFraction = frac
                outsideBounds = frac == null
            }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showGroupLegend) {
            Text(
                if (groupLocations.isEmpty()) {
                    "In attesa posizioni del gruppo (GPS + app attiva)"
                } else {
                    "Gruppo: ${groupLocations.joinToString { it.name }}"
                },
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        position?.let { gps ->
            Text(
                "GPS: ${"%.5f".format(gps.latitude)}, ${"%.5f".format(gps.longitude)}" +
                    if (outsideBounds) " — fuori mappa" else "",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = if (outsideBounds) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } ?: Text(
            "In attesa del GPS…",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { boxSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val transformModifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = offsetY
            }
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("file:///android_asset/$assetPath")
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().then(transformModifier),
            )
            if (boxSize.width > 0) {
                Canvas(Modifier.fillMaxSize().then(transformModifier)) {
                    groupLocations.forEach { person ->
                        val frac = MapGeoref.latLonToFraction(
                            person.latitude,
                            person.longitude,
                            bounds,
                        ) ?: return@forEach
                        val (fx, fy) = frac
                        val cx = size.width * fx
                        val cy = size.height * fy
                        drawCircle(Color(0xFF42A5F5), radius = 12f, center = Offset(cx, cy))
                        drawCircle(Color.White, radius = 12f, center = Offset(cx, cy), style = Stroke(2f))
                    }
                    mapFraction?.let { (fx, fy) ->
                        val cx = size.width * fx
                        val cy = size.height * fy
                        drawCircle(Color(0xFFE53935), radius = 14f, center = Offset(cx, cy))
                        drawCircle(Color.White, radius = 14f, center = Offset(cx, cy), style = Stroke(3f))
                    }
                }
                groupLocations.forEach { person ->
                    val frac = MapGeoref.latLonToFraction(
                        person.latitude,
                        person.longitude,
                        bounds,
                    ) ?: return@forEach
                    val (fx, fy) = frac
                    Text(
                        person.name,
                        color = Color(0xFF42A5F5),
                        fontSize = 11.sp,
                        modifier = Modifier
                            .then(transformModifier)
                            .offset(
                                x = (boxSize.width * fx * scale + offsetX - 8).dp,
                                y = (boxSize.height * fy * scale + offsetY - 28).dp,
                            ),
                    )
                }
            }
        }
        Text(
            "Mappa offline · pizzica per zoomare",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
