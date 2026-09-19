package org.mwangaza.app.scanner

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.io.File

class ProductBarcodeAnalyzer {
    fun scan(context: Context, imageFile: File): List<BarcodeResult> {
        val scanner = BarcodeScanning.getClient()
        return try {
            val image = InputImage.fromFilePath(context, Uri.fromFile(imageFile))
            Tasks.await(scanner.process(image)).mapNotNull { barcode ->
                barcode.rawValue?.takeIf { it.isNotBlank() }?.let { raw ->
                    BarcodeResult(
                        rawValue = raw,
                        format = formatName(barcode.format),
                        bounds = barcode.boundingBox?.let(::android.graphics.Rect),
                        cornerPoints = barcode.cornerPoints?.map { it.x to it.y }.orEmpty(),
                        sourceImageUri = Uri.fromFile(imageFile).toString(),
                        validationState = if (isPlausibleIdentifier(barcode.format, raw)) "PLAUSIBLE" else "UNVALIDATED"
                    )
                }
            }
        } finally {
            scanner.close()
        }
    }

    private fun formatName(format: Int): String = when (format) {
        Barcode.FORMAT_CODE_128 -> "CODE_128"
        Barcode.FORMAT_CODE_39 -> "CODE_39"
        Barcode.FORMAT_CODE_93 -> "CODE_93"
        Barcode.FORMAT_CODABAR -> "CODABAR"
        Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
        Barcode.FORMAT_EAN_13 -> "EAN_13"
        Barcode.FORMAT_EAN_8 -> "EAN_8"
        Barcode.FORMAT_ITF -> "ITF"
        Barcode.FORMAT_QR_CODE -> "QR_CODE"
        Barcode.FORMAT_UPC_A -> "UPC_A"
        Barcode.FORMAT_UPC_E -> "UPC_E"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_AZTEC -> "AZTEC"
        else -> "UNKNOWN"
    }

    private fun isPlausibleIdentifier(format: Int, raw: String): Boolean =
        when (format) {
            Barcode.FORMAT_EAN_13 -> raw.length == 13 && raw.all(Char::isDigit)
            Barcode.FORMAT_EAN_8 -> raw.length == 8 && raw.all(Char::isDigit)
            Barcode.FORMAT_UPC_A -> raw.length == 12 && raw.all(Char::isDigit)
            Barcode.FORMAT_UPC_E -> raw.length in 6..8 && raw.all(Char::isDigit)
            else -> false
        }
}
