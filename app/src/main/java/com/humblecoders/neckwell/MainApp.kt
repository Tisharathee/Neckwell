package com.humblecoders.neckwell

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.humblecoders.neckwell.ui.theme.NeckWellAccent
import com.humblecoders.neckwell.ui.theme.NeckWellBackground
import com.humblecoders.neckwell.ui.theme.NeckWellSurface
import com.humblecoders.neckwell.ui.theme.NeckWellTextSecondary

private data class NavItem(val route: String, val label: String, val icon: ImageVector, val isCenter: Boolean = false)

private val mainNavItems = listOf(
    NavItem("home", "Home", Icons.Filled.Home),
    NavItem("analytics", "Analytics", Icons.Outlined.BarChart),
    NavItem("calibration", "Calibrate", Icons.Outlined.RadioButtonChecked, isCenter = true),
    NavItem("settings", "Settings", Icons.Outlined.Settings)
)

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val currentRoute by navController.currentBackStackEntryAsState()
    val route = currentRoute?.destination?.route
    // Hide the bottom bar when inside the full-screen calibration camera flow
    val showBottomBar = route != "calibration"

    Scaffold(
        containerColor = NeckWellBackground,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                    containerColor = NeckWellSurface,
                    tonalElevation = 0.dp
                ) {
                    mainNavItems.forEach { item ->
                        val selected = route == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                if (item.isCenter) {
                                    // Raised green circular button for Calibrate
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .offset(y = (-4).dp)
                                            .shadow(8.dp, CircleShape)
                                            .background(NeckWellAccent, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            item.icon,
                                            contentDescription = item.label,
                                            tint = NeckWellBackground,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(
                                                if (selected) NeckWellAccent.copy(alpha = 0.12f) else Color.Transparent,
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            item.icon,
                                            contentDescription = item.label,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            },
                            label = {
                                Text(
                                    item.label,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NeckWellAccent,
                                selectedTextColor = NeckWellAccent,
                                unselectedIconColor = NeckWellTextSecondary,
                                unselectedTextColor = NeckWellTextSecondary,
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(paddingValues),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) }
        ) {
            composable("home") { HomeScreen() }
            composable("analytics") { AnalyticsScreen() }
            composable("tips") { TipsScreen() }
            composable("settings") { SettingsScreen(navController) }
            composable("calibration") { CalibrationScreen(navController) }
        }
    }
}
