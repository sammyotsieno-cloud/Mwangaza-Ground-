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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import core.domain.model.RationalCost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mwangaza.app.data.AppContainer
import org.mwangaza.app.ui.formatters.MoneyDisplayFormatter
import java.math.BigInteger

data class FinancialReportData(
    val totalInventoryValuation: RationalCost,
    val totalActiveCostLayers: Int,
    val totalSalesRevenueMinor: Long,
    val totalSalesCogs: RationalCost,
    val completedSalesCount: Int,
    val voidedSalesCount: Int,
    val totalReceiptsCount: Int,
    val totalProductsCount: Int,
    val totalStockMovementsCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var reportData by remember { mutableStateOf<FinancialReportData?>(null) }

    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true

            withContext(Dispatchers.IO) {
                val products =
                    container.productMasterDao.getAllProducts()

                val sales =
                    container.saleDao.getAllSales()

                val receipts =
                    container.goodsReceiptDao.getAllReceipts()

                val movements =
                    container.stockMovementDao.getAllMovements()

                val completedSales =
                    sales.filter { it.isCompleted }

                val voidedSales =
                    sales.filter { it.isVoided }

                val totalRevenueMinor =
                    completedSales.sumOf {
                        it.totalSellingAmount.amountMinorUnits
                    }

                val totalCogs =
                    completedSales.fold(
                        RationalCost(
                            numerator = BigInteger.ZERO,
                            denominator = BigInteger.ONE
                        )
                    ) { total, sale ->
                        total.add(sale.totalCogs)
                    }

                val totalValuation =
                    container.inventoryValuationService
                        .calculateTotalValuation()

                val totalActiveCostLayers =
                    container.inventoryCostLayerDao
                        .getAllActiveLayers()
                        .size

                reportData = FinancialReportData(
                    totalInventoryValuation = totalValuation,
                    totalActiveCostLayers = totalActiveCostLayers,
                    totalSalesRevenueMinor = totalRevenueMinor,
                    totalSalesCogs = totalCogs,
                    completedSalesCount = completedSales.size,
                    voidedSalesCount = voidedSales.size,
                    totalReceiptsCount = receipts.size,
                    totalProductsCount = products.size,
                    totalStockMovementsCount = movements.size
                )
            }

            isLoading = false
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text("Reports & Financials")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->

        if (isLoading || reportData == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val data = reportData!!

            val revenueExact =
                RationalCost(
                    numerator = BigInteger.valueOf(
                        data.totalSalesRevenueMinor
                    ),
                    denominator = BigInteger.ONE
                )

            val grossMarginExact =
                revenueExact.subtract(
                    data.totalSalesCogs
                )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Sales Performance & Profitability",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        val revenueString =
                            MoneyDisplayFormatter.formatMinorUnits(
                                data.totalSalesRevenueMinor
                            )

                        val cogsString =
                            MoneyDisplayFormatter.formatRationalCost(
                                data.totalSalesCogs
                            )

                        val marginString =
                            MoneyDisplayFormatter.formatRationalCost(
                                grossMarginExact
                            )

                        ReportRow(
                            label = "Total Sales Revenue",
                            value = revenueString,
                            isEmphasized = true
                        )

                        ReportRow(
                            label = "Cost of Goods Sold (COGS)",
                            value = cogsString
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        ReportRow(
                            label = "Gross Margin",
                            value = marginString,
                            isEmphasized = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text =
                                "Completed Sales: ${data.completedSalesCount} | " +
                                    "Voided / Reversed Sales: " +
                                    data.voidedSalesCount,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Inventory Valuation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        val valuationString =
                            MoneyDisplayFormatter.formatRationalCost(
                                data.totalInventoryValuation
                            )

                        ReportRow(
                            label = "Current Inventory Valuation",
                            value = valuationString,
                            isEmphasized = true
                        )

                        ReportRow(
                            label = "Active Unexhausted Cost Layers",
                            value = "${data.totalActiveCostLayers} pools"
                        )

                        ReportRow(
                            label = "Registered Products in Catalog",
                            value = "${data.totalProductsCount} products"
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Operational Ledger Integrity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        ReportRow(
                            label = "Goods Receipts",
                            value = "${data.totalReceiptsCount} transactions"
                        )

                        ReportRow(
                            label = "Immutable Stock Movement Records",
                            value =
                                "${data.totalStockMovementsCount} ledger entries"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportRow(
    label: String,
    value: String,
    isEmphasized: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style =
                if (isEmphasized) {
                    MaterialTheme.typography.bodyLarge
                } else {
                    MaterialTheme.typography.bodyMedium
                },
            color =
                if (isEmphasized) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            fontWeight =
                if (isEmphasized) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                }
        )

        Text(
            text = value,
            style =
                if (isEmphasized) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.bodyMedium
                },
            fontWeight = FontWeight.Bold,
            color =
                if (isEmphasized) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
        )
    }
}
