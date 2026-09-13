package org.mwangaza.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.mwangaza.app.ui.screens.DashboardScreen
import org.mwangaza.app.ui.screens.NotificationsScreen
import org.mwangaza.app.ui.screens.PlaceholderScreen
import org.mwangaza.app.ui.screens.SettingsScreen

private sealed class BottomNavItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Dashboard : BottomNavItem("Dashboard", Icons.Filled.Dashboard, Icons.Outlined.Dashboard)
    object Notifications : BottomNavItem("Notifications", Icons.Filled.Notifications, Icons.Outlined.Notifications)
    object Settings : BottomNavItem("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun AppNavigation() {
    var currentBottomTab by remember { mutableStateOf<BottomNavItem>(BottomNavItem.Dashboard) }
    var currentFeature by remember { mutableStateOf<String?>(null) }

    val bottomItems = listOf(
        BottomNavItem.Dashboard,
        BottomNavItem.Notifications,
        BottomNavItem.Settings
    )

    Scaffold(
        bottomBar = {
            if (currentFeature == null) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        val selected = currentBottomTab == item
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                currentBottomTab = item
                                currentFeature = null
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title
                                )
                            },
                            label = { Text(item.title) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        when {
            currentFeature != null -> {
                val title = when (currentFeature) {
                    "receiving" -> "Goods Receiving"
                    "dispensing" -> "Dispensing / Sales"
                    "inventory" -> "Inventory"
                    "products" -> "Products"
                    "alerts" -> "Expiry Alerts"
                    "reports" -> "Reports"
                    "suppliers" -> "Suppliers"
                    "adjustments" -> "Stock Adjustments"
                    else -> "Feature"
                }
                PlaceholderScreen(
                    title = title,
                    modifier = Modifier.padding(innerPadding)
                )
            }

            currentBottomTab is BottomNavItem.Dashboard -> {
                DashboardScreen(
                    onFeatureClick = { route -> currentFeature = route },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            currentBottomTab is BottomNavItem.Notifications -> {
                NotificationsScreen(modifier = Modifier.padding(innerPadding))
            }

            currentBottomTab is BottomNavItem.Settings -> {
                SettingsScreen(modifier = Modifier.padding(innerPadding))
            }
        }
    }
}
