package com.frenky.egypt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.frenky.egypt.ui.SplashScreen
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.frenky.egypt.ui.EgyptAppContent
import com.frenky.egypt.ui.OnboardingScreen
import com.frenky.egypt.ui.TAB_INDEX_CHAT
import com.frenky.egypt.ui.theme.EgyptTheme

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"
        const val TAB_CHAT = "chat"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as EgyptApp
        val openChatTab = intent?.getStringExtra(EXTRA_OPEN_TAB) == TAB_CHAT

        setContent {
            val userName by app.preferences.userName.collectAsState(initial = null)
            val userId by app.preferences.userId.collectAsState(initial = null)
            val config by app.configRepository.config.collectAsState(initial = com.frenky.egypt.data.EgyptConfig.defaults())

            EgyptTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black,
                ) {
                    var showSplash by remember { mutableStateOf(true) }
                    when {
                        showSplash -> SplashScreen(onFinished = { showSplash = false })
                        userName.isNullOrBlank() || userId.isNullOrBlank() -> {
                            OnboardingScreen(
                                preferences = app.preferences,
                                configRepository = app.configRepository,
                            )
                        }
                        else -> {
                            EgyptAppContent(
                                userName = userName!!,
                                userId = userId!!,
                                config = config,
                                configRepository = app.configRepository,
                                preferences = app.preferences,
                                initialTab = if (openChatTab) TAB_INDEX_CHAT else 0,
                            )
                        }
                    }
                }
            }
        }
    }
}
