package com.beacon.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val BeaconBlue = Color(0xFF1A73E8)
private val BeaconBlueDark = Color(0xFF8AB4F8)

private val LightColors = lightColorScheme(
    primary = BeaconBlue,
    secondary = Color(0xFF00897B),
    tertiary = Color(0xFFF4511E),
)

private val DarkColors = darkColorScheme(
    primary = BeaconBlueDark,
    secondary = Color(0xFF4DB6AC),
    tertiary = Color(0xFFFF8A65),
)

@Composable
fun BeaconTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
