package com.frenky.egypt.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.frenky.egypt.data.ConfigRepository
import com.frenky.egypt.data.EgyptConfig
import com.frenky.egypt.ui.screens.ConverterScreen
import com.frenky.egypt.ui.screens.TripScreen
import com.frenky.egypt.ui.screens.MessagesScreen
import com.frenky.egypt.ui.screens.NabqMapScreen
import com.frenky.egypt.ui.screens.OfflineMapScreen
import com.frenky.egypt.ui.components.CopyrightFooter
import com.frenky.egypt.ui.screens.PhrasesScreen
import com.frenky.egypt.ui.screens.SignTranslatorScreen

private data class Tab(val label: String, val icon: ImageVector)

@Composable
fun EgyptAppContent(
    userName: String,
    userId: String,
    config: EgyptConfig,
    configRepository: ConfigRepository,
) {
    ConfigSync(configRepository = configRepository, userId = userId, userName = userName)
    LocationSync(userId = userId, userName = userName)

    val tabs = listOf(
        Tab("Euro", Icons.Default.AttachMoney),
        Tab("Nabq", Icons.Default.Map),
        Tab("Villaggio", Icons.Default.Place),
        Tab("Frasi", Icons.Default.Translate),
        Tab("Insegne", Icons.Default.DocumentScanner),
        Tab("Viaggio", Icons.Default.Luggage),
        Tab("Chat", Icons.Default.Chat),
    )
    var selected by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Black,
        bottomBar = {
            Column {
                CopyrightFooter()
                NavigationBar {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = {},
                            alwaysShowLabel = false,
                        )
                    }
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding).fillMaxSize()
        when (selected) {
            0 -> ConverterScreen(modifier, config)
            1 -> NabqMapScreen(modifier = modifier, config = config)
            2 -> OfflineMapScreen(
                modifier = modifier,
                assetPath = "maps/resort_map.png",
                title = "Mappa villaggio Pickalbatros",
                bounds = config.resortMapBounds(),
            )
            3 -> PhrasesScreen(modifier, config.phrases_extra)
            4 -> SignTranslatorScreen(modifier)
            5 -> TripScreen(modifier, config, userId, userName, configRepository)
            6 -> MessagesScreen(modifier, userName, userId, configRepository)
        }
    }
}
