package com.jpdrw.household.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Context.dataStore by preferencesDataStore(name = "settings")
private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")

/** User-chosen theme override, independent of the system setting. Stored via DataStore. */
class ThemePrefs(private val context: Context) {
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[THEME_MODE_KEY]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[THEME_MODE_KEY] = mode.name }
    }
}
