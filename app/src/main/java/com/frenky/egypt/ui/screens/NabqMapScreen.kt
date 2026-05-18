package com.frenky.egypt.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.frenky.egypt.data.EgyptConfig
import com.frenky.egypt.map.MapsSupport

/**
 * Mappa Nabq: Google Maps se disponibile, altrimenti mappa offline (non crasha).
 */
@Composable
fun NabqMapScreen(modifier: Modifier = Modifier, config: EgyptConfig) {
    val context = LocalContext.current
    val useGoogle = remember(context) { MapsSupport.canUseGoogleMaps(context) }

    if (useGoogle) {
        GoogleMapScreen(modifier = modifier)
    } else {
        OfflineMapScreen(
            modifier = modifier,
            assetPath = "maps/area_map.png",
            title = "Mappa Nabq",
            bounds = config.areaMapBounds(),
            subtitle = if (!MapsSupport.hasApiKey()) {
                "Satellite offline · zoom con due dita · Per Google Maps: chiave API"
            } else {
                "Satellite offline · Google Play Services non disponibile"
            },
        )
    }
}
