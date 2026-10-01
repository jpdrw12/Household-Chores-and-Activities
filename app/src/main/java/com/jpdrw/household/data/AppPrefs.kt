package com.jpdrw.household.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Context.dataStore by preferencesDataStore(name = "settings")
private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
private val SPICY_ENABLED_KEY = booleanPreferencesKey("spicy_content_enabled")

/** App-wide settings independent of daily data, stored via DataStore. Admin-only, reached from the Admin tab. */
class AppPrefs(private val context: Context) {
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[THEME_MODE_KEY]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[THEME_MODE_KEY] = mode.name }
    }

    /** Off by default. Gates the "Intimate" (solo/together) parental activity suggestions from view. */
    val spicyContentEnabled: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[SPICY_ENABLED_KEY] ?: false }

    suspend fun setSpicyContentEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SPICY_ENABLED_KEY] = enabled }
    }
}
