package org.mwangaza.app.scanner

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ProductScanEngine(
    private val objectDetector: ProductObjectDetector = ProductObjectDetector(),
    private val ocrAnalyzer: ProductOcrAnalyzer = ProductOcrAnalyzer(),
    private val barcodeAnalyzer: ProductBarcodeAnalyzer = ProductBarcodeAnalyzer()
) {
    suspend fun process(context: Context, originalFile: File, workingFile: File): ProductScanAnalysis =
        withContext(Dispatchers.Default) {
            val originalBitmap = android.graphics.BitmapFactory.decodeFile(originalFile.absolutePath)
                ?: error("Unable to decode captured image")
            val quality = ProductImageQualityAnalyzer.analyze(originalBitmap, originalFile)
            val detections = runCatching { objectDetector.detect(context, originalFile) }.getOrDefault(emptyList())
            // Generic object detection is evidence for localization, not proof of a product boundary.
            // Do not crop automatically from a generic ML Kit object result.
            val workingBitmap = ProductImageProcessor.createWorkingCopy(originalFile, workingFile, null)
            workingBitmap.recycle()
            originalBitmap.recycle()
            val ocr = runCatching {
                listOf(ocrAnalyzer.recognize(context, workingFile)).filter { it.text.isNotBlank() }
            }.getOrDefault(emptyList())
            val barcodes = runCatching { barcodeAnalyzer.scan(context, workingFile) }.getOrDefault(emptyList())
            val draft = ProductExtractionEngine.extract(ocr, barcodes)
            ProductScanAnalysis(
                originalUri = Uri.fromFile(originalFile).toString(),
                workingUri = Uri.fromFile(workingFile).toString(),
                detectedRegions = detections,
                quality = quality,
                ocrResults = ocr,
                barcodeResults = barcodes,
                draft = draft
            )
        }
}
