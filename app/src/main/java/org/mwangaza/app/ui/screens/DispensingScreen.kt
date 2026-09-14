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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import core.domain.consumption.ConsumptionLineRequest
import core.domain.consumption.ConsumptionRequest
import core.domain.consumption.InsufficientStockException
import core.domain.fefo.ExpiryPolicy
import core.domain.model.Money
import core.domain.model.ProductMaster
import core.domain.model.ProductUnit
import core.domain.model.Quantity
import core.domain.model.Sale
import core.domain.model.SaleItem
import core.domain.model.UnitPriceConfig
import core.domain.time.DefaultTimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mwangaza.app.data.AppContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private data class TempDispenseLine(
    val product: ProductMaster,
    val unit: ProductUnit,
    val quantity: Quantity,
    val unitPriceMinor: Long,
    val lineTotalMinor: Long
)

private fun formatMoney(minorUnits: Long): String {
    val major = minorUnits / 100
    val minor = kotlin.math.abs(minorUnits % 100)
        .toString()
        .padStart(2, '0')

    return "KES $major.$minor"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispensingScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }

    var registeredProducts by remember {
        mutableStateOf<List<ProductMaster>>(emptyList())
    }

    var registeredUnits by remember {
        mutableStateOf<List<ProductUnit>>(emptyList())
    }

    var priceConfigsByUnitId by remember {
        mutableStateOf<Map<String, UnitPriceConfig>>(emptyMap())
    }

    var pastSales by remember {
        mutableStateOf<List<Sale>>(emptyList())
    }

    var saleNumber by remember { mutableStateOf("") }
    var customerRef by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val cartLines = remember {
        mutableStateOf<List<TempDispenseLine>>(emptyList())
    }

    var showAddLineDialog by remember { mutableStateOf(false) }

    var selectedSaleForDetail by remember {
        mutableStateOf<Sale?>(null)
    }

    var saleItemsForDetail by remember {
        mutableStateOf<List<SaleItem>>(emptyList())
    }

    var showVoidDialog by remember { mutableStateOf(false) }
    var voidReason by remember { mutableStateOf("") }

    fun refreshData() {
        scope.launch {
            isLoading = true

            withContext(Dispatchers.IO) {
                registeredProducts =
                    container.productMasterDao
                        .getAllProducts()
                        .filter { it.isActive }

                registeredUnits =
                    container.productMasterDao
                        .getAllUnits()
                        .filter { it.isActive }

                val prices =
                    container.productMasterDao.getAllPriceConfigs()

                priceConfigsByUnitId =
                    prices.associateBy { it.productUnitId }

                pastSales =
                    container.saleDao.getAllSales()
            }

            if (saleNumber.isBlank()) {
                saleNumber =
                    "SALE-" +
                        SimpleDateFormat(
                            "yyyyMMdd-HHmmss",
                            Locale.US
                        ).format(Date())
            }

            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    val totalCartSellingMinor = remember(cartLines.value) {
        cartLines.value.sumOf { it.lineTotalMinor }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
        topBar = {
            TopAppBar(
                title = {
                    Text("Dispensing / Sales")
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            TabRow(selectedTabIndex = selectedTab) {

                Tab(
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                    },
                    text = {
                        Text("New Dispense")
                    }
                )

                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                    },
                    text = {
                        Text("Sales History (${pastSales.size})")
                    }
                )
            }

            if (isLoading) {

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

            } else if (selectedTab == 0) {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    Text(
                        text = "Sale Transaction",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = saleNumber,
                        onValueChange = {
                            saleNumber = it
                        },
                        label = {
                            Text("Sale Identifier *")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = customerRef,
                        onValueChange = {
                            customerRef = it
                        },
                        label = {
                            Text("Patient / Customer Reference (Optional)")
                        },
                        placeholder = {
                            Text("e.g. Patient #1042 / Walk-in")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = {
                            notes = it
                        },
                        label = {
                            Text("Dispensing Notes (Optional)")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Text(
                            text = "Items to Dispense (${cartLines.value.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Button(
                            onClick = {
                                if (registeredProducts.isEmpty()) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            "Please register products first"
                                        )
                                    }
                                } else {
                                    showAddLineDialog = true
                                }
                            }
                        ) {

                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )

                            Spacer(
                                modifier = Modifier.width(4.dp)
                            )

                            Text("Add Item")
                        }
                    }

                    if (cartLines.value.isEmpty()) {

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme
                                    .surfaceVariant
                                    .copy(alpha = 0.5f)
                            )
                        ) {

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {

                                Text(
                                    text =
                                        "No items in cart. Tap 'Add Item' to select medication.",
                                    style =
                                        MaterialTheme.typography.bodyMedium,
                                    color =
                                        MaterialTheme.colorScheme
                                            .onSurfaceVariant
                                )
                            }
                        }

                    } else {

                        cartLines.value.forEachIndexed { index, line ->

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor =
                                        MaterialTheme.colorScheme.surface
                                )
                            ) {

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement =
                                        Arrangement.SpaceBetween,
                                    verticalAlignment =
                                        Alignment.CenterVertically
                                ) {

                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {

                                        Text(
                                            text = line.product.displayName,
                                            fontWeight = FontWeight.Bold,
                                            style =
                                                MaterialTheme.typography.bodyLarge
                                        )

                                        Text(
                                            text =
                                                "${line.quantity.toPlainString()} " +
                                                    line.unit.name +
                                                    " @ " +
                                                    formatMoney(
                                                        line.unitPriceMinor
                                                    ),
                                            style =
                                                MaterialTheme.typography.bodyMedium
                                        )

                                        Text(
                                            text =
                                                "Line Total: " +
                                                    formatMoney(
                                                        line.lineTotalMinor
                                                    ),
                                            style =
                                                MaterialTheme.typography.bodyMedium,
                                            fontWeight =
                                                FontWeight.SemiBold,
                                            color =
                                                MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            cartLines.value =
                                                cartLines.value
                                                    .filterIndexed { i, _ ->
                                                        i != index
                                                    }
                                        }
                                    ) {

                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Remove",
                                            tint =
                                                MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor =
                                    MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement =
                                    Arrangement.SpaceBetween,
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {

                                Text(
                                    text = "Grand Total:",
                                    style =
                                        MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color =
                                        MaterialTheme.colorScheme
                                            .onPrimaryContainer
                                )

                                Text(
                                    text =
                                        formatMoney(
                                            totalCartSellingMinor
                                        ),
                                    style =
                                        MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color =
                                        MaterialTheme.colorScheme
                                            .onPrimaryContainer
                                )
                            }
                        }
                    }

                    Spacer(
                        modifier = Modifier.height(16.dp)
                    )

                    Button(
                        onClick = {

                            if (saleNumber.isBlank()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Sale number is required"
                                    )
                                }
                                return@Button
                            }

                            if (cartLines.value.isEmpty()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Add at least one item to cart"
                                    )
                                }
                                return@Button
                            }

                            scope.launch {

                                isLoading = true

                                val now =
                                    System.currentTimeMillis()

                                val timeProvider =
                                    DefaultTimeProvider()

                                val facilityDate =
                                    timeProvider.localDate(
                                        "UTC",
                                        now
                                    )

                                val consumptionLines =
                                    cartLines.value.map { line ->

                                        ConsumptionLineRequest(
                                            productId =
                                                line.product.id,
                                            dispensingUnitId =
                                                line.unit.id,
                                            requestedQuantity =
                                                line.quantity,
                                            customUnitPrice =
                                                Money(
                                                    line.unitPriceMinor
                                                )
                                        )
                                    }

                                val request =
                                    ConsumptionRequest(
                                        saleId =
                                            UUID.randomUUID().toString(),
                                        saleNumber =
                                            saleNumber.trim(),
                                        items =
                                            consumptionLines,
                                        customerRef =
                                            customerRef
                                                .trim()
                                                .ifBlank { null },
                                        initiatedByUserId =
                                            "OPERATOR",
                                        notes =
                                            notes
                                                .trim()
                                                .ifBlank { null },
                                        facilityCalendarDate =
                                            facilityDate,
                                        expiryPolicy =
                                            ExpiryPolicy.DEFAULT,
                                        transactionTimestamp =
                                            now
                                    )

                                try {

                                    val result =
                                        withContext(Dispatchers.IO) {
                                            container
                                                .consumptionService
                                                .consumeStock(request)
                                        }

                                    snackbarHostState.showSnackbar(
                                        "Dispense complete! Sale " +
                                            "'${result.sale.saleNumber}' " +
                                            "recorded. Total: " +
                                            formatMoney(
                                                result.sale
                                                    .totalSellingAmount
                                                    .amountMinorUnits
                                            ) +
                                            " (COGS: " +
                                            formatMoney(
                                                result.sale
                                                    .totalCogs
                                                    .amountMinorUnits
                                            ) +
                                            ")"
                                    )

                                    saleNumber =
                                        "SALE-" +
                                            SimpleDateFormat(
                                                "yyyyMMdd-HHmmss",
                                                Locale.US
                                            ).format(Date())

                                    customerRef = ""
                                    notes = ""
                                    cartLines.value = emptyList()

                                    refreshData()
                                    selectedTab = 1

                                } catch (
                                    e: InsufficientStockException
                                ) {

                                    snackbarHostState.showSnackbar(
                                        "Cannot Dispense: ${e.message}"
                                    )

                                } catch (e: Exception) {

                                    snackbarHostState.showSnackbar(
                                        "Error completing dispense: " +
                                            "${e.message}"
                                    )

                                } finally {

                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = cartLines.value.isNotEmpty()
                    ) {

                        Icon(
                            Icons.Default.Check,
                            contentDescription = null
                        )

                        Spacer(
                            modifier = Modifier.width(8.dp)
                        )

                        Text("Confirm & Dispense Stock")
                    }
                }

            } else {

                if (pastSales.isEmpty()) {

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {

                        Column(
                            horizontalAlignment =
                                Alignment.CenterHorizontally
                        ) {

                            Icon(
                                Icons.Default.PointOfSale,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint =
                                    MaterialTheme.colorScheme
                                        .onSurfaceVariant
                                        .copy(alpha = 0.5f)
                            )

                            Spacer(
                                modifier = Modifier.height(16.dp)
                            )

                            Text(
                                "No sales or dispensing transactions found",
                                style =
                                    MaterialTheme.typography.titleMedium,
                                color =
                                    MaterialTheme.colorScheme
                                        .onSurfaceVariant
                            )

                            Spacer(
                                modifier = Modifier.height(8.dp)
                            )

                            Text(
                                "Transactions confirmed in 'New Dispense' " +
                                    "will appear here.",
                                style =
                                    MaterialTheme.typography.bodyMedium,
                                color =
                                    MaterialTheme.colorScheme
                                        .onSurfaceVariant
                                        .copy(alpha = 0.7f)
                            )
                        }
                    }

                } else {

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {

                        items(
                            pastSales,
                            key = { it.id }
                        ) { sale ->

                            val isVoided = sale.isVoided

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {

                                        scope.launch {

                                            val items =
                                                withContext(
                                                    Dispatchers.IO
                                                ) {
                                                    container.saleDao
                                                        .getItemsForSale(
                                                            sale.id
                                                        )
                                                }

                                            selectedSaleForDetail =
                                                sale

                                            saleItemsForDetail =
                                                items
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor =
                                        if (isVoided) {
                                            MaterialTheme.colorScheme
                                                .surfaceVariant
                                                .copy(alpha = 0.6f)
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        }
                                )
                            ) {

                                Column(
                                    modifier =
                                        Modifier.padding(16.dp)
                                ) {

                                    Row(
                                        modifier =
                                            Modifier.fillMaxWidth(),
                                        horizontalArrangement =
                                            Arrangement.SpaceBetween,
                                        verticalAlignment =
                                            Alignment.CenterVertically
                                    ) {

                                        Text(
                                            text = sale.saleNumber,
                                            fontWeight =
                                                FontWeight.Bold,
                                            style =
                                                MaterialTheme.typography
                                                    .titleMedium
                                        )

                                        Surface(
                                            color =
                                                if (isVoided) {
                                                    MaterialTheme.colorScheme
                                                        .errorContainer
                                                } else {
                                                    MaterialTheme.colorScheme
                                                        .primaryContainer
                                                },
                                            shape =
                                                MaterialTheme.shapes.small
                                        ) {

                                            Text(
                                                text = sale.status,
                                                modifier =
                                                    Modifier.padding(
                                                        horizontal = 8.dp,
                                                        vertical = 2.dp
                                                    ),
                                                style =
                                                    MaterialTheme.typography
                                                        .labelSmall,
                                                fontWeight =
                                                    FontWeight.Bold,
                                                color =
                                                    if (isVoided) {
                                                        MaterialTheme.colorScheme
                                                            .onErrorContainer
                                                    } else {
                                                        MaterialTheme.colorScheme
                                                            .onPrimaryContainer
                                                    }
                                            )
                                        }
                                    }

                                    Spacer(
                                        modifier =
                                            Modifier.height(4.dp)
                                    )

                                    Text(
                                        text =
                                            "Date: ${
                                                SimpleDateFormat(
                                                    "yyyy-MM-dd HH:mm",
                                                    Locale.US
                                                ).format(
                                                    Date(sale.occurredAt)
                                                )
                                            }",
                                        style =
                                            MaterialTheme.typography.bodySmall,
                                        color =
                                            MaterialTheme.colorScheme
                                                .onSurfaceVariant
                                    )

                                    if (!sale.customerRef.isNullOrBlank()) {

                                        Text(
                                            text =
                                                "Customer / Patient: " +
                                                    sale.customerRef,
                                            style =
                                                MaterialTheme.typography
                                                    .bodySmall,
                                            color =
                                                MaterialTheme.colorScheme
                                                    .onSurfaceVariant
                                        )
                                    }

                                    Spacer(
                                        modifier =
                                            Modifier.height(6.dp)
                                    )

                                    Row(
                                        modifier =
                                            Modifier.fillMaxWidth(),
                                        horizontalArrangement =
                                            Arrangement.SpaceBetween
                                    ) {

                                        Text(
                                            "Revenue: " +
                                                formatMoney(
                                                    sale.totalSellingAmount
                                                        .amountMinorUnits
                                                ),
                                            fontWeight =
                                                FontWeight.SemiBold,
                                            color =
                                                MaterialTheme.colorScheme
                                                    .primary
                                        )

                                        Text(
                                            "COGS: " +
                                                formatMoney(
                                                    sale.totalCogs
                                                        .amountMinorUnits
                                                ),
                                            style =
                                                MaterialTheme.typography
                                                    .bodySmall,
                                            color =
                                                MaterialTheme.colorScheme
                                                    .secondary
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

    if (showAddLineDialog) {

        var selectedProduct by remember {
            mutableStateOf(
                registeredProducts.firstOrNull()
            )
        }

        val productUnits =
            remember(
                selectedProduct,
                registeredUnits
            ) {
                registeredUnits.filter {
                    it.productId == selectedProduct?.id
                }
            }

        var selectedUnit by remember {
            mutableStateOf(
                productUnits.firstOrNull()
            )
        }

        LaunchedEffect(selectedProduct) {
            selectedUnit =
                productUnits.firstOrNull {
                    it.isDispensingUnit
                } ?: productUnits.firstOrNull()
        }

        var quantityStr by remember {
            mutableStateOf("")
        }

        var unitPriceMajorStr by remember {
            mutableStateOf("")
        }

        var dialogError by remember {
            mutableStateOf<String?>(null)
        }

        LaunchedEffect(selectedUnit) {

            val configuredPrice =
                selectedUnit?.let {
                    priceConfigsByUnitId[it.id]
                }

            if (configuredPrice != null) {
                unitPriceMajorStr =
                    String.format(
                        Locale.US,
                        "%.2f",
                        configuredPrice.sellingPrice
                            .amountMinorUnits / 100.0
                    )
            }
        }

        var productMenuExpanded by remember {
            mutableStateOf(false)
        }

        var unitMenuExpanded by remember {
            mutableStateOf(false)
        }

        AlertDialog(
            onDismissRequest = {
                showAddLineDialog = false
            },

            title = {
                Text("Add Item to Dispense")
            },

            text = {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(
                            rememberScrollState()
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(10.dp)
                ) {

                    Text(
                        "Product *",
                        style =
                            MaterialTheme.typography.labelMedium
                    )

                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        OutlinedButton(
                            onClick = {
                                productMenuExpanded = true
                            },
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {

                            Text(
                                selectedProduct?.displayName
                                    ?: "Choose product",
                                modifier =
                                    Modifier.weight(1f)
                            )

                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null
                            )
                        }

                        DropdownMenu(
                            expanded =
                                productMenuExpanded,
                            onDismissRequest = {
                                productMenuExpanded = false
                            }
                        ) {

                            registeredProducts.forEach { product ->

                                DropdownMenuItem(
                                    text = {
                                        Text(product.displayName)
                                    },
                                    onClick = {
                                        selectedProduct = product
                                        productMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        "Dispensing Unit *",
                        style =
                            MaterialTheme.typography.labelMedium
                    )

                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        OutlinedButton(
                            onClick = {
                                unitMenuExpanded = true
                            },
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {

                            Text(
                                text =
                                    selectedUnit?.let {
                                        "${it.name} " +
                                            "(${it.conversionFraction} base units)"
                                    } ?: "Choose unit",
                                modifier =
                                    Modifier.weight(1f)
                            )

                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null
                            )
                        }

                        DropdownMenu(
                            expanded =
                                unitMenuExpanded,
                            onDismissRequest = {
                                unitMenuExpanded = false
                            }
                        ) {

                            productUnits.forEach { unit ->

                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "${unit.name} " +
                                                "(${unit.conversionFraction} " +
                                                "base units)"
                                        )
                                    },
                                    onClick = {
                                        selectedUnit = unit
                                        unitMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    selectedProduct?.let { product ->

                        Text(
                            text =
                                "Quantity precision: " +
                                    product.quantityScale +
                                    " • Minimum increment: " +
                                    product.minimumTransactionIncrement
                                        .toPlainString(),
                            style =
                                MaterialTheme.typography.bodySmall,
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedTextField(
                        value = quantityStr,
                        onValueChange = {
                            quantityStr = it
                        },
                        label = {
                            Text("Dispensing Quantity *")
                        },
                        placeholder = {
                            Text(
                                selectedProduct
                                    ?.minimumTransactionIncrement
                                    ?.toPlainString()
                                    ?: "e.g. 2"
                            )
                        },
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType =
                                    KeyboardType.Decimal
                            ),
                        singleLine = true,
                        modifier =
                            Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = unitPriceMajorStr,
                        onValueChange = {
                            unitPriceMajorStr = it
                        },
                        label = {
                            Text("Unit Selling Price (KES) *")
                        },
                        placeholder = {
                            Text("e.g. 50.00")
                        },
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType =
                                    KeyboardType.Decimal
                            ),
                        singleLine = true,
                        modifier =
                            Modifier.fillMaxWidth()
                    )

                    dialogError?.let {
                        Text(
                            it,
                            color =
                                MaterialTheme.colorScheme.error,
                            style =
                                MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },

            confirmButton = {

                Button(
                    onClick = {

                        val product = selectedProduct
                        val unit = selectedUnit

                        if (product == null || unit == null) {
                            dialogError =
                                "Select product and unit."
                            return@Button
                        }

                        val quantity = try {
                            Quantity.fromDecimalString(
                                quantityStr.trim(),
                                product.quantityScale
                            )
                        } catch (e: Exception) {
                            null
                        }

                        if (quantity == null || !quantity.isPositive) {
                            dialogError =
                                "Enter a valid positive quantity at " +
                                    "the product's configured precision."
                            return@Button
                        }

                        if (
                            !quantity.isMultipleOf(
                                product.minimumTransactionIncrement
                            )
                        ) {
                            dialogError =
                                "Quantity must be a multiple of " +
                                    product.minimumTransactionIncrement
                                        .toPlainString()
                            return@Button
                        }

                        val unitPriceMinor = try {
                            Money.fromDecimalString(
                                unitPriceMajorStr.trim()
                            ).amountMinorUnits
                        } catch (e: Exception) {
                            null
                        }

                        if (
                            unitPriceMinor == null ||
                            unitPriceMinor < 0L
                        ) {
                            dialogError =
                                "Enter a valid unit selling price."
                            return@Button
                        }

                        val lineTotalMinor = try {
                            Math.multiplyExact(
                                quantity.storageUnits,
                                unitPriceMinor
                            )
                        } catch (e: ArithmeticException) {
                            dialogError =
                                "Line selling amount is too large."
                            return@Button
                        }

                        cartLines.value =
                            cartLines.value +
                                TempDispenseLine(
                                    product = product,
                                    unit = unit,
                                    quantity = quantity,
                                    unitPriceMinor =
                                        unitPriceMinor,
                                    lineTotalMinor =
                                        lineTotalMinor
                                )

                        showAddLineDialog = false
                    }
                ) {
                    Text("Add to Cart")
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        showAddLineDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    selectedSaleForDetail?.let { sale ->

        AlertDialog(
            onDismissRequest = {
                selectedSaleForDetail = null
                showVoidDialog = false
            },

            title = {
                Text("Sale: ${sale.saleNumber}")
            },

            text = {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(
                            rememberScrollState()
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    Text(
                        "Status: ${sale.status}",
                        fontWeight = FontWeight.Bold,
                        color =
                            if (sale.isVoided) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                    )

                    Text(
                        "Date: ${
                            SimpleDateFormat(
                                "yyyy-MM-dd HH:mm",
                                Locale.US
                            ).format(
                                Date(sale.occurredAt)
                            )
                        }"
                    )

                    if (!sale.customerRef.isNullOrBlank()) {
                        Text(
                            "Customer: ${sale.customerRef}"
                        )
                    }

                    if (!sale.notes.isNullOrBlank()) {
                        Text(
                            "Notes: ${sale.notes}"
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    Text(
                        "Items Sold (${saleItemsForDetail.size}):",
                        fontWeight = FontWeight.Bold
                    )

                    saleItemsForDetail.forEach { item ->

                        Card(
                            modifier =
                                Modifier.fillMaxWidth(),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        MaterialTheme.colorScheme
                                            .surfaceVariant
                                )
                        ) {

                            Column(
                                modifier =
                                    Modifier.padding(8.dp)
                            ) {

                                Text(
                                    "Product ID: ${item.productId}",
                                    fontWeight =
                                        FontWeight.SemiBold
                                )

                                Text(
                                    "Quantity: " +
                                        "${item.requestedQuantity.toPlainString()} " +
                                        "(Base units: " +
                                        "${item.baseQuantity.toPlainString()})"
                                )

                                Text(
                                    "Line Revenue: " +
                                        formatMoney(
                                            item.lineTotal
                                                .amountMinorUnits
                                        ) +
                                        " | Line COGS: " +
                                        formatMoney(
                                            item.lineCogs
                                                .amountMinorUnits
                                        ),
                                    style =
                                        MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        "Total Selling Amount: " +
                            formatMoney(
                                sale.totalSellingAmount
                                    .amountMinorUnits
                            ),
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "Total Acquisition Cost (COGS): " +
                            formatMoney(
                                sale.totalCogs.amountMinorUnits
                            ),
                        fontWeight = FontWeight.Medium
                    )

                    if (!sale.isVoided && !showVoidDialog) {

                        Spacer(
                            modifier = Modifier.height(12.dp)
                        )

                        OutlinedButton(
                            onClick = {
                                showVoidDialog = true
                            },
                            colors =
                                ButtonDefaults
                                    .outlinedButtonColors(
                                        contentColor =
                                            MaterialTheme.colorScheme
                                                .error
                                    ),
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {

                            Icon(
                                Icons.Default.RemoveCircleOutline,
                                contentDescription = null
                            )

                            Spacer(
                                modifier = Modifier.width(6.dp)
                            )

                            Text("Void / Reverse This Sale")
                        }
                    }

                    if (showVoidDialog) {

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        HorizontalDivider()

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        Text(
                            "Reversal Reason *",
                            style =
                                MaterialTheme.typography.labelMedium
                        )

                        OutlinedTextField(
                            value = voidReason,
                            onValueChange = {
                                voidReason = it
                            },
                            placeholder = {
                                Text(
                                    "e.g. Dispensed in error / returned by patient"
                                )
                            },
                            modifier =
                                Modifier.fillMaxWidth()
                        )

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        Button(
                            onClick = {

                                if (voidReason.isBlank()) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            "Void reason is required"
                                        )
                                    }

                                    return@Button
                                }

                                scope.launch {

                                    isLoading = true

                                    try {

                                        val now =
                                            System.currentTimeMillis()

                                        withContext(Dispatchers.IO) {
                                            container
                                                .consumptionService
                                                .voidSale(
                                                    saleId = sale.id,
                                                    voidTimestamp = now,
                                                    reason =
                                                        voidReason.trim()
                                                )
                                        }

                                        snackbarHostState.showSnackbar(
                                            "Sale '${sale.saleNumber}' voided. " +
                                                "Inventory and cost layers restored."
                                        )

                                        selectedSaleForDetail = null
                                        showVoidDialog = false
                                        voidReason = ""

                                        refreshData()

                                    } catch (e: Exception) {

                                        snackbarHostState.showSnackbar(
                                            "Error voiding sale: ${e.message}"
                                        )

                                    } finally {

                                        isLoading = false
                                    }
                                }
                            },
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor =
                                        MaterialTheme.colorScheme.error
                                ),
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {
                            Text("Confirm Void & Reverse Inventory")
                        }
                    }
                }
            },

            confirmButton = {

                TextButton(
                    onClick = {
                        selectedSaleForDetail = null
                        showVoidDialog = false
                    }
                ) {
                    Text("Close")
                }
            }
        )
    }
}
