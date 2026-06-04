package com.frenky.egypt.ui.screens

import android.Manifest
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.frenky.egypt.map.MapLandmarks
import kotlinx.coroutines.flow.catch
import kotlin.math.roundToInt

/**
 * @param fillViewport true per mappa hotel: riempie l'area, senza bande nere,
 *   pan bloccato a zoom 1, overlay testi sulla mappa (area stabile).
 */
@Composable
fun OfflineMapScreen(
    modifier: Modifier = Modifier,
    assetPath: String,
    title: String,
    bounds: MapBounds,
    subtitle: String? = null,
    groupLocations: List<EgyptApi.UserLocation> = emptyList(),
    showGroupLegend: Boolean = false,
    fillViewport: Boolean = false,
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

    val imageSize = remember(assetPath) { readAssetImageSize(context, assetPath) }

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
                mapFraction = MapGeoref.latLonToFraction(gps.latitude, gps.longitude, bounds)
                outsideBounds = mapFraction == null
            }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (!fillViewport) {
            MapHeaderTexts(
                title = title,
                subtitle = subtitle,
                showGroupLegend = showGroupLegend,
                groupLocations = groupLocations,
                position = position,
                outsideBounds = outsideBounds,
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            val viewportW = constraints.maxWidth.toFloat()
            val viewportH = constraints.maxHeight.toFloat()
            val layout = remember(viewportW, viewportH, imageSize, fillViewport) {
                computeMapLayout(viewportW, viewportH, imageSize, fillViewport)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(fillViewport, viewportW, viewportH) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val (s, ox, oy) = clampMapTransform(
                                scale = scale * zoom,
                                offsetX = offsetX + pan.x,
                                offsetY = offsetY + pan.y,
                                viewportW = viewportW,
                                viewportH = viewportH,
                                fillViewport = fillViewport,
                            )
                            scale = s
                            offsetX = ox
                            offsetY = oy
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val layerModifier = Modifier
                    .graphicsLayer {
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
                    contentScale = if (fillViewport) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().then(layerModifier),
                )

                Canvas(Modifier.fillMaxSize().then(layerModifier)) {
                    drawMapMarkers(layout, bounds, groupLocations, mapFraction)
                }

                MapMarkerLabels(
                    layout = layout,
                    bounds = bounds,
                    groupLocations = groupLocations,
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY,
                )

                if (fillViewport) {
                    Column(
                        Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        MapHeaderTexts(
                            title = title,
                            subtitle = null,
                            showGroupLegend = showGroupLegend,
                            groupLocations = groupLocations,
                            position = position,
                            outsideBounds = outsideBounds,
                        )
                    }
                }
            }
        }

        Text(
            if (fillViewport) "Mappa hotel · pizzica per zoomare" else "Mappa offline · pizzica per zoomare",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MapHeaderTexts(
    title: String,
    subtitle: String?,
    showGroupLegend: Boolean,
    groupLocations: List<EgyptApi.UserLocation>,
    position: GpsPosition?,
    outsideBounds: Boolean,
) {
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
}

@Composable
private fun MapMarkerLabels(
    layout: MapMarkerLayout,
    bounds: MapBounds,
    groupLocations: List<EgyptApi.UserLocation>,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
) {
    Box(Modifier.fillMaxSize()) {
        MapLandmarks.points.forEach { place ->
            val frac = MapGeoref.latLonToFraction(place.latitude, place.longitude, bounds) ?: return@forEach
            val (fx, fy) = frac
            LabelAt(
                text = place.name,
                color = Color(0xFFC9A227),
                xPx = (layout.left + layout.width * fx) * scale + offsetX - 8f,
                yPx = (layout.top + layout.height * fy) * scale + offsetY - 28f,
            )
        }
        groupLocations.forEach { person ->
            val frac = MapGeoref.latLonToFraction(person.latitude, person.longitude, bounds) ?: return@forEach
            val (fx, fy) = frac
            LabelAt(
                text = person.name,
                color = Color(0xFF42A5F5),
                xPx = (layout.left + layout.width * fx) * scale + offsetX - 8f,
                yPx = (layout.top + layout.height * fy) * scale + offsetY - 28f,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun BoxScope.LabelAt(
    text: String,
    color: Color,
    xPx: Float,
    yPx: Float,
    fontSize: androidx.compose.ui.unit.TextUnit = 10.sp,
) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        modifier = Modifier.align(Alignment.TopStart).graphicsLayer {
            translationX = xPx
            translationY = yPx
        },
    )
}

private data class MapMarkerLayout(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

private fun readAssetImageSize(context: android.content.Context, assetPath: String): IntSize =
    runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, opts) }
        IntSize(opts.outWidth.coerceAtLeast(1), opts.outHeight.coerceAtLeast(1))
    }.getOrDefault(IntSize(1, 1))

private fun computeMapLayout(
    viewportW: Float,
    viewportH: Float,
    image: IntSize,
    fillViewport: Boolean,
): MapMarkerLayout {
    if (viewportW <= 0f || viewportH <= 0f) {
        return MapMarkerLayout(0f, 0f, 0f, 0f)
    }
    if (fillViewport) {
        return MapMarkerLayout(0f, 0f, viewportW, viewportH)
    }
    val iw = image.width.toFloat()
    val ih = image.height.toFloat()
    val imageAspect = iw / ih
    val viewAspect = viewportW / viewportH
    return if (viewAspect > imageAspect) {
        val h = viewportH
        val w = h * imageAspect
        MapMarkerLayout((viewportW - w) / 2f, 0f, w, h)
    } else {
        val w = viewportW
        val h = w / imageAspect
        MapMarkerLayout(0f, (viewportH - h) / 2f, w, h)
    }
}

private fun clampMapTransform(
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    viewportW: Float,
    viewportH: Float,
    fillViewport: Boolean,
): Triple<Float, Float, Float> {
    if (viewportW <= 0f || viewportH <= 0f) {
        return Triple(1f, 0f, 0f)
    }
    val minScale = if (fillViewport) 1f else 0.5f
    var s = scale.coerceIn(minScale, 5f)
    if (fillViewport && s <= 1f) {
        return Triple(1f, 0f, 0f)
    }
    val maxX = viewportW * (s - 1f) / 2f
    val maxY = viewportH * (s - 1f) / 2f
    return Triple(s, offsetX.coerceIn(-maxX, maxX), offsetY.coerceIn(-maxY, maxY))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMapMarkers(
    layout: MapMarkerLayout,
    bounds: MapBounds,
    groupLocations: List<EgyptApi.UserLocation>,
    mapFraction: Pair<Float, Float>?,
) {
    MapLandmarks.points.forEach { place ->
        val frac = MapGeoref.latLonToFraction(place.latitude, place.longitude, bounds) ?: return@forEach
        val cx = layout.left + layout.width * frac.first
        val cy = layout.top + layout.height * frac.second
        drawCircle(Color(0xFFC9A227), 11f, Offset(cx, cy))
        drawCircle(Color.Black, 11f, Offset(cx, cy), style = Stroke(2f))
    }
    groupLocations.forEach { person ->
        val frac = MapGeoref.latLonToFraction(person.latitude, person.longitude, bounds) ?: return@forEach
        val cx = layout.left + layout.width * frac.first
        val cy = layout.top + layout.height * frac.second
        drawCircle(Color(0xFF42A5F5), 12f, Offset(cx, cy))
        drawCircle(Color.White, 12f, Offset(cx, cy), style = Stroke(2f))
    }
    mapFraction?.let { (fx, fy) ->
        val cx = layout.left + layout.width * fx
        val cy = layout.top + layout.height * fy
        drawCircle(Color(0xFFE53935), 14f, Offset(cx, cy))
        drawCircle(Color.White, 14f, Offset(cx, cy), style = Stroke(3f))
    }
}
