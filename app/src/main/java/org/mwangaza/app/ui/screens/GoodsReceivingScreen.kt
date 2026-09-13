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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import core.domain.model.StockBatch
import core.domain.receiving.ReceivingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mwangaza.app.data.AppContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private data class TempLineItem(
    val product: ProductMaster,
    val unit: ProductUnit,
    val quantity: Long,
    val costMajorStr: String,
    val trackingMode: String,
    val batchNumber: String,
    val expiryIso: String // "YYYY-MM-DD"
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

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Receive New Stock, 1: Receipts History
    var isLoading by remember { mutableStateOf(false) }

    var registeredProducts by remember { mutableStateOf<List<ProductMaster>>(emptyList()) }
    var registeredUnits by remember { mutableStateOf<List<ProductUnit>>(emptyList()) }
    var pastReceipts by remember { mutableStateOf<List<GoodsReceipt>>(emptyList()) }

    // Form State
    var receiptNumber by remember { mutableStateOf("") }
    var invoiceRef by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val lineItems = remember { mutableStateOf<List<TempLineItem>>(emptyList()) }

    // Add Line Dialog State
    var showAddLineDialog by remember { mutableStateOf(false) }

    // Receipt Detail Dialog
    var viewingReceipt by remember { mutableStateOf<GoodsReceipt?>(null) }
    var viewingReceiptItems by remember { mutableStateOf<List<GoodsReceiptItem>>(emptyList()) }

    fun refreshData() {
        scope.launch {
            isLoading = true
            withContext(Dispatchers.IO) {
                registeredProducts = container.productMasterDao.getAllProducts().filter { it.isActive }
                registeredUnits = container.productMasterDao.getAllUnits().filter { it.isActive }
                pastReceipts = container.goodsReceiptDao.getAllReceipts()
            }
            if (receiptNumber.isBlank()) {
                receiptNumber = "GR-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Goods Receiving") },
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
                    text = { Text("Receive Stock") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Receipts History (${pastReceipts.size})") }
                )
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (selectedTab == 0) {
                // Receive Stock Form
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
                        onValueChange = { receiptNumber = it },
                        label = { Text("Receipt Number / Identifier *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = invoiceRef,
                        onValueChange = { invoiceRef = it },
                        label = { Text("Supplier Invoice / Delivery Ref (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Receiving Notes (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Received Line Items (${lineItems.value.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = {
                                if (registeredProducts.isEmpty()) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Please register products first in Products module")
                                    }
                                } else {
                                    showAddLineDialog = true
                                }
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Line")
                        }
                    }

                    if (lineItems.value.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No line items added yet. Tap 'Add Line' to add received items.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        lineItems.value.forEachIndexed { index, item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.product.displayName,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = "Quantity: ${item.quantity} ${item.unit.name} (${item.quantity * item.unit.conversionMultiplier} base units)",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "Total Acquisition Cost: KES ${item.costMajorStr}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        val batchInfo = if (item.trackingMode == StockBatch.TRACKING_STANDARD) {
                                            "Batch: ${item.batchNumber} | Expiry: ${item.expiryIso}"
                                        } else {
                                            "Mode: ${item.trackingMode}" + if (item.batchNumber.isNotBlank()) " (${item.batchNumber})" else ""
                                        }
                                        Text(
                                            text = batchInfo,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            lineItems.value = lineItems.value.filterIndexed { i, _ -> i != index }
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (receiptNumber.isBlank()) {
                                scope.launch { snackbarHostState.showSnackbar("Receipt number is required") }
                                return@Button
                            }
                            if (lineItems.value.isEmpty()) {
                                scope.launch { snackbarHostState.showSnackbar("Add at least one line item to commit") }
                                return@Button
                            }

                            scope.launch {
                                isLoading = true
                                val now = System.currentTimeMillis()
                                val receiptId = UUID.randomUUID().toString()

                                val receipt = GoodsReceipt(
                                    id = receiptId,
                                    receiptNumber = receiptNumber.trim(),
                                    sourceDocumentRef = invoiceRef.trim().ifBlank { null },
                                    receivedAt = now,
                                    supplierId = null,
                                    notes = notes.trim().ifBlank { null },
                                    isCommitted = false,
                                    committedAt = null,
                                    createdAt = now,
                                    updatedAt = now
                                )

                                val domainItems = lineItems.value.mapIndexed { idx, temp ->
                                    val costMajor = temp.costMajorStr.toDoubleOrNull() ?: 0.0
                                    val costMinor = Math.round(costMajor * 100.0)

                                    val expiryInt = if (temp.trackingMode == StockBatch.TRACKING_STANDARD && temp.expiryIso.isNotBlank()) {
                                        val parts = temp.expiryIso.split("-")
                                        if (parts.size == 3) {
                                            (parts[0].toInt() * 10000) + (parts[1].toInt() * 100) + parts[2].toInt()
                                        } else -1
                                    } else -1

                                    GoodsReceiptItem(
                                        id = UUID.randomUUID().toString(),
                                        goodsReceiptId = receiptId,
                                        lineIndex = idx,
                                        productId = temp.product.id,
                                        unitId = temp.unit.id,
                                        quantity = Quantity.discrete(temp.quantity),
                                        acquisitionCost = Money(costMinor),
                                        batchTrackingMode = temp.trackingMode,
                                        supplierBatchNumber = temp.batchNumber.trim().ifBlank { null },
                                        expiryDate = expiryInt,
                                        createdAt = now,
                                        updatedAt = now
                                    )
                                }

                                val productsMap = registeredProducts.associateBy { it.id }
                                val unitsMap = registeredUnits.associateBy { it.id }

                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        container.receivingService.commitReceipt(
                                            receipt = receipt,
                                            items = domainItems,
                                            productsById = productsMap,
                                            unitsById = unitsMap,
                                            commitTimestamp = now
                                        )
                                    }

                                    when (result) {
                                        is ReceivingResult.Success -> {
                                            snackbarHostState.showSnackbar(
                                                "Receipt '${result.committedReceipt.receiptNumber}' committed! Created ${result.newBatches.size} batches and ${result.costLayers.size} cost layers."
                                            )
                                            // Reset form
                                            receiptNumber = "GR-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                                            invoiceRef = ""
                                            notes = ""
                                            lineItems.value = emptyList()
                                            refreshData()
                                            selectedTab = 1
                                        }
                                        is ReceivingResult.Failure -> {
                                            snackbarHostState.showSnackbar("Validation failure: ${result.errors.joinToString()}")
                                        }
                                    }
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Error committing receipt: ${e.message}")
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = lineItems.value.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Commit Goods Receipt")
                    }
                }
            } else {
                // Receipts History
                if (pastReceipts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Inbox, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No committed goods receipts found", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Stock receipts committed in the 'Receive Stock' tab will appear here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pastReceipts, key = { it.id }) { r ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            val items = withContext(Dispatchers.IO) {
                                                container.goodsReceiptDao.getItemsForReceipt(r.id)
                                            }
                                            viewingReceipt = r
                                            viewingReceiptItems = items
                                        }
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = r.receiptNumber, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = if (r.isCommitted) "COMMITTED" else "DRAFT",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Received: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(r.receivedAt))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!r.sourceDocumentRef.isNullOrBlank()) {
                                        Text(
                                            text = "Ref: ${r.sourceDocumentRef}",
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

    // Add Line Dialog
    if (showAddLineDialog) {
        var selectedProduct by remember { mutableStateOf<ProductMaster?>(registeredProducts.firstOrNull()) }
        val productUnits = remember(selectedProduct, registeredUnits) {
            registeredUnits.filter { it.productId == selectedProduct?.id }
        }
        var selectedUnit by remember { mutableStateOf<ProductUnit?>(productUnits.firstOrNull()) }

        LaunchedEffect(selectedProduct) {
            selectedUnit = productUnits.firstOrNull { it.isPurchaseUnit } ?: productUnits.firstOrNull()
        }

        var quantityStr by remember { mutableStateOf("") }
        var costMajorStr by remember { mutableStateOf("") }
        var trackingMode by remember { mutableStateOf(StockBatch.TRACKING_STANDARD) }
        var batchNumber by remember { mutableStateOf("") }
        var expiryIso by remember { mutableStateOf("") }
        var dialogError by remember { mutableStateOf<String?>(null) }

        var productMenuExpanded by remember { mutableStateOf(false) }
        var unitMenuExpanded by remember { mutableStateOf(false) }
        var modeMenuExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddLineDialog = false },
            title = { Text("Add Received Line Item") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Product Selection
                    Text("Select Product *", style = MaterialTheme.typography.labelMedium)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { productMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(selectedProduct?.displayName ?: "Choose product", modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = productMenuExpanded,
                            onDismissRequest = { productMenuExpanded = false }
                        ) {
                            registeredProducts.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.displayName) },
                                    onClick = {
                                        selectedProduct = p
                                        productMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Unit Selection
                    Text("Commercial Receiving Unit *", style = MaterialTheme.typography.labelMedium)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { unitMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = selectedUnit?.let { "${it.name} (${it.conversionMultiplier} base units)" } ?: "Choose unit",
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = unitMenuExpanded,
                            onDismissRequest = { unitMenuExpanded = false }
                        ) {
                            productUnits.forEach { u ->
                                DropdownMenuItem(
                                    text = { Text("${u.name} (x${u.conversionMultiplier} base units)") },
                                    onClick = {
                                        selectedUnit = u
                                        unitMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = quantityStr,
                        onValueChange = { quantityStr = it },
                        label = { Text("Received Quantity (Units) *") },
                        placeholder = { Text("e.g. 10") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = costMajorStr,
                        onValueChange = { costMajorStr = it },
                        label = { Text("Total Line Acquisition Cost (KES) *") },
                        placeholder = { Text("e.g. 5000.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Tracking Mode
                    Text("Batch Tracking Mode", style = MaterialTheme.typography.labelMedium)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { modeMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(trackingMode, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = modeMenuExpanded,
                            onDismissRequest = { modeMenuExpanded = false }
                        ) {
                            listOf(
                                StockBatch.TRACKING_STANDARD,
                                StockBatch.TRACKING_BATCH_UNKNOWN_EXPIRY,
                                StockBatch.TRACKING_SUPPLIER_UNTRACKED,
                                StockBatch.TRACKING_COMMODITY
                            ).forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode) },
                                    onClick = {
                                        trackingMode = mode
                                        modeMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (trackingMode == StockBatch.TRACKING_STANDARD || trackingMode == StockBatch.TRACKING_BATCH_UNKNOWN_EXPIRY) {
                        OutlinedTextField(
                            value = batchNumber,
                            onValueChange = { batchNumber = it },
                            label = { Text("Batch / Lot Number *") },
                            placeholder = { Text("e.g. BATCH-2026-A") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (trackingMode == StockBatch.TRACKING_STANDARD) {
                        OutlinedTextField(
                            value = expiryIso,
                            onValueChange = { expiryIso = it },
                            label = { Text("Expiry Date (YYYY-MM-DD) *") },
                            placeholder = { Text("e.g. 2027-12-31") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    dialogError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val product = selectedProduct
                        val unit = selectedUnit
                        if (product == null || unit == null) {
                            dialogError = "Select product and receiving unit."
                            return@Button
                        }
                        val qty = quantityStr.trim().toLongOrNull()
                        if (qty == null || qty <= 0L) {
                            dialogError = "Quantity must be a positive integer (> 0)."
                            return@Button
                        }
                        val cost = costMajorStr.trim().toDoubleOrNull()
                        if (cost == null || cost < 0.0) {
                            dialogError = "Enter valid non-negative acquisition cost."
                            return@Button
                        }

                        if (trackingMode == StockBatch.TRACKING_STANDARD) {
                            if (batchNumber.isBlank()) {
                                dialogError = "Batch number is required for standard tracked stock."
                                return@Button
                            }
                            val dateRegex = Regex("""^\d{4}-\d{2}-\d{2}$""")
                            if (!dateRegex.matches(expiryIso.trim())) {
                                dialogError = "Expiry date must be in YYYY-MM-DD format."
                                return@Button
                            }
                        } else if (trackingMode == StockBatch.TRACKING_BATCH_UNKNOWN_EXPIRY && batchNumber.isBlank()) {
                            dialogError = "Batch number is required."
                            return@Button
                        }

                        val newLine = TempLineItem(
                            product = product,
                            unit = unit,
                            quantity = qty,
                            costMajorStr = String.format(Locale.US, "%.2f", cost),
                            trackingMode = trackingMode,
                            batchNumber = batchNumber.trim(),
                            expiryIso = expiryIso.trim()
                        )

                        lineItems.value = lineItems.value + newLine
                        showAddLineDialog = false
                    }
                ) {
                    Text("Add Line")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddLineDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Receipt Detail Dialog
    viewingReceipt?.let { r ->
        AlertDialog(
            onDismissRequest = { viewingReceipt = null },
            title = { Text("Receipt: ${r.receiptNumber}") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Received At: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(r.receivedAt))}")
                    if (!r.sourceDocumentRef.isNullOrBlank()) {
                        Text("Invoice / Ref: ${r.sourceDocumentRef}")
                    }
                    if (!r.notes.isNullOrBlank()) {
                        Text("Notes: ${r.notes}")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Received Items (${viewingReceiptItems.size}):", fontWeight = FontWeight.Bold)

                    viewingReceiptItems.forEach { itm ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Product ID: ${itm.productId}", fontWeight = FontWeight.SemiBold)
                                Text("Quantity: ${itm.quantity.storageUnits} storage units")
                                val costStr = "KES ${(itm.acquisitionCost.amountMinorUnits / 100)}.${(itm.acquisitionCost.amountMinorUnits % 100).toString().padStart(2, '0')}"
                                Text("Cost: $costStr", color = MaterialTheme.colorScheme.primary)
                                if (itm.supplierBatchNumber != null) {
                                    Text("Batch: ${itm.supplierBatchNumber} | Expiry: ${itm.expiryDate}")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingReceipt = null }) {
                    Text("Close")
                }
            }
        )
    }
}
