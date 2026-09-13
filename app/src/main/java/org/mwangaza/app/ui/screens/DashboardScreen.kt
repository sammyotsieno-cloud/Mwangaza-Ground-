package org.mwangaza.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mwangaza.app.ui.components.FeatureCard

data class FeatureItem(
    val title: String,
    val icon: ImageVector,
    val route: String
)

@Composable
fun DashboardScreen(
    onFeatureClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val features = listOf(
        FeatureItem("Goods Receiving", Icons.Outlined.LocalShipping, "receiving"),
        FeatureItem("Dispensing / Sales", Icons.Outlined.ShoppingCart, "dispensing"),
        FeatureItem("Inventory", Icons.Outlined.Inventory, "inventory"),
        FeatureItem("Products", Icons.Outlined.MedicalServices, "products"),
        FeatureItem("Expiry Alerts", Icons.Outlined.Warning, "alerts"),
        FeatureItem("Reports", Icons.Outlined.Assessment, "reports"),
        FeatureItem("Suppliers", Icons.Outlined.People, "suppliers"),
        FeatureItem("Stock Adjustments", Icons.Outlined.Sync, "adjustments")
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Mwangaza Medical Centre",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(features) { feature ->
                FeatureCard(
                    title = feature.title,
                    icon = feature.icon,
                    onClick = { onFeatureClick(feature.route) }
                )
            }
        }
    }
}
