package org.mwangaza.app.scanner

import java.util.Locale

object ProductExtractionEngine {
    fun extract(ocr: List<OcrResult>, barcodes: List<BarcodeResult>): ProductScanDraft {
        val text = ocr.joinToString("\n") { it.text }.trim()
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        fun lineAfter(vararg keys: String): String? {
            val index = lines.indexOfFirst { line -> keys.any { key -> line.lowercase(Locale.ROOT).contains(key) } }
            return lines.getOrNull(index + 1)
        }
        val active = lineAfter("active ingredient", "active ingredients")
        val strength = Regex("""\b\d+(?:[.,]\d+)?\s*(?:mg|mcg|g|kg|ml|mL|%)(?:\s*/\s*\d+(?:[.,]\d+)?\s*(?:ml|mL|g))?\b""", RegexOption.IGNORE_CASE)
            .find(text)?.value
        val dosageForms = listOf("tablet", "tablets", "capsule", "capsules", "syrup", "suspension", "solution", "cream", "ointment", "gel", "injection", "drops", "suppository", "sachet", "powder")
        val dosageForm = dosageForms.firstOrNull { form -> Regex("\\b" + Regex.escape(form) + "\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) }
        val routes = listOf("oral", "topical", "intravenous", "intramuscular", "subcutaneous", "ophthalmic", "otic", "nasal", "rectal")
        val route = routes.firstOrNull { value -> Regex("\\b" + Regex.escape(value) + "\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) }
        val manufacturer = lineAfter("manufactured by", "manufacturer", "manufactured for")
        val storage = lineAfter("storage", "store below", "store at")
        val prescription = when {
            Regex("""\bprescription only\b|\bPOM\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "Prescription Only"
            Regex("""\bover the counter\b|\bOTC\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "OTC"
            else -> null
        }
        val brand = lines.firstOrNull { line ->
            line.length in 3..80 &&
                !line.contains("mg", true) &&
                !line.contains("tablet", true) &&
                !line.contains("capsule", true) &&
                !line.contains("manufactur", true) &&
                !line.contains("storage", true)
        }
        return ProductScanDraft(
            brandName = brand,
            genericName = active,
            manufacturer = manufacturer,
            activeIngredients = active,
            strength = strength,
            dosageForm = dosageForm,
            route = route,
            prescriptionClassification = prescription,
            storageCondition = storage,
            barcodeValue = barcodes.firstOrNull()?.rawValue,
            barcodeFormat = barcodes.firstOrNull()?.format,
            otherDetectedText = text.takeIf { it.isNotBlank() }
        )
    }
}
