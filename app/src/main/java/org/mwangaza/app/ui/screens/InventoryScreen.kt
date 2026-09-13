package org.mwangaza.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import core.domain.model.InventoryCostLayer
import core.domain.model.ProductMaster
import core.domain.model.ProductUnit
import core.domain.model.StockBatch
import core.domain.model.StockMovement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mwangaza.app.data.AppContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ProductStockSummary(
    val product: ProductMaster,
    val baseUnit: ProductUnit?,
    val physicalStockUnits: Long,
    val valuationMinorUnits: Long,
    val batches: List<BatchStockSummary>
)

data class BatchStockSummary(
    val batch: StockBatch,
    val physicalUnits: Long,
    val costLayers: List<InventoryCostLayer>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Stock Balances, 1: Movement Ledger
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    var productSummaries by remember { mutableStateOf<List<ProductStockSummary>>(emptyList()) }
    var allMovements by remember { mutableStateOf<List<StockMovement>>(emptyList()) }
    var productsById by remember { mutableStateOf<Map<String, ProductMaster>>(emptyMap()) }
    var batchesById by remember { mutableStateOf<Map<String, StockBatch>>(emptyMap()) }

    var selectedSummaryForDetail by remember { mutableStateOf<ProductStockSummary?>(null) }

    fun refreshData() {
        scope.launch {
            isLoading = true
            withContext(Dispatchers.IO) {
                val products = container.productMasterDao.getAllProducts()
                val units = container.productMasterDao.getAllUnits()
                val movements = container.stockMovementDao.getAllMovements()
                val batches = container.stockBatchDao.getAllBatches()

                val pMap = products.associateBy { it.id }
                val bMap = batches.associateBy { it.id }
                val baseUnitsByProd = units.filter { it.isBaseUnit }.associateBy { it.productId }

                // Group movements by product and batch to calculate true physical ledger balances
                val movementsByProd = movements.groupBy { it.productId }
                val movementsByBatch = movements.filter { it.stockBatchId != null }.groupBy { it.stockBatchId!! }
                val batchesByProd = batches.groupBy { it.productId }

                val summaries = products.map { p ->
                    val prodMovements = movementsByProd[p.id] ?: emptyList()
                    val totalPhysicalUnits = prodMovements.sumOf { it.quantity.storageUnits }

                    val activeLayers = container.inventoryCostLayerDao.getActiveLayersForProduct(p.id)
                    // Valuation = sum of (remainingQty * acquisitionUnitCost)
                    // Since unit cost is layer.initialCost / layer.initialQty,
                    // remaining valuation = (layer.remainingQuantity * layer.initialCost.amountMinorUnits) / layer.initialQuantity
                    val totalValuation = activeLayers.sumOf { l ->
                        if (l.initialQuantity.storageUnits > 0L) {
                            (l.remainingQuantity.storageUnits * l.initialCost.amountMinorUnits) / l.initialQuantity.storageUnits
                        } else 0L
                    }

                    val prodBatches = batchesByProd[p.id] ?: emptyList()
                    val batchSummaries = prodBatches.map { b ->
                        val bMovements = movementsByBatch[b.id] ?: emptyList()
                        val bUnits = bMovements.sumOf { it.quantity.storageUnits }
                        val bLayers = activeLayers.filter { it.stockBatchId == b.id }
                        BatchStockSummary(batch = b, physicalUnits = bUnits, costLayers = bLayers)
                    }

                    ProductStockSummary(
                        product = p,
                        baseUnit = baseUnitsByProd[p.id],
                        physicalStockUnits = totalPhysicalUnits,
                        valuationMinorUnits = totalValuation,
                        batches = batchSummaries
                    )
                }

                productSummaries = summaries
                allMovements = movements
                productsById = pMap
                batchesById = bMap
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    val filteredSummaries = remember(productSummaries, searchQuery) {
        if (searchQuery.isBlank()) productSummaries
        else {
            val q = searchQuery.trim().lowercase()
            productSummaries.filter {
                it.product.brandName?.lowercase()?.contains(q) == true ||
                    it.product.genericName?.lowercase()?.contains(q) == true
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Inventory & Ledger") },
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
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Stock Balances") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Movement Ledger (${allMovements.size})") }
                )
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (selectedTab == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        placeholder = { Text("Filter inventory by product name...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true
                    )

                    if (filteredSummaries.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No inventory records found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredSummaries, key = { it.product.id }) { s ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedSummaryForDetail = s },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = s.product.displayName,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                val unitLabel = s.baseUnit?.name ?: "units"
                                                Text(
                                                    text = "On Hand: ${s.physicalStockUnits} $unitLabel",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (s.physicalStockUnits > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                val valStr = "KES ${(s.valuationMinorUnits / 100)}.${(s.valuationMinorUnits % 100).toString().padStart(2, '0')}"
                                                Text(
                                                    text = "Valuation",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = valStr,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Batches on record: ${s.batches.size} | Active batches with stock: ${s.batches.count { it.physicalUnits > 0 }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Movement Ledger Tab
                if (allMovements.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No stock movements recorded yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Stock receipts and sales will create immutable ledger movements.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allMovements, key = { it.id }) { m ->
                            val prod = productsById[m.productId]
                            val batch = m.stockBatchId?.let { batchesById[it] }
                            val isPositive = m.quantity.storageUnits >= 0

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = prod?.displayName ?: "Product #${m.productId.take(8)}",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = (if (isPositive) "+" else "") + "${m.quantity.storageUnits} units",
                                            fontWeight = FontWeight.Bold,
                                            color = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Type: ${m.movementType}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(m.occurredAt)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (batch != null) {
                                        Text(
                                            text = "Batch: ${batch.batchNumber} (Exp: ${batch.expiryDateInt})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (!m.sourceTransactionRef.isNullOrBlank()) {
                                        Text(
                                            text = "Ref: ${m.sourceTransactionRef}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

    // Detail Dialog with batch breakdown
    selectedSummaryForDetail?.let { summary ->
        AlertDialog(
            onDismissRequest = { selectedSummaryForDetail = null },
            title = { Text(summary.product.displayName) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val unitName = summary.baseUnit?.name ?: "units"
                    Text("Total On-Hand Ledger Stock: ${summary.physicalStockUnits} $unitName", fontWeight = FontWeight.Bold)
                    val valStr = "KES ${(summary.valuationMinorUnits / 100)}.${(summary.valuationMinorUnits % 100).toString().padStart(2, '0')}"
                    Text("Total FIFO Inventory Valuation: $valStr", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Physical Batches (${summary.batches.size}):", fontWeight = FontWeight.Bold)

                    if (summary.batches.isEmpty()) {
                        Text("No batches registered for this product yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        summary.batches.forEach { bSum ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Batch: ${bSum.batch.batchNumber}", fontWeight = FontWeight.SemiBold)
                                        Text("Exp: ${bSum.batch.expiryDateInt}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text("Physical Stock: ${bSum.physicalUnits} $unitName", fontWeight = FontWeight.Medium)
                                    Text("Active Cost Layers: ${bSum.costLayers.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedSummaryForDetail = null }) {
                    Text("Close")
                }
            }
        )
    }
}
