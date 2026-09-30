package com.jpdrw.household.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue = Color(0xFF4F7DF3)
private val LightScheme = lightColorScheme(primary = Blue, secondary = Color(0xFF6FCF97))
private val DarkScheme = darkColorScheme(primary = Blue, secondary = Color(0xFF6FCF97))

@Composable
fun HouseholdTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkScheme else LightScheme
    MaterialTheme(colorScheme = colors, content = content)
}
