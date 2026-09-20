package org.mwangaza.app.scanner

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.io.File

class ProductBarcodeAnalyzer {
    fun scan(context: Context, imageFile: File, ocrResults: List<OcrResult> = emptyList()): List<BarcodeResult> {
        val scanner = BarcodeScanning.getClient()
        return try {
            val image = InputImage.fromFilePath(context, Uri.fromFile(imageFile))
            val engineResults = Tasks.await(scanner.process(image)).mapNotNull { barcode ->
                barcode.rawValue?.takeIf { it.isNotBlank() }?.let { raw ->
                    BarcodeResult(
                        rawValue = raw,
                        format = formatName(barcode.format),
                        bounds = barcode.boundingBox?.let { android.graphics.Rect(it) },
                        cornerPoints = barcode.cornerPoints?.map { it.x to it.y }.orEmpty(),
                        sourceImageUri = Uri.fromFile(imageFile).toString(),
                        validationState = if (isPlausibleIdentifier(barcode.format, raw)) "PLAUSIBLE" else "UNVALIDATED"
                    )
                }
            }
            if (engineResults.isNotEmpty()) engineResults else ocrFallback(ocrResults, imageFile)
        } finally {
            scanner.close()
        }
    }

    private fun ocrFallback(ocrResults: List<OcrResult>, imageFile: File): List<BarcodeResult> {
        val digitPattern = Regex("""\\b\\d{8,14}\\b""")
        return ocrResults.flatMap { result ->
            result.blocks.flatMap { block ->
                block.lines.flatMap { line -> digitPattern.findAll(line.text).map { it.value }.toList() }
            }.ifEmpty { digitPattern.findAll(result.text).map { it.value }.toList() }
        }.distinct().mapNotNull { raw ->
            val format = when (raw.length) {
                13 -> "EAN_13"
                8 -> "EAN_8"
                12 -> "UPC_A"
                else -> return@mapNotNull null
            }
            if (!validateByFormat(format, raw)) return@mapNotNull null
            BarcodeResult(raw, format, sourceImageUri = Uri.fromFile(imageFile).toString(), validationState = "PLAUSIBLE")
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
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_UPC_A -> "UPC_A"
        Barcode.FORMAT_UPC_E -> "UPC_E"
        else -> "UNKNOWN"
    }

    private fun isPlausibleIdentifier(format: Int, raw: String): Boolean =
        validateByFormat(formatName(format), raw)

    private fun validateByFormat(format: String, raw: String): Boolean = when (format) {
        "EAN_13" -> raw.length == 13 && raw.all(Char::isDigit) && validCheckDigit(raw)
        "EAN_8" -> raw.length == 8 && raw.all(Char::isDigit) && validCheckDigit(raw)
        "UPC_A" -> raw.length == 12 && raw.all(Char::isDigit) && validCheckDigit(raw)
        "UPC_E" -> validUpcE(raw)
        else -> false
    }

    private fun validCheckDigit(raw: String): Boolean {
        if (raw.length < 2 || !raw.all(Char::isDigit)) return false
        val check = raw.last().digitToInt()
        val sum = raw.dropLast(1).map { it.digitToInt() }.reversed()
            .mapIndexed { index, digit -> digit * if (index % 2 == 0) 3 else 1 }.sum()
        return (10 - (sum % 10)) % 10 == check
    }

    private fun validUpcE(raw: String): Boolean {
        if (!raw.all(Char::isDigit) || raw.length != 8) return false
        val numberSystem = raw[0]
        val compressed = raw.substring(1, 7)
        val check = raw[7].digitToInt()
        val expanded = expandUpcE(numberSystem, compressed) ?: return false
        return validCheckDigit(expanded + check)
    }

    private fun expandUpcE(numberSystem: Char, compressed: String): String? {
        if (compressed.length != 6 || numberSystem !in '0'..'1') return null
        val d = compressed.map { it.digitToInt() }
        val body = when (d[5]) {
            0, 1, 2 -> "${d[0]}${d[1]}${d[5]}0000${d[2]}${d[3]}${d[4]}"
            3 -> "${d[0]}${d[1]}${d[2]}00000${d[3]}${d[4]}"
            4 -> "${d[0]}${d[1]}${d[2]}${d[3]}00000${d[4]}"
            else -> "${d[0]}${d[1]}${d[2]}${d[3]}${d[4]}0000${d[5]}"
        }
        return numberSystem + body
    }
}
