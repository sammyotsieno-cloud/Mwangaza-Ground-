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
            "tablet", "tablets", "tab", "tabs", "capsule", "capsules", "cap", "caps",
            "caplet", "caplets", "syrup", "suspension", "susp", "solution", "soln", "sol",
            "powder", "pwd", "sachet", "sachets", "granules", "cream", "cr", "ointment",
            "oint", "gel", "lotion", "injection", "inj", "vial", "ampoule", "ampule", "amp",
            "drops", "spray", "inhaler", "patch", "patches", "suppository", "suppositories",
            "supp", "pessary", "pessaries", "pess", "device", "kit", "strip", "strips",
            "lozenge", "lozenges", "film", "emulsion", "eye drops", "ear drops",
            "nasal spray", "nasal drops", "granule", "paste", "foam", "shampoo",
            "mouthwash", "rinse", "elixir", "tincture", "liniment", "paint",
            "dressing", "bandage", "gauze", "catheter", "cannula", "syringe",
            "needle", "gloves", "mask", "condom"
        )
        val dosageForm = dosageForms.firstOrNull {
            Regex("\\b" + Regex.escape(it) + "\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        }

        val explicitRoute = listOf(
            "Ophthalmic" to Regex("\\bophthalmic\\b|\\beye\\s+drops?\\b", RegexOption.IGNORE_CASE),
            "Otic" to Regex("\\botic\\b|\\bear\\s+drops?\\b", RegexOption.IGNORE_CASE),
            "Nasal" to Regex("\\bnasal\\b|\\bintranasal\\b", RegexOption.IGNORE_CASE),
            "Topical" to Regex("\\btopical\\b|\\bdermal\\b|\\bcutaneous\\b", RegexOption.IGNORE_CASE),
            "Transdermal" to Regex("\\btransdermal\\b", RegexOption.IGNORE_CASE),
            "Parenteral" to Regex("\\bparenteral\\b|\\bintravenous\\b|\\bintramuscular\\b|\\bsubcutaneous\\b", RegexOption.IGNORE_CASE),
            "Rectal" to Regex("\\brectal\\b", RegexOption.IGNORE_CASE),
            "Vaginal" to Regex("\\bvaginal\\b|\\bintravaginal\\b", RegexOption.IGNORE_CASE),
            "Oral" to Regex("\\boral\\b|\\bby\\s+mouth\\b|\\bper\\s+os\\b", RegexOption.IGNORE_CASE)
        ).firstOrNull { it.second.containsMatchIn(text) }?.first

        val inferredRoute = when (dosageForm?.lowercase(Locale.ROOT)) {
            "tablet", "tablets", "tab", "tabs", "capsule", "capsules", "cap", "caps", "caplet", "caplets",
            "syrup", "suspension", "susp", "solution", "soln", "sol", "powder", "pwd", "sachet", "sachets", "granules",
            "lozenge", "lozenges", "film" -> "Oral"
            "cream", "cr", "ointment", "oint", "gel", "lotion", "patch", "patches" -> "Topical"
            "injection", "inj", "vial", "ampoule", "ampule", "amp" -> "Parenteral"
            "eye drops" -> "Ophthalmic"
            "ear drops" -> "Otic"
            "nasal spray", "nasal drops" -> "Nasal"
            "suppository", "suppositories", "supp" -> "Rectal"
            "pessary", "pessaries", "pess" -> "Vaginal"
            else -> null
        }
        val route = explicitRoute ?: inferredRoute
        val routeSource = when {
            explicitRoute != null -> "EXPLICIT"
            inferredRoute != null -> "INFERRED"
            else -> null
        }

        val ocrLines = ocr.flatMap { result ->
            result.blocks.flatMap { block ->
                block.lines.map { line ->
                    Triple(line.text.trim(), line.bounds?.width()?.toFloat() ?: 0f, line.bounds?.top?.toFloat() ?: Float.MAX_VALUE)
                }
            }
        }.filter { it.first.isNotBlank() }

        val maxWidth = ocrLines.maxOfOrNull { it.second }?.takeIf { it > 0f } ?: 1f
        val minTop = ocrLines.minOfOrNull { it.third }?.takeIf { it != Float.MAX_VALUE } ?: 0f
        val maxTop = ocrLines.maxOfOrNull { it.third }?.takeIf { it != Float.MAX_VALUE } ?: minTop
        val topRange = (maxTop - minTop).coerceAtLeast(1f)

        val brandCandidates = lines
            .filter { it.length in 3..80 }
            .filterNot { line ->
                val l = line.lowercase(Locale.ROOT)
                strengthRegex.containsMatchIn(line) ||
                    dosageForms.any { Regex("\\b" + Regex.escape(it) + "\\b", RegexOption.IGNORE_CASE).containsMatchIn(line) } ||
                    listOf("manufactured", "manufacturer", "mfd.", "mfg.", "marketed", "distributed", "packed", "repacked", "produced", "made by", "licence", "license", "storage", "batch", "lot", "expiry", "exp", "mfg").any { l.contains(it) } ||
                    Regex("\\b(?:contains?|each|per|net|volume|for\\s+oral\\s+use)\\b", RegexOption.IGNORE_CASE).containsMatchIn(line)
            }
            .map { line ->
                val evidence = ocrLines.firstOrNull { it.first.equals(line, ignoreCase = true) }
                val relativeWidth = ((evidence?.second ?: 0f) / maxWidth).coerceIn(0f, 1f)
                val relativeTop = if (evidence == null || evidence.third == Float.MAX_VALUE) 0.5f else ((evidence.third - minTop) / topRange).coerceIn(0f, 1f)
                val score = (0.32f + relativeWidth * 0.30f + (1f - relativeTop) * 0.20f +
                    when { line.length in 3..24 -> 0.12f; line.length in 25..40 -> 0.05f; else -> 0f } -
                    if (line.count { it == ' ' } > 7) 0.12f else 0f).coerceIn(0.05f, 0.97f)
                IdentityCandidate("brandName", line, score, listOf("OCR_LINE", "RELATIVE_SIZE", "VERTICAL_POSITION", "NEGATIVE_FILTERS"))
            }
            .distinctBy { it.value.lowercase(Locale.ROOT) }
            .sortedByDescending { it.confidence }

        val manufacturerCues = listOf(
            "Manufactured by", "Mfd. by", "Mfd by", "Mfg. by", "Mfg by",
            "Manufactured for", "Marketed by", "Distributed by", "Packed by",
            "Repacked by", "Produced by", "Made by", "Under licence by", "Under license by"
        )
        val manufacturerMatch = manufacturerCues.asSequence().flatMap { cue ->
            lines.asSequence().mapNotNull { line ->
                Regex("(?i)\\b" + Regex.escape(cue) + "\\s*[:\\-]?\\s*(.+)$").find(line)
            }
        }.firstOrNull()
        val manufacturer = manufacturerMatch?.groupValues?.get(1)?.trim()?.trimEnd('.', ',', ';', ':')?.ifBlank { null }

        val active = lines.firstOrNull {
            it.lowercase(Locale.ROOT).contains("active ingredient")
        }?.substringAfter(":", "").trim()?.ifBlank { null }

        val prescription = when {
            Regex("\\bprescription only\\b|\\bPOM\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "Prescription Only"
            Regex("\\bover the counter\\b|\\bOTC\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "OTC"
            else -> null
        }

        val categoryExtraction = CategoryExtractionProfiles.extract(productType, ocr)
        val categoryStrength = categoryExtraction.variables.firstOrNull { it.definitionKey == "strength" }?.value

        val draft = ProductScanDraft(
            brandName = brandCandidates.firstOrNull()?.value,
            genericName = if (productType == ProductType.MEDICINE) {
                categoryExtraction.ingredients.firstOrNull()?.ingredientName ?: active
            } else null,
            productType = productType,
            manufacturer = manufacturer,
            dosageForm = dosageForm,
            route = route,
            routeSource = routeSource,
            strength = categoryStrength ?: strengthMatches.firstOrNull(),
            activeIngredients = active,
            ingredientProposals = categoryExtraction.ingredients,
            prescriptionClassification = prescription,
            storageCondition = categoryExtraction.variables.firstOrNull { it.definitionKey == "storage_condition" }?.value,
            categoryVariables = categoryExtraction.variables,
            barcodeValue = barcodes.firstOrNull()?.rawValue,
            barcodeFormat = barcodes.firstOrNull()?.format,
            otherDetectedText = text.ifBlank { null }
        )

        val conflicts = brandCandidates.map { it.value }.distinct().drop(1)
        val candidates = brandCandidates.take(5).map {
            it.copy(conflictingValues = conflicts)
        } + (manufacturer?.let { listOf(IdentityCandidate("manufacturer", it, 0.97f, listOf("MANUFACTURER_CUE"))) } ?: emptyList()) + (dosageForm?.let { listOf(IdentityCandidate("productForm", it, 0.93f, listOf("OCR_FORM_KEYWORD"))) } ?: emptyList()) + barcodes.map {
            IdentityCandidate(
                field = "identifier",
                value = it.rawValue,
                confidence = if (it.validationState == "PLAUSIBLE") 0.95f else 0.70f,
                evidence = listOf("BARCODE", it.format)
            )
        }

        return ProductIdentityInterpretation(draft, candidates)
    }

}
