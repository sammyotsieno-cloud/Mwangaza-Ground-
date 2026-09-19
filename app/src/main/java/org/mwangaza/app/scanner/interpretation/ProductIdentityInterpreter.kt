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
            "nasal spray", "nasal drops"
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
            else -> if (productType == ProductType.MEDICINE && dosageForm == null) "Oral" else null
        }
        val route = explicitRoute ?: inferredRoute
        val routeSource = when {
            explicitRoute != null -> "EXPLICIT"
            inferredRoute != null -> "INFERRED"
            else -> null
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

        val draft = ProductScanDraft(
            brandName = brandCandidates.firstOrNull()?.value,
            genericName = if (productType == ProductType.MEDICINE) active else null,
            productType = productType,
            manufacturer = manufacturer,
            dosageForm = dosageForm,
            route = route,
            routeSource = routeSource,
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
