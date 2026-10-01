package com.jpdrw.household.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jpdrw.household.data.AppPrefs
import com.jpdrw.household.data.Repository
import com.jpdrw.household.ui.activities.FamilyActivitiesScreen
import com.jpdrw.household.ui.chores.ChoresScreen
import com.jpdrw.household.ui.mapper.TaskMapperScreen
import com.jpdrw.household.ui.parental.ParentalActivitiesScreen
import com.jpdrw.household.ui.stats.StatsScreen

private sealed class Destination(val route: String, val label: String) {
    data object Chores : Destination("chores", "Chores")
    data object Mapper : Destination("mapper", "Mapper")
    data object Activities : Destination("activities", "Activities")
    data object Parental : Destination("parental", "For Us")
    data object Stats : Destination("stats", "Admin")
}

private val destinations = listOf(Destination.Chores, Destination.Mapper, Destination.Activities, Destination.Parental, Destination.Stats)

@Composable
fun HouseholdNavHost(repository: Repository, appPrefs: AppPrefs) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination
            NavigationBar {
                destinations.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            val icon = when (destination) {
                                Destination.Chores -> Icons.Filled.Checklist
                                Destination.Mapper -> Icons.Filled.Map
                                Destination.Activities -> Icons.Filled.CheckCircle
                                Destination.Parental -> Icons.Filled.Favorite
                                Destination.Stats -> Icons.Filled.Settings
                            }
                            Icon(icon, contentDescription = destination.label)
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Chores.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.Chores.route) { ChoresScreen(repository) }
            composable(Destination.Mapper.route) { TaskMapperScreen(repository, appPrefs) }
            composable(Destination.Activities.route) { FamilyActivitiesScreen(repository) }
            composable(Destination.Parental.route) { ParentalActivitiesScreen(repository, appPrefs) }
            composable(Destination.Stats.route) { StatsScreen(repository, appPrefs) }
        }
    }
}
