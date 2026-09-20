package org.mwangaza.app.ui.navigation

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import org.mwangaza.app.data.AppContainer
import core.domain.model.ProductType
import org.mwangaza.app.ui.screens.DashboardScreen
import org.mwangaza.app.scanner.ProductScanDraft
import org.mwangaza.app.ui.screens.DispensingScreen
import org.mwangaza.app.ui.screens.ExpiryAlertsScreen
import org.mwangaza.app.ui.screens.GoodsReceivingScreen
import org.mwangaza.app.ui.screens.InventoryScreen
import org.mwangaza.app.ui.screens.NotificationsScreen
import org.mwangaza.app.ui.screens.PlaceholderScreen
import org.mwangaza.app.ui.screens.ProductScannerScreen
import org.mwangaza.app.ui.screens.ProductsScreen
import org.mwangaza.app.ui.screens.ReportsScreen
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
fun AppNavigation(
    container: AppContainer? = null
) {
    val context = LocalContext.current
    val appContainer = container ?: remember(context) { AppContainer(context.applicationContext) }

    var currentBottomTab by remember { mutableStateOf<BottomNavItem>(BottomNavItem.Dashboard) }
    var currentFeature by remember { mutableStateOf<String?>(null) }
    var pendingScanDraft by remember { mutableStateOf<ProductScanDraft?>(null) }
    var selectedScanProductType by remember { mutableStateOf<ProductType?>(null) }
    var selectedScanGenericName by remember { mutableStateOf<String?>(null) }
    var showExitConfirmation by remember { mutableStateOf(false) }

    BackHandler(enabled = currentFeature != null) {
        currentFeature = if (currentFeature == "product-scanner") {
            "products"
        } else {
            null
        }
    }

    BackHandler(enabled = currentFeature == null && currentBottomTab != BottomNavItem.Dashboard) {
        currentBottomTab = BottomNavItem.Dashboard
    }

    BackHandler(enabled = currentFeature == null && currentBottomTab == BottomNavItem.Dashboard) {
        showExitConfirmation = true
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("Exit Application") },
            text = { Text("Are you sure you want to exit Mwangaza Ground?") },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmation = false
                        (context as? Activity)?.finish()
                    }
                ) {
                    Text("Exit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        bottomBar = {
            if (currentFeature == null) {
                NavigationBar {
                    val bottomItems = listOf(
                        BottomNavItem.Dashboard,
                        BottomNavItem.Notifications,
                        BottomNavItem.Settings
                    )
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (currentFeature == null) innerPadding else PaddingValues())
        ) {
            when {
                currentFeature == "products" -> {
                    ProductsScreen(
                        container = appContainer,
                        onBack = { currentFeature = null },
                        onScanProduct = { productType, genericName ->
                            selectedScanProductType = productType
                            selectedScanGenericName = genericName
                            currentFeature = "product-scanner"
                        },
                        initialScanDraft = pendingScanDraft,
                        onScanDraftConsumed = { pendingScanDraft = null }
                    )
                }
                currentFeature == "product-scanner" -> {
                    ProductScannerScreen(
                        selectedProductType = selectedScanProductType ?: ProductType.OTHER_HEALTH_COMMODITY,
                        genericNameContext = selectedScanGenericName,
                        onConfirmed = { draft ->
                            pendingScanDraft = draft
                            selectedScanGenericName = null
                            currentFeature = "products"
                        }
                    )
                }
                currentFeature == "receiving" -> {
                    GoodsReceivingScreen(
                        container = appContainer,
                        onBack = { currentFeature = null }
                    )
                }
                currentFeature == "dispensing" -> {
                    DispensingScreen(
                        container = appContainer,
                        onBack = { currentFeature = null }
                    )
                }
                currentFeature == "inventory" -> {
                    InventoryScreen(
                        container = appContainer,
                        onBack = { currentFeature = null }
                    )
                }
                currentFeature == "alerts" -> {
                    ExpiryAlertsScreen(
                        container = appContainer,
                        onBack = { currentFeature = null }
                    )
                }
                currentFeature == "reports" -> {
                    ReportsScreen(
                        container = appContainer,
                        onBack = { currentFeature = null }
                    )
                }
                currentFeature == "suppliers" -> {
                    PlaceholderScreen(
                        title = "Suppliers",
                        explanation = "Supplier entity identity is defined in database schema, but automated supplier account ledger and procurement orchestration services are pending future architectural reconciliation. Use Goods Receiving for supplier invoice & batch tracking.",
                        onBack = { currentFeature = null }
                    )
                }
                currentFeature == "adjustments" -> {
                    PlaceholderScreen(
                        title = "Stock Adjustments",
                        explanation = "Direct stock adjustments require atomic inventory cost layer reallocation and write-off ledger reconciliation to maintain zero-drift FIFO integrity. Currently, intake is recorded via Goods Receiving and reversals via Dispensing Void.",
                        onBack = { currentFeature = null }
                    )
                }
                currentFeature != null -> {
                    PlaceholderScreen(
                        title = "Feature",
                        onBack = { currentFeature = null }
                    )
                }
                currentBottomTab is BottomNavItem.Dashboard -> {
                    DashboardScreen(
                        onFeatureClick = { route -> currentFeature = route },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                currentBottomTab is BottomNavItem.Notifications -> {
                    NotificationsScreen(modifier = Modifier.fillMaxSize())
                }
                currentBottomTab is BottomNavItem.Settings -> {
                    SettingsScreen(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
