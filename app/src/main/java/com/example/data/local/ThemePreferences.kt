package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "journal_theme_prefs")

class ThemePreferences(private val context: Context) {
  private val themeKey = stringPreferencesKey("theme_mode")

  val themeMode: Flow<ThemeMode> = context.themeDataStore.data.map { prefs ->
    when (prefs[themeKey]) {
      ThemeMode.LIGHT.name -> ThemeMode.LIGHT
      ThemeMode.DARK.name -> ThemeMode.DARK
      else -> ThemeMode.SYSTEM
    }
  }

  suspend fun setThemeMode(mode: ThemeMode) {
    context.themeDataStore.edit { prefs ->
      prefs[themeKey] = mode.name
    }
  }
}
