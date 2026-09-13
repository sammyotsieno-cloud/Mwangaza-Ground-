package org.mwangaza.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import core.domain.model.ProductMaster
import core.domain.model.StockBatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mwangaza.app.data.AppContainer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ExpiryRisk {
    EXPIRED,
    CRITICAL_30_DAYS,
    WARNING_90_DAYS,
    GOOD,
    NO_EXPIRY
}

data class BatchExpiryItem(
    val batch: StockBatch,
    val product: ProductMaster?,
    val physicalStockUnits: Long,
    val risk: ExpiryRisk,
    val formattedExpiry: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpiryAlertsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: All Batches, 1: Expired, 2: Expiring Soon (<90d)
    var isLoading by remember { mutableStateOf(true) }
    var batchItems by remember { mutableStateOf<List<BatchExpiryItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            withContext(Dispatchers.IO) {
                val batches = container.stockBatchDao.getAllBatches()
                val products = container.productMasterDao.getAllProducts().associateBy { it.id }
                val movements = container.stockMovementDao.getAllMovements()
                val movementsByBatch = movements.filter { it.stockBatchId != null }.groupBy { it.stockBatchId!! }

                val cal = Calendar.getInstance()
                val currentYear = cal.get(Calendar.YEAR)
                val currentMonth = cal.get(Calendar.MONTH) + 1
                val currentDay = cal.get(Calendar.DAY_OF_MONTH)
                val todayInt = (currentYear * 10000) + (currentMonth * 100) + currentDay

                cal.add(Calendar.DAY_OF_YEAR, 30)
                val in30DaysInt = (cal.get(Calendar.YEAR) * 10000) + ((cal.get(Calendar.MONTH) + 1) * 100) + cal.get(Calendar.DAY_OF_MONTH)

                cal.add(Calendar.DAY_OF_YEAR, 60) // 90 days total
                val in90DaysInt = (cal.get(Calendar.YEAR) * 10000) + ((cal.get(Calendar.MONTH) + 1) * 100) + cal.get(Calendar.DAY_OF_MONTH)

                batchItems = batches.map { b ->
                    val bMovements = movementsByBatch[b.id] ?: emptyList()
                    val onHand = bMovements.sumOf { it.quantity.storageUnits }
                    val prod = products[b.productId]

                    val risk = when {
                        b.expiryDateInt == -1 -> ExpiryRisk.NO_EXPIRY
                        b.expiryDateInt <= todayInt -> ExpiryRisk.EXPIRED
                        b.expiryDateInt <= in30DaysInt -> ExpiryRisk.CRITICAL_30_DAYS
                        b.expiryDateInt <= in90DaysInt -> ExpiryRisk.WARNING_90_DAYS
                        else -> ExpiryRisk.GOOD
                    }

                    val formatted = if (b.expiryDateInt == -1) {
                        "No expiry recorded"
                    } else {
                        val y = b.expiryDateInt / 10000
                        val m = (b.expiryDateInt % 10000) / 100
                        val d = b.expiryDateInt % 100
                        String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
                    }

                    BatchExpiryItem(
                        batch = b,
                        product = prod,
                        physicalStockUnits = onHand,
                        risk = risk,
                        formattedExpiry = formatted
                    )
                }
            }
            isLoading = false
        }
    }

    val filteredList = remember(batchItems, selectedTab) {
        when (selectedTab) {
            1 -> batchItems.filter { it.risk == ExpiryRisk.EXPIRED }
            2 -> batchItems.filter { it.risk == ExpiryRisk.CRITICAL_30_DAYS || it.risk == ExpiryRisk.WARNING_90_DAYS }
            else -> batchItems
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Expiry Alerts & FEFO") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val expiredCount = batchItems.count { it.risk == ExpiryRisk.EXPIRED }
            val warningCount = batchItems.count { it.risk == ExpiryRisk.CRITICAL_30_DAYS || it.risk == ExpiryRisk.WARNING_90_DAYS }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("All (${batchItems.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Expired ($expiredCount)") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Expiring Soon ($warningCount)") }
                )
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filteredList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (selectedTab == 1) "No expired batches on record!" else if (selectedTab == 2) "No batches expiring within 90 days." else "No batches found.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredList, key = { it.batch.id }) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = when (item.risk) {
                                    ExpiryRisk.EXPIRED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                                    ExpiryRisk.CRITICAL_30_DAYS -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                                    else -> MaterialTheme.colorScheme.surface
                                }
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.product?.displayName ?: "Unknown Product",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Batch: ${item.batch.batchNumber}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Surface(
                                        color = when (item.risk) {
                                            ExpiryRisk.EXPIRED -> MaterialTheme.colorScheme.error
                                            ExpiryRisk.CRITICAL_30_DAYS -> MaterialTheme.colorScheme.errorContainer
                                            ExpiryRisk.WARNING_90_DAYS -> MaterialTheme.colorScheme.secondaryContainer
                                            ExpiryRisk.GOOD -> MaterialTheme.colorScheme.primaryContainer
                                            ExpiryRisk.NO_EXPIRY -> MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            text = when (item.risk) {
                                                ExpiryRisk.EXPIRED -> "EXPIRED"
                                                ExpiryRisk.CRITICAL_30_DAYS -> "< 30 DAYS"
                                                ExpiryRisk.WARNING_90_DAYS -> "< 90 DAYS"
                                                ExpiryRisk.GOOD -> "OK"
                                                ExpiryRisk.NO_EXPIRY -> "NO EXPIRY"
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = when (item.risk) {
                                                ExpiryRisk.EXPIRED -> MaterialTheme.colorScheme.onError
                                                ExpiryRisk.CRITICAL_30_DAYS -> MaterialTheme.colorScheme.onErrorContainer
                                                ExpiryRisk.WARNING_90_DAYS -> MaterialTheme.colorScheme.onSecondaryContainer
                                                ExpiryRisk.GOOD -> MaterialTheme.colorScheme.onPrimaryContainer
                                                ExpiryRisk.NO_EXPIRY -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Expiry: ${item.formattedExpiry}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Stock On Hand: ${item.physicalStockUnits} units",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (item.physicalStockUnits > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
