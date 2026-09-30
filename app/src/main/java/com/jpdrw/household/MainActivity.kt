package com.jpdrw.household

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jpdrw.household.ui.HouseholdNavHost
import com.jpdrw.household.ui.theme.HouseholdTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as HouseholdApp).repository
        setContent {
            HouseholdTheme {
                HouseholdNavHost(repository)
            }
        }
    }
}
