package org.mwangaza.app.scanner

import android.graphics.Rect
import core.domain.model.ProductType

data class ReconciledFinding(
    val field: String,
    val value: String,
    val normalizedValue: String? = null,
    val status: String = "UNIQUE",
    val sourceImageUris: List<String> = emptyList(),
    val evidence: List<String> = emptyList(),
    val conflictingValues: List<String> = emptyList()
)

data class ProductScanObservation(
    val sourceImageUri: String,
    val ocrResults: List<OcrResult> = emptyList(),
    val barcodeResults: List<BarcodeResult> = emptyList()
)

data class ProductScanDraft(
    val brandName: String? = null,
    val genericName: String? = null,
    val productType: ProductType? = null,
    val manufacturer: String? = null,
    val description: String? = null,
    val activeIngredients: String? = null,
    val ingredientProposals: List<org.mwangaza.app.scanner.interpretation.ProductIngredientProposal> = emptyList(),
    val strength: String? = null,
    val dosageForm: String? = null,
    val route: String? = null,
    val routeSource: String? = null,
    val therapeuticCategory: String? = null,
    val prescriptionClassification: String? = null,
    val storageCondition: String? = null,
    val categoryVariables: List<org.mwangaza.app.scanner.interpretation.CategoryVariableProposal> = emptyList(),
    val barcodeValue: String? = null,
    val barcodeFormat: String? = null,
    val otherDetectedText: String? = null,
    val sourceImageUris: List<String> = emptyList()
)

data class DetectedRegion(val bounds: Rect, val confidence: Float, val labels: List<String>)
data class ImageQualityResult(
    val width: Int, val height: Int, val sharpnessScore: Double, val brightnessScore: Double,
    val glareScore: Double, val orientationDegrees: Int, val warnings: List<String>
) { val isUsable get() = warnings.none { it == "SEVERE_BLUR" || it == "EXTREME_DARKNESS" } }
data class OcrElementEvidence(val text: String, val bounds: Rect?, val confidence: Float? = null)
data class OcrLineEvidence(val text: String, val bounds: Rect?, val elements: List<OcrElementEvidence>, val confidence: Float? = null)
data class OcrBlockEvidence(val text: String, val bounds: Rect?, val lines: List<OcrLineEvidence>)
data class OcrResult(val text: String, val confidence: Float?, val sourceImageUri: String, val blocks: List<OcrBlockEvidence> = emptyList())
data class BarcodeResult(
    val rawValue: String, val format: String, val bounds: Rect? = null,
    val cornerPoints: List<Pair<Int, Int>> = emptyList(), val sourceImageUri: String? = null,
    val validationState: String = "UNVALIDATED"
)
data class ProductScanAnalysis(
    val originalUri: String, val workingUri: String, val detectedRegions: List<DetectedRegion>,
    val quality: ImageQualityResult, val ocrResults: List<OcrResult>, val barcodeResults: List<BarcodeResult>,
    val draft: ProductScanDraft, val identityCandidates: List<org.mwangaza.app.scanner.interpretation.IdentityCandidate> = emptyList(),
    val processingNotes: List<String> = emptyList(),
    val observations: List<ProductScanObservation> = emptyList(),
    val reconciliationFindings: List<ReconciledFinding> = emptyList()
)
