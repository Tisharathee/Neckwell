package com.humblecoders.neckwell

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.White
            ) {
                NavigationBarItem(
                    selected = currentRoute == "home",
                    onClick = { navController.navigate("home") },
                    icon = { Text("🏠") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = currentRoute == "analytics",
                    onClick = { navController.navigate("analytics") },
                    icon = { Text("📊") },
                    label = { Text("Analytics") }
                )
                NavigationBarItem(
                    selected = currentRoute == "tips",
                    onClick = { navController.navigate("tips") },
                    icon = { Text("💡") },
                    label = { Text("Tips") }
                )
                NavigationBarItem(
                    selected = currentRoute == "settings",
                    onClick = { navController.navigate("settings") },
                    icon = { Text("⚙️") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(paddingValues),
            enterTransition = { slideInHorizontally(initialOffsetX = { 300 }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -300 }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)) },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -300 }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { 300 }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)) }
        ) {
            composable("home") { HomeScreen() }
            composable("analytics") { AnalyticsScreen() }
            composable("tips") { TipsScreen() }
            composable("settings") { SettingsScreen(navController = navController) }
            composable("calibration") { CalibrationScreen(navController = navController) }
        }
    }
}