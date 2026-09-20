package org.mwangaza.app.scanner

import android.content.Context
import android.net.Uri
import core.domain.model.ProductType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ProductScanEngine(
    private val objectDetector: ProductObjectDetector = ProductObjectDetector(),
    private val ocrAnalyzer: ProductOcrAnalyzer = ProductOcrAnalyzer(),
    private val barcodeAnalyzer: ProductBarcodeAnalyzer = ProductBarcodeAnalyzer()
) {
    suspend fun process(
        context: Context,
        originalFile: File,
        workingFile: File,
        productType: ProductType
    ): ProductScanAnalysis =
        withContext(Dispatchers.Default) {
            val originalBitmap = android.graphics.BitmapFactory.decodeFile(originalFile.absolutePath)
                ?: error("Unable to decode captured image")
            val quality = ProductImageQualityAnalyzer.analyze(originalBitmap, originalFile)
            val detections = runCatching { objectDetector.detect(context, originalFile) }.getOrDefault(emptyList())
            val workingBitmap = ProductImageProcessor.createWorkingCopy(originalFile, workingFile, null)
            workingBitmap.recycle()
            originalBitmap.recycle()
            val ocr = runCatching {
                listOf(ocrAnalyzer.recognize(context, workingFile)).filter { it.text.isNotBlank() }
            }.getOrDefault(emptyList())
            val barcodes = runCatching { barcodeAnalyzer.scan(context, workingFile, ocr) }.getOrDefault(emptyList())
            val interpretation = ProductExtractionEngine.extract(productType, ocr, barcodes)
            ProductScanAnalysis(
                originalUri = Uri.fromFile(originalFile).toString(),
                workingUri = Uri.fromFile(workingFile).toString(),
                detectedRegions = detections,
                quality = quality,
                ocrResults = ocr,
                barcodeResults = barcodes,
                draft = interpretation.draft,
                identityCandidates = interpretation.candidates,
                observations = listOf(
                    ProductScanObservation(
                        sourceImageUri = Uri.fromFile(originalFile).toString(),
                        ocrResults = ocr,
                        barcodeResults = barcodes
                    )
                ),
                reconciliationFindings = interpretation.candidates.map {
                    ReconciledFinding(
                        field = it.field,
                        value = it.value,
                        status = if (it.conflictingValues.isNotEmpty()) "CONFLICT" else "UNIQUE",
                        sourceImageUris = listOf(Uri.fromFile(originalFile).toString()),
                        evidence = it.evidence,
                        conflictingValues = it.conflictingValues
                    )
                }
            )
        }
}
