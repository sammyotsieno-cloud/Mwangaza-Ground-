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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import core.domain.model.Money
import core.domain.model.PharmaceuticalDetail
import core.domain.model.ProductImage
import org.mwangaza.app.scanner.ProductScanDraft
import android.net.Uri
import core.domain.model.ProductMaster
import core.domain.model.ProductUnit
import core.domain.model.QuantityScale
import core.domain.model.UnitPriceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mwangaza.app.data.AppContainer
import java.util.UUID

data class ProductWithDetails(
    val product: ProductMaster,
    val units: List<ProductUnit>,
    val priceConfigsByUnitId: Map<String, UnitPriceConfig>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onScanProduct: () -> Unit,
    initialScanDraft: ProductScanDraft? = null,
    onScanDraftConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var productsWithDetails by remember { mutableStateOf<List<ProductWithDetails>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    var showAddProductDialog by remember { mutableStateOf(initialScanDraft != null) }
    var selectedProductForDetails by remember { mutableStateOf<ProductWithDetails?>(null) }
    var showAddUnitDialogForProduct by remember { mutableStateOf<ProductMaster?>(null) }
    var showEditPriceDialogForUnit by remember {
        mutableStateOf<Pair<ProductUnit, UnitPriceConfig?>?>(null)
    }

    fun refreshProducts() {
        scope.launch {
            isLoading = true

            val loaded = withContext(Dispatchers.IO) {
                val products = container.productMasterDao.getAllProducts()
                val allUnits = container.productMasterDao.getAllUnits()
                val allPrices = container.productMasterDao.getAllPriceConfigs()

                val unitsByProductId = allUnits.groupBy { it.productId }
                val pricesByUnitId = allPrices.associateBy { it.productUnitId }

                products.map { product ->
                    ProductWithDetails(
                        product = product,
                        units = unitsByProductId[product.id] ?: emptyList(),
                        priceConfigsByUnitId = pricesByUnitId
                    )
                }
            }

            productsWithDetails = loaded
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshProducts()
    }

    val filteredProducts = remember(productsWithDetails, searchQuery) {
        if (searchQuery.isBlank()) {
            productsWithDetails
        } else {
            val q = searchQuery.trim().lowercase()

            productsWithDetails.filter {
                it.product.brandName?.lowercase()?.contains(q) == true ||
                    it.product.genericName?.lowercase()?.contains(q) == true ||
                    it.product.manufacturer?.lowercase()?.contains(q) == true ||
                    it.product.productType?.lowercase()?.contains(q) == true
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
        topBar = {
            TopAppBar(
                title = {
                    Text("Products")
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    showAddProductDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add Product"
                )
            }
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {

            OutlinedButton(
                onClick = onScanProduct,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Product")
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = {
                    Text("Search by brand, generic name, type...")
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                searchQuery = ""
                            }
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear"
                            )
                        }
                    }
                },
                singleLine = true
            )

            if (isLoading) {

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

            } else if (filteredProducts.isEmpty()) {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Medication,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.5f
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (searchQuery.isBlank()) {
                                "No products registered yet"
                            } else {
                                "No matching products found"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (searchQuery.isBlank()) {
                                "Tap the + button to register your first product."
                            } else {
                                "Try a different search query."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.7f
                            )
                        )
                    }
                }

            } else {

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        filteredProducts,
                        key = { it.product.id }
                    ) { item ->

                        val product = item.product
                        val baseUnit = item.units.firstOrNull { it.isBaseUnit }
                        val basePrice = baseUnit?.let {
                            item.priceConfigsByUnitId[it.id]
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedProductForDetails = item
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (product.isActive) {
                                    MaterialTheme.colorScheme.surface
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = 0.6f
                                    )
                                }
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 1.dp
                            )
                        ) {

                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {

                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = product.displayName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )

                                        if (
                                            !product.productType.isNullOrBlank() ||
                                            !product.manufacturer.isNullOrBlank()
                                        ) {
                                            val subtitle = listOfNotNull(
                                                product.productType,
                                                product.manufacturer
                                            ).joinToString(" • ")

                                            Text(
                                                text = subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Surface(
                                        color = if (product.isActive) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.errorContainer
                                        },
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            text = if (product.isActive) {
                                                "Active"
                                            } else {
                                                "Retired"
                                            },
                                            modifier = Modifier.padding(
                                                horizontal = 8.dp,
                                                vertical = 2.dp
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (product.isActive) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onErrorContainer
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(
                                        alpha = 0.5f
                                    )
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {

                                    Column {
                                        Text(
                                            text = "Units: ${item.units.size} configured",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Text(
                                            text = "Precision: ${product.quantityScale.decimalPlaces} decimal place(s)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (baseUnit != null) {

                                        val priceStr = basePrice?.sellingPrice?.let {
                                            "KES ${(it.amountMinorUnits / 100)}." +
                                                (it.amountMinorUnits % 100)
                                                    .toString()
                                                    .padStart(2, '0')
                                        } ?: "Price not set"

                                        Text(
                                            text = "Base (${baseUnit.name}): $priceStr",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary
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

    selectedProductForDetails?.let { details ->

        val product = details.product

        AlertDialog(
            onDismissRequest = {
                selectedProductForDetails = null
            },
            title = {
                Text(product.displayName)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    if (!product.brandName.isNullOrBlank()) {
                        Text(
                            "Brand: ${product.brandName}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (!product.genericName.isNullOrBlank()) {
                        Text(
                            "Generic: ${product.genericName}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (!product.productType.isNullOrBlank()) {
                        Text(
                            "Type: ${product.productType}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (!product.manufacturer.isNullOrBlank()) {
                        Text(
                            "Manufacturer: ${product.manufacturer}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (!product.description.isNullOrBlank()) {
                        Text(
                            "Description: ${product.description}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Quantity Policy",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Precision: ${product.quantityScale.decimalPlaces} decimal place(s)",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "Minimum transaction increment: ${
                            product.quantityScale.fromStorageUnits(
                                product.minimumTransactionIncrementStorageUnits
                            )
                        } ${details.units.firstOrNull { it.isBaseUnit }?.name ?: "base units"}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Configured Commercial Units",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    details.units.forEach { unit ->

                        val priceConfig =
                            details.priceConfigsByUnitId[unit.id]

                        val priceStr = priceConfig?.sellingPrice?.let {
                            "KES ${(it.amountMinorUnits / 100)}." +
                                (it.amountMinorUnits % 100)
                                    .toString()
                                    .padStart(2, '0')
                        } ?: "No price"

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {

                                        Text(
                                            text = unit.name +
                                                (unit.abbreviation?.let {
                                                    " ($it)"
                                                } ?: ""),
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )

                                        if (unit.isBaseUnit) {
                                            Text(
                                                text = " [BASE]",
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }

                                    Text(
                                        text = if (unit.isBaseUnit) {
                                            "Canonical conversion: 1/1 base unit"
                                        } else {
                                            "Conversion: ${unit.conversionFraction} base units"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Text(
                                        text = "Selling Price: $priceStr",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        showEditPriceDialogForUnit =
                                            Pair(unit, priceConfig)
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit Price",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            showAddUnitDialogForProduct = product
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text("Add Commercial Unit")
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedButton(
                        onClick = {
                            scope.launch {

                                withContext(Dispatchers.IO) {
                                    container.productMasterDao.updateProduct(
                                        product.copy(
                                            isActive = !product.isActive,
                                            updatedAt = System.currentTimeMillis()
                                        )
                                    )
                                }

                                selectedProductForDetails = null
                                refreshProducts()

                                snackbarHostState.showSnackbar(
                                    "Product status updated"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (product.isActive) {
                                "Retire Product"
                            } else {
                                "Reactivate Product"
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedProductForDetails = null
                    }
                ) {
                    Text("Close")
                }
            }
        )
    }

    if (showAddProductDialog) {

        var brandName by remember { mutableStateOf(initialScanDraft?.brandName ?: "") }
        var genericName by remember { mutableStateOf(initialScanDraft?.genericName ?: "") }
        var productType by remember { mutableStateOf(initialScanDraft?.productType ?: "") }
        var manufacturer by remember { mutableStateOf(initialScanDraft?.manufacturer ?: "") }
        var description by remember { mutableStateOf(initialScanDraft?.description ?: "") }

        var activeIngredients by remember { mutableStateOf(initialScanDraft?.activeIngredients ?: "") }
        var strength by remember { mutableStateOf(initialScanDraft?.strength ?: "") }
        var dosageForm by remember { mutableStateOf(initialScanDraft?.dosageForm ?: "") }
        var route by remember { mutableStateOf(initialScanDraft?.route ?: "") }
        var therapeuticCategory by remember { mutableStateOf(initialScanDraft?.therapeuticCategory ?: "") }
        var prescriptionClassification by remember { mutableStateOf(initialScanDraft?.prescriptionClassification ?: "") }
        var storageCondition by remember { mutableStateOf(initialScanDraft?.storageCondition ?: "") }
        var scannedImageUris by remember { mutableStateOf(initialScanDraft?.sourceImageUris ?: emptyList()) }

        var baseUnitName by remember { mutableStateOf("") }
        var baseUnitAbbr by remember { mutableStateOf("") }

        var quantityScaleInput by remember { mutableStateOf("0") }
        var minimumIncrementInput by remember { mutableStateOf("1") }

        var initialPriceMajor by remember { mutableStateOf("") }

        var errorMessage by remember {
            mutableStateOf<String?>(null)
        }

        AlertDialog(
            onDismissRequest = {
                showAddProductDialog = false
            },
            title = {
                Text("Register New Product")
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    OutlinedTextField(
                        value = brandName,
                        onValueChange = {
                            brandName = it
                        },
                        label = {
                            Text("Brand / Trade Name")
                        },
                        placeholder = {
                            Text("e.g. Panadol, Amoxil")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = genericName,
                        onValueChange = {
                            genericName = it
                        },
                        label = {
                            Text("Generic / INN Name")
                        },
                        placeholder = {
                            Text("e.g. Paracetamol 500mg")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = productType,
                        onValueChange = {
                            productType = it
                        },
                        label = {
                            Text("Product Type")
                        },
                        placeholder = {
                            Text("e.g. Tablet, Capsule, Syrup, Vial")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = manufacturer,
                        onValueChange = {
                            manufacturer = it
                        },
                        label = {
                            Text("Manufacturer")
                        },
                        placeholder = {
                            Text("e.g. GSK, Dawa Ltd")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(value = activeIngredients, onValueChange = { activeIngredients = it }, label = { Text("Active Ingredient(s) (Optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = strength, onValueChange = { strength = it }, label = { Text("Strength (Optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = dosageForm, onValueChange = { dosageForm = it }, label = { Text("Dosage Form (Optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = route, onValueChange = { route = it }, label = { Text("Route (Optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = therapeuticCategory, onValueChange = { therapeuticCategory = it }, label = { Text("Therapeutic Category (Optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = prescriptionClassification, onValueChange = { prescriptionClassification = it }, label = { Text("Prescription Classification (Optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = storageCondition, onValueChange = { storageCondition = it }, label = { Text("Storage Condition (Optional)") }, modifier = Modifier.fillMaxWidth())

                    OutlinedTextField(
                        value = description,
                        onValueChange = {
                            description = it
                        },
                        label = {
                            Text("Description / Notes (Optional)")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Canonical Base Unit",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = baseUnitName,
                        onValueChange = {
                            baseUnitName = it
                        },
                        label = {
                            Text("Base Unit Name *")
                        },
                        placeholder = {
                            Text("e.g. Tablet, Capsule, mL, Piece")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = baseUnitAbbr,
                        onValueChange = {
                            baseUnitAbbr = it
                        },
                        label = {
                            Text("Abbreviation (Optional)")
                        },
                        placeholder = {
                            Text("e.g. tab, cap, mL")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Quantity Policy",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Quantity scale controls decimal precision. It is separate from packaging conversions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = quantityScaleInput,
                        onValueChange = {
                            quantityScaleInput = it
                        },
                        label = {
                            Text("Quantity Scale (0–6) *")
                        },
                        placeholder = {
                            Text("0 = whole units, 3 = 0.001 precision")
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = minimumIncrementInput,
                        onValueChange = {
                            minimumIncrementInput = it
                        },
                        label = {
                            Text("Minimum Transaction Increment (storage units) *")
                        },
                        placeholder = {
                            Text("e.g. 1 at scale 0, 500 at scale 3 for 0.5")
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = initialPriceMajor,
                        onValueChange = {
                            initialPriceMajor = it
                        },
                        label = {
                            Text("Selling Price per Base Unit (KES) *")
                        },
                        placeholder = {
                            Text("e.g. 5 or 10.50")
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

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

                        if (
                            brandName.isBlank() &&
                            genericName.isBlank()
                        ) {
                            errorMessage =
                                "Provide at least a brand name or a generic name."
                            return@Button
                        }

                        if (baseUnitName.isBlank()) {
                            errorMessage =
                                "Base unit name is required."
                            return@Button
                        }

                        val quantityScale = try {
                            QuantityScale.fromInt(
                                quantityScaleInput.trim().toInt()
                            )
                        } catch (_: Exception) {
                            errorMessage =
                                "Quantity scale must be an integer from 0 to 6."
                            return@Button
                        }

                        val minimumIncrement =
                            minimumIncrementInput.trim().toLongOrNull()

                        if (
                            minimumIncrement == null ||
                            minimumIncrement <= 0L
                        ) {
                            errorMessage =
                                "Minimum transaction increment must be a positive whole number."
                            return@Button
                        }

                        val priceMinor = try {
                            Money.fromDecimalString(
                                initialPriceMajor.trim()
                            ).amountMinorUnits
                        } catch (_: Exception) {
                            errorMessage =
                                "Enter a valid non-negative selling price."
                            return@Button
                        }

                        scope.launch {

                            val now =
                                System.currentTimeMillis()

                            val productId =
                                UUID.randomUUID().toString()

                            val unitId =
                                UUID.randomUUID().toString()

                            val priceConfigId =
                                UUID.randomUUID().toString()

                            val product = ProductMaster(
                                id = productId,
                                brandName = brandName
                                    .trim()
                                    .ifBlank { null },
                                genericName = genericName
                                    .trim()
                                    .ifBlank { null },
                                productType = productType
                                    .trim()
                                    .ifBlank { null },
                                manufacturer = manufacturer
                                    .trim()
                                    .ifBlank { null },
                                description = description
                                    .trim()
                                    .ifBlank { null },
                                quantityScale = quantityScale,
                                minimumTransactionIncrementStorageUnits =
                                    minimumIncrement,
                                isActive = true,
                                createdAt = now,
                                updatedAt = now
                            )

                            val baseUnit = ProductUnit(
                                id = unitId,
                                productId = productId,
                                name = baseUnitName.trim(),
                                abbreviation = baseUnitAbbr
                                    .trim()
                                    .ifBlank { null },
                                conversionNumerator = 1L,
                                conversionDenominator = 1L,
                                isBaseUnit = true,
                                isPurchaseUnit = true,
                                isDispensingUnit = true,
                                isDisplayUnit = true,
                                isActive = true,
                                sortOrder = 0,
                                createdAt = now,
                                updatedAt = now
                            )

                            val priceConfig = UnitPriceConfig(
                                id = priceConfigId,
                                productUnitId = unitId,
                                sellingPrice = Money(priceMinor),
                                isActive = true,
                                createdAt = now,
                                updatedAt = now
                            )

                            withContext(Dispatchers.IO) {
                                container.productMasterDao.insertProduct(product)
                                container.productMasterDao.insertUnit(baseUnit)
                                container.productMasterDao.savePriceConfig(priceConfig)

                                if (listOf(activeIngredients, strength, dosageForm, route, therapeuticCategory, prescriptionClassification, storageCondition).any { it.isNotBlank() }) {
                                    container.productMasterDao.insertPharmaceuticalDetail(
                                        PharmaceuticalDetail(
                                            id = UUID.randomUUID().toString(),
                                            productId = productId,
                                            activeIngredients = activeIngredients.trim().ifBlank { null },
                                            strength = strength.trim().ifBlank { null },
                                            dosageForm = dosageForm.trim().ifBlank { null },
                                            route = route.trim().ifBlank { null },
                                            therapeuticCategory = therapeuticCategory.trim().ifBlank { null },
                                            prescriptionClassification = prescriptionClassification.trim().ifBlank { null },
                                            storageCondition = storageCondition.trim().ifBlank { null },
                                            createdAt = now,
                                            updatedAt = now
                                        )
                                    )
                                }

                                scannedImageUris.forEachIndexed { index, uriString ->
                                    val source = java.io.File(Uri.parse(uriString).path ?: "")
                                    if (source.exists()) {
                                        val imageDir = java.io.File(context.filesDir, "product_images").apply { mkdirs() }
                                        val destination = java.io.File(imageDir, productId + "_" + index + ".jpg")
                                        source.copyTo(destination, overwrite = true)
                                        container.productMasterDao.insertProductImage(
                                            ProductImage(
                                                id = UUID.randomUUID().toString(),
                                                productId = productId,
                                                imageUri = Uri.fromFile(destination).toString(),
                                                imageSource = ProductImage.SOURCE_SCANNER_OUTPUT,
                                                isPrimary = index == 0,
                                                sortOrder = index,
                                                createdAt = now
                                            )
                                        )
                                    }
                                }
                            }

                            showAddProductDialog = false
                            onScanDraftConsumed()
                            refreshProducts()

                            snackbarHostState.showSnackbar(
                                "Product '${product.displayName}' registered"
                            )
                        }
                    }
                ) {
                    Text("Register")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddProductDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    showAddUnitDialogForProduct?.let { product ->

        var unitName by remember { mutableStateOf("") }
        var unitAbbr by remember { mutableStateOf("") }
        var numeratorStr by remember { mutableStateOf("") }
        var denominatorStr by remember { mutableStateOf("1") }
        var priceStr by remember { mutableStateOf("") }
        var isPurchase by remember { mutableStateOf(true) }
        var isDispensing by remember { mutableStateOf(true) }
        var unitError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = {
                showAddUnitDialogForProduct = null
            },
            title = {
                Text("Add Commercial Unit")
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "For: ${product.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = unitName,
                        onValueChange = { unitName = it },
                        label = { Text("Unit Name *") },
                        placeholder = { Text("e.g. Box of 100, Blister of 10") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = unitAbbr,
                        onValueChange = { unitAbbr = it },
                        label = { Text("Abbreviation (Optional)") },
                        placeholder = { Text("e.g. box100, blist") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Exact Commercial Conversion",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "1 commercial unit = numerator / denominator canonical base units.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = numeratorStr,
                        onValueChange = { numeratorStr = it },
                        label = { Text("Conversion Numerator *") },
                        placeholder = { Text("100 for a box containing 100 tablets") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = denominatorStr,
                        onValueChange = { denominatorStr = it },
                        label = { Text("Conversion Denominator *") },
                        placeholder = { Text("1 for whole-base-unit packaging") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = priceStr,
                        onValueChange = { priceStr = it },
                        label = { Text("Selling Price for this Unit (KES)") },
                        placeholder = { Text("e.g. 500") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isPurchase,
                            onCheckedChange = { isPurchase = it }
                        )
                        Text("Available for Goods Receiving (Purchase Unit)")
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isDispensing,
                            onCheckedChange = { isDispensing = it }
                        )
                        Text("Available for Dispensing (Sale Unit)")
                    }

                    unitError?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (unitName.isBlank()) {
                            unitError = "Unit name is required."
                            return@Button
                        }

                        val numerator = numeratorStr.trim().toLongOrNull()
                        if (numerator == null || numerator <= 0L) {
                            unitError = "Conversion numerator must be a positive whole number."
                            return@Button
                        }

                        val denominator = denominatorStr.trim().toLongOrNull()
                        if (denominator == null || denominator <= 0L) {
                            unitError = "Conversion denominator must be a positive whole number."
                            return@Button
                        }

                        if (numerator == 1L && denominator != 1L) {
                            unitError = "A conversion of 1/n must be intentional. Verify that this commercial unit really represents a fractional base quantity."
                        }

                        val priceMinor = try {
                            Money.fromDecimalString(priceStr.trim()).amountMinorUnits
                        } catch (_: Exception) {
                            null
                        }

                        scope.launch {
                            val now = System.currentTimeMillis()
                            val unitId = UUID.randomUUID().toString()

                            val unit = ProductUnit(
                                id = unitId,
                                productId = product.id,
                                name = unitName.trim(),
                                abbreviation = unitAbbr.trim().ifBlank { null },
                                conversionNumerator = numerator,
                                conversionDenominator = denominator,
                                isBaseUnit = false,
                                isPurchaseUnit = isPurchase,
                                isDispensingUnit = isDispensing,
                                isDisplayUnit = false,
                                isActive = true,
                                sortOrder = 1,
                                createdAt = now,
                                updatedAt = now
                            )

                            withContext(Dispatchers.IO) {
                                container.productMasterDao.insertUnit(unit)

                                if (priceMinor != null) {
                                    val priceConfig = UnitPriceConfig(
                                        id = UUID.randomUUID().toString(),
                                        productUnitId = unitId,
                                        sellingPrice = Money(priceMinor),
                                        isActive = true,
                                        createdAt = now,
                                        updatedAt = now
                                    )
                                    container.productMasterDao.savePriceConfig(priceConfig)
                                }
                            }

                            showAddUnitDialogForProduct = null
                            selectedProductForDetails = null
                            refreshProducts()

                            snackbarHostState.showSnackbar(
                                "Added unit '${unit.name}' for ${product.displayName}"
                            )
                        }
                    }
                ) {
                    Text("Save Unit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddUnitDialogForProduct = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    showEditPriceDialogForUnit?.let { (unit, existingConfig) ->
        val currentPriceMajor = existingConfig?.sellingPrice?.let {
            "${it.amountMinorUnits / 100}." +
                (it.amountMinorUnits % 100).toString().padStart(2, '0')
        } ?: ""

        var newPriceMajor by remember { mutableStateOf(currentPriceMajor) }
        var priceError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = {
                showEditPriceDialogForUnit = null
            },
            title = { Text("Configure Selling Price") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Unit: ${unit.name} (${unit.conversionFraction} base units)")

                    OutlinedTextField(
                        value = newPriceMajor,
                        onValueChange = { newPriceMajor = it },
                        label = { Text("Selling Price (KES) *") },
                        placeholder = { Text("e.g. 50.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    priceError?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val minor = try {
                            Money.fromDecimalString(newPriceMajor.trim()).amountMinorUnits
                        } catch (_: Exception) {
                            priceError = "Enter a valid non-negative price."
                            return@Button
                        }

                        scope.launch {
                            val now = System.currentTimeMillis()
                            val config = UnitPriceConfig(
                                id = existingConfig?.id ?: UUID.randomUUID().toString(),
                                productUnitId = unit.id,
                                sellingPrice = Money(minor),
                                isActive = true,
                                createdAt = existingConfig?.createdAt ?: now,
                                updatedAt = now
                            )

                            withContext(Dispatchers.IO) {
                                container.productMasterDao.savePriceConfig(config)
                            }

                            showEditPriceDialogForUnit = null
                            selectedProductForDetails = null
                            refreshProducts()

                            snackbarHostState.showSnackbar(
                                "Updated price for ${unit.name}"
                            )
                        }
                    }
                ) {
                    Text("Save Price")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditPriceDialogForUnit = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
