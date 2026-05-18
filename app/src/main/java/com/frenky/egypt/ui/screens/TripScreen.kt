package com.frenky.egypt.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.frenky.egypt.data.ConfigRepository
import com.frenky.egypt.data.EgyptConfig

@Composable
fun TripScreen(
    modifier: Modifier = Modifier,
    config: EgyptConfig,
    userId: String,
    userName: String,
    configRepository: ConfigRepository,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Checklist", "Documenti")

    Column(modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(title) },
                )
            }
        }
        HorizontalDivider()
        when (tab) {
            0 -> ChecklistScreen(Modifier.fillMaxSize(), config, userId, userName, configRepository)
            1 -> DocumentsScreen(Modifier.fillMaxSize(), config)
        }
    }
}
