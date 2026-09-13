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
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import core.domain.model.GoodsReceipt
import core.domain.model.GoodsReceiptItem
import core.domain.model.Money
import core.domain.model.ProductMaster
import core.domain.model.ProductUnit
import core.domain.model.Quantity
import core.domain.model.QuantityScale
import core.domain.model.StockBatch
import core.domain.receiving.ReceivingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mwangaza.app.data.AppContainer
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private data class TempLineItem(
val product: ProductMaster,
val unit: ProductUnit,
val quantity: Quantity,
val totalCost: Money,
val trackingMode: String,
val batchNumber: String,
val expiryDateInt: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoodsReceivingScreen(
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

var pastReceipts by remember {
    mutableStateOf<List<GoodsReceipt>>(emptyList())
}

var receiptNumber by remember { mutableStateOf("") }
var invoiceRef by remember { mutableStateOf("") }
var notes by remember { mutableStateOf("") }

var lineItems by remember {
    mutableStateOf<List<TempLineItem>>(emptyList())
}

var showAddLineDialog by remember { mutableStateOf(false) }

var viewingReceipt by remember {
    mutableStateOf<GoodsReceipt?>(null)
}

var viewingReceiptItems by remember {
    mutableStateOf<List<GoodsReceiptItem>>(emptyList())
}

fun generateReceiptNumber(): String {
    return "GR-" +
        SimpleDateFormat(
            "yyyyMMdd-HHmmss",
            Locale.US
        ).format(Date())
}

fun refreshData() {
    scope.launch {
        isLoading = true

        try {
            val data = withContext(Dispatchers.IO) {
                Triple(
                    container.productMasterDao
                        .getAllProducts()
                        .filter { it.isActive },

                    container.productMasterDao
                        .getAllUnits()
                        .filter { it.isActive },

                    container.goodsReceiptDao
                        .getAllReceipts()
                )
            }

            registeredProducts = data.first
            registeredUnits = data.second
            pastReceipts = data.third

            if (receiptNumber.isBlank()) {
                receiptNumber = generateReceiptNumber()
            }
        } finally {
            isLoading = false
        }
    }
}

LaunchedEffect(Unit) {
    refreshData()
}

Scaffold(
    modifier = modifier.fillMaxSize(),
    snackbarHost = {
        SnackbarHost(snackbarHostState)
    },
    topBar = {
        TopAppBar(
            title = {
                Text("Goods Receiving")
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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

        TabRow(
            selectedTabIndex = selectedTab
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text("Receive Stock")
                }
            )

            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text("Receipts History (${pastReceipts.size})")
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

            ReceiveStockContent(
                receiptNumber = receiptNumber,
                invoiceRef = invoiceRef,
                notes = notes,
                lineItems = lineItems,
                registeredProducts = registeredProducts,
                onReceiptNumberChange = {
                    receiptNumber = it
                },
                onInvoiceRefChange = {
                    invoiceRef = it
                },
                onNotesChange = {
                    notes = it
                },
                onAddLine = {
                    if (registeredProducts.isEmpty()) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Please register products first in Products."
                            )
                        }
                    } else {
                        showAddLineDialog = true
                    }
                },
                onRemoveLine = { index ->
                    lineItems = lineItems.filterIndexed { i, _ ->
                        i != index
                    }
                },
                onCommit = {

                    if (receiptNumber.isBlank()) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Receipt number is required."
                            )
                        }

                        return@ReceiveStockContent
                    }

                    if (lineItems.isEmpty()) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Add at least one line item."
                            )
                        }

                        return@ReceiveStockContent
                    }

                    scope.launch {

                        isLoading = true

                        val now = System.currentTimeMillis()
                        val receiptId = UUID.randomUUID().toString()

                        val receipt = GoodsReceipt(
                            id = receiptId,
                            supplierId = null,
                            receiptNumber = receiptNumber.trim(),
                            sourceDocumentRef = invoiceRef
                                .trim()
                                .ifBlank { null },
                            status = GoodsReceipt.STATUS_DRAFT,
                            receivedAt = now,
                            committedAt = null,
                            receivedByUserId = null,
                            notes = notes
                                .trim()
                                .ifBlank { null },
                            createdAt = now,
                            updatedAt = now
                        )

                        val domainItems = lineItems.mapIndexed { index, line ->

                            val unitCost = deriveUnitCost(
                                totalCost = line.totalCost,
                                quantity = line.quantity
                            )

                            GoodsReceiptItem(
                                id = UUID.randomUUID().toString(),
                                goodsReceiptId = receiptId,
                                lineIndex = index,
                                productId = line.product.id,
                                receivingUnitId = line.unit.id,
                                receivedQuantity = line.quantity,
                                unitCost = unitCost,
                                totalCost = line.totalCost,
                                batchNumber = line.batchNumber
                                    .trim()
                                    .ifBlank { null },
                                expiryDateInt = line.expiryDateInt,
                                trackingMode = line.trackingMode,
                                createdAt = now,
                                updatedAt = now
                            )
                        }

                        val productsById =
                            registeredProducts.associateBy { it.id }

                        val unitsById =
                            registeredUnits.associateBy { it.id }

                        try {

                            val result = withContext(Dispatchers.IO) {
                                container.receivingService.commitReceipt(
                                    receipt = receipt,
                                    items = domainItems,
                                    productsById = productsById,
                                    unitsById = unitsById,
                                    commitTimestamp = now
                                )
                            }

                            when (result) {

                                is ReceivingResult.Success -> {

                                    snackbarHostState.showSnackbar(
                                        "Receipt '${result.committedReceipt.receiptNumber}' committed."
                                    )

                                    receiptNumber =
                                        generateReceiptNumber()

                                    invoiceRef = ""
                                    notes = ""
                                    lineItems = emptyList()

                                    refreshData()

                                    selectedTab = 1
                                }

                                is ReceivingResult.Failure -> {

                                    snackbarHostState.showSnackbar(
                                        result.errors.joinToString(
                                            prefix = "Receiving failed: "
                                        )
                                    )
                                }
                            }

                        } catch (exception: Exception) {

                            snackbarHostState.showSnackbar(
                                exception.message
                                    ?: "Unable to commit goods receipt."
                            )

                        } finally {

                            isLoading = false
                        }
                    }
                }
            )

        } else {

            ReceiptHistoryContent(
                receipts = pastReceipts,
                onReceiptSelected = { receipt ->

                    scope.launch {

                        val items = withContext(Dispatchers.IO) {
                            container.goodsReceiptDao
                                .getItemsForReceipt(receipt.id)
                        }

                        viewingReceipt = receipt
                        viewingReceiptItems = items
                    }
                }
            )
        }
    }
}

if (showAddLineDialog) {

    AddReceiptLineDialog(
        products = registeredProducts,
        units = registeredUnits,
        onDismiss = {
            showAddLineDialog = false
        },
        onAdd = { line ->

            lineItems = lineItems + line
            showAddLineDialog = false
        }
    )
}

viewingReceipt?.let { receipt ->

    ReceiptDetailDialog(
        receipt = receipt,
        items = viewingReceiptItems,
        onDismiss = {
            viewingReceipt = null
            viewingReceiptItems = emptyList()
        }
    )
}

}

@Composable
private fun ReceiveStockContent(
receiptNumber: String,
invoiceRef: String,
notes: String,
lineItems: List<TempLineItem>,
registeredProducts: List<ProductMaster>,
onReceiptNumberChange: (String) -> Unit,
onInvoiceRefChange: (String) -> Unit,
onNotesChange: (String) -> Unit,
onAddLine: () -> Unit,
onRemoveLine: (Int) -> Unit,
onCommit: () -> Unit
) {
Column(
modifier = Modifier
.fillMaxSize()
.padding(16.dp)
.verticalScroll(rememberScrollState()),
verticalArrangement = Arrangement.spacedBy(12.dp)
) {

    Text(
        text = "Receipt Header",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )

    OutlinedTextField(
        value = receiptNumber,
        onValueChange = onReceiptNumberChange,
        label = {
            Text("Receipt Number / Identifier *")
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    OutlinedTextField(
        value = invoiceRef,
        onValueChange = onInvoiceRefChange,
        label = {
            Text("Supplier Invoice / Delivery Ref")
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    OutlinedTextField(
        value = notes,
        onValueChange = onNotesChange,
        label = {
            Text("Receiving Notes")
        },
        modifier = Modifier.fillMaxWidth()
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            text = "Received Line Items (${lineItems.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Button(onClick = onAddLine) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null
            )

            Spacer(modifier = Modifier.width(4.dp))

            Text("Add Line")
        }
    }

    if (lineItems.isEmpty()) {

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {

                Text(
                    text = if (registeredProducts.isEmpty()) {
                        "No products are registered yet."
                    } else {
                        "No line items added yet. Tap Add Line."
                    },
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

    } else {

        lineItems.forEachIndexed { index, item ->

            Card(
                modifier = Modifier.fillMaxWidth()
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
                            text = item.product.displayName,
                            style =
                                MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text =
                                "${item.quantity.toPlainString()} ${item.unit.name}",
                            style =
                                MaterialTheme.typography.bodyMedium
                        )

                        Text(
                            text =
                                "Total cost: KSh ${item.totalCost.toPlainString()}",
                            style =
                                MaterialTheme.typography.bodyMedium,
                            color =
                                MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = trackingDescription(item),
                            style =
                                MaterialTheme.typography.bodySmall,
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            onRemoveLine(index)
                        }
                    ) {

                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Button(
        onClick = onCommit,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        enabled = lineItems.isNotEmpty()
    ) {

        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text("Commit Goods Receipt")
    }
}

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddReceiptLineDialog(
products: List<ProductMaster>,
units: List<ProductUnit>,
onDismiss: () -> Unit,
onAdd: (TempLineItem) -> Unit
) {
var selectedProduct by remember {
mutableStateOf(products.firstOrNull())
}

var selectedUnit by remember {
    mutableStateOf<ProductUnit?>(null)
}

var quantityText by remember {
    mutableStateOf("")
}

var totalCostText by remember {
    mutableStateOf("")
}

var trackingMode by remember {
    mutableStateOf(
        StockBatch.TRACKING_STANDARD_BATCHED
    )
}

var batchNumber by remember {
    mutableStateOf("")
}

var expiryText by remember {
    mutableStateOf("")
}

var errorMessage by remember {
    mutableStateOf<String?>(null)
}

var productMenuExpanded by remember {
    mutableStateOf(false)
}

var unitMenuExpanded by remember {
    mutableStateOf(false)
}

var trackingMenuExpanded by remember {
    mutableStateOf(false)
}

val productUnits = units.filter {
    it.productId == selectedProduct?.id && it.isActive
}

LaunchedEffect(selectedProduct, productUnits) {

    selectedUnit =
        productUnits.firstOrNull { it.isPurchaseUnit }
            ?: productUnits.firstOrNull()

    trackingMode =
        StockBatch.TRACKING_STANDARD_BATCHED

    batchNumber = ""
    expiryText = ""
    errorMessage = null
}

AlertDialog(
    onDismissRequest = onDismiss,

    title = {
        Text("Add Received Line Item")
    },

    text = {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            Text(
                text = "Product",
                style = MaterialTheme.typography.labelMedium
            )

            Box(
                modifier = Modifier.fillMaxWidth()
            ) {

                OutlinedButton(
                    onClick = {
                        productMenuExpanded = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Text(
                        text =
                            selectedProduct?.displayName
                                ?: "Choose product",
                        modifier = Modifier.weight(1f)
                    )

                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null
                    )
                }

                DropdownMenu(
                    expanded = productMenuExpanded,
                    onDismissRequest = {
                        productMenuExpanded = false
                    }
                ) {

                    products.forEach { product ->

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
                text = "Receiving Unit",
                style = MaterialTheme.typography.labelMedium
            )

            Box(
                modifier = Modifier.fillMaxWidth()
            ) {

                OutlinedButton(
                    onClick = {
                        unitMenuExpanded = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Text(
                        text =
                            selectedUnit?.name
                                ?: "Choose unit",
                        modifier = Modifier.weight(1f)
                    )

                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null
                    )
                }

                DropdownMenu(
                    expanded = unitMenuExpanded,
                    onDismissRequest = {
                        unitMenuExpanded = false
                    }
                ) {

                    productUnits.forEach { unit ->

                        DropdownMenuItem(
                            text = {
                                Text(
                                    "${unit.name} × ${unit.conversionMultiplier}"
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

            OutlinedTextField(
                value = quantityText,
                onValueChange = {
                    quantityText = it
                },
                label = {
                    Text("Received Quantity *")
                },
                placeholder = {
                    Text("e.g. 1 or 100.5")
                },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            KeyboardType.Decimal
                    ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = totalCostText,
                onValueChange = {
                    totalCostText = it
                },
                label = {
                    Text("Total Line Acquisition Cost (KES) *")
                },
                placeholder = {
                    Text("e.g. 500.00")
                },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            KeyboardType.Decimal
                    ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Batch Tracking",
                style = MaterialTheme.typography.labelMedium
            )

            Box(
                modifier = Modifier.fillMaxWidth()
            ) {

                OutlinedButton(
                    onClick = {
                        trackingMenuExpanded = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Text(
                        trackingMode,
                        modifier = Modifier.weight(1f)
                    )

                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null
                    )
                }

                DropdownMenu(
                    expanded = trackingMenuExpanded,
                    onDismissRequest = {
                        trackingMenuExpanded = false
                    }
                ) {

                    listOf(
                        StockBatch.TRACKING_STANDARD_BATCHED,
                        StockBatch.TRACKING_BATCH_UNKNOWN_EXPIRY,
                        StockBatch.TRACKING_SUPPLIER_UNTRACKED,
                        StockBatch.TRACKING_NON_BATCHED_COMMODITY
                    ).forEach { mode ->

                        DropdownMenuItem(
                            text = {
                                Text(mode)
                            },
                            onClick = {

                                trackingMode = mode
                                trackingMenuExpanded = false
                            }
                        )
                    }
                }
            }

            if (
                trackingMode ==
                StockBatch.TRACKING_STANDARD_BATCHED ||
                trackingMode ==
                StockBatch.TRACKING_BATCH_UNKNOWN_EXPIRY
            ) {

                OutlinedTextField(
                    value = batchNumber,
                    onValueChange = {
                        batchNumber = it
                    },
                    label = {
                        Text("Batch / Lot Number *")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (
                trackingMode ==
                StockBatch.TRACKING_STANDARD_BATCHED
            ) {

                OutlinedTextField(
                    value = expiryText,
                    onValueChange = {
                        expiryText = it
                    },
                    label = {
                        Text("Expiry Date (YYYY-MM-DD) *")
                    },
                    placeholder = {
                        Text("e.g. 2027-12-31")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    },

    confirmButton = {

        Button(
            onClick = {

                val product = selectedProduct
                val unit = selectedUnit

                if (product == null) {
                    errorMessage = "Select a product."
                    return@Button
                }

                if (unit == null) {
                    errorMessage = "Select a receiving unit."
                    return@Button
                }

                val baseUnit =
                    units.firstOrNull {
                        it.productId == product.id &&
                            it.isBaseUnit
                    }

                if (baseUnit == null) {
                    errorMessage =
                        "This product has no canonical base unit."
                    return@Button
                }

                val quantityScale =
                    QuantityScale.entries.firstOrNull {
                        it.multiplier ==
                            baseUnit.conversionMultiplier
                    }

                if (quantityScale == null) {
                    errorMessage =
                        "The product's base-unit precision is not supported."
                    return@Button
                }

                val quantity = try {
                    Quantity.fromDecimalString(
                        decimalString = quantityText,
                        scale = quantityScale
                    )
                } catch (exception: Exception) {
                    errorMessage =
                        exception.message
                            ?: "Invalid quantity."
                    return@Button
                }

                if (!quantity.isPositive) {
                    errorMessage =
                        "Quantity must be greater than zero."
                    return@Button
                }

                val totalCost = try {
                    Money.fromDecimalString(
                        totalCostText
                    )
                } catch (exception: Exception) {
                    errorMessage =
                        exception.message
                            ?: "Invalid acquisition cost."
                    return@Button
                }

                if (totalCost.isNegative) {
                    errorMessage =
                        "Acquisition cost cannot be negative."
                    return@Button
                }

                val resolvedExpiry =
                    when (trackingMode) {

                        StockBatch.TRACKING_STANDARD_BATCHED -> {

                            val parsed =
                                parseExpiryDateInt(expiryText)

                            if (parsed == null) {
                                errorMessage =
                                    "Enter a valid expiry date in YYYY-MM-DD format."
                                return@Button
                            }

                            parsed
                        }

                        else ->
                            StockBatch.EXPIRY_UNKNOWN_OR_NONE
                    }

                when (trackingMode) {

                    StockBatch.TRACKING_STANDARD_BATCHED,
                    StockBatch.TRACKING_BATCH_UNKNOWN_EXPIRY -> {

                        if (batchNumber.isBlank()) {
                            errorMessage =
                                "Batch number is required."
                            return@Button
                        }
                    }
                }

                onAdd(
                    TempLineItem(
                        product = product,
                        unit = unit,
                        quantity = quantity,
                        totalCost = totalCost,
                        trackingMode = trackingMode,
                        batchNumber = batchNumber.trim(),
                        expiryDateInt = resolvedExpiry
                    )
                )
            }
        ) {
            Text("Add Line")
        }
    },

    dismissButton = {

        TextButton(
            onClick = onDismiss
        ) {
            Text("Cancel")
        }
    }
)

}

@Composable
private fun ReceiptHistoryContent(
receipts: List<GoodsReceipt>,
onReceiptSelected: (GoodsReceipt) -> Unit
) {

if (receipts.isEmpty()) {

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Icon(
                imageVector = Icons.Default.Inbox,
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
                text = "No goods receipts found.",
                style =
                    MaterialTheme.typography.titleMedium
            )
        }
    }

    return
}

LazyColumn(
    modifier = Modifier
        .fillMaxSize()
        .padding(16.dp),
    verticalArrangement =
        Arrangement.spacedBy(8.dp)
) {

    items(
        items = receipts,
        key = { it.id }
    ) { receipt ->

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onReceiptSelected(receipt)
                }
        ) {

            Column(
                modifier = Modifier.padding(16.dp)
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.SpaceBetween
                ) {

                    Text(
                        text = receipt.receiptNumber,
                        style =
                            MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = receipt.status,
                        style =
                            MaterialTheme.typography.labelSmall,
                        color =
                            MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text =
                        "Received: ${
                            SimpleDateFormat(
                                "yyyy-MM-dd HH:mm",
                                Locale.US
                            ).format(
                                Date(receipt.receivedAt)
                            )
                        }",
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant
                )

                receipt.sourceDocumentRef
                    ?.takeIf { it.isNotBlank() }
                    ?.let { reference ->

                        Text(
                            text = "Reference: $reference",
                            style =
                                MaterialTheme.typography.bodySmall,
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
            }
        }
    }
}

}

@Composable
private fun ReceiptDetailDialog(
receipt: GoodsReceipt,
items: List<GoodsReceiptItem>,
onDismiss: () -> Unit
) {

AlertDialog(
    onDismissRequest = onDismiss,

    title = {
        Text("Receipt: ${receipt.receiptNumber}")
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
                "Status: ${receipt.status}"
            )

            Text(
                "Received: ${
                    SimpleDateFormat(
                        "yyyy-MM-dd HH:mm",
                        Locale.US
                    ).format(
                        Date(receipt.receivedAt)
                    )
                }"
            )

            receipt.sourceDocumentRef
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    Text("Reference: $it")
                }

            receipt.notes
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    Text("Notes: $it")
                }

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = "Received Items (${items.size})",
                fontWeight = FontWeight.Bold
            )

            items.forEach { item ->

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                MaterialTheme.colorScheme
                                    .surfaceVariant
                        )
                ) {

                    Column(
                        modifier = Modifier.padding(10.dp)
                    ) {

                        Text(
                            text =
                                "Product: ${item.productId}",
                            fontWeight =
                                FontWeight.SemiBold
                        )

                        Text(
                            text =
                                "Quantity: ${item.receivedQuantity.toPlainString()}"
                        )

                        Text(
                            text =
                                "Unit cost: KSh ${
                                    item.unitCost.toPlainString()
                                }"
                        )

                        Text(
                            text =
                                "Total cost: KSh ${
                                    item.totalCost.toPlainString()
                                }"
                        )

                        Text(
                            text =
                                "Tracking: ${item.trackingMode}"
                        )

                        item.batchNumber
                            ?.takeIf { it.isNotBlank() }
                            ?.let {
                                Text("Batch: $it")
                            }

                        if (
                            item.expiryDateInt !=
                            StockBatch.EXPIRY_UNKNOWN_OR_NONE
                        ) {
                            Text(
                                "Expiry: ${
                                    formatExpiryDateInt(
                                        item.expiryDateInt
                                    )
                                }"
                            )
                        }
                    }
                }
            }
        }
    },

    confirmButton = {

        TextButton(
            onClick = onDismiss
        ) {
            Text("Close")
        }
    }
)

}

private fun trackingDescription(
item: TempLineItem
): String {

return when (item.trackingMode) {

    StockBatch.TRACKING_STANDARD_BATCHED ->
        "Batch: ${item.batchNumber} | Expiry: ${
            formatExpiryDateInt(
                item.expiryDateInt
            )
        }"

    StockBatch.TRACKING_BATCH_UNKNOWN_EXPIRY ->
        "Batch: ${item.batchNumber} | Expiry unknown"

    StockBatch.TRACKING_SUPPLIER_UNTRACKED ->
        "Supplier-untracked stock"

    StockBatch.TRACKING_NON_BATCHED_COMMODITY ->
        "Non-batched commodity"

    else ->
        item.trackingMode
}

}

private fun parseExpiryDateInt(
value: String
): Int? {

val parts = value.trim().split("-")

if (parts.size != 3) {
    return null
}

val year = parts[0].toIntOrNull()
val month = parts[1].toIntOrNull()
val day = parts[2].toIntOrNull()

if (
    year == null ||
    month == null ||
    day == null
) {
    return null
}

val dateInt =
    year * 10_000 +
        month * 100 +
        day

return if (
    StockBatch.isValidExpiryDateInt(dateInt)
) {
    dateInt
} else {
    null
}

}

private fun formatExpiryDateInt(
value: Int
): String {

if (
    value ==
    StockBatch.EXPIRY_UNKNOWN_OR_NONE
) {
    return "Unknown"
}

val year = value / 10_000
val month = (value % 10_000) / 100
val day = value % 100

return String.format(
    Locale.US,
    "%04d-%02d-%02d",
    year,
    month,
    day
)

}

/**

GoodsReceiptItem requires a unitCost snapshot in addition to the

authoritative totalCost.



The total acquisition cost remains authoritative for receiving and

cost-layer creation. When the commercial quantity does not divide the

total cost into an exact minor-unit price, the unit-cost snapshot is

deterministically rounded HALF_UP.



This does NOT alter totalCost or the acquisition cost pool.
*/
private fun deriveUnitCost(
totalCost: Money,
quantity: Quantity
): Money {

require(quantity.isPositive) {
"Quantity must be positive."
}

val physicalQuantity =
BigDecimal(quantity.storageUnits)
.movePointLeft(quantity.scale.scale)

require(physicalQuantity.signum() > 0) {
"Quantity must be positive."
}

val roundedMinorUnits =
BigDecimal.valueOf(
totalCost.amountMinorUnits
)
.divide(
physicalQuantity,
0,
RoundingMode.HALF_UP
)
.longValueExact()

return Money(roundedMinorUnits)
}
