package org.mwangaza.app.scanner.interpretation

import core.domain.model.ProductType
import org.mwangaza.app.scanner.BarcodeResult
import org.mwangaza.app.scanner.OcrResult
import org.mwangaza.app.scanner.ProductScanDraft
import java.util.Locale

data class IdentityCandidate(
    val field: String,
    val value: String,
    val confidence: Float,
    val evidence: List<String>,
    val conflictingValues: List<String> = emptyList()
)

data class ProductIdentityInterpretation(
    val draft: ProductScanDraft,
    val candidates: List<IdentityCandidate>
)

object ProductIdentityInterpreter {
    fun interpret(
        productType: ProductType,
        ocr: List<OcrResult>,
        barcodes: List<BarcodeResult>
    ): ProductIdentityInterpretation {
        val lines = ocr.flatMap { result ->
            result.blocks.flatMap { block ->
                block.lines.map { it.text.trim() }
            }.ifEmpty { result.text.lines().map(String::trim) }
        }.filter { it.isNotBlank() }.distinct()

        val text = lines.joinToString("\n")
        val lower = text.lowercase(Locale.ROOT)

        val strengthRegex = Regex(
            """\b\d+(?:[.,]\d+)?\s*(?:mg|mcg|g|kg|ml|%)(?:\s*/\s*\d+(?:[.,]\d+)?\s*(?:ml|mL|g))?\b""",
            RegexOption.IGNORE_CASE
        )
        val strengthMatches = strengthRegex.findAll(text).map { it.value }.distinct().toList()

        val dosageForms = listOf(
            "tablet", "capsule", "syrup", "suspension", "solution",
            "cream", "ointment", "gel", "injection", "drops",
            "suppository", "sachet", "powder", "patch", "device"
        )
        val dosageForm = dosageForms.firstOrNull {
            Regex("\\b" + Regex.escape(it) + "\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        }

        val brandCandidates = lines
            .filter { it.length in 3..80 }
            .filterNot { line ->
                val l = line.lowercase(Locale.ROOT)
                strengthRegex.containsMatchIn(line) ||
                    dosageForms.any { Regex("\\b" + Regex.escape(it) + "\\b", RegexOption.IGNORE_CASE).containsMatchIn(line) } ||
                    listOf("manufactured", "manufacturer", "mfd.", "mfg.", "marketed", "distributed", "packed", "repacked", "storage", "batch", "expiry", "exp").any { l.contains(it) }
            }
            .map { line ->
                val visualProminence = ocr.flatMap { it.blocks }.flatMap { it.lines }
                    .firstOrNull { it.text.equals(line, ignoreCase = true) }
                    ?.bounds?.let { it.width().toFloat() * it.height().toFloat() } ?: 0f
                val score = (0.55f + (visualProminence / 5_000_000f).coerceIn(0f, 0.30f) +
                    if (productType == ProductType.MEDICINE) 0.05f else 0f).coerceAtMost(0.95f)
                IdentityCandidate("brandName", line, score, listOf("OCR_LINE", "VISUAL_PROMINENCE"))
            }
            .sortedByDescending { it.confidence }

        val manufacturerLine = lines.firstOrNull {
            it.lowercase(Locale.ROOT).matches(
                Regex(".*\\b(manufactured by|mfd\\.? by|mfg\\.? by)\\b.*")
            )
        }
        val manufacturer = manufacturerLine?.substringAfter("by", "").trim()?.ifBlank { null }

        val active = lines.firstOrNull {
            it.lowercase(Locale.ROOT).contains("active ingredient")
        }?.substringAfter(":", "").trim()?.ifBlank { null }

        val prescription = when {
            Regex("\\bprescription only\\b|\\bPOM\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "Prescription Only"
            Regex("\\bover the counter\\b|\\bOTC\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "OTC"
            else -> null
        }

        val draft = ProductScanDraft(
            brandName = brandCandidates.firstOrNull()?.value,
            genericName = if (productType == ProductType.MEDICINE) active else null,
            productType = productType.keycode,
            manufacturer = manufacturer,
            dosageForm = dosageForm,
            route = deriveRoute(dosageForm),
            strength = strengthMatches.firstOrNull(),
            activeIngredients = active,
            prescriptionClassification = prescription,
            barcodeValue = barcodes.firstOrNull()?.rawValue,
            barcodeFormat = barcodes.firstOrNull()?.format,
            otherDetectedText = text.ifBlank { null }
        )

        val conflicts = brandCandidates.map { it.value }.distinct().drop(1)
        val candidates = brandCandidates.take(5).map {
            it.copy(conflictingValues = conflicts)
        } + barcodes.map {
            IdentityCandidate(
                field = "identifier",
                value = it.rawValue,
                confidence = if (it.validationState == "PLAUSIBLE") 0.95f else 0.70f,
                evidence = listOf("BARCODE", it.format)
            )
        }

        return ProductIdentityInterpretation(draft, candidates)
    }

    private fun deriveRoute(dosageForm: String?): String? = when (dosageForm?.lowercase(Locale.ROOT)) {
        "tablet", "capsule", "syrup", "suspension", "solution", "powder", "sachet" -> "Oral"
        "cream", "ointment", "gel", "patch" -> "Topical"
        "injection" -> "Parenteral"
        "drops" -> "Ophthalmic"
        "suppository" -> "Rectal"
        else -> null
    }
}
