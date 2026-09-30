package com.jpdrw.household

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.jpdrw.household.data.ThemeMode
import com.jpdrw.household.ui.HouseholdNavHost
import com.jpdrw.household.ui.theme.HouseholdTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as HouseholdApp
        val repository = app.repository

        setContent {
            val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            val themeMode by app.appPrefs.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            HouseholdTheme(themeMode = themeMode) {
                HouseholdNavHost(repository, app.appPrefs)
            }
        }
    }
}
