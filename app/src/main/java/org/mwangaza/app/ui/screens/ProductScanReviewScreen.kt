package org.mwangaza.app.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.Image
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mwangaza.app.scanner.ProductScanAnalysis
import org.mwangaza.app.scanner.ProductScanDraft

@Composable
fun ProductScanReviewScreen(
    analysis: ProductScanAnalysis,
    draft: ProductScanDraft = analysis.draft,
    onRetake: () -> Unit,
    onSaveAsIs: () -> Unit,
    onAddAnother: () -> Unit,
    onConfirm: (ProductScanDraft) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Scan Review", style = MaterialTheme.typography.headlineSmall)
        remember(analysis.workingUri) { BitmapFactory.decodeFile(java.io.File(Uri.parse(analysis.workingUri).path ?: "").absolutePath) }?.let { bitmap ->
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Processed product image", modifier = Modifier.fillMaxWidth().height(280.dp))
        }
        Text("Quality: ${analysis.quality.warnings.ifEmpty { listOf("No quality warnings") }.joinToString()}")
        analysis.barcodeResults.forEach { Text("Barcode: ${it.rawValue} (${it.format})") }
        Text("Detected text", style = MaterialTheme.typography.titleMedium)
        Text(analysis.ocrResults.joinToString("\n") { it.text }.ifBlank { "No readable text detected." })
        Text("Extracted product information", style = MaterialTheme.typography.titleMedium)
        Text("Brand: ${draft.brandName ?: "Unavailable"}")
        Text("Generic / active ingredient: ${draft.genericName ?: "Unavailable"}")
        Text("Strength: ${draft.strength ?: "Unavailable"}")
        Text("Dosage form: ${draft.dosageForm ?: "Unavailable"}")
        Text("Route: ${draft.route ?: "Unavailable"}")
        Text("Manufacturer: ${draft.manufacturer ?: "Unavailable"}")
        Text("Prescription classification: ${draft.prescriptionClassification ?: "Unavailable"}")
        Text("Storage: ${draft.storageCondition ?: "Unavailable"}")
        Text("Other text: ${draft.otherDetectedText ?: "Unavailable"}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRetake) { Text("Retake") }
            OutlinedButton(onClick = onSaveAsIs) { Text("Save As Is") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAddAnother) { Text("Add Another Photo") }
            Button(onClick = { onConfirm(draft) }) { Text("Use in Registration") }
        }
    }
}
