package org.mwangaza.app.scanner

import android.graphics.Rect

data class ProductScanDraft(
    val brandName: String? = null,
    val genericName: String? = null,
    val productType: String? = null,
    val manufacturer: String? = null,
    val description: String? = null,
    val activeIngredients: String? = null,
    val strength: String? = null,
    val dosageForm: String? = null,
    val route: String? = null,
    val therapeuticCategory: String? = null,
    val prescriptionClassification: String? = null,
    val storageCondition: String? = null,
    val barcodeValue: String? = null,
    val barcodeFormat: String? = null,
    val otherDetectedText: String? = null,
    val sourceImageUris: List<String> = emptyList()
)

data class DetectedRegion(val bounds: Rect, val confidence: Float, val labels: List<String>)

data class ImageQualityResult(
    val width: Int,
    val height: Int,
    val sharpnessScore: Double,
    val brightnessScore: Double,
    val glareScore: Double,
    val orientationDegrees: Int,
    val warnings: List<String>
) {
    val isUsable: Boolean
        get() = warnings.none { it == "SEVERE_BLUR" || it == "EXTREME_DARKNESS" }
}

data class BarcodeResult(val rawValue: String, val format: String)

data class OcrResult(val text: String, val confidence: Float?, val sourceImageUri: String)

data class ProductScanAnalysis(
    val originalUri: String,
    val workingUri: String,
    val detectedRegions: List<DetectedRegion>,
    val quality: ImageQualityResult,
    val ocrResults: List<OcrResult>,
    val barcodeResults: List<BarcodeResult>,
    val draft: ProductScanDraft,
    val processingNotes: List<String> = emptyList()
)
