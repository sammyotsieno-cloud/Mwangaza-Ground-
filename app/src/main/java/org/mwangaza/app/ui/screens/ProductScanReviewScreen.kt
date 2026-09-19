package org.mwangaza.app.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import org.mwangaza.app.scanner.ProductScanAnalysis
import org.mwangaza.app.scanner.ProductScanDraft

@Composable
fun ProductScanReviewScreen(
    analysis: ProductScanAnalysis,
    onRetake: () -> Unit,
    onSaveAsIs: (ProductScanDraft) -> Unit,
    onAddAnother: () -> Unit,
    onConfirm: (ProductScanDraft) -> Unit
) {
    var brand by remember { mutableStateOf(analysis.draft.brandName.orEmpty()) }
    var generic by remember { mutableStateOf(analysis.draft.genericName.orEmpty()) }
    var manufacturer by remember { mutableStateOf(analysis.draft.manufacturer.orEmpty()) }
    var strength by remember { mutableStateOf(analysis.draft.strength.orEmpty()) }
    var dosageForm by remember { mutableStateOf(analysis.draft.dosageForm.orEmpty()) }
    var route by remember { mutableStateOf(analysis.draft.route.orEmpty()) }
    var barcode by remember { mutableStateOf(analysis.draft.barcodeValue.orEmpty()) }
    val routeSource = analysis.draft.routeSource
    var therapeutic by remember { mutableStateOf(analysis.draft.therapeuticCategory.orEmpty()) }
    var prescription by remember { mutableStateOf(analysis.draft.prescriptionClassification.orEmpty()) }
    var storage by remember { mutableStateOf(analysis.draft.storageCondition.orEmpty()) }

    fun currentDraft() = analysis.draft.copy(
        brandName = brand.trim().ifBlank { null },
        genericName = generic.trim().ifBlank { null },
        manufacturer = manufacturer.trim().ifBlank { null },
        strength = strength.trim().ifBlank { null },
        dosageForm = dosageForm.trim().ifBlank { null },
        route = route.trim().ifBlank { null },
        routeSource = routeSource,
        barcodeValue = barcode.trim().ifBlank { null },
        therapeuticCategory = therapeutic.trim().ifBlank { null },
        prescriptionClassification = prescription.trim().ifBlank { null },
        storageCondition = storage.trim().ifBlank { null },
        sourceImageUris = listOf(analysis.originalUri)
    )

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Scan Review", style = MaterialTheme.typography.headlineSmall)
        remember(analysis.workingUri) {
            BitmapFactory.decodeFile(Uri.parse(analysis.workingUri).path ?: "")
        }?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Processed product image",
                modifier = Modifier.fillMaxWidth().height(280.dp)
            )
        }
        Text(
            "Quality: " + analysis.quality.warnings.ifEmpty { listOf("No quality warnings") }.joinToString()
        )
        analysis.barcodeResults.forEach { Text("Barcode: ${it.rawValue} (${it.format})") }
        OutlinedTextField(brand, { brand = it }, label = { Text("Brand / Trade Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(generic, { generic = it }, label = { Text("Generic / Active Ingredient") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(strength, { strength = it }, label = { Text("Strength") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(dosageForm, { dosageForm = it }, label = { Text("Dosage Form") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(manufacturer, { manufacturer = it }, label = { Text("Manufacturer") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = barcode,
            onValueChange = { barcode = it },
            label = { Text("Barcode / Identifier") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(route, { route = it }, label = { Text("Route") }, modifier = Modifier.fillMaxWidth())
        Text(
            "Route source: " + when (routeSource) {
                "EXPLICIT" -> "Explicit on packaging"
                "INFERRED" -> "Inferred from product form"
                else -> "Not determined"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(therapeutic, { therapeutic = it }, label = { Text("Therapeutic Category") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(prescription, { prescription = it }, label = { Text("Prescription Classification") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(storage, { storage = it }, label = { Text("Storage Condition") }, modifier = Modifier.fillMaxWidth())
        Text("Detected text", style = MaterialTheme.typography.titleMedium)
        Text(analysis.ocrResults.joinToString("\n") { it.text }.ifBlank { "No readable text detected." })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRetake) { Text("Retake") }
            OutlinedButton(onClick = { onSaveAsIs(currentDraft()) }) { Text("Save As Is") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAddAnother) { Text("Add Another Photo") }
            Button(onClick = { onConfirm(currentDraft()) }) { Text("Use in Registration") }
        }
    }
}
