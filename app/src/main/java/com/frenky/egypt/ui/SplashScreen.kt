package com.frenky.egypt.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.frenky.egypt.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.88f) }
    val textAlpha = remember { Animatable(0f) }
    val exitAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        logoScale.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        delay(350)
        textAlpha.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        delay(1400)
        launch {
            exitAlpha.animateTo(0f, tween(450, easing = FastOutSlowInEasing))
        }
        logoAlpha.animateTo(0f, tween(450, easing = FastOutSlowInEasing))
        textAlpha.animateTo(0f, tween(450, easing = FastOutSlowInEasing))
        delay(480)
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .alpha(exitAlpha.value)
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.fr_italy_logo),
                contentDescription = "FR Italy",
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .alpha(logoAlpha.value)
                    .scale(logoScale.value),
                contentScale = ContentScale.Fit,
            )
            Text(
                text = stringResource(R.string.splash_credit),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.92f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 28.dp)
                    .alpha(textAlpha.value),
            )
        }
    }
}
